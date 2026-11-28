#!/usr/bin/env python3
"""Compare Gradle JUnit XML reports without changing any test outcome."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

from android_test_summary import validated_cases


def read_reports(directory):
    files = sorted(Path(directory).glob('TEST-*.xml'))
    if not files:
        raise ValueError(f'No TEST-*.xml reports in {directory}')
    cases = {}
    for file in files:
        root = ET.parse(file).getroot()
        for row in validated_cases(root):
            key = (row.attrib['classname'], row.attrib['name'])
            if key in cases:
                raise ValueError(f'Duplicate testcase: {key!r}')
            problem = row.find('failure')
            if problem is None:
                problem = row.find('error')
            if problem is not None:
                kind = problem.get('type', '')
                status = 'compose_idle' if kind.endswith('.AppNotIdleException') else 'failed'
            else:
                status = 'skipped' if row.find('skipped') is not None else 'passed'
                kind = ''
            cases[key] = {'status': status, 'type': kind}
    if not cases:
        raise ValueError(f'No testcases in {directory}')
    return cases


def compare(baseline, current):
    failed = {'failed', 'compose_idle'}
    def describe(key):
        return {'class': key[0], 'test': key[1], 'baseline': baseline.get(key), 'current': current.get(key)}
    def counts(cases):
        return {'total': len(cases), **{s: sum(v['status'] == s for v in cases.values())
            for s in ('passed', 'skipped', 'failed', 'compose_idle')}}
    def is_failed(cases, key):
        return cases.get(key, {}).get('status') in failed
    keys = sorted(baseline.keys() | current.keys())
    return {
        'baseline_counts': counts(baseline), 'current_counts': counts(current),
        'new_failures': [describe(k) for k in keys if is_failed(current, k) and not is_failed(baseline, k)],
        'persistent_failures': [describe(k) for k in keys if is_failed(current, k) and is_failed(baseline, k)],
        'recovered': [describe(k) for k in keys if is_failed(baseline, k) and current.get(k, {}).get('status') == 'passed'],
        'newly_skipped': [describe(k) for k in keys if k in baseline and baseline[k]['status'] != 'skipped' and current.get(k, {}).get('status') == 'skipped'],
        'missing': [describe(k) for k in keys if k not in current],
        'added': [describe(k) for k in keys if k not in baseline],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('current', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        result = compare(read_reports(args.baseline), read_reports(args.current))
        args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    except (OSError, ValueError, KeyError, ET.ParseError) as exc:
        parser.exit(2, f'Comparison unavailable: {exc}\n')


if __name__ == '__main__':
    main()
