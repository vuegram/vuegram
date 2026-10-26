#!/usr/bin/env bash
# Builds TDLib (and OpenSSL) for Android from source with TDLib's own Dockerfile, at a pinned commit, and copies
# the result into the app: app/src/main/jniLibs/<abi>/libtdjni.so and app/src/main/java/org/drinkless/tdlib/*.java.
#
# Usage: scripts/build-tdlib.sh [commit]    (default: the commit the app's TdApi.java was generated from)
# Needs Docker. The first run downloads Ubuntu packages, the Android SDK/NDK and the sources (about 2 GB) and
# compiles for four ABIs, which takes a while; later runs reuse Docker's cache.
set -euo pipefail

COMMIT=${1:-cb863c1600082404428f1a84e407b866b9d412a8}
ABIS="armeabi-v7a arm64-v8a"   # what the app ships; the zip has x86 and x86_64 too
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${TDLIB_BUILD_DIR:-$ROOT/build/tdlib-$COMMIT}"
SRC="https://raw.githubusercontent.com/tdlib/td/$COMMIT/example/android"

mkdir -p "$WORK/context" "$WORK/out"
# TDLib's own build files from the same commit, unmodified.
for f in Dockerfile check-environment.sh fetch-sdk.sh build-openssl.sh build-tdlib.sh; do
  curl -fsSL "$SRC/$f" -o "$WORK/context/$f"
done
chmod +x "$WORK/context/"*.sh

docker build --progress=plain --build-arg COMMIT_HASH="$COMMIT" --output "$WORK/out" "$WORK/context"

rm -rf "$WORK/unzipped"
mkdir -p "$WORK/unzipped"
unzip -q "$WORK/out/tdlib.zip" -d "$WORK/unzipped"

for abi in $ABIS; do
  mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
  cp "$WORK/unzipped/tdlib/libs/$abi/libtdjni.so" "$ROOT/app/src/main/jniLibs/$abi/libtdjni.so"
done
cp "$WORK/unzipped/tdlib/java/org/drinkless/tdlib/"*.java "$ROOT/app/src/main/java/org/drinkless/tdlib/"

echo "TDLib $COMMIT installed. sha256:"
(cd "$ROOT/app/src/main/jniLibs" && sha256sum */libtdjni.so)
