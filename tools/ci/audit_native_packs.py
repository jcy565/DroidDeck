"""Report staged pack hashes without executing or extracting archives."""
import hashlib
import json
import re
import stat
from pathlib import Path

PACKS = ('proot', 'directaudio', 'tracking', 'xbox', 'comet', 'butler',
         'powervr', 'ubisoft', 'rockstar', 'dirt', 'epic')


def proot_pin(root):
    values = {}
    for line in (root / 'tools/packs/proot/release.env').read_text(encoding='utf-8').splitlines():
        if not line.strip() or line.startswith('#'):
            continue
        key, value = line.split('=', 1)
        if key in values:
            raise ValueError('Duplicate pack pin')
        values[key] = value
    digest = values['PROOT_PACK_SHA256']
    size = values['PROOT_PACK_SIZE']
    if not re.fullmatch(r'[0-9a-f]{64}', digest) or not re.fullmatch(r'[1-9][0-9]*', size):
        raise ValueError('Invalid proot pin')
    return digest, int(size)


def inspect(root):
    root = root.resolve(strict=True)
    pack_dir = root / 'app/build/packs'
    if pack_dir.resolve() != pack_dir:
        raise ValueError('Pack directory contains a symbolic link')
    expected = proot_pin(root)
    records = []
    for name in PACKS:
        path = pack_dir / (name + '.tar.zst')
        record = {'name': name, 'path': path.relative_to(root).as_posix(),
                  'status': 'missing', 'sha256': None, 'bytes': None}
        try:
            info = path.lstat()
        except FileNotFoundError:
            records.append(record)
            continue
        if not stat.S_ISREG(info.st_mode):
            record['status'] = 'invalid_file'
        else:
            with path.open('rb') as source:
                digest = hashlib.file_digest(source, 'sha256').hexdigest()
            record.update(sha256=digest, bytes=info.st_size)
            if name == 'proot':
                record['status'] = 'pin_match' if (digest, info.st_size) == expected else 'pin_mismatch'
            else:
                record['status'] = 'unreviewed_hash'
        records.append(record)
    return {'scope': 'Staged pack bytes only; no archive execution, provenance or malware verdict',
            'packs': records, 'release_approved': False}


if __name__ == '__main__':
    report = inspect(Path(__file__).resolve().parents[2])
    print(json.dumps(report, indent=2))
    if any(p['status'] in ('pin_mismatch', 'invalid_file') for p in report['packs']):
        raise SystemExit(1)
