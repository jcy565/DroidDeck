#!/usr/bin/env python3
"""Record evidence for each filtered Compose diagnostic, never a release gate."""
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

from android_test_summary import validated_cases


def summarize(directory, selectors, exit_code):
    observed = {}
    problems = []
    files = sorted((directory / "results").glob("TEST-*.xml"))
    if not files:
        problems.append("No JUnit XML reports found")
    for path in files:
        try:
            cases = validated_cases(ET.parse(path).getroot())
        except (ET.ParseError, OSError, ValueError) as exc:
            problems.append(f"{path.name}: {exc}")
            continue
        for case in cases:
            cls, method = case.get("classname"), case.get("name")
            name = f"{cls}.{method}"
            status = next((value for tag, value in (
                ("error", "error"), ("failure", "failed"), ("skipped", "skipped")
            ) if case.find(tag) is not None), "passed")
            if name in observed:
                problems.append(f"Duplicate testcase: {name}")
            observed.setdefault(name, []).append(status)

    matched = {selector: sorted(name for name in observed
                               if name == selector or name.rpartition(".")[0] == selector)
               for selector in selectors}
    unexpected = sorted(set(observed) - {name for names in matched.values() for name in names})
    missing = [selector for selector, names in matched.items() if not names]
    counts = {status: sum(values.count(status) for values in observed.values())
              for status in ("passed", "failed", "error", "skipped")}
    return {
        "gradle_exit_code": exit_code, "report_files": len(files), "counts": counts,
        "selectors": matched, "missing_selectors": missing, "unexpected_cases": unexpected,
        "problems": problems, "observed": observed,
        "all_selectors_observed_passing": bool(selectors) and exit_code == 0
        and not (problems or missing or unexpected or counts["failed"] or counts["error"] or counts["skipped"]),
    }


def main():
    directory = Path(sys.argv[1])
    selectors = sys.argv[3:]
    print(json.dumps(summarize(directory, selectors, int(sys.argv[2])), indent=2))


if __name__ == "__main__":
    main()
