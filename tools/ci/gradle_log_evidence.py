#!/usr/bin/env python3
"""Conservative log observations for Gradle test runs with missing XML."""
import argparse
import json
import re
from pathlib import Path

# Match Gradle's unindented, simple JUnit case headings only. Do not emit raw logs.
HEADING = re.compile(r'([A-Za-z_][A-Za-z0-9_.$]*) > ([A-Za-z_$][A-Za-z0-9_$]*(?:\[\d+\])?) (STANDARD_ERROR|STANDARD_OUT|FAILED|SKIPPED|PASSED)')
START = re.compile(r'Gradle Test Executor (\d+) started executing tests\.')
END = re.compile(r'Gradle Test Executor (\d+) finished executing tests\.')
EXIT = re.compile(r"Process 'Gradle Test Executor (\d+)' finished with non-zero exit value (\d+)")
TOTAL = re.compile(r'(\d+) tests? completed(?:, (\d+) failed)?(?:, (\d+) skipped)?')


def summarize_log(lines):
    active = set()
    last_heading = {}
    failed, skipped, exits, totals = set(), set(), set(), set()
    for line in lines:
        line = line.rstrip('\r\n')
        if m := START.fullmatch(line):
            active.add(int(m[1]))
        elif m := END.fullmatch(line):
            active.discard(int(m[1]))
        elif m := HEADING.fullmatch(line):
            identity = m[1] + '.' + m[2]
            if len(active) == 1:
                last_heading[next(iter(active))] = identity
            if m[3] == 'FAILED':
                failed.add(identity)
            elif m[3] == 'SKIPPED':
                skipped.add(identity)
        elif m := TOTAL.fullmatch(line):
            totals.add(tuple(int(v or 0) for v in m.groups()))
        for m in EXIT.finditer(line):
            exits.add((int(m[1]), int(m[2])))
    return {
        'reported_totals': [dict(completed=t, failed=f, skipped=s) for t, f, s in sorted(totals)],
        'observed_failed_headings': sorted(failed),
        'observed_skipped_headings': sorted(skipped),
        'nonzero_test_worker_exits': [dict(worker=w, exit_code=c, last_observed_heading=last_heading.get(w))
                                      for w, c in sorted(exits)],
        'note': 'Partial log observations only; not XML counts, pass evidence, or proof of crash cause.'
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    args = parser.parse_args()
    with args.log.open(encoding='utf-8', errors='replace') as lines:
        print(json.dumps(summarize_log(lines), indent=2))


if __name__ == '__main__':
    main()
