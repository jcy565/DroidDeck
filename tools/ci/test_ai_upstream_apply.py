import subprocess
import tempfile
import unittest
from pathlib import Path

from ai_upstream_apply import apply_candidate


class ApplyTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Test')
        self.git('config', 'user.email', 'test@example.invalid')
        self.git('config', 'core.autocrlf', 'false')
        self.base = self.save({'docs/a.md': b'a\nb\n'})
        self.ai = self.save({'docs/a.md': b'a\r\nb\r\n', 'docs/ai.md': b'ai\n'})

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args])

    def save(self, files):
        for name, data in files.items():
            p = self.repo / name
            p.parent.mkdir(exist_ok=True, parents=True)
            p.write_bytes(data)
        self.git('add', '.')
        self.git('commit', '-qm', 'change')
        return self.git('rev-parse', 'HEAD').decode().strip()

    def upstream(self, files):
        self.git('checkout', '-q', '--detach', self.base)
        return self.save(files)

    def test_repair_preserves_both_sides_and_index(self):
        up = self.upstream({'docs/a.md': b'x\nb\n', 'docs/engine.md': b'engine\n'})
        refs = self.git('show-ref')
        head = self.git('rev-parse', 'HEAD')
        # A staged unrelated edit must not be overwritten by the disposable index.
        (self.repo / 'docs/engine.md').write_bytes(b'staged\n')
        self.git('add', 'docs/engine.md')
        staged = self.git('diff', '--cached', '--binary')
        r = apply_candidate(self.repo, self.ai, up)
        self.assertTrue(r['repair_applied_to_candidate'])
        self.assertFalse(r['release_approved'])
        self.assertEqual(r['candidate_status'], 'regression_required')
        tree = r['candidate_tree']
        self.assertEqual(self.git('show', tree + ':docs/a.md'), b'x\nb\n')
        self.assertEqual(self.git('show', tree + ':docs/ai.md'), b'ai\n')
        self.assertEqual(self.git('show', tree + ':docs/engine.md'), b'engine\n')
        self.assertEqual(self.git('show-ref'), refs)
        self.assertEqual(self.git('rev-parse', 'HEAD'), head)
        self.assertEqual(self.git('diff', '--cached', '--binary'), staged)
        self.assertEqual((self.repo / 'docs/engine.md').read_bytes(), b'staged\n')

    def test_manual_review_blocks_even_repairable_conflict(self):
        up = self.upstream({'docs/a.md': b'x\nb\n', 'app/src/main/AndroidManifest.xml': b'manifest'})
        r = apply_candidate(self.repo, self.ai, up)
        self.assertEqual(r['candidate_status'], 'blocked')
        self.assertIsNone(r['candidate_tree'])
        self.assertFalse(r['repair_applied_to_candidate'])

    def test_unresolved_conflict_blocks(self):
        ai = self.save({'docs/a.md': b'ours\n'})
        up = self.upstream({'docs/a.md': b'theirs\n'})
        r = apply_candidate(self.repo, ai, up)
        self.assertEqual(r['candidate_status'], 'blocked')
        self.assertIsNone(r['candidate_tree'])

    def test_clean_candidate(self):
        up = self.upstream({'docs/engine.md': b'engine\n'})
        r = apply_candidate(self.repo, self.ai, up)
        self.assertEqual(r['candidate_status'], 'regression_required')
        self.assertFalse(r['repair_applied_to_candidate'])

    def test_no_change(self):
        r = apply_candidate(self.repo, self.ai, self.base)
        self.assertEqual(r['candidate_status'], 'no_change')
        self.assertIsNone(r['candidate_tree'])


if __name__ == '__main__':
    unittest.main()
