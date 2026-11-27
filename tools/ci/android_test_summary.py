#!/usr/bin/env python3
"""Summarize observed Gradle JUnit XML without changing the test gate."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def summarize(directory, baseline, outcome):
    observed = {}
    problems = []
    duplicates = set()
    counts = dict(passed=0, skipped=0, failed=0, error=0)
    files = sorted(directory.glob('TEST-*.xml'))
    if not files:
        problems.append('No JUnit XML reports found')
    for path in files:
        try:
            root = ET.parse(path).getroot()
            if root.tag not in ('testsuite', 'testsuites'):
                raise ValueError('unexpected XML root')
            for case in root.iter('testcase'):
                name = case.get('classname', '') + '.' + case.get('name', '')
                if not case.get('classname') or not case.get('name'):
                    problems.append(f'{path.name}: missing test identity')
                    continue
                status = next((status for tag, status in [('error', 'error'), ('failure', 'failed'), ('skipped', 'skipped')]
                               if case.find(tag) is not None), 'passed')
                counts[status] += 1
                if name in observed:
                    problems.append(f'duplicate test identity: {name}')
                    duplicates.add(name)
                observed.setdefault(name, set()).add(status)
        except (ET.ParseError, OSError, ValueError) as exc:
            problems.append(f'{path.name}: {exc}')
    failed = {name for name, statuses in observed.items() if statuses & {'failed', 'error'}}
    return dict(gradle_outcome=outcome, report_files=len(files), counts=counts,
                observed_cases=sum(counts.values()), known_failures=sorted(failed & baseline),
                other_failures=sorted(failed - baseline),
                baseline_observed_passing=sorted(name for name in baseline if observed.get(name) == {'passed'} and name not in duplicates),
                baseline_skipped_or_ambiguous=sorted(name for name in baseline & observed.keys()
                    if name not in failed and (observed[name] != {'passed'} or name in duplicates)),
                baseline_not_observed=sorted(baseline - observed.keys()),
                problems=problems,
                clean_observed_reports=bool(files) and not problems and bool(observed),
                note='Observed XML only; absent cases are not passes. Gradle outcome remains authoritative.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('outcome')
    args = parser.parse_args()
    baseline = {line.strip() for line in args.baseline.read_text().splitlines() if line.strip()}
    print(json.dumps(summarize(args.directory, baseline, args.outcome), indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main()
