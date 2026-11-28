import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'ci/gradle_log_evidence.py'
SPEC = importlib.util.spec_from_file_location('gradle_log_evidence', SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class GradleLogEvidenceTest(unittest.TestCase):
    def parse(self, text):
        return MODULE.summarize_log(text.splitlines())

    def test_crash_and_failures_are_distinct_from_xml(self):
        result = self.parse("""Gradle Test Executor 29 started executing tests.
SteamControllerBleTest > stoppingBeforeQueuedStartPreservesAnotherSourcesHeldKey STANDARD_ERROR
    java.lang.IllegalStateException: Failed to resolve AttributionSource
Gradle Test Executor 29 finished executing tests.
Gradle Test Executor 59 started executing tests.
TrialDatabaseTest > invalidRecordCannotBecomeComparisonBaseline FAILED
TrialDatabaseTest > realHelperMigratesBothLegacyVersionsAndPreservesRecords FAILED
SteamControllerBleTest > stoppingBeforeQueuedStartPreservesAnotherSourcesHeldKey SKIPPED
4207 tests completed, 2 failed, 1 skipped
> Process 'Gradle Test Executor 29' finished with non-zero exit value 2
Caused by: Process 'Gradle Test Executor 29' finished with non-zero exit value 2""")
        self.assertEqual([dict(completed=4207, failed=2, skipped=1)], result['reported_totals'])
        self.assertEqual(['TrialDatabaseTest.invalidRecordCannotBecomeComparisonBaseline',
                          'TrialDatabaseTest.realHelperMigratesBothLegacyVersionsAndPreservesRecords'], result['observed_failed_headings'])
        self.assertEqual(['SteamControllerBleTest.stoppingBeforeQueuedStartPreservesAnotherSourcesHeldKey'], result['observed_skipped_headings'])
        self.assertEqual([dict(worker=29, exit_code=2, last_observed_heading='SteamControllerBleTest.stoppingBeforeQueuedStartPreservesAnotherSourcesHeldKey')], result['nonzero_test_worker_exits'])
        self.assertNotIn('passed', result)

    def test_indented_output_and_unrecognized_headings_not_reported_as_failures(self):
        result = self.parse('    ExampleTest > fake FAILED\nexample log FAILED\nExampleTest > real PASSED\n')
        self.assertEqual([], result['observed_failed_headings'])
        self.assertEqual([], result['reported_totals'])
        self.assertEqual([], result['nonzero_test_worker_exits'])

    def test_parallel_workers_do_not_get_ambiguous_heading_attribution(self):
        result = self.parse("""Gradle Test Executor 1 started executing tests.
Gradle Test Executor 2 started executing tests.
Test > ambiguous STANDARD_ERROR
> Process 'Gradle Test Executor 1' finished with non-zero exit value 137""")
        self.assertEqual([dict(worker=1, exit_code=137, last_observed_heading=None)], result['nonzero_test_worker_exits'])

    def test_totals_are_preserved_not_added(self):
        result = self.parse('3 tests completed, 1 failed\n3 tests completed, 1 failed\n4 tests completed, 2 skipped\n1 test completed')
        self.assertEqual([dict(completed=1, failed=0, skipped=0), dict(completed=3, failed=1, skipped=0), dict(completed=4, failed=0, skipped=2)], result['reported_totals'])


if __name__ == '__main__':
    unittest.main()
