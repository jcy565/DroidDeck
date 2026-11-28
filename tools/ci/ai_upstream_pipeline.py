"""Link upstream observation to an unapproved candidate and core compile check."""
import argparse
import json
from pathlib import Path

from ai_upstream_observe import observe
from ai_upstream_apply import apply_candidate
from ai_upstream_artifact import export
from ai_upstream_core_check import check
from ai_upstream_preview import git


def write(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=True) + '\n', encoding='utf-8')


def run(repo, baseline, ai, upstream, policy, output):
    observation = observe(repo, baseline, ai, upstream, policy)
    if git(repo, 'merge-base', '--is-ancestor', observation['baseline'],
           observation['ai'], allowed=(0, 1)).returncode != 0:
        raise ValueError('AI input does not contain the pinned integration baseline')
    output.mkdir(parents=True, exist_ok=False)
    write(output / 'observation.json', observation)
    result = {'schema': 1, 'observation_key': observation['observation_key'],
              'ai': observation['ai'], 'upstream': observation['upstream'],
              'status': observation['status'], 'candidate_tree': None,
              'android_build_verified': False, 'last_good_changed': False,
              'release_approved': False}
    if observation['requires_preview']:
        candidate = apply_candidate(repo, ai, upstream)
        write(output / 'candidate.json', candidate)
        result['status'] = candidate['candidate_status']
        if result['status'] == 'regression_required':
            artifact = output / 'verified-candidate'
            manifest = export(repo, ai, upstream, artifact)
            if manifest['candidate_tree'] != candidate['candidate_tree']:
                raise ValueError('Candidate tree changed between checks')
            compiled = check(repo, ai, upstream, artifact)
            write(output / 'core-check.json', compiled)
            if compiled['candidate_tree'] != manifest['candidate_tree']:
                raise ValueError('Compiled tree differs from candidate')
            result['candidate_tree'] = manifest['candidate_tree']
            result['status'] = ('android_regression_required' if compiled['compile_status'] == 'passed'
                                else 'core_compile_failed')
    write(output / 'pipeline.json', result)
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, default=Path('.'))
    for key in ('baseline', 'ai', 'upstream', 'policy'):
        p.add_argument('--' + key, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    result = run(a.repo, a.baseline, a.ai, a.upstream, a.policy, a.output)
    print(json.dumps(result, indent=2))
    return 0 if result['status'] in ('unchanged', 'no_change', 'android_regression_required') else 1


if __name__ == '__main__':
    raise SystemExit(main())
