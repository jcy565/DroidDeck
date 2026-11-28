"""Suggest narrow, lossless line-ending conflict repairs; never apply them."""
import argparse
import hashlib
import json
import re
from pathlib import Path

from ai_upstream_candidate import category
from ai_upstream_preview import git, preview

MAX_BYTES = 256 * 1024


def read_blob(repo, revision, path):
    # Only regular non-executable files with a bounded UTF-8 text payload.
    raw = git(repo, 'ls-tree', '-z', revision, '--', path).stdout
    entries = raw.rstrip(b'\0').split(b'\0')
    if len(entries) != 1 or b'\t' not in entries[0]:
        return None
    meta, name = entries[0].split(b'\t', 1)
    mode, kind, oid = meta.decode('ascii').split()
    if mode != '100644' or kind != 'blob' or name != path.encode('utf-8', 'surrogateescape'):
        return None
    if int(git(repo, 'cat-file', '-s', oid).stdout) > MAX_BYTES:
        return None
    data = git(repo, 'cat-file', 'blob', oid).stdout
    if b'\0' in data:
        return None
    try:
        data.decode('utf-8')
    except UnicodeDecodeError:
        return None
    return oid, data


def normalized(data):
    return data.replace(b'\r\n', b'\n')


def choose(base, ours, theirs):
    if normalized(ours) == normalized(theirs):
        return 'ours'
    if normalized(ours) == normalized(base):
        return 'theirs'
    if normalized(theirs) == normalized(base):
        return 'ours'
    return None


def propose(repo, ai, upstream):
    report = preview(repo, ai, upstream)
    suggestions, unresolved = [], []
    for path in report['conflicts']:
        # Only simple paths in the documented allowlist. No pathspec magic.
        if not re.fullmatch(r'[A-Za-z0-9_./-]+', path) or category(path) == 'manual_review':
            unresolved.append(path)
            continue
        blobs = [read_blob(repo, rev, path) for rev in (report['merge_base'], report['ai'], report['upstream'])]
        if any(blob is None for blob in blobs):
            unresolved.append(path)
            continue
        base, ours, theirs = blobs
        side = choose(base[1], ours[1], theirs[1])
        if side is None:
            unresolved.append(path)
            continue
        selected = ours if side == 'ours' else theirs
        suggestions.append({'path': path, 'rule': 'line_endings_only', 'selected_side': side,
                            'base_blob': base[0], 'ai_blob': ours[0], 'upstream_blob': theirs[0],
                            'selected_blob': selected[0], 'sha256': hashlib.sha256(selected[1]).hexdigest()})
    report['repair_suggestions'] = suggestions
    report['unresolved_conflicts'] = unresolved
    report['repair_applied'] = False
    report['release_approved'] = False
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--ai', required=True)
    parser.add_argument('--upstream', required=True)
    args = parser.parse_args()
    report = propose(args.repo, args.ai, args.upstream)
    print(json.dumps(report, ensure_ascii=True, indent=2))
    return 1 if report['conflicts'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
