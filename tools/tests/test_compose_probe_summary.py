"""Verify diagnostic evidence without running Android tests."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "ci" / "compose_probe_summary.py"


class ComposeProbeSummaryTest(unittest.TestCase):
    def test_missing_duplicate_unexpected_and_unsuccessful_cases(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            folder = directory / "results"
            folder.mkdir()
            path = folder / "TEST-demo.xml"

            def report(rows, declared=None):
                count = len(rows) if declared is None else declared
                path.write_text(f'<testsuite tests="{count}">' + ''.join(rows) + '</testsuite>')

            def evidence(*selectors, exit_code=0):
                return json.loads(subprocess.check_output(
                    [sys.executable, str(SCRIPT), str(directory), str(exit_code), *selectors],
                    text=True))

            a = '<testcase classname="pkg.A" name="one"/>'
            b = '<testcase classname="pkg.B" name="two"/>'
            report([a, b])
            self.assertTrue(evidence("pkg.A.one", "pkg.B")["all_selectors_observed_passing"])
            self.assertEqual(evidence("pkg.A", "pkg.B")["counts"]["passed"], 2)
            self.assertEqual(evidence("pkg.A")["unexpected_cases"], ["pkg.B.two"])
            self.assertEqual(evidence("pkg.Missing")["missing_selectors"], ["pkg.Missing"])
            self.assertFalse(evidence("pkg.A", "pkg.B", exit_code=17)["all_selectors_observed_passing"])
            report([a, a])
            self.assertTrue(evidence("pkg.A")["problems"])
            for marker, status in (("failure", "failed"), ("error", "error"), ("skipped", "skipped")):
                with self.subTest(marker=marker):
                    report([f'<testcase classname="pkg.A" name="one"><{marker}/></testcase>'])
                    outcome = evidence("pkg.A")
                    self.assertEqual(outcome["counts"][status], 1)
                    self.assertFalse(outcome["all_selectors_observed_passing"])
            report([a], 2)
            self.assertTrue(evidence("pkg.A")["problems"])
            path.unlink()
            self.assertFalse(evidence("pkg.A")["all_selectors_observed_passing"])
