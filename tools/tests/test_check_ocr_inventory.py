import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ci'))
from check_ocr_inventory import check, PREFIX, LANGUAGES


class OcrInventoryTest(unittest.TestCase):
    def setUp(self):
        self.entries = {'classes.dex': {'size': 1}}
        self.paths = [PREFIX + 'gocr/gocr_models/line_recognition_legacy_mobile/'
                      + language + '/optical/conv_model.fb' for language in LANGUAGES]

    def test_disabled_profile(self):
        check(self.entries, False)
        self.entries[self.paths[0]] = {'size': 1}
        with self.assertRaises(ValueError):
            check(self.entries, False)

    def test_enabled_profile(self):
        self.entries.update({path: {'size': 1} for path in self.paths})
        check(self.entries, True)
        for path in self.paths:
            with self.subTest(path=path):
                value = self.entries.pop(path)
                with self.assertRaises(ValueError):
                    check(self.entries, True)
                self.entries[path] = {'size': 0}
                with self.assertRaises(ValueError):
                    check(self.entries, True)
                self.entries[path] = value

    def test_empty_inventory(self):
        for entries in ({}, None, []):
            with self.assertRaises(ValueError):
                check(entries, True)
