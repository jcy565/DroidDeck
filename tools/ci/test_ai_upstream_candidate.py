import subprocess
import tempfile
import unittest
from pathlib import Path

from ai_upstream_candidate import candidate, category


class CandidateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Test')
        self.git('config', 'user.email', 'test@example.invalid')
        self.base = self.save('docs/base.md', 'base')
        self.ai = self.save('app/src/main/java/Ai.java', 'ai')

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args], text=True).strip()

    def save(self, name, text):
        p = self.repo / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text)
        self.git('add', '--', name)
        self.git('commit', '-qm', name)
        return self.git('rev-parse', 'HEAD')

    def upstream(self, name, value, start=None):
        self.git('checkout', '-q', '--detach', start or self.base)
        return self.save(name, value)

    def test_clean_candidate_preserves_refs_and_worktree(self):
        up = self.upstream('app/src/main/java/Engine.java', 'new engine')
        refs = self.git('show-ref')
        r = candidate(self.repo, self.ai, up)
        self.assertEqual(r['candidate_status'], 'regression_required')
        self.assertFalse(r['release_approved'])
        self.assertEqual(self.git('show', r['candidate_tree'] + ':app/src/main/java/Ai.java'), 'ai')
        self.assertEqual(self.git('show', r['candidate_tree'] + ':app/src/main/java/Engine.java'), 'new engine')
        self.assertEqual(self.git('show-ref'), refs)
        self.assertEqual(self.git('rev-parse', 'HEAD'), up)
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_conflict_blocks(self):
        up = self.upstream('app/src/main/java/Ai.java', 'different')
        r = candidate(self.repo, self.ai, up)
        self.assertEqual(r['candidate_status'], 'blocked')
        self.assertIsNone(r['candidate_tree'])

    def test_manifest_blocks(self):
        up = self.upstream('app/src/main/AndroidManifest.xml', 'manifest')
        self.assertEqual(candidate(self.repo, self.ai, up)['candidate_status'], 'blocked')

    def test_unknown_binary_blocks(self):
        up = self.upstream('assets/new.bin', 'data')
        self.assertEqual(candidate(self.repo, self.ai, up)['candidate_status'], 'blocked')

    def test_no_change(self):
        self.assertEqual(candidate(self.repo, self.ai, self.base)['candidate_status'], 'no_change')

    def test_classification(self):
        self.assertEqual(category('docs/readme.md'), 'documentation')
        self.assertEqual(category('app/src/main/java/App.kt'), 'manual_review')
        self.assertEqual(category('.github/workflows/x.yml'), 'manual_review')


if __name__ == '__main__':
    unittest.main()
