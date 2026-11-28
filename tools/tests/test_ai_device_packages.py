import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, call

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ai_device_packages import INDEPENDENT, OFFICIAL, read_snapshot, compare_snapshots, load_snapshot
from tempfile import TemporaryDirectory


class PackageSnapshotTest(unittest.TestCase):
    def test_only_exact_packages_for_current_user(self):
        shell = Mock(side_effect=["10\n", "package:android versionCode:36\n",
                                  f"package:{OFFICIAL}.other versionCode:99\n"
                                  f"package:{INDEPENDENT} versionCode:8\n"
                                  f"package:{OFFICIAL} versionCode:7\n", "10\n"])
        self.assertEqual(read_snapshot(shell), {
            "android_user": 10, "packages": {OFFICIAL: 7, INDEPENDENT: 8}})
        self.assertEqual(shell.call_args_list, [
            call("am", "get-current-user"),
            call("pm", "list", "packages", "--show-versioncode", "--user", "10", "android"),
            call("pm", "list", "packages", "--show-versioncode", "--user", "10", OFFICIAL),
            call("am", "get-current-user")])

    def test_absent(self):
        shell = Mock(side_effect=["0", "package:android versionCode:36", "", "0"])
        self.assertEqual(read_snapshot(shell)["packages"], {OFFICIAL: None, INDEPENDENT: None})

    def test_user_change_invalidates_snapshot(self):
        shell = Mock(side_effect=["0", "package:android versionCode:36", "", "10"])
        with self.assertRaises(ValueError):
            read_snapshot(shell)

    def test_comparison_never_claims_full_device_acceptance(self):
        before = {"android_user": 0, "packages": {OFFICIAL: 7, INDEPENDENT: None}}
        after = {"android_user": 0, "packages": {OFFICIAL: 7, INDEPENDENT: 8}}
        result = compare_snapshots(before, after, 8)
        self.assertTrue(result["package_checks_passed"])
        for flag in ("d01_passed", "device_identity_verified", "save_integrity_verified", "signature_verified"):
            self.assertFalse(result[flag])
        for changed in (
            {"android_user": 10, "packages": after["packages"]},
            {"android_user": 0, "packages": {OFFICIAL: None, INDEPENDENT: 8}},
            {"android_user": 0, "packages": {OFFICIAL: 9, INDEPENDENT: 8}},
            {"android_user": 0, "packages": {OFFICIAL: 7, INDEPENDENT: 9}},
        ):
            self.assertFalse(compare_snapshots(before, changed, 8)["package_checks_passed"])
        self.assertFalse(compare_snapshots(after, after, 8)["package_checks_passed"])
        absent = {"android_user": 0, "packages": {OFFICIAL: None, INDEPENDENT: None}}
        self.assertFalse(compare_snapshots(absent, after, 8)["package_checks_passed"])

    def test_invalid_snapshot_files_fail_closed(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "snapshot.json"
            for data in (
                b'{"android_user":0,"android_user":1,"packages":{}}',
                b'{"android_user":false,"packages":{}}',
                b'{"android_user":0,"packages":{"com.droiddeck.launcher":true,"com.droiddeck.launcher.ai":null}}',
                b' ' * 8193,
                b'\xff',
            ):
                path.write_bytes(data)
                with self.assertRaises(ValueError):
                    load_snapshot(path)

    def test_unreadable_queries(self):
        for replies in (["error"], ["0", ""], ["0", "Error: access denied"],
                        ["0", "package:android versionCode:36", "unexpected"],
                        ["0", "package:android versionCode:36",
                         (f"package:{OFFICIAL} versionCode:1\n") * 2]):
            with self.subTest(replies=replies), self.assertRaises(ValueError):
                read_snapshot(Mock(side_effect=replies))


if __name__ == "__main__":
    unittest.main()
