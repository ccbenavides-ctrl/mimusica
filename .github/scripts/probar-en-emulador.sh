#!/usr/bin/env bash
# Instala la app, la abre, concede los permisos tocando los botones y recorre las pantallas principales.
PKG=app.mimusica
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml; }
show() { echo "===== $1 ====="; dump | tr '>' '\n' | grep -o 'text="[^"]\+"[^/]*bounds="[^"]*"' | sed -E 's/ (resource-id|class|package|content-desc|checkable|checked|clickable|enabled|focusable|focused|scrollable|long-clickable|password|selected|index)="[^"]*"//g' | head -60; }
tap() {
  local B; B=$(dump | tr '>' '\n' | grep -E "text=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1)
  if [ -z "$B" ]; then echo "(no se encontró «$1»)"; return 1; fi
  read X1 Y1 X2 Y2 <<< "$(echo "$B" | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/')"
  echo "tocar «$1» en $(( (X1+X2)/2 )),$(( (Y1+Y2)/2 ))"
  adb shell input tap $(( (X1+X2)/2 )) $(( (Y1+Y2)/2 ))
}
anr() { echo "===== ANR / CRASH ($1) ====="; adb logcat -d | grep -E "ANR in|FATAL EXCEPTION|E AndroidRuntime" -A 12 | head -60; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 15
show "Inicio sin permisos"; anr "arranque"
tap "Dar permiso"; sleep 4
show "Diálogo de permisos"
tap "Allow" || tap "Permitir"; sleep 3
show "Segundo diálogo"
tap "Allow" || tap "Permitir"; sleep 6
show "Inicio con permisos"; anr "permisos"
tap "Radio en vivo"; sleep 12
show "Radio en vivo"; anr "radio"
adb shell input keyevent KEYCODE_BACK; sleep 3
tap "Biblioteca"; sleep 4
show "Biblioteca"
tap "Descubrir"; sleep 6
show "Descubrir"; anr "final"
adb root >/dev/null 2>&1; sleep 3
for f in $(adb shell ls /data/anr/ 2>/dev/null); do
  echo "--- traza $f"; adb shell cat /data/anr/$f | awk '/"main"/{p=1} p{print} /^$/{if(p) exit}' | head -40
done
echo "pid=$(adb shell pidof $PKG)"
exit 0
