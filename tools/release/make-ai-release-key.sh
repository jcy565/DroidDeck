#!/usr/bin/env bash
set -euo pipefail
umask 077
fail() { echo "$*" >&2; exit 1; }
[[ $# == 1 ]] || fail 'Usage: make-ai-release-key.sh NEW_PRIVATE_DIRECTORY'
[[ -t 0 ]] || fail 'Run interactively; do not pipe passwords.'
command -v keytool >/dev/null || fail 'Java keytool is required.'
command -v sha256sum >/dev/null || fail 'sha256sum is required.'
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
parent=$(cd "$(dirname "$1")" && pwd -P)
name=$(basename "$1")
[[ "$name" != . && "$name" != .. && "$name" != / ]] || fail 'Choose a new directory.'
out="$parent/$name"
case "$out" in "$repo"|"$repo"/*) fail 'Keep keys outside the repository.' ;; esac
# mkdir is atomic and refuses existing files, directories and symbolic links.
mkdir -m 700 -- "$out" || fail 'Choose a new private directory; never replace an existing key.'
key="$out/droiddeck-ai.p12"
# keytool prompts interactively; passwords are not written to disk or command arguments.
keytool -genkeypair -keystore "$key" -storetype PKCS12 -alias droiddeck-ai \
  -keyalg RSA -keysize 3072 -validity 10950 -dname 'CN=DroidDeck AI Independent'
keytool -exportcert -keystore "$key" -storetype PKCS12 -alias droiddeck-ai \
  -file "$out/certificate.der"
[[ -s "$out/certificate.der" ]] || fail 'Certificate export is empty.'
digest=$(sha256sum "$out/certificate.der")
printf '%s\n' "${digest%% *}" > "$out/certificate.sha256"
printf 'Private keystore: %s\nPublic certificate SHA-256: %s\n' "$key" "${digest%% *}"
printf '%s\n' 'Keep the keystore and its password private; back up securely. Do not upload them.'
