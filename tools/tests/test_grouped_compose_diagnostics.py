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
                for name in ("target-pair", "predecessors-drawer", "predecessors-profile"):
                    stale_subset = out / name / "results"
                    stale_subset.mkdir(parents=True)
                    (stale_subset / "stale.xml").write_text("stale")
                for directory in ("test-results", "reports/tests"):
                    stale = root / "app/build" / directory / "testReleaseUnitTest"
                    stale.mkdir(parents=True)
                    (stale / "stale.xml").write_text("stale")
                subprocess.run(["bash", str(SCRIPT)], cwd=root, check=True,
                               env={**os.environ, "NDK_VERSION": "mock", "GROUP_EXIT": str(group_exit)},
                               capture_output=True, text=True)
                calls = [json.loads(line) for line in (root / "calls.jsonl").read_text().splitlines()]
                subsets = [GROUP[-2:], GROUP[:4], GROUP[:3] + GROUP[-1:]] if group_exit else []
                self.assertEqual(14 + len(subsets), len(calls))
                self.assertEqual(GROUP, calls[0])
                self.assertEqual(["test\texit_code", f"component-navigation\t{group_exit}"],
                                 (out / "group-summary.tsv").read_text().splitlines())
                self.assertEqual(GROUP, json.loads((old / "TEST.xml").read_text()))
                self.assertFalse((old / "stale.xml").exists())
                subset_summary = (out / "subset-summary.tsv").read_text().splitlines()
                names = ["target-pair", "predecessors-drawer", "predecessors-profile"]
                self.assertEqual(["test\texit_code"] + [
                    f"{name}\t{group_exit}" for name in names if group_exit
                ], subset_summary)
                self.assertEqual(subsets, calls[1:1 + len(subsets)])
                for name, selectors in zip(names, subsets):
                    self.assertEqual(selectors, json.loads(
                        (out / name / "results/TEST.xml").read_text()))
                for name in names:
                    self.assertFalse((out / name / "results/stale.xml").exists())
                if not group_exit:
                    self.assertTrue(all(not (out / name).exists() for name in names))
                for filename, count, offset in (("summary.tsv", 11, 1 + len(subsets)),
                                                ("class-summary.tsv", 2, 12 + len(subsets))):
                    rows = (out / filename).read_text().splitlines()[1:]
                    self.assertEqual(count, len(rows))
                    self.assertEqual([[row.split("\t")[0]] for row in rows], calls[offset:offset+count])
                    self.assertTrue(all(row.endswith("\t0") for row in rows))
                self.assertEqual(GROUP[-2:], [selectors[0] for selectors in calls[-2:]])
                self.assertIn("diagnostic selectors", (out / "component-navigation/run.log").read_text())


if __name__ == "__main__":
    unittest.main()
