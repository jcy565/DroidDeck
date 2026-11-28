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
        for group_exit in (0, 17):
            with self.subTest(group_exit=group_exit), tempfile.TemporaryDirectory() as tmp:
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
sys.exit(int(os.environ["GROUP_EXIT"]) if len(selectors) > 1 else 0)
''')
                gradle.chmod(0o755)
                out = root / "app/build/compose-idle-diagnostics"
                old = out / "component-navigation/results"
                old.mkdir(parents=True)
                (old / "stale.xml").write_text("stale")
                for name in ("navigation-pair", "predecessors-and-drawer", "predecessors-and-profile"):
                    stale_split = out / name / "results"
                    stale_split.mkdir(parents=True)
                    (stale_split / "stale.xml").write_text("stale")
                for directory in ("test-results", "reports/tests"):
                    stale = root / "app/build" / directory / "testReleaseUnitTest"
                    stale.mkdir(parents=True)
                    (stale / "stale.xml").write_text("stale")
                subprocess.run(["bash", str(SCRIPT)], cwd=root, check=True,
                               env={**os.environ, "NDK_VERSION": "mock", "GROUP_EXIT": str(group_exit)},
                               capture_output=True, text=True)
                calls = [json.loads(line) for line in (root / "calls.jsonl").read_text().splitlines()]
                self.assertEqual(14 if group_exit == 0 else 17, len(calls))
                self.assertEqual(GROUP, calls[0])
                self.assertEqual(["test\texit_code", f"component-navigation\t{group_exit}"],
                                 (out / "group-summary.tsv").read_text().splitlines())
                self.assertEqual(GROUP, json.loads((old / "TEST.xml").read_text()))
                self.assertFalse((old / "stale.xml").exists())
                for filename, count, offset in (("summary.tsv", 11, 1), ("class-summary.tsv", 2, 12)):
                    rows = (out / filename).read_text().splitlines()[1:]
                    self.assertEqual(count, len(rows))
                    self.assertEqual([[row.split("\t")[0]] for row in rows], calls[offset:offset+count])
                    self.assertTrue(all(row.endswith("\t0") for row in rows))
                self.assertEqual(GROUP[-2:], [selectors[0] for selectors in calls[12:14]])
                self.assertIn("diagnostic selectors", (out / "component-navigation/run.log").read_text())
                split_rows = (out / "split-summary.tsv").read_text().splitlines()
                names = ["navigation-pair", "predecessors-and-drawer", "predecessors-and-profile"]
                expected = [GROUP[-2:], GROUP[:3] + [GROUP[-2]], GROUP[:3] + [GROUP[-1]]]
                self.assertEqual("test\texit_code", split_rows[0])
                if group_exit == 0:
                    self.assertEqual(1, len(split_rows))
                    self.assertTrue(all(not (out / name).exists() for name in names))
                else:
                    self.assertEqual(expected, calls[14:])
                    self.assertEqual([f"{name}\t{group_exit}" for name in names], split_rows[1:])
                    for name, selectors in zip(names, expected):
                        self.assertEqual(selectors, json.loads((out / name / "results/TEST.xml").read_text()))
                        self.assertFalse((out / name / "results/stale.xml").exists())


if __name__ == "__main__":
    unittest.main()
