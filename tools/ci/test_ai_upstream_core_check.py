import subprocess
import tempfile
import unittest
from pathlib import Path

from ai_upstream_artifact import export
from ai_upstream_core_check import check, compile_tree


class CoreCheckTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        self.git('init', '-q')
        self.git('config', 'user.name', 'Test')
        self.git('config', 'user.email', 'test@example.invalid')
        self.source = 'ai-core/src/main/java/Core.java'
        self.base = self.save(self.source, 'public class Core {}\n')
        self.ai = self.save('docs/ai.md', 'AI\n')
        self.git('checkout', '-q', '--detach', self.base)

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args]).decode().strip()

    def save(self, name, data):
        p = self.repo / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(data)
        self.git('add', '--', name)
        self.git('commit', '-qm', 'change')
        return self.git('rev-parse', 'HEAD')

    def test_verified_candidate_compiles_without_modifying_checkout(self):
        up = self.save(self.source, 'public class Core { int x = 1; }\n')
        out = self.root / 'artifact'
        export(self.repo, self.ai, up, out)
        before = self.git('show-ref')
        r = check(self.repo, self.ai, up, out)
        self.assertEqual(r['compile_status'], 'passed')
        self.assertFalse(r['android_build_verified'])
        self.assertFalse(r['release_approved'])
        self.assertEqual(self.git('show-ref'), before)
        self.assertEqual(self.git('rev-parse', 'HEAD'), up)
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_compiler_failure_not_passed(self):
        up = self.save(self.source, 'public class Core { invalid }\n')
        out = self.root / 'artifact'
        export(self.repo, self.ai, up, out)
        self.assertEqual(check(self.repo, self.ai, up, out)['compile_status'], 'failed')

    def test_tampered_artifact_rejected(self):
        up = self.save(self.source, 'public class Core { int x = 1; }\n')
        out = self.root / 'artifact'
        export(self.repo, self.ai, up, out)
        (out / 'candidate.patch').write_bytes(b'bad')
        with self.assertRaises(ValueError):
            check(self.repo, self.ai, up, out)

    def test_missing_sources_rejected(self):
        self.git('rm', self.source)
        self.git('commit', '-qm', 'delete')
        with self.assertRaisesRegex(ValueError, 'No Java'):
            compile_tree(self.repo, self.git('rev-parse', 'HEAD'))


if __name__ == '__main__':
    unittest.main()
