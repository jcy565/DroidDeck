"""Validate the diagnostic-only decision without running Gradle."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'ci/android_test_summary.py'


class ComposeDiagnosticDecisionTest(unittest.TestCase):
    def test_cli_decision_and_original_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            reports = root / 'reports'
            reports.mkdir()
            baseline = root / 'baseline.txt'
            baseline.write_text('pkg.Compose.case\n')
            xml = reports / 'TEST-case.xml'
            output = root / 'github-output'

            def check(expected, document=None):
                if document is None:
                    xml.unlink(missing_ok=True)
                else:
                    xml.write_text(document)
                output.unlink(missing_ok=True)
                result = json.loads(subprocess.check_output(
                    [sys.executable, str(SCRIPT), str(reports), str(baseline),
                     'failure', '--github-output', str(output)], text=True))
                self.assertEqual(result['gradle_outcome'], 'failure')
                self.assertEqual(result['compose_diagnostics']['run'], expected)
                self.assertTrue(result['compose_diagnostics']['reason'])
                self.assertEqual(output.read_text(), f'run_compose_diagnostics={str(expected).lower()}\n')
                return result

            missing = check(False)
            self.assertEqual(missing['observed_cases'], 0)
            self.assertFalse(missing['clean_observed_reports'])
            self.assertTrue(missing['problems'])
            known = check(True, '<testsuite tests="1"><testcase classname="pkg.Compose" name="case"><failure/></testcase></testsuite>')
            self.assertEqual(known['known_failures'], ['pkg.Compose.case'])
            unrelated = check(False, '<testsuite tests="1"><testcase classname="pkg.Other" name="case"><error/></testcase></testsuite>')
            self.assertEqual(unrelated['other_failures'], ['pkg.Other.case'])
            check(False, '<testsuite tests="1"><testcase classname="pkg.Compose" name="case"/></testsuite>')
            check(False, '<testsuite tests="1"><testcase classname="pkg.Compose" name="case"><skipped/></testcase></testsuite>')
            malformed = check(True, '<testsuite>')
            self.assertTrue(malformed['problems'])
            check(True, '<testsuite tests="2"><testcase classname="pkg.Compose" name="case"/></testsuite>')


if __name__ == '__main__':
    unittest.main()
