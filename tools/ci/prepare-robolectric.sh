#!/usr/bin/env bash
set -euo pipefail

# Explicit retries also cover curl builds that exit immediately on HTTP 429.
download() {
  local relative="$1" dest="$2" delay host
  local hosts=(repo.maven.apache.org repo1.maven.org)
  # Use the Google Cloud hosted Maven Central mirror for payloads only.
  # Expected SHA-512 must still come from Maven Central, not the fallback mirror.
  if [[ "$relative" != *.sha512 ]]; then
    hosts+=(maven-central.storage-download.googleapis.com)
  fi
  for delay in 0 15 30 60; do
    if (( delay > 0 )); then sleep "$delay"; fi
    for host in "${hosts[@]}"; do
      rm -f -- "$dest"
      if curl --fail --silent --show-error --location --proto '=https' \
          --proto-redir '=https' --connect-timeout 30 --max-time 300 \
          "https://$host/maven2/$relative" -o "$dest" && test -s "$dest"; then
        return 0
      fi
    done
  done
  echo "Unable to download $relative after bounded retries" >&2
  return 1
}

verified() {
  local file="$1" checksum="$2" digest
  [[ -s "$file" && -s "$checksum" ]] || return 1
  digest="$(cat "$checksum")"
  [[ "$digest" =~ ^[[:xdigit:]]{128}$ ]] || return 1
  printf '%s  %s\n' "$digest" "$file" | sha512sum -c --status
}

prepare() (
  local version="$1" ext="$2" artifact=android-all-instrumented
  local relative="org/robolectric/$artifact/$version/$artifact-$version.$ext"
  local file="$HOME/.m2/repository/$relative" tmp
  if verified "$file" "$file.sha512"; then return 0; fi
  mkdir -p "$(dirname "$file")"
  tmp="$(mktemp -d "$(dirname "$file")/.prefetch.XXXXXX")"
  trap 'rm -rf -- "$tmp"' EXIT
  echo "Preparing $artifact:$version ($ext)"
  download "$relative.sha512" "$tmp/checksum"
  download "$relative" "$tmp/payload"
  if ! verified "$tmp/payload" "$tmp/checksum"; then
    echo "SHA-512 verification failed: $relative" >&2
    return 1
  fi
  mv -- "$tmp/payload" "$file"
  mv -- "$tmp/checksum" "$file.sha512"
)

for version in 9-robolectric-4913185-2-i7 13-robolectric-9030017-i7 14-robolectric-10818077-i7; do
  for ext in pom jar; do
    prepare "$version" "$ext"
  done
done
