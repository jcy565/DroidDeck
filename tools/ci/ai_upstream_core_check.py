"""Compile the verified candidate's Java AI core in a disposable directory."""
import argparse
import json
import re
import subprocess
import tempfile
from pathlib import Path

from ai_upstream_artifact import verify
from ai_upstream_preview import git

PREFIX = 'ai-core/src/main/java/'
MAX_SOURCE = 512 * 1024
MAX_TOTAL = 8 * 1024 * 1024


def compile_tree(repo, tree):
    raw = git(repo, 'ls-tree', '-r', '-z', tree, '--', PREFIX).stdout
    with tempfile.TemporaryDirectory(prefix='dd-core-check-') as tmp:
        root = Path(tmp)
        sources, total = [], 0
        for entry in raw.split(b'\0'):
            if not entry:
                continue
            meta, path = entry.split(b'\t', 1)
            mode, kind, oid = meta.decode('ascii').split()
            name = path.decode('ascii')
            if not name.startswith(PREFIX) or not re.fullmatch(r'[A-Za-z0-9_./-]+', name):
                raise ValueError('Unexpected source path')
            relative = Path(name[len(PREFIX):])
            if relative.is_absolute() or '..' in relative.parts or mode != '100644' or kind != 'blob':
                raise ValueError('Unexpected source entry')
            if relative.suffix != '.java':
                raise ValueError('Unexpected non-Java source entry')
            size = int(git(repo, 'cat-file', '-s', oid).stdout)
            total += size
            if size > MAX_SOURCE or total > MAX_TOTAL:
                raise ValueError('Source size limit exceeded')
            data = git(repo, 'cat-file', 'blob', oid).stdout
            data.decode('utf-8')
            target = root / 'src' / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            sources.append(target)
        if not sources:
            raise ValueError('No Java AI core sources')
        for directory in ('classes', 'empty'):
            (root / directory).mkdir()
        result = subprocess.run(['javac', '--release', '17', '-proc:none', '-encoding', 'UTF-8',
                                 '-classpath', str(root / 'empty'), '-sourcepath', str(root / 'empty'),
                                 '-d', str(root / 'classes'), *map(str, sorted(sources))],
                                cwd=root, capture_output=True, text=True, timeout=90)
        return {'source_count': len(sources), 'compile_exit_code': result.returncode,
                'compile_status': 'passed' if result.returncode == 0 else 'failed',
                'compiler_output': (result.stdout + result.stderr)[-12000:],
                'android_build_verified': False, 'release_approved': False}


def check(repo, ai, upstream, artifact):
    manifest = verify(repo, ai, upstream, artifact)
    return dict(compile_tree(repo, manifest['candidate_tree']),
                candidate_tree=manifest['candidate_tree'], ai=manifest['ai'], upstream=manifest['upstream'])


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, default=Path('.'))
    p.add_argument('--ai', required=True)
    p.add_argument('--upstream', required=True)
    p.add_argument('--artifact', type=Path, required=True)
    args = p.parse_args()
    report = check(args.repo, args.ai, args.upstream, args.artifact)
    print(json.dumps(report, indent=2))
    return 0 if report['compile_status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
