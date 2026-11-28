#!/usr/bin/env python3
"""Compare downloaded CI artifacts; does not replace apksigner or device tests."""
import argparse
import hashlib
import json
import re
import tempfile
import zipfile
from pathlib import Path


def unique_files(archive):
    files = [entry for entry in archive.infolist() if not entry.is_dir()]
    if len(files) != len({entry.filename for entry in files}):
        raise ValueError('Duplicate ZIP entry')
    return {entry.filename: entry for entry in files}


def digest(stream):
    sha = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        sha.update(block)
    return sha.hexdigest()


def verify(apk_zip, inventory_zip, expected_commit):
    if not re.fullmatch(r'[a-f0-9]{40}', expected_commit):
        raise ValueError('Expected full source commit SHA')
    with zipfile.ZipFile(inventory_zip) as inv:
        unique_files(inv)
        if inv.read('source-commit.txt').decode().strip() != expected_commit:
            raise ValueError('Source commit mismatch')
        expected_hash = inv.read('apk-sha256.txt').decode().split()[0]
        if not re.fullmatch(r'[a-f0-9]{64}', expected_hash):
            raise ValueError('Invalid APK SHA-256')
        entries = json.loads(inv.read('app-release.apk.json'))['entries']
        comparison = json.loads(inv.read('official-comparison.json'))
        if comparison['assistant']['package'] != 'com.droiddeck.launcher.ai':
            raise ValueError('Unexpected inventory package')
    with zipfile.ZipFile(apk_zip) as outer, tempfile.TemporaryFile() as apk:
        members = unique_files(outer)
        if set(members) != {'app-release.apk'}:
            raise ValueError('Unexpected APK artifact contents')
        with outer.open(members['app-release.apk']) as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                apk.write(block)
        apk.seek(0)
        if digest(apk) != expected_hash:
            raise ValueError('APK SHA-256 mismatch')
        with zipfile.ZipFile(apk) as inner:
            files = unique_files(inner)
            if set(files) != set(entries):
                raise ValueError('APK inventory entry set mismatch')
            for name, entry in files.items():
                with inner.open(entry) as stream:
                    if entry.file_size != entries[name]['size'] or digest(stream) != entries[name]['sha256']:
                        raise ValueError(f'APK inventory mismatch: {name}')
            abis = sorted({name.split('/')[1] for name in files if name.startswith('lib/') and name.count('/') >= 2})
    return {'source_commit': expected_commit, 'apk_sha256': expected_hash,
            'verified_entries': len(entries), 'native_abis': abis,
            'package_from_ci_inventory': comparison['assistant']['package'],
            'signature_verified_here': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk_zip', type=Path)
    parser.add_argument('inventory_zip', type=Path)
    parser.add_argument('expected_commit')
    args = parser.parse_args()
    print(json.dumps(verify(args.apk_zip, args.inventory_zip, args.expected_commit), indent=2))


if __name__ == '__main__':
    main()
