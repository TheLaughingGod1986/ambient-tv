#!/usr/bin/env bash
set -euo pipefail
serial=${1:?Pass the clean emulator serial}
case "$serial" in emulator-*) ;; *) echo 'Refusing to run demo integration tests on a physical TV' >&2; exit 1;; esac
trap 'adb -s "$serial" shell wm size reset; adb -s "$serial" shell wm density reset' EXIT
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$serial" root
adb -s "$serial" wait-for-device
adb -s "$serial" shell settings put secure immersive_mode_confirmations confirmed
for size in 1920x1080 1280x720; do
  adb -s "$serial" shell wm size "$size"
  adb -s "$serial" shell wm density 160
  adb -s "$serial" shell input keyevent 224 82
  adb -s "$serial" shell wm dismiss-keyguard
  sleep 2
  adb -s "$serial" shell am instrument -w org.ambienttv.home.test/org.ambienttv.home.TvRegression
  adb -s "$serial" exec-out run-as org.ambienttv.home cat files/regression.json | python3 -c 'import json,sys; r=json.load(sys.stdin); print(r); assert r.get("passed"), r'
done
adb -s "$serial" shell wm size reset
adb -s "$serial" shell wm density reset
