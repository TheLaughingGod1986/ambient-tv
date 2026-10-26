#!/usr/bin/env bash
set -euo pipefail
: "${AMBIENT_KEYSTORE:?Provide the private signing keystore path}"
: "${AMBIENT_STORE_PASSWORD:?Provide the store password}"
: "${AMBIENT_KEY_ALIAS:?Provide the key alias}"
: "${AMBIENT_KEY_PASSWORD:?Provide the key password}"
./gradlew :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
printf 'Signed APK: app/build/outputs/apk/release/app-release.apk\n'
shasum -a 256 app/build/outputs/apk/release/app-release.apk
