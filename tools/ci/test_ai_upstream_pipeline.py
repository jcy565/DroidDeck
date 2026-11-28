import subprocess
import tempfile
import unittest
from pathlib import Path

from ai_upstream_pipeline import run


class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        self.out = self.root / 'output'
        self.git('init', '-q')
        self.git('config', 'user.name', 'Test')
        self.git('config', 'user.email', 'test@example.invalid')
        self.source = 'ai-core/src/main/java/Core.java'
        self.root_commit = self.save(self.source, 'public class Core {}\n')
        self.baseline = self.save('docs/base.md', 'baseline\n')
        self.ai = self.save('docs/ai.md', 'AI\n')
        self.git('checkout', '-q', '--detach', self.baseline)

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args], text=True).strip()

    def save(self, name, data):
        p = self.repo / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(data)
        self.git('add', '--', name)
        self.git('commit', '-qm', name)
        return self.git('rev-parse', 'HEAD')

    def pipeline(self, upstream, ai=None):
        return run(self.repo, self.baseline, ai or self.ai, upstream, 'a' * 64, self.out)

    def test_valid_candidate_needs_android_regression(self):
        up = self.save(self.source, 'public class Core { int x = 1; }\n')
        refs = self.git('show-ref')
        r = self.pipeline(up)
        self.assertEqual(r['status'], 'android_regression_required')
        self.assertFalse(r['release_approved'])
        self.assertFalse(r['android_build_verified'])
        self.assertFalse(r['last_good_changed'])
        self.assertTrue((self.out / 'verified-candidate/manifest.json').exists())
        self.assertTrue((self.out / 'core-check.json').exists())
        self.assertEqual(self.git('show-ref'), refs)
        self.assertEqual(self.git('rev-parse', 'HEAD'), up)
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_compile_failure(self):
        up = self.save(self.source, 'public class Core { invalid }\n')
        self.assertEqual(self.pipeline(up)['status'], 'core_compile_failed')

    def test_sensitive_change_blocks(self):
        up = self.save('app/src/main/AndroidManifest.xml', 'manifest')
        self.assertEqual(self.pipeline(up)['status'], 'blocked')
        self.assertFalse((self.out / 'verified-candidate').exists())

    def test_unchanged_no_candidate(self):
        self.assertEqual(self.pipeline(self.baseline)['status'], 'unchanged')
        self.assertFalse((self.out / 'candidate.json').exists())

    def test_rewind_no_candidate(self):
        self.assertEqual(self.pipeline(self.root_commit)['status'], 'history_review_required')
        self.assertFalse((self.out / 'candidate.json').exists())

    def test_incomplete_ai_baseline_rejected(self):
        with self.assertRaises(ValueError):
            self.pipeline(self.baseline, self.root_commit)
        self.assertFalse(self.out.exists())


if __name__ == '__main__':
    unittest.main()
