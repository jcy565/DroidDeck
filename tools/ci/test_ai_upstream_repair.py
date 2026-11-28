import subprocess
import tempfile
import unittest
from pathlib import Path

from ai_upstream_repair import choose, read_blob, propose


class RepairTests(unittest.TestCase):
    def test_ours_only_eol(self):
        self.assertEqual(choose(b'a\nb\n', b'a\r\nb\r\n', b'x\nb\n'), 'theirs')

    def test_theirs_only_eol(self):
        self.assertEqual(choose(b'a\n', b'x\n', b'a\r\n'), 'ours')

    def test_equal_content(self):
        self.assertEqual(choose(b'a\n', b'x\r\n', b'x\n'), 'ours')

    def test_real_conflict_not_repaired(self):
        self.assertIsNone(choose(b'a\n', b'x\n', b'y\n'))

    def test_whitespace_not_ignored(self):
        self.assertIsNone(choose(b'a\n', b'a  \n', b'b\n'))

    def test_integration(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            def git(*args):
                return subprocess.check_output(['git', '-C', tmp, *args]).decode().strip()
            git('init', '-q')
            git('config', 'user.name', 'Test')
            git('config', 'user.email', 'test@example.invalid')
            git('config', 'core.autocrlf', 'false')
            def save(data):
                (repo / 'docs').mkdir(exist_ok=True)
                (repo / 'docs/a.md').write_bytes(data)
                git('add', '.')
                git('commit', '-qm', 'change')
                return git('rev-parse', 'HEAD')
            base = save(b'a\nb\n')
            ai = save(b'a\r\nb\r\n')
            git('checkout', '-q', '--detach', base)
            upstream = save(b'x\nb\n')
            before = git('show-ref')
            report = propose(repo, ai, upstream)
            self.assertEqual(report['conflicts'], ['docs/a.md'])
            self.assertEqual(report['repair_suggestions'][0]['selected_side'], 'theirs')
            self.assertFalse(report['repair_applied'])
            self.assertFalse(report['release_approved'])
            self.assertEqual(git('show-ref'), before)
            self.assertEqual(git('status', '--porcelain'), '')
            self.assertEqual(git('rev-parse', 'HEAD'), upstream)
            self.assertIsNone(read_blob(repo, upstream, 'missing'))


if __name__ == '__main__':
    unittest.main()
