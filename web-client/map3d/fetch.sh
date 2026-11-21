#!/usr/bin/env bash
# Copies Strata's grid relay (the prebuilt 3D map renderer the game page drives through its JS page
# API; Strata's web/kit/grid-relay/WIRE.md) into src/main/resources/static/map3d/, from Strata's
# wasm release tarball checked against the pinned SHA-256. Nothing is compiled: the tarball carries
# the relay prebuilt under kit/grid-relay/dist/. The copies are committed, so the web client's
# Maven build needs no network; `fetch.sh --check` (CI) fails if they differ from the release.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
client="$(dirname "$here")"
work="${MAP3D_WORK:-$here/.build}"
out="$client/src/main/resources/static/map3d"
mkdir -p "$work"

STRATA_VERSION=0.9.1
STRATA_SHA256=80aa63085857a50186a4db6f22c14663dad34fd3856f8de03b528fdbfe33fc96
FILES="grid-relay.js grid-relay-scene.js grid-relay-scene.wasm strata-kit.js strata-loader.js"

tarball="$work/strata-$STRATA_VERSION-wasm32-emscripten.tar.gz"
if [ ! -f "$tarball" ]; then
  curl -fsSL -o "$tarball.part" \
    "https://github.com/Preponderous-Software/strata/releases/download/v$STRATA_VERSION/strata-$STRATA_VERSION-wasm32-emscripten.tar.gz"
  mv "$tarball.part" "$tarball"
fi
echo "$STRATA_SHA256  $tarball" | sha256sum -c --quiet -
rm -rf "$work/kit-src" && mkdir -p "$work/kit-src"
tar xzf "$tarball" -C "$work/kit-src" ./kit/grid-relay/dist
dist="$work/kit-src/kit/grid-relay/dist"

if [ "${1:-}" = "--check" ]; then
  status=0
  for f in $FILES; do
    if ! cmp -s "$dist/$f" "$out/$f"; then
      echo "static/map3d/$f differs from Strata $STRATA_VERSION's kit/grid-relay/dist/$f (run web-client/map3d/fetch.sh)" >&2
      status=1
    fi
  done
  exit $status
fi

mkdir -p "$out"
for f in $FILES; do cp "$dist/$f" "$out/$f"; done
echo "Copied Strata $STRATA_VERSION's grid relay into $out"
