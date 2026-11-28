#!/usr/bin/env python3
"""Compare downloaded CI artifacts; does not replace apksigner or device tests."""
import argparse
import hashlib
import json
import re
import subprocess
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
           inventory_zip_sha256=None, apksigner=None, expected_cert_sha256=None):
    if not re.fullmatch(r'[a-f0-9]{40}', expected_commit):
        raise ValueError('Expected full source commit SHA')
    pinned = apk_zip_sha256 is not None or inventory_zip_sha256 is not None
    if pinned:
        for expected in (apk_zip_sha256, inventory_zip_sha256):
            if not isinstance(expected, str) or not re.fullmatch(r'[a-f0-9]{64}', expected):
                raise ValueError('Both expected archive SHA-256 values are required')
    check_signature = apksigner is not None or expected_cert_sha256 is not None
    if check_signature and (not pinned or apksigner is None
                            or not isinstance(expected_cert_sha256, str)
                            or not re.fullmatch(r'[a-f0-9]{64}', expected_cert_sha256)):
        raise ValueError('Signature verification requires both archive pins, apksigner and expected certificate SHA-256')
    with ExitStack() as stack:
        if pinned:
            apk_zip = stack.enter_context(snapshot_archive(apk_zip, apk_zip_sha256, 'APK artifact'))
            inventory_zip = stack.enter_context(snapshot_archive(
                inventory_zip, inventory_zip_sha256, 'Inventory artifact'))
        return verify_payload(apk_zip, inventory_zip, expected_commit, pinned,
                              apksigner, expected_cert_sha256)


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


def reject_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'Duplicate JSON key: {key}')
        result[key] = value
    return result


def inventory_entries(raw):
    document = json.loads(raw, object_pairs_hook=reject_duplicate_keys)
    entries = document.get('entries') if isinstance(document, dict) else None
    if not isinstance(entries, dict) or not entries:
        raise ValueError('Missing or invalid APK inventory entries')
    for name, entry in entries.items():
        if (not name or not isinstance(entry, dict)
                or type(entry.get('size')) is not int or entry['size'] < 0
                or not isinstance(entry.get('sha256'), str)
                or not re.fullmatch(r'[a-f0-9]{64}', entry['sha256'])):
            raise ValueError(f'Invalid APK inventory entry: {name}')
    return entries


def verify_signer(apksigner, apk_path, expected_cert_sha256):
    report = subprocess.run([str(apksigner), 'verify', '--verbose', '--print-certs', str(apk_path)],
                            check=True, capture_output=True, text=True, timeout=60).stdout
    signers = re.findall(r'^Signer #([0-9]+) certificate SHA-256 digest: ([a-fA-F0-9]+)$',
                         report, re.MULTILINE)
    if (len(signers) != 1 or signers[0][0] != '1'
            or signers[0][1].lower() != expected_cert_sha256):
        raise ValueError('Unexpected signing certificate or signer count')
    if not re.search(r'^Verified using v2 scheme .*: true$', report, re.MULTILINE):
        raise ValueError('APK v2 signature verification required')


def read_metadata(archive, name, limit):
    """Reject missing or oversized metadata before allocating its full contents."""
    try:
        entry = archive.getinfo(name)
    except KeyError as exc:
        raise ValueError(f'Missing APK metadata: {name}') from exc
    if entry.is_dir() or entry.file_size > limit:
        raise ValueError(f'Invalid or oversized APK metadata: {name}')
    with archive.open(entry) as stream:
        raw = stream.read(limit + 1)
    if len(raw) > limit:
        raise ValueError(f'Oversized APK metadata: {name}')
    return raw


def verify_payload(apk_zip, inventory_zip, expected_commit, pinned,
                   apksigner, expected_cert_sha256):
    with zipfile.ZipFile(inventory_zip) as inv:
        unique_files(inv)
        if read_metadata(inv, 'source-commit.txt', 4096).decode().strip() != expected_commit:
            raise ValueError('Source commit mismatch')
        hash_fields = read_metadata(inv, 'apk-sha256.txt', 4096).decode().split()
        expected_hash = hash_fields[0] if hash_fields else ''
        if not re.fullmatch(r'[a-f0-9]{64}', expected_hash):
            raise ValueError('Invalid APK SHA-256')
        entries = inventory_entries(read_metadata(inv, 'app-release.apk.json', 16 * 1024 * 1024))
        comparison = json.loads(read_metadata(inv, 'official-comparison.json', 4 * 1024 * 1024),
                                object_pairs_hook=reject_duplicate_keys)
        assistant = comparison.get('assistant') if isinstance(comparison, dict) else None
        if not isinstance(assistant, dict) or assistant.get('package') != 'com.droiddeck.launcher.ai':
            raise ValueError('Unexpected inventory package')
    with zipfile.ZipFile(apk_zip) as outer, tempfile.TemporaryDirectory() as directory:
        apk_path = Path(directory) / 'app-release.apk'
        with apk_path.open('w+b') as apk:
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
        if apksigner is not None:
            verify_signer(apksigner, apk_path, expected_cert_sha256)
    return {'source_commit': expected_commit, 'apk_sha256': expected_hash,
            'verified_entries': len(entries), 'native_abis': abis,
            'package_from_ci_inventory': comparison['assistant']['package'],
            'archive_hashes_matched': pinned, 'signature_verified_here': apksigner is not None,
            'verified_certificate_sha256': expected_cert_sha256 if apksigner is not None else None}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk_zip', type=Path)
    parser.add_argument('inventory_zip', type=Path)
    parser.add_argument('expected_commit')
    parser.add_argument('--apk-zip-sha256', help='Trusted CI artifact ZIP SHA-256')
    parser.add_argument('--inventory-zip-sha256', help='Trusted CI inventory ZIP SHA-256')
    parser.add_argument('--apksigner', type=Path, help='Trusted Android SDK apksigner executable')
    parser.add_argument('--expected-cert-sha256', help='Independently trusted signer SHA-256')
    args = parser.parse_args()
    print(json.dumps(verify(args.apk_zip, args.inventory_zip, args.expected_commit,
                            apk_zip_sha256=args.apk_zip_sha256,
                            inventory_zip_sha256=args.inventory_zip_sha256,
                            apksigner=args.apksigner,
                            expected_cert_sha256=args.expected_cert_sha256), indent=2))


if __name__ == '__main__':
    main()
