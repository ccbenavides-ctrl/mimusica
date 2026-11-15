#!/usr/bin/env bash
# Instala la app en el emulador, la abre, toca "Dar permiso" y vuelca el estado y los registros.
set -x
PKG=app.mimusica
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 20
echo "===== PANTALLA (inicio) ====="
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | head -60
echo "===== TOCAR Dar permiso ====="
B=$(adb shell cat /sdcard/ui.xml | grep -o 'text="Dar permiso"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1)
echo "$B"
if [ -z "$B" ]; then
  B=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -B0 -A0 'Dar permiso' | grep -o 'bounds="[^"]*"' | head -1)
fi
XY=$(echo "$B" | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/')
read X1 Y1 X2 Y2 <<< "$XY"
if [ -n "$X1" ]; then adb shell input tap $(( (X1+X2)/2 )) $(( (Y1+Y2)/2 )); fi
sleep 12
echo "===== PANTALLA (después de tocar) ====="
adb shell uiautomator dump /sdcard/ui2.xml >/dev/null 2>&1; adb shell cat /sdcard/ui2.xml | grep -o 'text="[^"]*"' | head -60
echo "===== ANR / CRASH ====="
adb logcat -d | grep -E "ANR in|FATAL|AndroidRuntime|Input dispatching timed out|not responding" | head -40
adb root >/dev/null 2>&1; sleep 3
echo "===== TRAZAS ANR (hilo principal) ====="
for f in $(adb shell ls /data/anr/ 2>/dev/null); do
  echo "--- $f"
  adb shell cat /data/anr/$f | awk '/"main"/{p=1} p{print} /^$/{if(p) exit}' | head -80
done
echo "===== LOGCAT DE LA APP ====="
PID=$(adb shell pidof $PKG)
echo "pid=$PID"
adb logcat -d | grep -E "$PKG|MiMusica|ExoPlayer|Media3|MediaSession|DataStore|Choreographer" | tail -150
exit 0
