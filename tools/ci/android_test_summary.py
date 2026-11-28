#!/usr/bin/env python3
"""Summarize observed Gradle JUnit XML without changing the test gate."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def validated_cases(root):
    """Reject incomplete or contradictory suites before counting any of their rows."""
    if root.tag == 'testsuite':
        suites = [root]
    elif root.tag == 'testsuites':
        suites = root.findall('testsuite')
        if not suites or any(child.tag != 'testsuite' for child in root):
            raise ValueError('unexpected testsuites contents')
    else:
        raise ValueError('unexpected XML root')
    cases = []
    aggregate = dict(failures=0, errors=0, skipped=0)
    for suite in suites:
        rows = suite.findall('testcase')
        if len(list(suite.iter('testcase'))) != len(rows) or suite.findall('testsuite'):
            raise ValueError('nested testsuite or testcase')
        if int(suite.get('tests', '-1')) != len(rows):
            raise ValueError('missing or inconsistent declared testcase count')
        totals = dict(failures=0, errors=0, skipped=0)
        for case in rows:
            if not case.get('classname', '').strip() or not case.get('name', '').strip():
                raise ValueError('missing test identity')
            markers = [tag for tag in ('failure', 'error', 'skipped') for _ in case.findall(tag)]
            if len(markers) > 1:
                raise ValueError('contradictory testcase status')
            if markers:
                totals[{'failure': 'failures', 'error': 'errors', 'skipped': 'skipped'}[markers[0]]] += 1
            cases.append(case)
        for key, actual in totals.items():
            aggregate[key] += actual
            if key in suite.attrib and int(suite.attrib[key]) != actual:
                raise ValueError(f'inconsistent declared {key} count')
    if root.tag == 'testsuites':
        aggregate['tests'] = len(cases)
        for key, actual in aggregate.items():
            if key in root.attrib and int(root.attrib[key]) != actual:
                raise ValueError(f'inconsistent aggregate {key} count')
    return cases


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
            for case in validated_cases(root):
                name = case.get('classname', '') + '.' + case.get('name', '')
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



def compose_diagnostic_decision(result):
    """Only skip optional probes when the observed evidence warrants it."""
    if result['known_failures']:
        return True, 'Known Compose failure observed'
    if not result['report_files']:
        return False, 'No JUnit XML; no Compose failure was observed'
    if result['problems']:
        return True, 'Incomplete or ambiguous XML; preserve diagnostics'
    return False, 'No known Compose failure in validated XML'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('outcome')
    parser.add_argument('--github-output', type=Path)
    args = parser.parse_args()
    baseline = {line.strip() for line in args.baseline.read_text().splitlines() if line.strip()}
    result = summarize(args.directory, baseline, args.outcome)
    run, reason = compose_diagnostic_decision(result)
    result['compose_diagnostics'] = dict(run=run, reason=reason)
    print(json.dumps(result, indent=2, ensure_ascii=False))
    if args.github_output is not None:
        with args.github_output.open('a', encoding='utf-8') as output:
            output.write(f'run_compose_diagnostics={str(run).lower()}\n')


if __name__ == '__main__':
    main()
