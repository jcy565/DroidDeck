"""Classify a pinned upstream merge; never apply or publish it."""
import argparse
import json
from pathlib import Path

from ai_upstream_preview import git, preview, changed, sensitive


def category(path):
    lower = path.lower()
    if sensitive(path) or any(term in lower for term in (
        'security', 'credential', 'token', 'auth', 'accessibility', 'storage',
        'runtimeinstaller', 'app.kt', 'sessionservice', 'frontendactivity',
        'sessionactivity', 'gradlew', 'proguard', 'cmakelists', 'makefile',
    )):
        return 'manual_review'
    if lower.startswith('docs/') and lower.endswith('.md'):
        return 'documentation'
    if lower.startswith(('app/src/main/java/', 'ai-core/src/main/java/')) and lower.endswith(('.kt', '.java')):
        return 'regression_required'
    return 'manual_review'


def candidate(repo, ai, upstream):
    report = preview(repo, ai, upstream)
    groups = {key: [] for key in ('documentation', 'regression_required', 'manual_review')}
    # Classify incoming changes, not historical AI-only changes.
    for path in report['upstream_changed']:
        groups[category(path)].append(path)
    report['incoming_classification'] = groups
    report['candidate_tree'] = None
    report['candidate_status'] = 'blocked'
    report['release_approved'] = False
    if report['conflicts'] or groups['manual_review']:
        return report
    # Recompute the pinned merge, require a clean tree and classify its actual delta.
    result = git(repo, 'merge-tree', '--write-tree', '--name-only', '--no-messages',
                 '-z', report['ai'], report['upstream'])
    tree = result.stdout.rstrip(b'\0\n').decode('ascii')
    delta = sorted(changed(repo, report['ai'], tree))
    report['candidate_delta'] = delta
    if any(category(p) == 'manual_review' for p in delta):
        return report
    if not delta:
        report['candidate_status'] = 'no_change'
        return report
    report['candidate_tree'] = tree
    report['candidate_status'] = 'regression_required'
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--ai', required=True)
    parser.add_argument('--upstream', required=True)
    args = parser.parse_args()
    report = candidate(args.repo, args.ai, args.upstream)
    print(json.dumps(report, ensure_ascii=True, indent=2))
    return 1 if report['candidate_status'] == 'blocked' else 0


if __name__ == '__main__':
    raise SystemExit(main())
