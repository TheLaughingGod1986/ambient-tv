# Releases, updates and recovery

Download the signed APK and SHA256SUMS from the repository's GitHub Releases page.
Verify the checksum before sideloading. Open the app and try the demo before changing
Android's default Home. Existing Plex, YouTube and Synology apps remain separate.

Install or update with `adb -s YOUR_TV_SERIAL install -r ambient-tv.apk`. Updates from
this release series use the same signing key and retain application data. A developer
debug build uses a different key: Android rejects an in-place update from it. Do not
uninstall a configured app just to bypass that error without saving preferences and
being prepared to reconnect accounts. Household custom builds using another package
name are separate applications, not upgraded by this APK.

Before updating, use Settings → Status & backup → Export preferences. This includes
screensaver layout, default/per-photo framing and favourite photo IDs. It excludes
credentials, server addresses, photographs, watched/hidden history and Android system
settings. A system document picker selects USB/local/cloud storage. TVs without a usable picker offer an explicit local-file fallback in the app’s external-files directory. Copy that file to another device for recovery; uninstalling/clearing data deletes it.
Restore validates the complete file and asks before replacing those preferences.
Favourites and framing IDs only match when restoring the same photo library.

## Recovery

Keep the previous APK. Android normally refuses a lower version code, so the safest
rollback is a corrective release with a higher version code signed by the same key.
Do not uninstall or clear app data as a routine rollback: that removes sign-ins and
cached photos. You can switch Android's default Home/screensaver back in system
settings while awaiting a corrective build. No rollback should require disabling a
manufacturer's system packages.

## Maintainer build

Keep the release keystore and a secure backup outside the repository. Supply
`AMBIENT_KEYSTORE`, `AMBIENT_STORE_PASSWORD`, `AMBIENT_KEY_ALIAS`, and
`AMBIENT_KEY_PASSWORD` through your local secret environment, then run
`scripts/build-release.sh`. Never commit signing keys or passwords. Verify the APK
with Android build-tools `apksigner verify --verbose --print-certs`, retain the signer
fingerprint, and publish the APK plus its SHA-256 checksum with release notes.

## Verification

`scripts/test-tv.sh emulator-SERIAL` runs only on a clean, unconfigured emulator.
It checks all eight page bounds, Home directional focus/Enter/Back, preference round
trip, and real screensaver dismissal at 1080p and 720p. It does not prove external
Plex/YouTube app behaviour or live account data. The same checks run in CI.
