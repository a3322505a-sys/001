# Stable v2 release identity

Package: com.a3322505a.guitarlearning
First release: 2.0.0-alpha01, versionCode 18.
All subsequent delivered APKs must use the SAME private key and increasing versionCode.
Certificate SHA-256:
43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320

The private key is durably saved in the owner's private file
001-release-signing-backup.zip (alias guitar-release). Retrieve the EXISTING backup for
future releases. Never generate a replacement key, commit it, or put its password in CI logs.
The public certificate is release-cert.pem. It does not grant signing authority.

CI produces an unsigned release plus the official Android apksigner tool. Sign locally with
scripts/sign-release.sh and the owner's preserved key. This separation keeps private signing
material out of this public repository; transient CI debug APKs are never upgrade deliveries.
The signing script fails on the wrong certificate and never creates a fallback key.

Example (paths only; password is read from a file):
APKSIGNER_JAR=/private/tools/apksigner.jar \
RELEASE_KEYSTORE=/private/001-release.p12 \
RELEASE_PASSWORD_FILE=/private/001-release.password \
  scripts/sign-release.sh input-unsigned.apk output.apk

When CI secrets are configured in future, use this same key, not another new identity.
App data lives in the private database and is not cleared on version changes.
An Android package update retains it; uninstalling clears it. The transition from old v1
requires one final reinstall if its old debug certificate differs (authorized by user).

References:
- https://developer.android.com/studio/publish/app-signing
- https://developer.android.com/tools/apksigner
- https://developer.android.com/jetpack/androidx/releases/room
