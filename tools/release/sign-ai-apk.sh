#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 4 ]]; then
  echo "usage: sign-ai-apk.sh INPUT.apk OUTPUT.apk KEYSTORE KEY_ALIAS" >&2
  exit 2
fi
input=$1
output=$2
keystore=$3
alias_name=$4
[[ -f "$input" && -f "$keystore" && ! -e "$output" ]] || { echo "Check inputs and choose a new output file." >&2; exit 1; }
umask 077
work=$(mktemp -d "$(dirname "$output")/.dd-ai-sign.XXXXXXXX")
trap 'rm -rf -- "$work"' EXIT
aapt dump permissions "$input" > "$work/permissions"
grep -Fxq 'package: com.droiddeck.launcher.ai' "$work/permissions" || { echo "Unexpected package ID." >&2; exit 1; }
apksigner verify --print-certs "$input" > "$work/input-signer"
grep -Fxiq 'Signer #1 certificate SHA-256 digest: a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc' "$work/input-signer" || { echo "Expected a verified CI test build." >&2; exit 1; }
apksigner sign --ks "$keystore" --ks-key-alias "$alias_name" --v4-signing-enabled false --out "$work/signed.apk" "$input"
apksigner verify --verbose --print-certs "$work/signed.apk" > "$work/output-signer"
if grep -Eiq 'SHA-256 digest: (a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc|b241ea7d77822d39b89d3227e06f3ecf62adec6cbfd38ad06196787991b5d3f0)' "$work/output-signer"; then
  echo "An independent private signing key is required." >&2
  exit 1
fi
# Reserve the output without overwriting an existing APK, including concurrent signers.
(set -o noclobber; : > "$output")
mv -- "$work/signed.apk" "$output"
cat "$work/output-signer"
sha256sum "$output"
