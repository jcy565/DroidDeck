"""Exercise the verified prefetch with synthetic HTTP responses, not live Maven."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'ci/prepare-robolectric.sh'


class PrepareRobolectricTest(unittest.TestCase):
    def test_native_runtime_fallback_cache_and_bad_checksum(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            bin_dir = root / 'bin'
            bin_dir.mkdir()
            curl = bin_dir / 'curl'
            curl.write_text(r'''#!/usr/bin/env bash
set -euo pipefail
while (($#)); do
    case "$1" in
        https://*) url="$1" ;;
        -o) shift; dest="$1" ;;
    esac
    shift
done
printf '%s\n' "$url" >> "$HTTP_LOG"
host="${url#https://}"
host="${host%%/*}"
relative="${url#*/maven2/}"
name="${relative%.sha512}"
[[ "$host" != repo.maven.apache.org ]] || exit 22
if [[ "$relative" == *.sha512 ]]; then
    [[ "$host" == repo1.maven.org ]] || exit 99
    digest="$(printf '%s' "$name" | sha512sum)"
    printf '%s' "${digest%% *}" > "$dest"
else
    [[ "$host" != repo1.maven.org ]] || exit 22
    printf '%s' "$name" > "$dest"
    if [[ "${BAD_PAYLOAD:-0}" == 1 && "$name" == */nativeruntime-dist-compat-1.0.18.jar ]]; then
        printf 'bad' >> "$dest"
    fi
fi
''')
            curl.chmod(0o755)
            log = root / 'requests'
            env = dict(os.environ, HOME=str(root / 'home'), HTTP_LOG=str(log),
                       PATH=str(bin_dir) + os.pathsep + os.environ['PATH'])
            run = lambda **overrides: subprocess.run(['bash', str(SCRIPT)], env=dict(env, **overrides),
                                                       capture_output=True, text=True)
            result = run()
            self.assertEqual(result.returncode, 0, result.stderr)
            repo = root / 'home/.m2/repository/org/robolectric'
            self.assertEqual(len(list(repo.rglob('*.sha512'))), 8)
            for ext in ('pom', 'jar'):
                f = repo / 'nativeruntime-dist-compat/1.0.18' / f'nativeruntime-dist-compat-1.0.18.{ext}'
                self.assertEqual(hashlib.sha512(f.read_bytes()).hexdigest(),
                                 f.with_name(f.name + '.sha512').read_text())
            requests = log.read_text()
            self.assertIn('maven-central.storage-download.googleapis.com/maven2/org/robolectric/nativeruntime-dist-compat/', requests)
            self.assertNotIn('maven-central.storage-download.googleapis.com/maven2/org/robolectric/nativeruntime-dist-compat/1.0.18/nativeruntime-dist-compat-1.0.18.jar.sha512', requests)
            self.assertEqual(run().returncode, 0)
            self.assertEqual(log.read_text(), requests)
            f.write_bytes(b'corrupt cache')
            result = run(BAD_PAYLOAD='1')
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('SHA-512 verification failed', result.stderr)
            self.assertEqual(f.read_bytes(), b'corrupt cache')
            self.assertFalse(list(repo.rglob('.prefetch.*')))
            self.assertEqual(run().returncode, 0)
            self.assertEqual(hashlib.sha512(f.read_bytes()).hexdigest(),
                             f.with_name(f.name + '.sha512').read_text())
            # Refuse nonregular cache destinations rather than moving files into
            # a directory or treating a symlink as an already verified artifact.
            checksum = f.with_name(f.name + '.sha512')
            for destination in (f, checksum):
                original = destination.read_bytes()
                destination.unlink()
                destination.mkdir()
                before = log.read_text()
                result = run()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('Unsafe Robolectric cache destination', result.stderr)
                self.assertEqual(list(destination.iterdir()), [])
                self.assertEqual(log.read_text(), before)
                destination.rmdir()
                target = root / 'external'
                target.write_bytes(original)
                destination.symlink_to(target)
                result = run()
                self.assertNotEqual(result.returncode, 0)
                self.assertTrue(destination.is_symlink())
                self.assertEqual(target.read_bytes(), original)
                self.assertEqual(log.read_text(), before)
                destination.unlink()
                destination.write_bytes(original)
            self.assertEqual(run().returncode, 0)
