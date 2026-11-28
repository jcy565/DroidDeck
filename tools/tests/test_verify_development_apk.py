"""Exercise artifact consistency checks using disposable ZIP fixtures."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import warnings
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / 'ci/verify-development-apk.py'
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
