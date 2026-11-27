import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/ci/prepare-robolectric.sh"


class RobolectricPrefetchTest(unittest.TestCase):
    def test_retry_cache_and_checksum_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            curl = bin_dir / "curl"
            curl.write_text("""#!/usr/bin/env python3
import hashlib, os, pathlib, sys
args = sys.argv[1:]
url = next(a for a in args if a.startswith('https://'))
out = pathlib.Path(args[args.index('-o') + 1])
log = pathlib.Path(os.environ['MOCK_LOG'])
seen = log.read_text().splitlines() if log.exists() else []
with log.open('a') as f:
    f.write(url + '\\n')
if url not in seen:
    sys.exit(22)
payload = b'verified fixture'
if url.endswith('.sha512'):
    out.write_text(hashlib.sha512(payload).hexdigest())
else:
    out.write_bytes(b'bad' if os.environ.get('MOCK_BAD') == '1' else payload)
""")
            curl.chmod(0o755)
            sleep = bin_dir / "sleep"
            sleep.write_text("#!/bin/sh\nexit 0\n")
            sleep.chmod(0o755)
            log = root / "log"
            env = dict(os.environ, HOME=str(root), MOCK_LOG=str(log),
                       PATH=str(bin_dir) + os.pathsep + os.environ["PATH"])
            run = lambda **kw: subprocess.run(
                ["bash", str(SCRIPT)], env=dict(env, **kw),
                capture_output=True, text=True, timeout=30)
            first = run()
            self.assertEqual(first.returncode, 0, first.stderr)
            self.assertEqual(36, len(log.read_text().splitlines()))
            files = list((root / ".m2").rglob("*.jar"))
            self.assertEqual(3, len(files))
            self.assertEqual(0, run().returncode)
            self.assertEqual(36, len(log.read_text().splitlines()))
            files[0].write_bytes(b"corrupted cached file")
            failed = run(MOCK_BAD="1")
            self.assertNotEqual(0, failed.returncode)
            self.assertIn("SHA-512 verification failed", failed.stderr)
            self.assertEqual(b"corrupted cached file", files[0].read_bytes())
            self.assertFalse(list((root / ".m2").rglob(".prefetch.*")))
            self.assertEqual(0, run().returncode)
            self.assertEqual(b"verified fixture", files[0].read_bytes())


if __name__ == "__main__":
    unittest.main()
