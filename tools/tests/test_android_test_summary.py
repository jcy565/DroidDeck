import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('android_test_summary', ROOT / 'tools/ci/android_test_summary.py')
summary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary)


class AndroidTestSummaryTest(unittest.TestCase):
    def test_failure_pass_skip_and_missing_are_distinct(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / 'TEST-a.xml').write_text('''<testsuite>
                <testcase classname="A" name="known"><failure/></testcase>
                <testcase classname="A" name="fixed"/>
                <testcase classname="A" name="skip"><skipped/></testcase>
                <testcase classname="B" name="new"><error/></testcase>
            </testsuite>''')
            result = summary.summarize(directory, {'A.known', 'A.fixed', 'A.skip', 'A.missing'}, 'failure')
            self.assertEqual(result['known_failures'], ['A.known'])
            self.assertEqual(result['other_failures'], ['B.new'])
            self.assertEqual(result['baseline_observed_passing'], ['A.fixed'])
            self.assertEqual(result['baseline_skipped_or_ambiguous'], ['A.skip'])
            self.assertEqual(result['baseline_not_observed'], ['A.missing'])
            self.assertEqual(result['observed_cases'], 4)
            self.assertEqual(result['gradle_outcome'], 'failure')

    def test_duplicate_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / 'TEST-a.xml').write_text('''<testsuite>
                <testcase classname="A" name="case"/>
                <testcase classname="A" name="case"/>
            </testsuite>''')
            result = summary.summarize(directory, {'A.case'}, 'success')
            self.assertFalse(result['clean_observed_reports'])
            self.assertEqual(result['baseline_observed_passing'], [])
            self.assertEqual(result['baseline_skipped_or_ambiguous'], ['A.case'])

    def test_missing_and_malformed_reports_are_not_clean(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            result = summary.summarize(directory, {'A.case'}, 'failure')
            self.assertFalse(result['clean_observed_reports'])
            self.assertEqual(result['baseline_not_observed'], ['A.case'])
            (directory / 'TEST-broken.xml').write_text('<testsuite>')
            result = summary.summarize(directory, {'A.case'}, 'failure')
            self.assertFalse(result['clean_observed_reports'])
            self.assertTrue(result['problems'])

    def test_missing_identity_is_not_counted_as_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / 'TEST-a.xml').write_text('<testsuite><testcase name="x"/></testsuite>')
            result = summary.summarize(directory, set(), 'failure')
            self.assertEqual(result['observed_cases'], 0)
            self.assertFalse(result['clean_observed_reports'])


if __name__ == '__main__':
    unittest.main()
