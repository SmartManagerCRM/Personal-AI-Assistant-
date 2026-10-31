#!/usr/bin/env bash
# Downloads the sherpa-onnx Android AAR (Apache-2.0) into app/libs and verifies its SHA-256.
set -euo pipefail
VER=1.12.9
SHA=08b1e34d196cfaf6d8af600f559179248684d2589665e3855a885fdacd49bd89
DEST="$(dirname "$0")/../app/libs/sherpa-onnx-$VER.aar"
mkdir -p "$(dirname "$DEST")"
[ -f "$DEST" ] && echo "$SHA  $DEST" | sha256sum -c - >/dev/null 2>&1 && { echo "already present"; exit 0; }
curl -fL -o "$DEST" "https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-$VER.aar"
echo "$SHA  $DEST" | sha256sum -c -
