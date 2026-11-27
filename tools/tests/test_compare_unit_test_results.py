import sys
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ci"))
from compare_unit_test_results import compare, read_reports


class CompareReportsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def report(self, name, rows, declared=None):
        directory = self.root / name
        directory.mkdir(exist_ok=True)
        root = ET.Element('testsuite', tests=str(len(rows) if declared is None else declared))
        for identity, status in rows:
            case = ET.SubElement(root, 'testcase', classname='example.Test', name=identity)
            if status == 'idle':
                ET.SubElement(case, 'failure', type='androidx.test.espresso.AppNotIdleException')
            elif status == 'failure':
                ET.SubElement(case, 'error', type='java.lang.IllegalStateException')
            elif status == 'skipped':
                ET.SubElement(case, 'skipped')
        ET.ElementTree(root).write(directory / 'TEST-example.xml')
        return directory

    def test_distinguishes_failures_and_recovery(self):
        old = self.report('old', [('same', 'idle'), ('fixed', 'idle'), ('new', 'passed'), ('gone', 'failure'), ('skip', 'failure')])
        new = self.report('new', [('same', 'idle'), ('fixed', 'passed'), ('new', 'failure'), ('skip', 'skipped'), ('extra', 'passed')])
        result = compare(read_reports(old), read_reports(new))
        for category, expected in [('persistent_failures', 'same'), ('recovered', 'fixed'), ('new_failures', 'new'), ('missing', 'gone'), ('newly_skipped', 'skip'), ('added', 'extra')]:
            self.assertEqual([expected], [row['test'] for row in result[category]])
        self.assertEqual({'total': 5, 'passed': 2, 'skipped': 1, 'failed': 1, 'compose_idle': 1}, result['current_counts'])

    def test_empty_directory_rejected(self):
        with self.assertRaises(ValueError):
            read_reports(self.root)

    def test_duplicate_identity_rejected(self):
        with self.assertRaises(ValueError):
            read_reports(self.report('duplicate', [('same', 'passed'), ('same', 'failure')]))

    def test_incomplete_report_rejected(self):
        with self.assertRaises(ValueError):
            read_reports(self.report('incomplete', [('test', 'passed')], declared=2))

    def test_empty_suite_rejected(self):
        with self.assertRaises(ValueError):
            read_reports(self.report('empty', []))

    def test_same_test_different_exception_visible(self):
        old = self.report('old', [('same', 'idle')])
        new = self.report('new', [('same', 'failure')])
        row = compare(read_reports(old), read_reports(new))['persistent_failures'][0]
        self.assertEqual('compose_idle', row['baseline']['status'])
        self.assertEqual('failed', row['current']['status'])


if __name__ == '__main__':
    unittest.main()
