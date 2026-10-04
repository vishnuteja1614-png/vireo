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

echo "== UI stress: 600 random interactions =="
# A launch-only check proves almost nothing. Monkey drives real taps, swipes
# and rotations through the editor, which is what surfaces crashes in panels
# and gesture handlers that a cold start never touches.
adb shell monkey -p com.vireo.editor \
  --pct-syskeys 0 --pct-majornav 15 --pct-touch 55 --pct-motion 25 \
  --throttle 60 -v 600 > monkey.txt 2>&1 || true
tail -5 monkey.txt
sleep 5
adb shell screencap -p /sdcard/after_stress.png
adb pull /sdcard/after_stress.png after_stress.png || true

adb logcat -d > logcat.txt || true
echo "== ANR scan =="
grep -c "ANR in com.vireo" logcat.txt || true

echo "== crash scan =="
grep -c "FATAL EXCEPTION" logcat.txt > /tmp/crashes || true
CRASHES=$(cat /tmp/crashes 2>/dev/null || echo 0)
echo "FATAL EXCEPTION count: $CRASHES"
grep -A 25 "FATAL EXCEPTION" logcat.txt || echo "no fatal exceptions"
test "$CRASHES" = "0"
