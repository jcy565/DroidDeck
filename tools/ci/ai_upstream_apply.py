"""Apply verified repair proposals to a disposable Git index, not a branch."""
import argparse
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path

from ai_upstream_candidate import candidate, category
from ai_upstream_preview import changed, git
from ai_upstream_repair import propose, read_blob


def apply_candidate(repo, ai, upstream):
    report = propose(repo, ai, upstream)
    report['candidate_tree'] = None
    report['candidate_status'] = 'blocked'
    report['repair_applied_to_candidate'] = False
    if any(category(p) == 'manual_review' for p in report['upstream_changed']):
        return report
    if not report['conflicts']:
        clean = candidate(repo, ai, upstream)
        for key in ('candidate_tree', 'candidate_status', 'candidate_delta'):
            if key in clean:
                report[key] = clean[key]
        return report
    repairs = {r['path']: r for r in report['repair_suggestions']}
    if report['unresolved_conflicts'] or set(repairs) != set(report['conflicts']):
        return report
    # Recompute from pinned inputs; do not consume externally supplied repair data.
    result = git(repo, 'merge-tree', '--write-tree', '--name-only', '--no-messages',
                 '-z', report['ai'], report['upstream'], allowed=(1,))
    tree = result.stdout.split(b'\0', 1)[0].decode('ascii').strip()
    if not re.fullmatch(r'[0-9a-f]{40}', tree):
        raise ValueError('Invalid merge tree')
    with tempfile.TemporaryDirectory(prefix='dd-repair-index-') as tmp:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(tmp) / 'index'))

        def index(*args):
            return subprocess.check_output(['git', '-C', str(repo), *args], env=env)

        index('read-tree', tree)
        for path, repair in sorted(repairs.items()):
            index('update-index', '--add', '--cacheinfo', '100644', repair['selected_blob'], path)
        repaired = index('write-tree').decode('ascii').strip()
        if not re.fullmatch(r'[0-9a-f]{40}', repaired):
            raise ValueError('Invalid repaired tree')
    if not changed(repo, tree, repaired) <= set(repairs):
        raise ValueError('Unexpected changes during repair')
    for path, repair in repairs.items():
        blob = read_blob(repo, repaired, path)
        if blob is None or blob[0] != repair['selected_blob']:
            raise ValueError('Repaired blob mismatch')
    delta = sorted(changed(repo, report['ai'], repaired))
    report['candidate_delta'] = delta
    if any(category(p) == 'manual_review' for p in delta):
        return report
    report['repair_applied_to_candidate'] = True
    if not delta:
        report['candidate_status'] = 'no_change'
    else:
        report['candidate_tree'] = repaired
        report['candidate_status'] = 'regression_required'
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--ai', required=True)
    parser.add_argument('--upstream', required=True)
    args = parser.parse_args()
    report = apply_candidate(args.repo, args.ai, args.upstream)
    print(json.dumps(report, ensure_ascii=True, indent=2))
    return 1 if report['candidate_status'] == 'blocked' else 0


if __name__ == '__main__':
    raise SystemExit(main())
