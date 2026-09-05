#!/usr/bin/env bash
set -euo pipefail
: "${APKSIGNER_JAR:?Provide the official apksigner.jar}"
: "${RELEASE_KEYSTORE:?Provide the existing private release keystore}"
: "${RELEASE_PASSWORD_FILE:?Provide its password file}"
if [ "$#" -ne 2 ]; then
  echo 'Usage: sign-release.sh unsigned.apk signed.apk' >&2
  exit 2
fi
expected=43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320
cert=$(mktemp)
trap 'rm -f "$cert"' EXIT
keytool -exportcert -keystore "$RELEASE_KEYSTORE" -storepass:file "$RELEASE_PASSWORD_FILE" -alias guitar-release -file "$cert" >/dev/null 2>&1
actual=$(sha256sum "$cert" | cut -d ' ' -f 1)
if [ "$actual" != "$expected" ]; then
  echo 'Refusing to sign: this is not the pinned v2 release key.' >&2
  exit 1
fi
java -jar "$APKSIGNER_JAR" sign --ks "$RELEASE_KEYSTORE" --ks-key-alias guitar-release --ks-pass "file:$RELEASE_PASSWORD_FILE" --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "$2" "$1"
java -jar "$APKSIGNER_JAR" verify --verbose --print-certs "$2"
