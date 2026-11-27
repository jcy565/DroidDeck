import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'tools/components/recipes/debian-image.sh'
PAYLOAD = b'fixed image fixture'


class DebianImageFallbackTest(unittest.TestCase):
    def run_recipe(self, mode, repo='fork/DroidDeck', checksum=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'tools/linuxfs').mkdir(parents=True)
            (root / 'tools/linuxfs/debian-image.env').write_text(
                'DEBIAN_IMAGE_TAG=fixture\nDEBIAN_IMAGE_ASSET=image.tar.zst\n'
                'DEBIAN_IMAGE_SHA256=' + (checksum if checksum is not None else hashlib.sha256(PAYLOAD).hexdigest()) + '\n')
            bin_dir = root / 'bin'
            bin_dir.mkdir()
            mock = '''#!/usr/bin/env python3
import os, sys
from pathlib import Path
name = Path(sys.argv[0]).name
args = sys.argv[1:]
mode = os.environ['MOCK_MODE']
with open(os.environ['MOCK_LOG'], 'a') as log:
    log.write(name + ' ' + ' '.join(args) + '\\n')
if name == 'gh':
    repo = args[args.index('-R') + 1]
    if mode == 'missing' or (mode in ('upstream', 'bad') and repo != 'Droid-Deck/DroidDeck'):
        sys.exit(1)
    Path(args[args.index('-O') + 1]).write_bytes(b'corrupt' if mode == 'bad' else b'fixed image fixture')
elif name == 'zstd':
    sys.stdout.buffer.write(Path(args[-1]).read_bytes())
elif name == 'docker' and args[0] == 'load':
    assert sys.stdin.buffer.read() == b'fixed image fixture'
'''
            for name in ('gh', 'zstd', 'docker', 'sleep'):
                path = bin_dir / name
                path.write_text(mock)
                path.chmod(0o755)
            log = root / 'log'
            env = dict(os.environ, GITHUB_REPOSITORY=repo, MOCK_MODE=mode,
                       MOCK_LOG=str(log), PATH=str(bin_dir) + os.pathsep + os.environ['PATH'])
            result = subprocess.run(['bash', str(SCRIPT)], cwd=root, env=env,
                                    capture_output=True, text=True, timeout=30)
            return result, log.read_text().splitlines() if log.exists() else []

    def test_fork_missing_uses_verified_upstream(self):
        result, log = self.run_recipe('upstream')
        self.assertEqual(0, result.returncode, result.stderr)
        requests = [line for line in log if line.startswith('gh ')]
        self.assertEqual(2, len(requests))
        self.assertIn('-R fork/DroidDeck', requests[0])
        self.assertIn('-R Droid-Deck/DroidDeck', requests[1])
        self.assertIn('docker load', log)
        self.assertFalse(any(line.startswith('docker pull') for line in log))

    def test_local_release_and_upstream_deduplication(self):
        for repo in ('fork/DroidDeck', 'Droid-Deck/DroidDeck'):
            result, log = self.run_recipe('local', repo=repo)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(1, sum(line.startswith('gh ') for line in log))
            self.assertIn('docker load', log)

    def test_corrupt_download_and_invalid_pin_fail_closed(self):
        result, log = self.run_recipe('bad')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(line.startswith('docker ') for line in log))
        result, log = self.run_recipe('local', checksum='')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], log)

    def test_missing_releases_preserve_registry_fallback(self):
        result, log = self.run_recipe('missing')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, sum(line.startswith('gh ') for line in log))
        self.assertIn('docker pull public.ecr.aws/docker/library/debian:bullseye', log)
        self.assertNotIn('docker load', log)


if __name__ == '__main__':
    unittest.main()
