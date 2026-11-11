#!/usr/bin/env bash
# Builds the 3D battlefield's engine (battle3d/main.cpp) into
# src/main/resources/static/battle3d/battle-engine.{js,wasm}, and copies the
# Strata web kit's page scripts (strata-kit.js, strata-loader.js) beside
# them. All four are committed, so the web client's Maven build needs no
# Emscripten; CI rebuilds them and fails if the committed bytes differ.
#
# The kit and the engine both come from Strata's v0.7.0 wasm release tarball
# (its kit/ directory), checked against the pinned SHA-256: no vendored copy.
# Needs emsdk 6.0.11 activated (the version Strata's release is built with;
# everything linked into one .wasm must agree on it and on -fwasm-exceptions).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
client="$(dirname "$here")"
work="${BATTLE3D_WORK:-$here/.build}"
mkdir -p "$work"

export STRATA_VERSION=0.7.0
export STRATA_SHA256=1aab559eb2477f42bfbeae1cd2e3cf70fcb8aa8b28fc8dce16ba1e0ba855049f
tarball="$work/strata-$STRATA_VERSION-wasm32-emscripten.tar.gz"
if [ ! -f "$tarball" ]; then
  curl -fsSL -o "$tarball.part" \
    "https://github.com/Preponderous-Software/strata/releases/download/v$STRATA_VERSION/strata-$STRATA_VERSION-wasm32-emscripten.tar.gz"
  mv "$tarball.part" "$tarball"
fi
echo "$STRATA_SHA256  $tarball" | sha256sum -c --quiet -
rm -rf "$work/kit-src" && mkdir -p "$work/kit-src"
tar xzf "$tarball" -C "$work/kit-src"

export STRATA_TARBALL="$tarball"
export KIT_DIR="$work/kit-src/kit"
export KIT_SOURCE="$here/main.cpp"
export KIT_OUT="$client/src/main/resources/static/battle3d"
export KIT_NAME=battle-engine
export KIT_EXPORT_NAME=createBattleEngine
export KIT_EXPORTS=_bt_set_prims,_bt_set_points,_bt_set_lights
export KIT_WORK="$work"
export EMSDK_VERSION=6.0.11

bash "$KIT_DIR/build-kit.sh"
