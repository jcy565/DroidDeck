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
            curl.write_text(r'''#!/usr/bin/env bash
set -euo pipefail
while (($#)); do
    case "$1" in
        https://*) url="$1" ;;
        -o) shift; dest="$1" ;;
    esac
    shift
done
seen=0
if [[ -f "$MOCK_LOG" ]] && grep -Fxq -- "$url" "$MOCK_LOG"; then seen=1; fi
printf '%s\n' "$url" >> "$MOCK_LOG"
mirror=0
if [[ "$url" == *maven-central.storage-download.googleapis.com* ]]; then mirror=1; fi
if [[ "$url" == *.sha512 ]]; then
    (( mirror == 0 && seen == 1 )) || exit 22
    digest="$(printf '%s' 'verified fixture' | sha512sum)"
    printf '%s' "${digest%% *}" > "$dest"
else
    (( mirror == 1 )) || exit 22
    if [[ "${MOCK_BAD:-0}" == 1 ]]; then printf 'bad' > "$dest"; else printf 'verified fixture' > "$dest"; fi
fi
''')
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
            requests = log.read_text().splitlines()
            versions = [("android-all-instrumented", version) for version in (
                "9-robolectric-4913185-2-i7", "13-robolectric-9030017-i7", "14-robolectric-10818077-i7")]
            versions.append(("nativeruntime-dist-compat", "1.0.18"))
            artifacts = [f"org/robolectric/{name}/{version}/{name}-{version}.{ext}"
                         for name, version in versions for ext in ("pom", "jar")]
            primary = "https://repo.maven.apache.org/maven2/"
            secondary = "https://repo1.maven.org/maven2/"
            mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
            expected = [url for path in artifacts for url in (
                primary + path + ".sha512", secondary + path + ".sha512", primary + path + ".sha512",
                primary + path, secondary + path, mirror + path)]
            self.assertEqual(expected, requests)
            repo = root / ".m2/repository"
            for path in artifacts:
                payload = repo / path
                self.assertEqual(b"verified fixture", payload.read_bytes())
                self.assertEqual(hashlib.sha512(payload.read_bytes()).hexdigest(),
                                 payload.with_name(payload.name + ".sha512").read_text())
            files = sorted(repo.rglob("*.jar"))
            self.assertEqual(len(versions), len(files))
            self.assertEqual(0, run().returncode)
            self.assertEqual(requests, log.read_text().splitlines())
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
