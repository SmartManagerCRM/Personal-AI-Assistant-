#!/usr/bin/env bash
# Fetches the llama.cpp sources (MIT) bundled in the llama-cpp-python 0.3.36 sdist on PyPI (SHA-256 pinned) into app/src/main/cpp/llama.cpp.
# GitHub is not required. The Gradle build compiles libsmartllama.so only when this directory exists.
set -euo pipefail
URL="https://files.pythonhosted.org/packages/ec/e9/e7de2b0463ea3ffbf0ede6cb21b58c1258a8f6521aae45ca773a59fe7cf3/llama_cpp_python-0.3.36.tar.gz"
SHA=832db0699007f1be95a7e41ef12e88926b02ba836461e36a36372db2760c1a2e
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/cpp/llama.cpp"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
curl -fL -o "$TMP/sdist.tgz" "$URL"
echo "$SHA  $TMP/sdist.tgz" | sha256sum -c -
rm -rf "$DEST"; mkdir -p "$DEST"
tar xzf "$TMP/sdist.tgz" -C "$DEST" --strip-components=3 llama_cpp_python-0.3.36/vendor/llama.cpp
rm -rf "$DEST"/{tests,examples,docs,media,benches,models,tools,pocs,scripts,gguf-py,.devops,ci}
echo "llama.cpp sources ready in $DEST"
