#!/usr/bin/env bash
# Builds a signed VueGram release and gets it ready for GitHub, where the app's update check finds it.
#
#   scripts/release.sh             build, check the signing key, write the checksum, tag v<versionName>
#   scripts/release.sh --publish   the same, then create the GitHub release on vuegram/vuegram with `gh`
#
# Before running it: raise appVersion in app/build.gradle.kts (versionCode follows it) and commit. The release key is read
# from ~/.vuegram/signing.properties (or the file $VUEGRAM_SIGNING names); it never goes into the repository.
# Lose that key and installed copies can no longer be updated, so keep a backup of ~/.vuegram somewhere safe.
set -euo pipefail

# SHA-256 of VueGram's release certificate. A build signed with anything else is refused: the app would refuse it too.
EXPECTED_CERT="7b69b3ef239f7f9d80e3b1d11eebabaa75fb0cb7232e9327bcae298652d2da1b"
REPO="vuegram/vuegram"

publish=0
[ "${1:-}" = "--publish" ] && publish=1

cd "$(dirname "$0")/.."

die() { echo "release: $*" >&2; exit 1; }

version=$(sed -n 's/^ *val appVersion = "\(.*\)"/\1/p' app/build.gradle.kts)
[ -n "$version" ] || die "could not read appVersion from app/build.gradle.kts"
IFS=. read -r major minor patch <<< "$version"
code=$((10#$major * 10000 + 10#$minor * 100 + 10#$patch))
tag="v$version"

[ -z "$(git status --porcelain)" ] || die "commit or stash your changes first"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && die "$tag already exists: raise appVersion"

signing="${VUEGRAM_SIGNING:-$HOME/.vuegram/signing.properties}"
[ -f "$signing" ] || die "no signing properties at $signing"

# apksigner from the newest build-tools in the Android SDK.
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f local.properties ]; then
    sdk=$(sed -n 's/^sdk\.dir=//p' local.properties | sed 's/\\:/:/g; s/\\\\/\//g')
fi
[ -z "$sdk" ] && [ -n "${LOCALAPPDATA:-}" ] && sdk="$LOCALAPPDATA/Android/Sdk"
apksigner=""
for dir in $(ls -d "$sdk"/build-tools/*/ 2>/dev/null | sort -V -r); do
    for name in apksigner apksigner.bat; do
        if [ -f "$dir$name" ]; then apksigner="$dir$name"; break 2; fi
    done
done
[ -n "$apksigner" ] || die "apksigner not found under $sdk/build-tools"

echo "release: building VueGram $version (versionCode $code)"
./gradlew --no-daemon -q --console=plain testDebugUnitTest assembleRelease

# Three APKs: one per processor type (what the updater downloads) and a universal one (vuegram.com/get, first
# installs). Their names don't carry the version, so github.com/.../releases/latest/download/vuegram.apk always works.
out=app/build/outputs/apk/release
mkdir -p dist
rm -f dist/vuegram*.apk dist/vuegram*.apk.sha256
files=()
for pair in "universal:vuegram.apk" "armeabi-v7a:vuegram-armeabi-v7a.apk" "arm64-v8a:vuegram-arm64-v8a.apk"; do
    abi=${pair%%:*}
    name=${pair#*:}
    apk="$out/app-$abi-release.apk"
    [ -f "$apk" ] || die "no $apk: is the release signing configured?"
    certs=$("$apksigner" verify --print-certs "$apk" 2>&1) || die "apksigner could not verify $apk: $certs"
    cert=$(printf '%s\n' "$certs" | sed -n 's/.*certificate SHA-256 digest: //p' | head -n 1 | tr -d '\r')
    [ "$cert" = "$EXPECTED_CERT" ] || die "$apk is signed with $cert, not VueGram's release key"
    cp "$apk" "dist/$name"
    (cd dist && sha256sum "$name" > "$name.sha256")
    files+=("dist/$name" "dist/$name.sha256")
done
echo "release: in dist/:"
(cd dist && ls -l vuegram* && cat vuegram*.sha256)

git tag -a "$tag" -m "VueGram $version"
echo "release: tagged $tag (in this repository)"

# The public release lives on $REPO, a flat copy of this repository: its tag is created there, on its own main.
if [ "$publish" = 1 ]; then
    command -v gh >/dev/null || die "gh is not installed: publish by hand (steps below), the files are ready"
    gh release create "$tag" "${files[@]}" --repo "$REPO" --target main --title "VueGram $version" --generate-notes
    echo "release: published https://github.com/$REPO/releases/tag/$tag"
else
    cat <<STEPS

To publish on https://github.com/$REPO/releases/new (after $REPO's main has this version):
  tag $tag on main, title "VueGram $version",
  attach all six files in dist/ (each APK with its .sha256: the app refuses an update without its checksum),
  write what changed, and publish it as the latest release (not a pre-release or draft).
STEPS
fi
