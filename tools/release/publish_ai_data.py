#!/usr/bin/env python3
"""Create signed DDAG or DDAM data manifests locally with an existing RSA PEM key.
No network, key generation, model execution, or APK signing.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import struct
import subprocess
import tempfile

MAX_MODEL = 1024 * 1024 * 1024
MAX_GLOSSARY = 65536


def positive(value):
    n = int(value)
    if not 1 <= n <= 2147483647:
        raise argparse.ArgumentTypeError('Expected positive int32')
    return n


def glossary_header(path, revision, min_host, max_host):
    with path.open('rb') as stream:
        payload = stream.read(MAX_GLOSSARY + 1)
    if not 1 <= len(payload) <= MAX_GLOSSARY:
        raise ValueError('Glossary size out of range')
    rows = payload.decode('utf-8', errors='strict').split('\n')
    if len(rows) > 500:
        raise ValueError('Too many glossary entries')
    seen = set()
    for row in rows:
        fields = row.split('\t')
        if len(fields) != 2:
            raise ValueError('Expected exactly two tab-separated columns')
        source, translated = fields
        if (not re.fullmatch(r"[a-z0-9][a-z0-9 .!?'-]{0,79}", source)
                or source != source.strip() or source in seen
                or not translated or len(translated.encode('utf-16-le')) // 2 > 160
                or translated != translated.strip()
                or any(ord(c) < 32 or 127 <= ord(c) <= 159 or c in '{}%\\<>' for c in translated)):
            raise ValueError('Invalid or duplicate glossary entry')
        seen.add(source)
    return struct.pack('>6i', 0x44444147, 1, revision, min_host, max_host, len(payload)) + payload


def model_header(path, revision, min_host, max_host, runtime):
    digest = hashlib.sha256()
    size = 0
    with path.open('rb') as stream:
        while chunk := stream.read(128 * 1024):
            size += len(chunk)
            if size > MAX_MODEL:
                raise ValueError('Model exceeds 1 GiB')
            digest.update(chunk)
    if not size:
        raise ValueError('Empty model')
    return struct.pack('>6iq', 0x4444414d, 1, revision, min_host, max_host, runtime, size) + digest.digest()


def sign(header, key):
    # Passphrase, if required, is requested directly by OpenSSL; never accepted by this CLI.
    with tempfile.TemporaryDirectory(prefix='dd-ai-sign-') as tmp:
        message = Path(tmp) / 'message'
        message.write_bytes(header)
        signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(key.resolve()),
                                    '-sigopt', 'rsa_padding_mode:pkcs1', str(message)],
                                   check=True, stdout=subprocess.PIPE).stdout
        if not 256 <= len(signature) <= 512:
            raise ValueError('RSA signature size out of range')
    return header + struct.pack('>i', len(signature)) + signature


def write_new(path, data):
    # Publish atomically without replacing existing output, including concurrent publishers.
    fd, tmp = tempfile.mkstemp(prefix='.dd-ai-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(tmp, path)
    finally:
        os.unlink(tmp)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kind', choices=['glossary', 'model'])
    parser.add_argument('input', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--key', type=Path, required=True)
    parser.add_argument('--revision', type=positive, required=True)
    parser.add_argument('--min-host', type=positive, required=True)
    parser.add_argument('--max-host', type=positive, required=True)
    parser.add_argument('--runtime', type=positive)
    args = parser.parse_args()
    try:
        if args.min_host > args.max_host:
            raise ValueError('Invalid host API range')
        if (args.kind == 'model') != (args.runtime is not None):
            raise ValueError('Runtime API required only for model manifests')
        if args.output.exists() or args.output.is_symlink():
            raise ValueError('Output already exists')
        if not args.input.is_file() or not args.key.is_file():
            raise ValueError('Input and existing signing key must be regular files')
        if args.input.samefile(args.key):
            raise ValueError('Input must not be the signing key')
        if args.kind == 'glossary':
            header = glossary_header(args.input, args.revision, args.min_host, args.max_host)
        else:
            header = model_header(args.input, args.revision, args.min_host, args.max_host, args.runtime)
        write_new(args.output, sign(header, args.key))
        print(f'Created {args.kind} revision {args.revision}: {args.output}')
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'Publish failed: {error}\n')


if __name__ == '__main__':
    main()
