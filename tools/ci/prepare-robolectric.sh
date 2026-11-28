#!/usr/bin/env bash
set -euo pipefail

# Explicit retries also cover curl builds that exit immediately on HTTP 429.
download() {
  local relative="$1" dest="$2" checksum="${3:-}" delay host
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
        if [[ "$relative" == *.sha512 ]]; then
          if checksum_valid "$dest"; then return 0; fi
          echo "Invalid SHA-512 response: $relative from $host" >&2
        else
          if verified "$dest" "$checksum"; then return 0; fi
          echo "SHA-512 verification failed: $relative from $host" >&2
        fi
      fi
    done
  done
  echo "Unable to download $relative after bounded retries" >&2
  return 1
}

checksum_valid() {
  local file="$1" digest
  [[ ! -L "$file" && -f "$file" ]] || return 1
  # Limit the read even if a server returns an error page with HTTP 200.
  [[ $(wc -c < "$file") -le 130 ]] || return 1
  digest="$(cat "$file")"
  [[ "$digest" =~ ^[[:xdigit:]]{128}$ ]]
}

verified() {
  local file="$1" checksum="$2" digest
  [[ ! -L "$file" && ! -L "$checksum" && -f "$file" && -f "$checksum" &&
     -s "$file" && -s "$checksum" ]] || return 1
  checksum_valid "$checksum" || return 1
  digest="$(cat "$checksum")"
  printf '%s  %s\n' "$digest" "$file" | sha512sum -c --status
}

safe_destination() {
  local path="$1"
  if [[ -L "$path" || ( -e "$path" && ! -f "$path" ) ]]; then
    echo "Unsafe Robolectric cache destination: $path" >&2
    return 1
  fi
}

prepare() (
  local artifact="$1" version="$2" ext="$3"
  local relative="org/robolectric/$artifact/$version/$artifact-$version.$ext"
  local file="$HOME/.m2/repository/$relative" tmp
  safe_destination "$file"
  safe_destination "$file.sha512"
  if verified "$file" "$file.sha512"; then return 0; fi
  mkdir -p "$(dirname "$file")"
  tmp="$(mktemp -d "$(dirname "$file")/.prefetch.XXXXXX")"
  trap 'rm -rf -- "$tmp"' EXIT
  echo "Preparing $artifact:$version ($ext)"
  download "$relative.sha512" "$tmp/checksum"
  download "$relative" "$tmp/payload" "$tmp/checksum"
  if ! verified "$tmp/payload" "$tmp/checksum"; then
    echo "SHA-512 verification failed: $relative" >&2
    return 1
  fi
  safe_destination "$file"
  safe_destination "$file.sha512"
  mv -T -- "$tmp/payload" "$file"
  mv -T -- "$tmp/checksum" "$file.sha512"
  verified "$file" "$file.sha512"
)

for version in 9-robolectric-4913185-2-i7 13-robolectric-9030017-i7 14-robolectric-10818077-i7; do
  for ext in pom jar; do
    prepare android-all-instrumented "$version" "$ext"
  done
done

# Gradle also needs the native runtime artifact, which is not part of android-all.
for ext in pom jar; do
  prepare nativeruntime-dist-compat 1.0.18 "$ext"
done
