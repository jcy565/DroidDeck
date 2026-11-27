#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 6 ]]; then
  echo "usage: sign-ai-apk.sh INPUT.apk OUTPUT.apk KEYSTORE KEY_ALIAS APPROVED_INPUT_SHA256 EXPECTED_RELEASE_CERT_SHA256" >&2
  exit 2
fi
input=$1
output=$2
keystore=$3
alias_name=$4
approved=${5,,}
expected=${6,,}
test_cert=a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc
official_cert=b241ea7d77822d39b89d3227e06f3ecf62adec6cbfd38ad06196787991b5d3f0
fail() { echo "$*" >&2; exit 1; }
[[ -f "$input" && -f "$keystore" && ! -e "$output" && ! -L "$output" ]] || fail "Check inputs and choose a new output file."
[[ "$approved" =~ ^[a-f0-9]{64}$ && "$expected" =~ ^[a-f0-9]{64}$ ]] || fail "Expected SHA-256 digests are required."
[[ "$expected" != "$test_cert" && "$expected" != "$official_cert" ]] || fail "Independent release certificate required."
umask 077
work=$(mktemp -d "$(dirname "$output")/.dd-ai-sign.XXXXXXXX")
trap 'rm -rf -- "$work"' EXIT
# Snapshot the reviewed artifact before checking and signing it.
cp -- "$input" "$work/input.apk"
actual=$(sha256sum "$work/input.apk")
[[ "${actual%% *}" == "$approved" ]] || fail "Input differs from the reviewed APK."
check_package() {
  aapt dump permissions "$1" > "$work/permissions"
  grep -Fxq 'package: com.droiddeck.launcher.ai' "$work/permissions" || fail "Unexpected package ID."
}
check_signer() {
  local apk=$1 cert=$2 report=$3
  apksigner verify --verbose --print-certs "$apk" > "$report"
  local -a signers=()
  mapfile -t signers < <(grep -Ei '^Signer #[0-9]+ certificate SHA-256 digest:' "$report" || true)
  [[ ${#signers[@]} == 1 && "${signers[0],,}" == "signer #1 certificate sha-256 digest: $cert" ]] || fail "Unexpected signing certificate or signer count."
}
check_package "$work/input.apk"
check_signer "$work/input.apk" "$test_cert" "$work/input-signer"
apksigner sign --ks "$keystore" --ks-key-alias "$alias_name" --v4-signing-enabled false --out "$work/signed.apk" "$work/input.apk"
check_package "$work/signed.apk"
check_signer "$work/signed.apk" "$expected" "$work/output-signer"
# Only JAR signature metadata may differ; package code/resources must be identical.
python3 - "$work/input.apk" "$work/signed.apk" <<'PY'
import hashlib
import re
import sys
import zipfile

signature = re.compile(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', re.I)
def payload(path):
    result = {}
    seen = set()
    with zipfile.ZipFile(path) as apk:
        for entry in apk.infolist():
            name = entry.filename
            if name in seen:
                raise ValueError('Duplicate ZIP entry')
            seen.add(name)
            if entry.is_dir() or signature.fullmatch(name):
                continue
            h = hashlib.sha256()
            with apk.open(entry) as f:
                for chunk in iter(lambda: f.read(1024 * 1024), b''):
                    h.update(chunk)
            result[name] = (entry.file_size, h.hexdigest())
    return result
if payload(sys.argv[1]) != payload(sys.argv[2]):
    sys.exit('APK payload changed during signing.')
PY
# Same-filesystem hard link publishes complete bytes atomically, never overwrites.
ln -T -- "$work/signed.apk" "$output"
cat "$work/output-signer"
sha256sum "$output"
