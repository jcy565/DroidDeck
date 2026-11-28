"""Fail closed when apksigner's reported certificate is missing or ambiguous."""
import argparse
from pathlib import Path
import re


def single_certificate(report):
    lines = [line for line in report.splitlines()
             if 'certificate sha-256 digest:' in line.lower()]
    if len(lines) != 1:
        raise ValueError('Unexpected signing certificate or signer count')
    match = re.fullmatch(r'(Signer #1 |V3\.[012] Signer: )certificate SHA-256 digest: ([a-fA-F0-9]{64})',
                         lines[0], re.IGNORECASE)
    counts = [line for line in report.splitlines() if line.startswith('Number of signers:')]
    if (not match or (counts and counts != ['Number of signers: 1'])
            or (match.group(1).lower().startswith('v3.') and not counts)):
        raise ValueError('Unexpected signing certificate or signer count')
    return match.group(2).lower()


def require_certificate(report, expected):
    if single_certificate(report) != expected:
        raise ValueError('Unexpected signing certificate or signer count')
    if not re.search(r'^Verified using v2 scheme .*: true$', report, re.MULTILINE):
        raise ValueError('APK v2 signature verification required')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('expected')
    args = parser.parse_args()
    require_certificate(args.report.read_text(), args.expected)
