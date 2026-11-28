import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, call

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ai_device_packages import INDEPENDENT, OFFICIAL, read_snapshot


class PackageSnapshotTest(unittest.TestCase):
    def test_only_exact_packages_for_current_user(self):
        shell = Mock(side_effect=["10\n", "package:android versionCode:36\n",
                                  f"package:{OFFICIAL}.other versionCode:99\n"
                                  f"package:{INDEPENDENT} versionCode:8\n"
                                  f"package:{OFFICIAL} versionCode:7\n"])
        self.assertEqual(read_snapshot(shell), {
            "android_user": 10, "packages": {OFFICIAL: 7, INDEPENDENT: 8}})
        self.assertEqual(shell.call_args_list, [
            call("am", "get-current-user"),
            call("pm", "list", "packages", "--show-versioncode", "--user", "10", "android"),
            call("pm", "list", "packages", "--show-versioncode", "--user", "10", OFFICIAL)])

    def test_absent(self):
        shell = Mock(side_effect=["0", "package:android versionCode:36", ""])
        self.assertEqual(read_snapshot(shell)["packages"], {OFFICIAL: None, INDEPENDENT: None})

    def test_unreadable_queries(self):
        for replies in (["error"], ["0", ""], ["0", "Error: access denied"],
                        ["0", "package:android versionCode:36", "unexpected"],
                        ["0", "package:android versionCode:36",
                         (f"package:{OFFICIAL} versionCode:1\n") * 2]):
            with self.subTest(replies=replies), self.assertRaises(ValueError):
                read_snapshot(Mock(side_effect=replies))


if __name__ == "__main__":
    unittest.main()
