#!/bin/sh
set -eu
case "${1:-}" in
  emulator-*) ;;
  *) echo 'Usage: scripts/make-demo-screenshots.sh emulator-SERIAL (clean emulator only)' >&2; exit 2 ;;
esac
cd "$(dirname "$0")/.."
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PtvTestRunner=org.ambienttv.home.DemoScreenshots
adb -s "$1" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$1" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result_file=$(mktemp)
trap 'rm -f "$result_file"' EXIT
adb -s "$1" shell am instrument -w org.ambienttv.home.test/org.ambienttv.home.DemoScreenshots > "$result_file"
cat "$result_file"
grep -q 'INSTRUMENTATION_CODE: -1' "$result_file"
adb -s "$1" exec-out run-as org.ambienttv.home cat files/demo-screenshots/home.png > docs/images/home.png
adb -s "$1" exec-out run-as org.ambienttv.home cat files/demo-screenshots/screensaver.png > docs/images/screensaver.png
adb -s "$1" exec-out run-as org.ambienttv.home cat files/demo-verification.json
python3 scripts/check-public.py
