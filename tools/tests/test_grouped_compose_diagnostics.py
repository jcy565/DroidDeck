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
import xml.etree.ElementTree as ET
group_exit = int(os.environ["GROUP_EXIT"])
code = group_exit if len(selectors) == 5 else (
    23 if selectors[-1].endswith("ComponentsPageProfileTest") else 17
) if len(selectors) == 4 else 0
suite = ET.Element("testsuite", tests=str(len(selectors)), failures=str(int(code != 0)))
for i, selector in enumerate(selectors):
    cls, method = (selector, "example") if selector.endswith("Test") else selector.rsplit(".", 1)
    case = ET.SubElement(suite, "testcase", classname=cls, name=method)
    if code and i == len(selectors) - 1:
        ET.SubElement(case, "failure").text = "synthetic diagnostic failure"
ET.ElementTree(suite).write(results / "TEST-probe.xml", encoding="utf-8")
print("diagnostic selectors:", selectors)
sys.exit(code)
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
                    f"{name}\t{code}" for name, code in zip(names, (0, 17, 23)) if group_exit
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
                # Verify actual per-probe evidence, including mixed exit status,
                # rather than trusting invocation count or a zero exit alone.
                probes = [("component-navigation", GROUP, group_exit)]
                probes += [(name, selectors, code) for name, selectors, code in
                           zip(names, subsets, (0, 17, 23))]
                for filename, offset in (("summary.tsv", 1 + len(subsets)),
                                         ("class-summary.tsv", 12 + len(subsets))):
                    for i, row in enumerate((out / filename).read_text().splitlines()[1:]):
                        selector = calls[offset + i][0]
                        name = selector.rsplit(".", 1)[-1]
                        if filename == "class-summary.tsv":
                            name = "class-" + name
                        probes.append((name, [selector], int(row.split("\t")[1])))
                self.assertEqual(len(calls), len(probes))
                for name, selectors, code in probes:
                    with self.subTest(probe=name):
                        evidence = json.loads((out / name / "evidence.json").read_text())
                        self.assertEqual(code, evidence["gradle_exit_code"])
                        self.assertEqual(set(selectors), set(evidence["selectors"]))
                        self.assertEqual([], evidence["missing_selectors"])
                        self.assertEqual([], evidence["unexpected_cases"])
                        self.assertEqual([], evidence["problems"])
                        self.assertEqual(len(selectors) - int(code != 0), evidence["counts"]["passed"])
                        self.assertEqual(int(code != 0), evidence["counts"]["failed"])
                        self.assertEqual(code == 0, evidence["all_selectors_observed_passing"])



if __name__ == "__main__":
    unittest.main()
