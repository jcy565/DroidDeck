"""Standalone Java signing policy checks with an ephemeral test-only PKCS12."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class AiSigningPolicyTest(unittest.TestCase):
    def test_external_key_policy(self):
        source = Path(__file__).with_name('AiSigningPolicy.java').resolve()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo = root / 'repo'
            repo.mkdir()
            classes = root / 'classes'
            classes.mkdir()
            subprocess.run(['javac', '--release', '17', '-d', str(classes), str(source)], check=True)
            store = root / 'fixture.p12'
            password = 'fixture-only-123'
            subprocess.run([
                'keytool', '-genkeypair', '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048',
                '-validity', '2', '-dname', 'CN=Temporary test fixture', '-storetype', 'PKCS12',
                '-keystore', str(store), '-storepass', password, '-keypass', password
            ], check=True, capture_output=True)
            cert = subprocess.check_output(['keytool', '-exportcert', '-alias', 'fixture',
                                            '-keystore', str(store), '-storepass', password])
            env = {**os.environ, 'DD_AI_KEYSTORE': str(store), 'DD_AI_KEY_ALIAS': 'fixture',
                   'DD_AI_STORE_PASSWORD': password, 'DD_AI_KEY_PASSWORD': password,
                   'DD_AI_CERT_SHA256': hashlib.sha256(cert).hexdigest()}
            inside = repo / 'inside.p12'
            inside.write_bytes(store.read_bytes())
            link = root / 'linked.p12'
            link.symlink_to(store)
            command = ['java', '-cp', str(classes), 'AiSigningPolicy', str(repo)]
            cases = [
                ('valid', {}, 0),
                ('missing', {'DD_AI_KEYSTORE': ''}, 1),
                ('wrong pin', {'DD_AI_CERT_SHA256': '0' * 64}, 1),
                ('inside repository', {'DD_AI_KEYSTORE': str(inside)}, 1),
                ('symlink', {'DD_AI_KEYSTORE': str(link)}, 1),
                ('wrong alias', {'DD_AI_KEY_ALIAS': 'missing'}, 1),
                ('wrong password', {'DD_AI_STORE_PASSWORD': 'invalid'}, 1),
                ('public test certificate', {'DD_AI_CERT_SHA256':
                    'a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc'}, 1),
                ('official certificate', {'DD_AI_CERT_SHA256':
                    'b241ea7d77822d39b89d3227e06f3ecf62adec6cbfd38ad06196787991b5d3f0'}, 1),
            ]
            for name, changes, expected in cases:
                with self.subTest(name=name):
                    result = subprocess.run(command, env=env | changes, capture_output=True, text=True)
                    self.assertEqual(result.returncode, expected)
                    self.assertNotIn(password, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
