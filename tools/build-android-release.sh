#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
android_root="$repo_root/android"
keychain_service="dev.resetlight.android.release-signing"
keychain_account=${USER:?USER must be set}
keystore_file="$HOME/Library/Application Support/Reset Moto Reminders/signing/reset-moto-reminders-release.p12"
key_alias="reset-moto-reminders"

if [ ! -f "$keystore_file" ]; then
  echo "Release keystore not found: $keystore_file" >&2
  echo "Create or restore the protected project key before building a public APK." >&2
  exit 1
fi

if [ "${ALLOW_UNTAGGED_RELEASE:-0}" != "1" ]; then
  if [ -n "$(git -C "$repo_root" status --porcelain)" ]; then
    echo "Refusing a release build from a dirty working tree." >&2
    exit 1
  fi
  if ! git -C "$repo_root" describe --tags --exact-match >/dev/null 2>&1; then
    echo "Refusing a release build that is not checked out at an exact public tag." >&2
    exit 1
  fi
fi

keystore_password=$(security find-generic-password \
  -a "$keychain_account" \
  -s "$keychain_service" \
  -w)

cleanup() {
  unset RESET_MOTO_KEYSTORE_PASSWORD RESET_MOTO_KEY_PASSWORD keystore_password
}
trap cleanup EXIT HUP INT TERM

export RESET_MOTO_KEYSTORE_FILE="$keystore_file"
export RESET_MOTO_KEYSTORE_PASSWORD="$keystore_password"
export RESET_MOTO_KEY_ALIAS="$key_alias"
export RESET_MOTO_KEY_PASSWORD="$keystore_password"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}

version=$(sed -nE 's/.*versionName = "([^"]+)".*/\1/p' "$android_root/app/build.gradle.kts")
if [ -z "$version" ]; then
  echo "Could not read Android versionName." >&2
  exit 1
fi

(cd "$android_root" && ./gradlew --offline --no-daemon clean testDebugUnitTest lintDebug assembleRelease)

source_apk="$android_root/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$source_apk" ]; then
  echo "Signed release APK was not produced: $source_apk" >&2
  exit 1
fi

dist_dir="$repo_root/dist"
artifact="$dist_dir/reset-moto-reminders-android-v$version.apk"
mkdir -p "$dist_dir"
install -m 0644 "$source_apk" "$artifact"

apksigner="$ANDROID_HOME/build-tools/35.0.0/apksigner"
signer_report=$($apksigner verify --verbose --print-certs "$artifact")
printf '%s\n' "$signer_report" | grep -q "Verified using v2 scheme (APK Signature Scheme v2): true"
if printf '%s\n' "$signer_report" | grep -q "CN=Android Debug"; then
  echo "Refusing an APK signed with the Android debug certificate." >&2
  exit 1
fi

(cd "$dist_dir" && shasum -a 256 "$(basename "$artifact")" > "$(basename "$artifact").sha256")

printf 'Built: %s\n' "$artifact"
printf 'Checksum: %s.sha256\n' "$artifact"
printf '%s\n' "$signer_report" | sed -n '/Signer #1 certificate DN:/p;/Signer #1 certificate SHA-256 digest:/p'
