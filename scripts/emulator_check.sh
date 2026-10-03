#!/usr/bin/env bash
set -o pipefail
echo "== devices =="
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n com.vireo.editor/.MainActivity
sleep 25
adb shell screencap -p /sdcard/launch.png
adb pull /sdcard/launch.png launch.png || true
adb logcat -d > logcat.txt || true
echo "== crash scan =="
grep -c "FATAL EXCEPTION" logcat.txt > /tmp/crashes || true
CRASHES=$(cat /tmp/crashes 2>/dev/null || echo 0)
echo "FATAL EXCEPTION count: $CRASHES"
grep -A 25 "FATAL EXCEPTION" logcat.txt || echo "no fatal exceptions"
test "$CRASHES" = "0"
