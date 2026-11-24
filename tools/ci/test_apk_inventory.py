import importlib.util
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

spec = importlib.util.spec_from_file_location("apk_inventory", Path(__file__).with_name("apk_inventory.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class InventoryTests(unittest.TestCase):
    def test_comparison(self):
        with tempfile.TemporaryDirectory() as directory:
            old, new = [Path(directory) / name for name in ("old.apk", "new.apk")]
            for path, entries in [(old, {"AndroidManifest.xml": b"a", "same": b"x", "removed": b"r"}), (new, {"AndroidManifest.xml": b"b", "same": b"x", "added": b"n"})]:
                with zipfile.ZipFile(path, "w") as archive:
                    for name, data in entries.items():
                        archive.writestr(name, data)
            result = module.compare(module.inventory(old), module.inventory(new))
            self.assertEqual(result["added"], ["added"])
            self.assertEqual(result["removed"], ["removed"])
            self.assertEqual(result["changed"], ["AndroidManifest.xml"])
            self.assertTrue(result["manifest_changed"])

    def test_review_categories(self):
        old = {"classes.dex": {"sha256": "a"}, "res/a": {"sha256": "a"}}
        new = {"classes.dex": {"sha256": "b"}, "res/a": {"sha256": "b"}, "lib/a.so": {"sha256": "c"}}
        self.assertEqual(module.compare(old, new)["review_required"], ["classes.dex", "lib/a.so"])

    def test_duplicate_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "duplicate.apk"
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr("same", b"a")
                    archive.writestr("same", b"b")
            with self.assertRaises(ValueError):
                module.inventory(path)

    def test_invalid_zip(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "invalid.apk"
            path.write_bytes(b"not a zip")
            with self.assertRaises(zipfile.BadZipFile):
                module.inventory(path)

if __name__ == "__main__":
    unittest.main()
