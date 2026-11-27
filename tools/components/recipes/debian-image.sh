#!/usr/bin/env bash
set -euo pipefail
. tools/linuxfs/debian-image.env
image=public.ecr.aws/docker/library/debian:bullseye
# Forks do not inherit release assets. The official release is pinned to the same SHA-256.
upstream=Droid-Deck/DroidDeck
[[ "$DEBIAN_IMAGE_SHA256" =~ ^[[:xdigit:]]{64}$ ]] || { echo "::error::invalid Debian image checksum"; exit 1; }
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
archive="$tmp/debian-image.tar.zst"
loaded=false
repos=("$GITHUB_REPOSITORY")
if [[ "$GITHUB_REPOSITORY" != "$upstream" ]]; then repos+=("$upstream"); fi
for repo in "${repos[@]}"; do
  rm -f -- "$archive"
  echo "Fetching pinned Debian image from $repo"
  if gh release download "$DEBIAN_IMAGE_TAG" -R "$repo" -p "$DEBIAN_IMAGE_ASSET" -O "$archive"; then
    echo "$DEBIAN_IMAGE_SHA256  $archive" | sha256sum -c -
    zstd -dc "$archive" | docker load
    loaded=true
    break
  fi
done
if [[ "$loaded" == false ]]; then
  echo "::warning::the hosted Debian image could not be fetched; pulling from the registry instead"
  for attempt in 1 2 3 4 5; do
    docker pull "$image" && break
    [ "$attempt" -lt 5 ] || { echo "::error::could not pull $image after $attempt attempts"; exit 1; }
    echo "pull attempt $attempt failed (registry busy?); retrying in $((attempt * 30)) s"
    sleep $((attempt * 30))
  done
fi
docker image ls "$image"
