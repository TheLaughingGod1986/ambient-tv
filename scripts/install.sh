#!/bin/sh
set -eu
if [ "$#" -ne 1 ]; then
  echo 'Usage: scripts/install.sh YOUR_TV_SERIAL' >&2
  echo 'Find the serial with adb devices. This installs only Ambient TV.' >&2
  exit 2
fi
cd "$(dirname "$0")/.."
adb -s "$1" get-state >/dev/null
./gradlew :app:assembleDebug
adb -s "$1" install -r app/build/outputs/apk/debug/app-debug.apk
printf '\nInstalled. Open Ambient TV Photos from your TV app list.\n'
