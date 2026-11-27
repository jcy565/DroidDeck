"""Host tests for release guard; mocked Android tools, no real APK/certificate validation."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).with_name('sign-ai-apk.sh')
TEST = 'a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc'
OFFICIAL = 'b241ea7d77822d39b89d3227e06f3ecf62adec6cbfd38ad06196787991b5d3f0'
RELEASE = '1' * 64


class SigningGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.dir = Path(self.temp.name)
        self.src = self.dir / 'reviewed.apk'
        self.out = self.dir / 'release.apk'
        self.key = self.dir / 'placeholder-key'
        self.key.write_text('not a private key')
        with zipfile.ZipFile(self.src, 'w') as apk:
            apk.writestr('AndroidManifest.xml', b'manifest')
            apk.writestr('classes.dex', b'code')
        self.sha = hashlib.sha256(self.src.read_bytes()).hexdigest()
        self.env = dict(os.environ, PATH=f'{self.dir}:' + os.environ['PATH'], MODE='ok',
                        TEST_CERT=TEST, RELEASE_CERT=RELEASE)
        self.tool('aapt', '''#!/bin/sh
case "$MODE" in
wrong-package) echo 'package: com.droiddeck.launcher';;
*) echo 'package: com.droiddeck.launcher.ai';;
esac
''')
        self.tool('apksigner', '''#!/usr/bin/env python3
import os, sys, shutil, zipfile
from pathlib import Path
args = sys.argv[1:]
mode = os.environ['MODE']
if args[0] == 'verify':
    signed = Path(args[-1]).name == 'signed.apk'
    cert = os.environ['RELEASE_CERT'] if signed else os.environ['TEST_CERT']
    if mode == 'bad-input' and not signed or mode == 'bad-output' and signed:
        cert = '2' * 64
    print('Signer #1 certificate SHA-256 digest: ' + cert)
    if mode == 'multiple' or mode == 'multiple-output' and signed:
        print('Signer #2 certificate SHA-256 digest: ' + '3' * 64)
    if mode == 'verify-failure': sys.exit(1)
else:
    target = args[args.index('--out') + 1]
    shutil.copyfile(args[-1], target)
    if mode == 'changed':
        with zipfile.ZipFile(target, 'a') as apk: apk.writestr('added.dex', b'other code')
    if mode == 'duplicate':
        with zipfile.ZipFile(target, 'a') as apk: apk.writestr('classes.dex', b'other code')
    if mode == 'metadata':
        with zipfile.ZipFile(target, 'a') as apk: apk.writestr('META-INF/KEY.RSA', b'signature')
    if mode == 'sign-failure': sys.exit(1)
''')

    def tool(self, name, script):
        file = self.dir / name
        file.write_text(script)
        file.chmod(0o700)

    def invoke(self, mode='ok', sha=None, cert=RELEASE):
        return subprocess.run(['bash', str(SCRIPT), str(self.src), str(self.out), str(self.key),
                               'ai', self.sha if sha is None else sha, cert],
                              env=dict(self.env, MODE=mode), capture_output=True, text=True)

    def test_reviewed_payload(self):
        self.assertEqual(self.invoke('metadata').returncode, 0)
        self.assertTrue(self.out.is_file())
        with zipfile.ZipFile(self.out) as apk:
            self.assertEqual(apk.read('classes.dex'), b'code')

    def test_reject_invalid_inputs(self):
        for sha, cert in [('0' * 64, RELEASE), ('bad', RELEASE), (self.sha, TEST),
                          (self.sha, OFFICIAL), (self.sha, 'invalid')]:
            with self.subTest(sha=sha, cert=cert):
                self.assertNotEqual(self.invoke(sha=sha, cert=cert).returncode, 0)
                self.assertFalse(self.out.exists())

    def test_reject_tool_failures_and_payload_changes(self):
        for mode in ['wrong-package', 'bad-input', 'bad-output', 'multiple', 'multiple-output',
                     'verify-failure', 'changed', 'duplicate', 'sign-failure']:
            with self.subTest(mode=mode):
                self.assertNotEqual(self.invoke(mode).returncode, 0)
                self.assertFalse(self.out.exists())
                self.assertEqual(list(self.dir.glob('.dd-ai-sign.*')), [])

    def test_never_overwrite(self):
        self.out.write_bytes(b'old')
        self.assertNotEqual(self.invoke().returncode, 0)
        self.assertEqual(self.out.read_bytes(), b'old')
        self.out.unlink()
        self.out.symlink_to(self.dir / 'missing')
        self.assertNotEqual(self.invoke().returncode, 0)
        self.assertTrue(self.out.is_symlink())
        self.assertFalse((self.dir / 'missing').exists())


if __name__ == '__main__':
    unittest.main()
