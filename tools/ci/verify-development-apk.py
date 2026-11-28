#!/usr/bin/env python3
"""Compare downloaded CI artifacts; does not replace apksigner or device tests."""
import argparse
import hashlib
import json
import re
import tempfile
import zipfile
from contextlib import ExitStack, contextmanager
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


def verify(apk_zip, inventory_zip, expected_commit, *, apk_zip_sha256=None,
           inventory_zip_sha256=None):
    if not re.fullmatch(r'[a-f0-9]{40}', expected_commit):
        raise ValueError('Expected full source commit SHA')
    pinned = apk_zip_sha256 is not None or inventory_zip_sha256 is not None
    if pinned:
        for expected in (apk_zip_sha256, inventory_zip_sha256):
            if not isinstance(expected, str) or not re.fullmatch(r'[a-f0-9]{64}', expected):
                raise ValueError('Both expected archive SHA-256 values are required')
    with ExitStack() as stack:
        if pinned:
            apk_zip = stack.enter_context(snapshot_archive(apk_zip, apk_zip_sha256, 'APK artifact'))
            inventory_zip = stack.enter_context(snapshot_archive(
                inventory_zip, inventory_zip_sha256, 'Inventory artifact'))
        return verify_payload(apk_zip, inventory_zip, expected_commit, pinned)


@contextmanager
def snapshot_archive(path, expected, label):
    """Hash exactly the bytes copied into a private, seekable parsing snapshot."""
    with tempfile.TemporaryFile() as snapshot, open(path, 'rb') as source:
        sha = hashlib.sha256()
        for block in iter(lambda: source.read(1024 * 1024), b''):
            sha.update(block)
            snapshot.write(block)
        if sha.hexdigest() != expected:
            raise ValueError(f'{label} SHA-256 mismatch')
        snapshot.seek(0)
        yield snapshot


def verify_payload(apk_zip, inventory_zip, expected_commit, pinned):
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
            'archive_hashes_matched': pinned, 'signature_verified_here': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk_zip', type=Path)
    parser.add_argument('inventory_zip', type=Path)
    parser.add_argument('expected_commit')
    parser.add_argument('--apk-zip-sha256', help='Trusted CI artifact ZIP SHA-256')
    parser.add_argument('--inventory-zip-sha256', help='Trusted CI inventory ZIP SHA-256')
    args = parser.parse_args()
    print(json.dumps(verify(args.apk_zip, args.inventory_zip, args.expected_commit,
                            apk_zip_sha256=args.apk_zip_sha256,
                            inventory_zip_sha256=args.inventory_zip_sha256), indent=2))


if __name__ == '__main__':
    main()
