"""Persist a reproducible, unapproved merge candidate as a Git binary patch."""
import argparse
import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path

from ai_upstream_apply import apply_candidate
from ai_upstream_preview import git

MAX_PATCH = 16 * 1024 * 1024


def replay(repo, ai, patch, expected_tree):
    if not patch or len(patch) > MAX_PATCH:
        raise ValueError('Invalid patch size')
    with tempfile.TemporaryDirectory(prefix='dd-candidate-replay-') as tmp:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(tmp) / 'index'))
        def index(*args, data=None):
            return subprocess.run(['git', '-C', str(repo), *args], env=env,
                                  input=data, capture_output=True, check=True).stdout
        index('read-tree', ai)
        index('apply', '--cached', '--binary', '--whitespace=nowarn', '-', data=patch)
        tree = index('write-tree').decode('ascii').strip()
    if tree != expected_tree:
        raise ValueError('Replayed tree does not match independently computed candidate')
    return tree


def build(repo, ai, upstream):
    report = apply_candidate(repo, ai, upstream)
    if report['candidate_status'] != 'regression_required' or report['release_approved'] is not False:
        raise ValueError('No eligible candidate; blocked or unchanged')
    tree = report['candidate_tree']
    patch = git(repo, 'diff', '--binary', '--full-index', '--no-renames', '--no-ext-diff',
                '--no-textconv', '--src-prefix=a/', '--dst-prefix=b/', report['ai'], tree, '--').stdout
    replay(repo, report['ai'], patch, tree)
    manifest = {'schema': 1, 'ai': report['ai'], 'upstream': report['upstream'],
                'candidate_tree': tree, 'patch_sha256': hashlib.sha256(patch).hexdigest(),
                'patch_bytes': len(patch), 'candidate_status': 'regression_required',
                'release_approved': False}
    return manifest, patch


def export(repo, ai, upstream, output):
    manifest, patch = build(repo, ai, upstream)
    output.mkdir(parents=True, exist_ok=False)
    (output / 'candidate.patch').write_bytes(patch)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    return manifest


def verify(repo, ai, upstream, output):
    expected, _ = build(repo, ai, upstream)
    manifest = json.loads((output / 'manifest.json').read_text(encoding='utf-8'))
    if manifest != expected or manifest.get('release_approved') is not False:
        raise ValueError('Manifest differs from independently computed candidate')
    path = output / 'candidate.patch'
    if path.stat().st_size != expected['patch_bytes']:
        raise ValueError('Patch size mismatch')
    patch = path.read_bytes()
    if hashlib.sha256(patch).hexdigest() != expected['patch_sha256']:
        raise ValueError('Patch digest mismatch')
    replay(repo, expected['ai'], patch, expected['candidate_tree'])
    return expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('export', 'verify'))
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--ai', required=True)
    parser.add_argument('--upstream', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    action = export if args.mode == 'export' else verify
    print(json.dumps(action(args.repo, args.ai, args.upstream, args.output), indent=2))


if __name__ == '__main__':
    main()
