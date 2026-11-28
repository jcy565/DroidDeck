"""Check grouped diagnostic selectors and failure isolation with a fake Gradle."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "ci" / "diagnose-compose-idle.sh"
PREFIX = "com.droiddeck.launcher.ui."
GROUP = [PREFIX + name for name in (
    "BottomScreenBarTest", "BottomScreenTypingTest", "CloudMessageTest",
    "ComponentDownloadsDrawerTest", "ComponentsPageProfileTest",
)]


class GroupedComposeDiagnosticsTest(unittest.TestCase):
    def test_all_group_filters_and_failure_do_not_suppress_later_cases(self):
        for group_exit, class_exit, pair_exit in ((0, 0, 0), (17, 0, 0), (17, 0, 9), (17, 7, 0)):
            with self.subTest(group_exit=group_exit, class_exit=class_exit, pair_exit=pair_exit), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                gradle = root / "gradlew"
                gradle.write_text('''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
args = sys.argv[1:]
assert args[0] == "testReleaseUnitTest"
selectors = [args[i+1] for i, arg in enumerate(args) if arg == "--tests"]
assert "-PndkVersion=mock" in args
assert not Path("app/build/test-results/testReleaseUnitTest").exists()
assert not Path("app/build/reports/tests/testReleaseUnitTest").exists()
with open("calls.jsonl", "a") as f:
    f.write(json.dumps(selectors) + "\\n")
results = Path("app/build/test-results/testReleaseUnitTest")
results.mkdir(parents=True)
(results / "TEST.xml").write_text(json.dumps(selectors))
print("diagnostic selectors:", selectors)
if len(selectors) == 5:
    sys.exit(int(os.environ["GROUP_EXIT"]))
if len(selectors) == 2:
    sys.exit(int(os.environ["PAIR_EXIT"]))
if selectors[0].endswith(".ComponentDownloadsDrawerTest"):
    sys.exit(int(os.environ["CLASS_EXIT"]))
sys.exit(0)
''')
                gradle.chmod(0o755)
                out = root / "app/build/compose-idle-diagnostics"
                old = out / "component-navigation/results"
                old.mkdir(parents=True)
                (old / "stale.xml").write_text("stale")
                for directory in ("test-results", "reports/tests"):
                    stale = root / "app/build" / directory / "testReleaseUnitTest"
                    stale.mkdir(parents=True)
                    (stale / "stale.xml").write_text("stale")
                stale_pair = out / "component-pair/results"
                stale_pair.mkdir(parents=True)
                (stale_pair / "stale.xml").write_text("stale")
                subprocess.run(["bash", str(SCRIPT)], cwd=root, check=True,
                               env={**os.environ, "NDK_VERSION": "mock", "GROUP_EXIT": str(group_exit), "CLASS_EXIT": str(class_exit), "PAIR_EXIT": str(pair_exit)},
                               capture_output=True, text=True)
                calls = [json.loads(line) for line in (root / "calls.jsonl").read_text().splitlines()]
                pair_expected = group_exit != 0 and class_exit == 0
                self.assertEqual(15 if pair_expected else 14, len(calls))
                self.assertEqual(GROUP, calls[0])
                self.assertEqual(["test\texit_code", f"component-navigation\t{group_exit}"],
                                 (out / "group-summary.tsv").read_text().splitlines())
                self.assertEqual(GROUP, json.loads((old / "TEST.xml").read_text()))
                self.assertFalse((old / "stale.xml").exists())
                for filename, count, offset in (("summary.tsv", 11, 1), ("class-summary.tsv", 2, 12)):
                    rows = (out / filename).read_text().splitlines()[1:]
                    self.assertEqual(count, len(rows))
                    self.assertEqual([[row.split("\t")[0]] for row in rows], calls[offset:offset+count])
                    expected_codes = [0] * count if offset == 1 else [class_exit, 0]
                    self.assertEqual(expected_codes, [int(row.split("\t")[1]) for row in rows])
                self.assertEqual(GROUP[-2:], [selectors[0] for selectors in calls[12:14]])
                pair_rows = (out / "pair-summary.tsv").read_text().splitlines()
                self.assertEqual(["test\texit_code"] + ([f"component-pair\t{pair_exit}"] if pair_expected else []), pair_rows)
                self.assertFalse((stale_pair / "stale.xml").exists())
                if pair_expected:
                    self.assertEqual(GROUP[-2:], calls[14])
                    self.assertEqual(GROUP[-2:], json.loads((stale_pair / "TEST.xml").read_text()))
                else:
                    self.assertFalse(stale_pair.exists())
                self.assertIn("diagnostic selectors", (out / "component-navigation/run.log").read_text())


if __name__ == "__main__":
    unittest.main()
