"""Regression checks for diagnostic orchestration, not Android UI tests."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "ci" / "diagnose-compose-idle.sh"


class DiagnoseComposeIdleTest(unittest.TestCase):
    def test_class_runs_are_separate_and_stale_results_are_removed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            gradle = root / "gradlew"
            gradle.write_text("""#!/usr/bin/env bash
set -eu
[[ "$1" == testReleaseUnitTest && "$2" == --tests ]] || exit 99
[[ ! -d app/build/test-results/testReleaseUnitTest ]] || exit 98
printf '%s\\n' "$3" >> calls.txt
case "$3" in
  *.ComponentDownloadsDrawerTest) exit 7 ;;
  *.aNewNavigationTargetReplacesThePreviousProfile) exit 4 ;;
esac
mkdir -p app/build/test-results/testReleaseUnitTest
printf '%s' "$3" > app/build/test-results/testReleaseUnitTest/TEST.xml
""")
            gradle.chmod(0o755)
            out = root / "app/build/compose-idle-diagnostics"
            stale = out / "class-ComponentDownloadsDrawerTest/results"
            stale.mkdir(parents=True)
            (stale / "TEST.xml").write_text("stale")
            subprocess.run(
                ["bash", str(SCRIPT)], cwd=root,
                env={**os.environ, "NDK_VERSION": "mock"},
                check=True, capture_output=True, text=True,
            )
            methods = (out / "summary.tsv").read_text().splitlines()
            classes = (out / "class-summary.tsv").read_text().splitlines()
            self.assertEqual(12, len(methods))
            self.assertEqual(3, len(classes))
            self.assertEqual("test\texit_code", methods[0])
            self.assertEqual(methods[0], classes[0])
            group = (out / "group-summary.tsv").read_text().splitlines()
            self.assertEqual([methods[0], "component-navigation\t0"], group)
            first_group_selector = "com.droiddeck.launcher.ui.BottomScreenBarTest"
            self.assertEqual([methods[0], "component-pair\t7"],
                             (out / "pair-summary.tsv").read_text().splitlines())
            self.assertFalse((out / "component-pair/results").exists())
            expected_calls = [first_group_selector, "com.droiddeck.launcher.ui.ComponentDownloadsDrawerTest"] + [
                row.split("\t")[0] for row in methods[1:] + classes[1:]
            ]
            self.assertEqual(expected_calls, (root / "calls.txt").read_text().splitlines())
            self.assertEqual(first_group_selector,
                             (out / "component-navigation/results/TEST.xml").read_text())
            self.assertTrue(methods[2].endswith("\t4"))
            self.assertTrue(classes[1].endswith("\t7"))
            self.assertTrue(classes[2].endswith("\t0"))
            self.assertFalse(stale.exists())
            self.assertFalse((out / "aNewNavigationTargetReplacesThePreviousProfile/results").exists())
            self.assertEqual(
                "com.droiddeck.launcher.ui.ComponentsPageProfileTest",
                (out / "class-ComponentsPageProfileTest/results/TEST.xml").read_text(),
            )
            for row in methods[1:]:
                method = row.split("\t")[0].rsplit(".", 1)[-1]
                self.assertTrue((out / method / "run.log").exists())


if __name__ == "__main__":
    unittest.main()
