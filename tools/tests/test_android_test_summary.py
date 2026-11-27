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
            (directory / 'TEST-a.xml').write_text('''<testsuite tests="4">
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
            (directory / 'TEST-a.xml').write_text('''<testsuite tests="2">
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
            (directory / 'TEST-a.xml').write_text('<testsuite tests="1"><testcase name="x"/></testsuite>')
            result = summary.summarize(directory, set(), 'failure')
            self.assertEqual(result['observed_cases'], 0)
            self.assertFalse(result['clean_observed_reports'])

    def check_xml(self, xml):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / 'TEST-a.xml').write_text(xml)
            return summary.summarize(directory, {'A.case'}, 'success')

    def test_incomplete_or_missing_declared_count(self):
        for attrs in ('tests="2"', '', 'tests="invalid"', 'tests="-1"'):
            with self.subTest(attrs=attrs):
                result = self.check_xml(f'<testsuite {attrs}><testcase classname="A" name="case"/></testsuite>')
                self.assertFalse(result['clean_observed_reports'])
                self.assertEqual(result['baseline_observed_passing'], [])
                self.assertEqual(result['baseline_not_observed'], ['A.case'])

    def test_declared_failure_count_mismatch(self):
        result = self.check_xml('<testsuite tests="1" failures="1"><testcase classname="A" name="case"/></testsuite>')
        self.assertFalse(result['clean_observed_reports'])
        self.assertEqual(result['observed_cases'], 0)

    def test_contradictory_status_is_rejected(self):
        result = self.check_xml('<testsuite tests="1"><testcase classname="A" name="case"><failure/><skipped/></testcase></testsuite>')
        self.assertFalse(result['clean_observed_reports'])
        self.assertEqual(result['observed_cases'], 0)

    def test_aggregate_suite_count(self):
        suite = '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="A" name="case"/></testsuite>'
        self.assertTrue(self.check_xml(f'<testsuites tests="1">{suite}</testsuites>')['clean_observed_reports'])
        result = self.check_xml(f'<testsuites tests="2">{suite}</testsuites>')
        self.assertFalse(result['clean_observed_reports'])
        self.assertEqual(result['observed_cases'], 0)

    def test_aggregate_status_counts_must_match_cases_across_suites(self):
        suites = ''.join(
            f'<testsuite tests="1"><testcase classname="A" name="{tag}"><{tag}/></testcase></testsuite>'
            for tag in ('failure', 'error', 'skipped'))
        counts = dict(tests=3, failures=1, errors=1, skipped=1)
        for wrong in (None, 'failures', 'errors', 'skipped'):
            with self.subTest(wrong=wrong):
                attrs = ' '.join(f'{key}="{0 if key == wrong else value}"' for key, value in counts.items())
                result = self.check_xml(f'<testsuites {attrs}>{suites}</testsuites>')
                self.assertEqual(result['clean_observed_reports'], wrong is None)
                self.assertEqual(result['observed_cases'], 3 if wrong is None else 0)

    def test_nested_cases_are_not_counted(self):
        result = self.check_xml('<testsuite tests="0"><wrapper><testcase classname="A" name="case"/></wrapper></testsuite>')
        self.assertFalse(result['clean_observed_reports'])
        self.assertEqual(result['observed_cases'], 0)


if __name__ == '__main__':
    unittest.main()
