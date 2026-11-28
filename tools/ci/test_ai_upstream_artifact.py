import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from ai_upstream_artifact import export, verify, replay


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        self.out = self.root / 'artifact'
        self.git('init', '-q')
        self.git('config', 'user.name', 'Test')
        self.git('config', 'user.email', 'test@example.invalid')
        self.git('config', 'core.autocrlf', 'false')
        self.base = self.save('docs/a.md', b'a\nb\n')
        self.ai = self.save('docs/a.md', b'a\r\nb\r\n')
        self.git('checkout', '-q', '--detach', self.base)
        self.up = self.save('docs/a.md', b'x\nb\n')

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args])

    def save(self, path, content):
        p = self.repo / path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(content)
        self.git('add', '--', path)
        self.git('commit', '-qm', 'change')
        return self.git('rev-parse', 'HEAD').decode().strip()

    def test_roundtrip_preserves_refs_and_staged_index(self):
        refs = self.git('show-ref')
        (self.repo / 'docs/a.md').write_bytes(b'staged\n')
        self.git('add', 'docs/a.md')
        staged = self.git('diff', '--cached', '--binary')
        m = export(self.repo, self.ai, self.up, self.out)
        self.assertEqual(m, verify(self.repo, self.ai, self.up, self.out))
        self.assertFalse(m['release_approved'])
        self.assertEqual(self.git('show', m['candidate_tree'] + ':docs/a.md'), b'x\nb\n')
        self.assertEqual(self.git('show-ref'), refs)
        self.assertEqual(self.git('diff', '--cached', '--binary'), staged)
        self.assertEqual((self.repo / 'docs/a.md').read_bytes(), b'staged\n')

    def test_modified_patch_rejected(self):
        export(self.repo, self.ai, self.up, self.out)
        p = self.out / 'candidate.patch'
        b = p.read_bytes()
        p.write_bytes(bytes([b[0] ^ 1]) + b[1:])
        with self.assertRaisesRegex(ValueError, 'digest'):
            verify(self.repo, self.ai, self.up, self.out)

    def test_forged_manifest_rejected(self):
        export(self.repo, self.ai, self.up, self.out)
        p = self.out / 'manifest.json'
        m = json.loads(p.read_text())
        m['release_approved'] = True
        p.write_text(json.dumps(m))
        with self.assertRaisesRegex(ValueError, 'Manifest'):
            verify(self.repo, self.ai, self.up, self.out)

    def test_blocked_candidate_not_exported(self):
        up = self.save('app/src/main/AndroidManifest.xml', b'manifest')
        with self.assertRaisesRegex(ValueError, 'No eligible'):
            export(self.repo, self.ai, up, self.out)
        self.assertFalse(self.out.exists())

    def test_wrong_tree_and_existing_output_rejected(self):
        export(self.repo, self.ai, self.up, self.out)
        with self.assertRaisesRegex(ValueError, 'Replayed tree'):
            replay(self.repo, self.ai, (self.out / 'candidate.patch').read_bytes(), '0' * 40)
        with self.assertRaises(FileExistsError):
            export(self.repo, self.ai, self.up, self.out)


if __name__ == '__main__':
    unittest.main()
