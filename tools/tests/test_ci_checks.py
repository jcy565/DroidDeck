import itertools
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[2]


class CiChecksTest(unittest.TestCase):
    def test_pr_cache_writes_and_source_timestamp_policy(self):
        jobs = yaml.safe_load((ROOT / '.github/workflows/build.yml').read_text())['jobs']
        cache = next(step for step in jobs['build']['steps']
                     if step.get('uses') == 'gradle/actions/setup-gradle@v4')['with']
        self.assertEqual(cache['cache-read-only'],
                         "${{ github.event_name == 'pull_request' && github.event.pull_request.head.repo.full_name != github.repository }}")
        gradle = (ROOT / 'app/build.gradle').read_text()
        self.assertIn("gitValue(['show', '-s', '--format=%ct', '--end-of-options', sourceCommit])", gradle)

    def test_report_comment_lookup_ignores_human_marker(self):
        if not shutil.which('jq'):
            self.skipTest('jq required to execute comment selection')
        script = (ROOT / 'tools/release/report-build.sh').read_text()
        line = next(line for line in script.splitlines() if line.startswith('ID='))
        arguments = shlex.split(line[len('ID=$('):].split(' | head', 1)[0])
        query = arguments[arguments.index('--jq') + 1].replace('$MARK', '<!-- build -->')
        comments = [dict(id=1, user=dict(login='someone'), body='<!-- build --> forged'),
                    dict(id=2, user=dict(login='github-actions[bot]'), body='<!-- build --> result')]
        result = subprocess.check_output(['jq', '-r', query], input=json.dumps(comments), text=True)
        self.assertEqual(result.strip(), '2')

    def test_build_and_tests_use_identical_generated_metadata(self):
        jobs = yaml.safe_load((ROOT / '.github/workflows/build.yml').read_text())['jobs']
        steps = {step.get('id'): step for step in jobs['build']['steps']}
        self.assertEqual(steps['build']['env'], steps['unit-tests']['env'])
        self.assertIn('DROIDDECK_BUILD_LABEL', steps['unit-tests']['env'])
        self.assertIn('SGDB_API_KEY', steps['unit-tests']['env'])
        gradle = (ROOT / 'app/build.gradle').read_text()
        self.assertIn("outputs.doNotCacheIf('CI executes the test suite on every run') { true }", gradle)
        self.assertIn('outputs.upToDateWhen { false }', gradle)

    def test_ocr_variant_is_shared_by_build_tests_and_isolated_diagnostics(self):
        build = yaml.safe_load((ROOT / '.github/workflows/build.yml').read_text())['jobs']['build']
        flag = 'ORG_GRADLE_PROJECT_experimentalImageOcr'
        self.assertEqual(build['env'][flag],
                         "${{ contains(fromJSON('[\"ai-chatgpt-exchange-next\",\"ai-png-compose-integration-next\",\"ai-integrated-ci-diagnostic-next\",\"ai-shared-reply-review-next\"]'), github.ref_name) && 'true' || 'false' }}")
        steps = {step.get('id'): step for step in build['steps']}
        for name in ('build', 'unit-tests', 'compose-idle-diagnostics'):
            self.assertNotIn(flag, steps[name].get('env', {}))
            self.assertNotIn('experimentalImageOcr', steps[name]['run'])

    def test_independent_jobs_wait_only_for_the_pinned_source(self):
        jobs = yaml.safe_load((ROOT / '.github/workflows/build.yml').read_text())['jobs']
        for name in ('xbox', 'python-tests', 'components-runner'):
            self.assertEqual(jobs[name]['needs'], 'source')
        self.assertEqual(set(jobs['components']['needs']), {'source', 'components-runner'})
        self.assertEqual(jobs['build-runner']['needs'], ['components'])
        self.assertEqual(set(jobs['build']['needs']), {'source', 'components', 'xbox', 'build-runner'})

    def test_dependency_update_errors_block_installation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sudo = root / 'sudo'
            sudo.write_text('#!/bin/bash\nprintf "%s\\n" "$*" >> "$APT_LOG"\nexit "${APT_RESULT:-0}"\n')
            sudo.chmod(0o755)
            query = root / 'dpkg-query'
            query.write_text('#!/bin/sh\nexit 1\n')
            query.chmod(0o755)
            log = root / 'apt.log'
            sources = root / 'ubuntu.sources'
            sources.write_text('URIs: http://mirror.invalid/ubuntu\n')
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'], APT_LOG=str(log),
                       UBUNTU_SOURCES_FILE=str(sources))
            installer = ROOT / 'tools/ci/install-ubuntu-packages.sh'
            result = subprocess.run(['bash', str(installer), 'qemu-user'], env=dict(env, APT_RESULT='17'))
            self.assertEqual(result.returncode, 17)
            self.assertEqual(len(log.read_text().splitlines()), 2)
            log.unlink()
            subprocess.run(['bash', str(installer), 'qemu-user'], env=env, check=True)
            update, install = log.read_text().splitlines()
            for command in (update, install):
                self.assertIn('Dir::Etc::sourceparts=-', command)
                self.assertIn(f'Dir::Etc::sourcelist={sources}', command)
                self.assertIn('Acquire::https::Timeout=15', command)
                self.assertIn('Acquire::Retries=1', command)
            self.assertIn('APT::Update::Error-Mode=any', update)
            self.assertIn('install -y -q --no-install-recommends qemu-user', install)

            log.unlink()
            absent = dict(env, UBUNTU_SOURCES_FILE=str(root / 'missing.sources'), APT_RESULT='17')
            result = subprocess.run(['bash', str(installer), 'qemu-user'], env=absent)
            self.assertEqual(result.returncode, 1)
            self.assertEqual(len(log.read_text().splitlines()), 1)
            self.assertNotIn('Dir::Etc::sourceparts', log.read_text())

    def test_required_status_rejects_failed_cancelled_or_skipped_test_jobs(self):
        jobs = yaml.safe_load((ROOT / '.github/workflows/build.yml').read_text())['jobs']
        gate = jobs['checks']
        self.assertEqual(gate['name'], 'Compile and test APK')
        self.assertEqual(set(gate['needs']), {'build', 'python-tests'})
        self.assertIn('!cancelled()', gate['if'])
        for build, python in itertools.product(('success', 'failure', 'cancelled', 'skipped'), repeat=2):
            with self.subTest(build=build, python=python):
                result = subprocess.run(
                    ['bash', '-c', gate['steps'][0]['run']],
                    env=dict(os.environ, BUILD_RESULT=build, PYTHON_RESULT=python),
                    capture_output=True)
                self.assertEqual(result.returncode == 0, build == python == 'success')
        for publisher in ('sign', 'packs'):
            self.assertIn('checks', jobs[publisher]['needs'])

    def test_compose_diagnostics_do_not_replace_full_suite_gate(self):
        jobs = yaml.safe_load((ROOT / '.github/workflows/build.yml').read_text())['jobs']
        steps = jobs['build']['steps']
        ids = {step.get('id'): step for step in steps}
        diag = ids['compose-idle-diagnostics']
        self.assertIn('failure()', diag['if'])
        self.assertIn("steps.unit-tests.outcome == 'failure'", diag['if'])
        self.assertIn("steps.android-test-summary.outputs.run_compose_diagnostics != 'false'", diag['if'])
        summary = ids['android-test-summary']
        self.assertIn('--github-output "$GITHUB_OUTPUT"', summary['run'])
        self.assertIn('rm -rf app/build/test-results/testReleaseUnitTest', ids['unit-tests']['run'])
        self.assertLess(steps.index(ids['unit-tests']), steps.index(summary))
        self.assertLess(steps.index(summary), steps.index(diag))
        self.assertEqual(diag['env'], ids['unit-tests']['env'])
        self.assertNotIn('continue-on-error', ids['unit-tests'])
        self.assertLess(steps.index(ids['unit-test-diagnostics']), steps.index(diag))
        self.assertLess(steps.index(diag), steps.index(ids['apk-upload']))
        self.assertNotIn('continue-on-error', jobs['build'])
        self.assertIn('checks', jobs['sign']['needs'])

    def test_isolated_compose_diagnostics_preserve_each_exit_code(self):
        script = ROOT / 'tools/ci/diagnose-compose-idle.sh'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            gradle = root / 'gradlew'
            gradle.write_text("""#!/usr/bin/env bash
set -eu
[[ "$2" == --tests ]]
mkdir -p app/build/test-results/testReleaseUnitTest
printf '%s\\n' "$3" > app/build/test-results/testReleaseUnitTest/test.xml
if [[ "$3" == *.BottomScreenBarTest ]]; then
    # Subset probes also start with BottomScreenBarTest. Preserve only the
    # initial five-class invocation rather than replacing it with a subset.
    if [[ ! -e group-arguments ]]; then
        printf '%s\\n' "$@" > group-arguments
    fi
    exit 7
fi
case "$3" in
    *HeadingLocaleTest*) exit 1 ;;
    *ModeHeaderFocusTest*) exit 0 ;;
    com.droiddeck.launcher.ui.*) exit 0 ;;
    *) exit 99 ;;
esac
""")
            gradle.chmod(0o755)
            subprocess.run(['bash', str(script)], cwd=root,
                           env=dict(os.environ, NDK_VERSION='test'), check=True)
            output = root / 'app/build/compose-idle-diagnostics'
            self.assertEqual((output / 'group-summary.tsv').read_text().splitlines(),
                             ['test\texit_code', 'component-navigation\t7'])
            arguments = (root / 'group-arguments').read_text().splitlines()
            grouped = [arguments[i + 1] for i, arg in enumerate(arguments) if arg == '--tests']
            self.assertEqual(grouped, ['com.droiddeck.launcher.ui.' + name for name in (
                'BottomScreenBarTest', 'BottomScreenTypingTest', 'CloudMessageTest',
                'ComponentDownloadsDrawerTest', 'ComponentsPageProfileTest')])
            self.assertTrue((output / 'component-navigation/results/test.xml').is_file())
            rows = (output / 'summary.tsv').read_text().splitlines()
            self.assertEqual(rows[0], 'test\\texit_code'.replace('\\t', '\t'))
            expected = [
                'com.droiddeck.launcher.ui.ComponentDownloadsDrawerTest.controllerPicksNightlyOnItsTabThenDownloadsItFromTheRow',
                'com.droiddeck.launcher.ui.ComponentsPageProfileTest.aNewNavigationTargetReplacesThePreviousProfile',
                'com.droiddeck.launcher.ui.FlathubFocusTest.openingAnAppReleasesTheCoveredNativeSearchField',
                'com.droiddeck.launcher.ui.FocusGlideWindowsTest.dropdownItemsShareTheirOwnWindowGlide',
                'com.droiddeck.launcher.ui.GameEnvironmentEditorTest.backgroundSelectionCannotRetargetAnOpenVariableEdit',
                'com.droiddeck.launcher.ui.GlassesCalibrationControlsTest.startRequiresInstructionsAndDoneRecentersAfterSuccessfulCapture',
                'com.droiddeck.launcher.ui.GlassesPanelTest.ultraControlsDisappearOnDisconnectAndForOtherGlassesWithSavedPreferences',
                'com.droiddeck.launcher.ui.GlassesPanelTest.spatialSteamVrIncludesHeadTrackingAndRestoresTheSavedVirtualScreenSwitch',
                'com.droiddeck.launcher.ui.HeadingLocaleTest.headingsFollowTheAppLanguageAndRecomposeWhenItChanges',
                'com.droiddeck.launcher.ui.ModeHeaderFocusTest.clickingSearchIconFocusesField',
                'com.droiddeck.launcher.ui.ModeHeaderFocusTest.reenteringCompositionWithNonEmptyQueryDoesNotStealFocus',
            ]
            self.assertEqual([line.split('\t')[0] for line in rows[1:]], expected)
            self.assertEqual([line.rsplit('\t', 1)[1] for line in rows[1:]],
                             ['1' if 'HeadingLocaleTest' in name else '0' for name in expected])
            for row in rows[1:]:
                method = row.split('\t')[0]
                dest = output / method.rsplit('.', 1)[1]
                self.assertTrue((dest / 'run.log').is_file())
                self.assertEqual((dest / 'results/test.xml').read_text().strip(), method)

    def test_staging_build_is_explicit_and_isolated_from_original(self):
        workflow = yaml.safe_load((ROOT / '.github/workflows/build.yml').read_text())
        # PyYAML YAML 1.1 treats the unquoted GitHub Actions "on" key as True.
        triggers = workflow.get('on', workflow.get(True))
        self.assertIn('ai-recovery-review-next', triggers['push']['branches'])
        self.assertIn('ai-png-compose-integration-next', triggers['push']['branches'])
        self.assertIn('ai-integrated-ci-diagnostic-next', triggers['push']['branches'])
        self.assertIn('ai-shared-reply-review-next', triggers['push']['branches'])
        gate = workflow['jobs']['source']['if']
        self.assertIn('ai-compatibility-history', gate)
        self.assertIn('ai-recovery-review-next', gate)
        self.assertIn('ai-png-compose-integration-next', gate)
        self.assertIn('ai-integrated-ci-diagnostic-next', gate)
        self.assertIn('ai-shared-reply-review-next', gate)
        self.assertIn("contains(github.event.head_commit.message, '[build apk]')", gate)
        self.assertIn('github.ref_name', workflow['concurrency']['group'])
        self.assertIn("github.event_name == 'push'", workflow['concurrency']['group'])
        steps = workflow['jobs']['build']['steps']
        diagnostic = next(s for s in steps if s.get('name') == 'Retain checked APK for failed test investigation')
        self.assertIn('ai-recovery-review-next', diagnostic['if'])
        self.assertIn('ai-png-compose-integration-next', diagnostic['if'])
        self.assertIn('ai-integrated-ci-diagnostic-next', diagnostic['if'])
        self.assertIn('ai-shared-reply-review-next', diagnostic['if'])
        self.assertIn("steps.unit-tests.outcome == 'failure'", diagnostic['if'])
        self.assertNotIn('continue-on-error', next(s for s in steps if s.get('id') == 'unit-tests'))
        for name in ('sign', 'packs'):
            self.assertIn("refs/heads/main", workflow['jobs'][name]['if'])

    def test_ai_ci_worker_recycling_keeps_unfiltered_test_gate(self):
        gradle = (ROOT / 'app/build.gradle').read_text()
        self.assertRegex(
            gradle,
            r"if \(ciBuild && providers\.environmentVariable\('GITHUB_REF_NAME'\)"
            r"\.getOrNull\(\)\?\.startsWith\('ai-'\)\) \{\s*"
            r"(?://[^\n]*\n\s*)*forkEvery = providers\.environmentVariable"
            r"\('GITHUB_REF_NAME'\)\.getOrNull\(\) == 'ai-version-integration-next'"
            r" \? 5L : 10L\s*\}",
        )
        self.assertEqual(1, gradle.count("forkEvery ="))
        jobs = yaml.safe_load((ROOT / '.github/workflows/build.yml').read_text())['jobs']
        step = next(s for s in jobs['build']['steps'] if s.get('id') == 'unit-tests')
        self.assertIn('testReleaseUnitTest', step['run'])
        self.assertNotIn('--tests', step['run'])
        self.assertNotIn('continue-on-error', step)
