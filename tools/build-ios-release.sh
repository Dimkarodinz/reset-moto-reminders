#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "Usage: tools/build-ios-release.sh <version>" >&2
  exit 2
fi

ROOT="$(git rev-parse --show-toplevel)"
TAG="ios-v${VERSION}"
PROJECT="$ROOT/ios/ResetMotoReminders/ResetMotoReminders.xcodeproj"
DIST="$ROOT/dist"
IPA_NAME="reset-moto-reminders-ios-v${VERSION}.ipa"
SOURCE_NAME="reset-moto-reminders-ios-v${VERSION}-source.zip"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/reset-moto-ios-release.XXXXXX")"
DERIVED="$WORK/DerivedData"
PAYLOAD="$WORK/Payload"

cd "$ROOT"

if ! git diff --quiet || ! git diff --cached --quiet || [[ -n "$(git status --porcelain)" ]]; then
  echo "Release builds require a clean worktree." >&2
  exit 1
fi

if [[ "$(git describe --tags --exact-match 2>/dev/null || true)" != "$TAG" ]]; then
  echo "HEAD must have the exact tag $TAG." >&2
  exit 1
fi

if ! rg -q "MARKETING_VERSION = ${VERSION};" "$PROJECT/project.pbxproj"; then
  echo "Xcode marketing version does not match $VERSION." >&2
  exit 1
fi

mkdir -p "$DIST"
for artifact in "$DIST/$IPA_NAME" "$DIST/$IPA_NAME.sha256" "$DIST/$SOURCE_NAME" "$DIST/$SOURCE_NAME.sha256"; do
  if [[ -e "$artifact" ]]; then
    echo "Refusing to overwrite $artifact" >&2
    exit 1
  fi
done

xcodebuild \
  -project "$PROJECT" \
  -scheme ResetMotoReminders \
  -configuration Release \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "$DERIVED" \
  CODE_SIGNING_ALLOWED=NO \
  build

APP="$DERIVED/Build/Products/Release-iphoneos/Reset Moto Reminders.app"
[[ -d "$APP" ]] || { echo "Built app not found." >&2; exit 1; }

if codesign -dv "$APP" >/dev/null 2>&1; then
  echo "Refusing to package a signed application." >&2
  exit 1
fi

if find "$APP" \( -name embedded.mobileprovision -o -name _CodeSignature \) -print -quit | grep -q .; then
  echo "Refusing to package signing material." >&2
  exit 1
fi

[[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Info.plist")" == "$VERSION" ]]
[[ "$(find "$APP" -maxdepth 1 -type d -name '*.lproj' | wc -l | tr -d ' ')" == "5" ]]

cp "$ROOT/LICENSE" "$APP/LICENSE.txt"
cp "$ROOT/NOTICE" "$APP/NOTICE.txt"
cp "$ROOT/THIRD_PARTY_NOTICES.md" "$APP/THIRD_PARTY_NOTICES.txt"

mkdir -p "$PAYLOAD"
ditto "$APP" "$PAYLOAD/Reset Moto Reminders.app"
(cd "$WORK" && ditto -c -k --sequesterRsrc --keepParent Payload "$DIST/$IPA_NAME")

git archive \
  --format=zip \
  --prefix="reset-moto-reminders-ios-v${VERSION}/" \
  --output="$DIST/$SOURCE_NAME" \
  "$TAG" \
  LICENSE NOTICE README.md THIRD_PARTY_NOTICES.md \
  adapter-maps dtc-maps ecu-maps \
  ios/README.md ios/ResetMotoCore ios/ResetMotoReminders

(cd "$DIST" && shasum -a 256 "$IPA_NAME" > "$IPA_NAME.sha256")
(cd "$DIST" && shasum -a 256 "$SOURCE_NAME" > "$SOURCE_NAME.sha256")

echo "Created:"
echo "  $DIST/$IPA_NAME"
echo "  $DIST/$IPA_NAME.sha256"
echo "  $DIST/$SOURCE_NAME"
echo "  $DIST/$SOURCE_NAME.sha256"
