#!/usr/bin/env bash
# Uploads the Play store listing (texts, icon, feature graphic, TV banner, TV
# and phone screenshots) for every language in docs/play/listing/, through
# `~/ops/bin/play listing`. Extra arguments pass through (--dry-run, --lang).
#
# Texts live in docs/play/listing/<lang>/; graphics are shared from docs/play/
# and screenshots come from docs/play/screenshots/<lang>/. The script copies
# them into the fastlane-supply layout the command reads, in a temp dir.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
play="${PLAY:-$HOME/ops/bin/play}"
out="$(mktemp -d "${TMPDIR:-/tmp}/qupa-listing.XXXXXX")"
trap 'rm -rf "$out"' EXIT

for src in "$root"/docs/play/listing/*/; do
  lang="$(basename "$src")"
  dst="$out/$lang"
  mkdir -p "$dst/images/tvScreenshots"
  cp "$src"/*.txt "$dst/"
  cp "$root/docs/play/icon-512.png" "$dst/images/icon.png"
  cp "$root/docs/play/feature-graphic-1024x500.png" "$dst/images/featureGraphic.png"
  cp "$root/docs/play/tv-banner-1280x720.png" "$dst/images/tvBanner.png"
  shots="$root/docs/play/screenshots/$lang"
  if ! ls "$shots"/*.png >/dev/null 2>&1; then
    echo "play-listing: no screenshots in docs/play/screenshots/$lang" >&2
    exit 1
  fi
  cp "$shots"/*.png "$dst/images/tvScreenshots/"
  # Play requires phone screenshots even for a TV-only app; 16:9 is accepted.
  mkdir -p "$dst/images/phoneScreenshots"
  cp "$shots"/*.png "$dst/images/phoneScreenshots/"
done

"$play" listing --package com.fabriquefutur.qupa --dir "$out" "$@"
