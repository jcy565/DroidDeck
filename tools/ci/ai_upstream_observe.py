"""Observe a pinned upstream revision; never promote or modify a Git ref."""
import argparse
import hashlib
import json
import re
from pathlib import Path

from ai_upstream_preview import commit, git


def observe(repo, baseline, ai, upstream, policy, previous=None):
    baseline, ai, upstream = (commit(repo, x) for x in (baseline, ai, upstream))
    if not re.fullmatch(r'[0-9a-f]{64}', policy):
        raise ValueError('Expected a SHA-256 policy digest')
    key = hashlib.sha256('\n'.join((baseline, ai, upstream, policy)).encode('ascii')).hexdigest()
    forward = git(repo, 'merge-base', '--is-ancestor', baseline, upstream, allowed=(0, 1)).returncode == 0
    if upstream == baseline:
        status = 'unchanged'
    elif not forward:
        status = 'history_review_required'
    else:
        status = 'new_update'
    # Prior reports are advisory, not evidence of a successful build or a reason to skip checks.
    repeat = isinstance(previous, dict) and all(previous.get(k) == v for k, v in {
        'schema': 1, 'baseline': baseline, 'ai': ai, 'upstream': upstream,
        'policy_sha256': policy, 'observation_key': key, 'status': status,
        'release_approved': False}.items())
    return {'schema': 1, 'baseline': baseline, 'ai': ai, 'upstream': upstream,
            'policy_sha256': policy, 'observation_key': key, 'status': status,
            'repeat_observation': repeat, 'requires_preview': status == 'new_update',
            'last_good_changed': False, 'release_approved': False}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, default=Path('.'))
    p.add_argument('--baseline', required=True)
    p.add_argument('--ai', required=True)
    p.add_argument('--upstream', required=True)
    p.add_argument('--policy', required=True)
    p.add_argument('--previous', type=Path)
    args = p.parse_args()
    prior = json.loads(args.previous.read_text()) if args.previous else None
    report = observe(args.repo, args.baseline, args.ai, args.upstream, args.policy, prior)
    print(json.dumps(report, indent=2))
    return 1 if report['status'] == 'history_review_required' else 0


if __name__ == '__main__':
    raise SystemExit(main())
