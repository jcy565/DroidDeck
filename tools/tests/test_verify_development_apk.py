"""Exercise artifact consistency checks using disposable ZIP fixtures."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import sys
import unittest
from unittest import mock
import warnings
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / 'ci/verify-development-apk.py'
sys.path.insert(0, str(SCRIPT.parent))
SPEC = importlib.util.spec_from_file_location('verify_development_apk', SCRIPT)
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)
COMMIT = 'a' * 40
MEMBERS = {'AndroidManifest.xml': b'fixture manifest',
           'lib/arm64-v8a/libfixture.so': b'fixture native library'}


def zip_bytes(entries):
    buf = io.BytesIO()
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore', message='Duplicate name:', category=UserWarning)
        with zipfile.ZipFile(buf, 'w') as archive:
            for name, data in entries:
                archive.writestr(name, data)
    return buf.getvalue()


class DevelopmentApkAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.apk = Path(self.temp.name) / 'apk.zip'
        self.inv = Path(self.temp.name) / 'inventory.zip'

    def fixture(self, *, inner=None, outer=None, change=None, duplicate_inventory=False):
        payload = zip_bytes(list(MEMBERS.items()) if inner is None else inner)
        entries = {name: {'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
                   for name, data in MEMBERS.items()}
        manifest = {'source-commit.txt': COMMIT, 'apk-sha256.txt': hashlib.sha256(payload).hexdigest(),
                    'app-release.apk.json': json.dumps({'entries': entries}),
                    'official-comparison.json': json.dumps({'assistant': {'package': 'com.droiddeck.launcher.ai'}})}
        if change:
            manifest.update(change)
        records = list(manifest.items())
        if duplicate_inventory:
            records.append(records[0])
        self.inv.write_bytes(zip_bytes(records))
        self.apk.write_bytes(zip_bytes([('app-release.apk', payload)] if outer is None else outer))

    def verify(self, commit=COMMIT):
        return AUDIT.verify(self.apk, self.inv, commit)

    def test_valid_artifact_and_explicit_signature_limitation(self):
        self.fixture()
        result = self.verify()
        self.assertEqual(COMMIT, result['source_commit'])
        self.assertEqual(2, result['verified_entries'])
        self.assertEqual(['arm64-v8a'], result['native_abis'])
        self.assertEqual('com.droiddeck.launcher.ai', result['package_from_ci_inventory'])
        self.assertFalse(result['signature_verified_here'])

    def test_archive_pins_match_both_downloads(self):
        self.fixture()
        self.assertFalse(self.verify()['archive_hashes_matched'])
        a = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        b = hashlib.sha256(self.inv.read_bytes()).hexdigest()
        result = AUDIT.verify(self.apk, self.inv, COMMIT,
                              apk_zip_sha256=a, inventory_zip_sha256=b)
        self.assertTrue(result['archive_hashes_matched'])
        self.assertEqual(2, result['verified_entries'])
        self.assertFalse(result['signature_verified_here'])

    def test_pinned_parse_uses_snapshot_not_reopened_path(self):
        self.fixture()
        a = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        b = hashlib.sha256(self.inv.read_bytes()).hexdigest()
        original_snapshot = AUDIT.snapshot_archive

        @AUDIT.contextmanager
        def replace_after_snapshot(path, expected, label):
            with original_snapshot(path, expected, label) as snapshot:
                # Replace bytes after validation, before ZIP parsing.
                path.write_bytes(b'not the verified archive')
                yield snapshot

        with mock.patch.object(AUDIT, 'snapshot_archive', replace_after_snapshot):
            result = AUDIT.verify(self.apk, self.inv, COMMIT,
                                  apk_zip_sha256=a, inventory_zip_sha256=b)
        self.assertTrue(result['archive_hashes_matched'])
        self.assertEqual(2, result['verified_entries'])
        self.assertFalse(result['signature_verified_here'])
        self.assertEqual(b'not the verified archive', self.apk.read_bytes())
        self.assertEqual(b'not the verified archive', self.inv.read_bytes())

    def test_archive_pins_reject_missing_invalid_or_mismatched_digests(self):
        self.fixture()
        a = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        b = hashlib.sha256(self.inv.read_bytes()).hexdigest()
        for pins in (dict(apk_zip_sha256=a), dict(inventory_zip_sha256=b),
                     dict(apk_zip_sha256='bad', inventory_zip_sha256=b),
                     dict(apk_zip_sha256=a, inventory_zip_sha256='bad'),
                     dict(apk_zip_sha256='0' * 64, inventory_zip_sha256=b),
                     dict(apk_zip_sha256=a, inventory_zip_sha256='0' * 64)):
            with self.subTest(pins=pins), self.assertRaises(ValueError):
                AUDIT.verify(self.apk, self.inv, COMMIT, **pins)

    def test_optional_signature_uses_verified_private_apk(self):
        self.fixture()
        cert = 'c' * 64
        pins = dict(apk_zip_sha256=hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                    inventory_zip_sha256=hashlib.sha256(self.inv.read_bytes()).hexdigest())
        with zipfile.ZipFile(self.apk) as archive:
            payload = archive.read('app-release.apk')
        report = f'Verified using v2 scheme (APK Signature Scheme v2): true\nVerified using v3 scheme (APK Signature Scheme v3): true\nSigner #1 certificate SHA-256 digest: {cert}\n'

        def run(args, **kwargs):
            self.assertEqual(['/trusted/apksigner', 'verify', '--verbose', '--print-certs'], args[:-1])
            self.assertEqual(payload, Path(args[-1]).read_bytes())
            self.assertTrue(kwargs['check'])
            self.assertEqual(60, kwargs['timeout'])
            return mock.Mock(stdout=report)

        with mock.patch.object(AUDIT.subprocess, 'run', side_effect=run) as tool:
            result = AUDIT.verify(self.apk, self.inv, COMMIT, **pins,
                                  apksigner='/trusted/apksigner', expected_cert_sha256=cert)
        tool.assert_called_once()
        self.assertTrue(result['signature_verified_here'])
        self.assertEqual(cert, result['verified_certificate_sha256'])
        self.assertFalse(Path(tool.call_args.args[0][-1]).exists())
        for wrong in (report.replace(cert, 'b' * 64), report.replace('true', 'false'), report.replace('v3): true', 'v3): false'),
                      report + f'Signer #2 certificate SHA-256 digest: {cert}\n', ''):
            with self.subTest(report=wrong), mock.patch.object(AUDIT.subprocess, 'run',
                    return_value=mock.Mock(stdout=wrong)), self.assertRaises(ValueError):
                AUDIT.verify(self.apk, self.inv, COMMIT, **pins,
                             apksigner='/trusted/apksigner', expected_cert_sha256=cert)
        with mock.patch.object(AUDIT.subprocess, 'run', side_effect=AUDIT.subprocess.CalledProcessError(1, 'apksigner')):
            with self.assertRaises(AUDIT.subprocess.CalledProcessError):
                AUDIT.verify(self.apk, self.inv, COMMIT, **pins,
                             apksigner='/trusted/apksigner', expected_cert_sha256=cert)

    def test_actual_package_verification(self):
        self.fixture()
        cert = 'c' * 64
        pins = dict(apk_zip_sha256=hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                    inventory_zip_sha256=hashlib.sha256(self.inv.read_bytes()).hexdigest())
        signature = f'Verified using v2 scheme (APK Signature Scheme v2): true\nVerified using v3 scheme (APK Signature Scheme v3): true\nSigner #1 certificate SHA-256 digest: {cert}\n'
        good = "package: name='com.droiddeck.launcher.ai' versionCode='19' versionName='0.7.1'\n"
        options = dict(**pins, apksigner='/sdk/apksigner', expected_cert_sha256=cert,
                       aapt='/sdk/aapt', expected_version_code=19)
        with mock.patch.object(AUDIT.subprocess, 'run', side_effect=[mock.Mock(stdout=signature), mock.Mock(stdout=good)]) as tool:
            result = AUDIT.verify(self.apk, self.inv, COMMIT, **options)
            self.assertTrue(result['package_verified_here'])
            self.assertEqual(19, result['verified_version_code'])
            self.assertFalse(result['device_acceptance_verified'])
            self.assertEqual(tool.call_args_list[0].args[0][-1], tool.call_args_list[1].args[0][-1])
        for bad in (good.replace("launcher.ai", "launcher"), good.replace("'19'", "'18'"), good + good, ""):
            with mock.patch.object(AUDIT.subprocess, 'run', side_effect=[mock.Mock(stdout=signature), mock.Mock(stdout=bad)]):
                with self.assertRaises(ValueError):
                    AUDIT.verify(self.apk, self.inv, COMMIT, **options)
        with self.assertRaises(ValueError):
            AUDIT.verify(self.apk, self.inv, COMMIT, aapt='tool', expected_version_code=19)

    def test_signature_requires_all_trust_inputs(self):
        self.fixture()
        pins = dict(apk_zip_sha256=hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                    inventory_zip_sha256=hashlib.sha256(self.inv.read_bytes()).hexdigest())
        for options in (dict(apksigner='tool'), dict(expected_cert_sha256='c' * 64),
                        dict(apksigner='tool', expected_cert_sha256='bad', **pins),
                        dict(apksigner='tool', expected_cert_sha256='c' * 64),
                        dict(apksigner='tool', **pins)):
            with self.subTest(options=options), self.assertRaises(ValueError):
                AUDIT.verify(self.apk, self.inv, COMMIT, **options)

    def test_rejects_wrong_source_hash_and_package(self):
        self.fixture()
        for commit in ('a', 'b' * 40, 'A' * 40):
            with self.subTest(commit=commit), self.assertRaises(ValueError):
                self.verify(commit)
        for change in ({'apk-sha256.txt': 'b' * 64}, {'apk-sha256.txt': 'invalid'},
                       {'official-comparison.json': json.dumps({'assistant': {'package': 'com.droiddeck.launcher'}})}):
            with self.subTest(change=change):
                self.fixture(change=change)
                with self.assertRaises(ValueError):
                    self.verify()

    def test_rejects_unexpected_and_duplicate_artifact_entries(self):
        for outer in ([], [('wrong.apk', b'x')], [('app-release.apk', b'x'), ('other', b'x')],
                      [('app-release.apk', b'x'), ('app-release.apk', b'y')]):
            with self.subTest(outer=outer):
                self.fixture(outer=outer)
                with self.assertRaises(ValueError):
                    self.verify()
        self.fixture(duplicate_inventory=True)
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_ambiguous_or_malformed_inventory(self):
        valid = {'size': 0, 'sha256': 'a' * 64}
        for raw in ('{"entries":{},"entries":{}}',
                    '{"entries":{"x":{"size":0,"size":1,"sha256":"' + 'a' * 64 + '"}}}',
                    '[]', '{}', '{"entries":{}}',
                    json.dumps({'entries': {'x': None}}),
                    *(json.dumps({'entries': {'x': dict(valid, size=size)}})
                      for size in (True, -1, 0.0, '0')),
                    *(json.dumps({'entries': {'x': dict(valid, sha256=sha)}})
                      for sha in (None, 'bad', 'A' * 64))):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                self.fixture(change={'app-release.apk.json': raw})
                self.verify()
        self.fixture(change={'apk-sha256.txt': ''})
        with self.assertRaisesRegex(ValueError, 'Invalid APK SHA-256'):
            self.verify()
        self.fixture(change={'official-comparison.json':
                             '{"assistant":{"package":"wrong","package":"com.droiddeck.launcher.ai"}}'})
        with self.assertRaisesRegex(ValueError, 'Duplicate JSON key'):
            self.verify()

    def test_rejects_malformed_comparison_structure(self):
        for value in (None, [], {}, {'assistant': None}, {'assistant': []},
                      {'assistant': {}}, {'assistant': {'package': 123}}):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, 'Unexpected inventory package'):
                self.fixture(change={'official-comparison.json': json.dumps(value)})
                self.verify()

    def test_metadata_read_limit_and_missing_entry(self):
        with zipfile.ZipFile(io.BytesIO(zip_bytes([('exact', b'1234'),
                                                  ('large', b'12345')]))) as archive:
            self.assertEqual(b'1234', AUDIT.read_metadata(archive, 'exact', 4))
            with self.assertRaisesRegex(ValueError, 'oversized APK metadata'):
                AUDIT.read_metadata(archive, 'large', 4)
            with self.assertRaisesRegex(ValueError, 'Missing APK metadata'):
                AUDIT.read_metadata(archive, 'missing', 4)
        self.fixture(change={'source-commit.txt': COMMIT + ' ' * 4096})
        with self.assertRaisesRegex(ValueError, 'oversized APK metadata'):
            self.verify()

    def test_rejects_inner_inventory_mismatch_even_with_matching_apk_digest(self):
        valid = list(MEMBERS.items())
        for inner in (valid[:-1], valid + [('extra', b'x')], valid + [valid[0]],
                      [(valid[0][0], b'x' * len(valid[0][1])), valid[1]],
                      [(valid[0][0], b'x'), valid[1]]):
            with self.subTest(inner=inner):
                self.fixture(inner=inner)
                with self.assertRaises(ValueError):
                    self.verify()


if __name__ == '__main__':
    unittest.main()
