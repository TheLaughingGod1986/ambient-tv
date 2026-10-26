# Working on Ambient TV

Read README.md and docs/DEVELOPMENT.md first. Use JDK 17 and Android SDK 35.
Run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`
and `python3 scripts/check-public.py` before committing.

Keep all documentation, examples and screenshots generic. Use only bundled demo
scenery for public images. Never read, copy or commit a contributor's account
sessions, device dumps, network addresses, photos, local.properties or signing keys.
Do not weaken the publication check to hide an actual finding.

Keep media accounts independent. An empty selected-profile Plex response must not
be filled from another profile. Never invent watch progress, next episodes or
photo membership. Preserve bounded decoding and caches, offline photo fallback,
the active-audio screensaver guard, and cancellation when Home is paused.

Use an emulator for examples. A real-TV install or control action needs the user's
explicit device choice and a free TV. Always target ADB with `-s SERIAL`. Never
clear app data, reset a TV, disable stock launchers, remove system packages, change
accounts or interfere with Cast/Google Home as a routine setup step.

Use plain language in user guides. Explain what a command changes and how to undo
it. Distinguish unit tests, emulator checks and tests on actual TV hardware.
