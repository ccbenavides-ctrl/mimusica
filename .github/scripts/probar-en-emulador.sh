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

# Pantalla parecida a la del celular de Camilo (1080x2400) con letra grande.
adb shell wm size 1080x2400
adb shell wm density 440
adb shell settings put system font_scale 1.15
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 15
show "Inicio sin permisos"; anr "arranque"
echo "===== Elementos de la parte de arriba (para revisar recortes) ====="
dump | tr '>' '\n' | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | awk -F'[][,"]' '$4 < 500' | sort -u | head -40
adb shell screencap -p /sdcard/inicio.png
tap "Dar permiso" || { adb shell input swipe 540 1450 540 650 600; sleep 2; tap "Dar permiso"; }; sleep 4
show "Diálogo de permisos"; dump | tr '>' '\n' | grep -oE 'text="[^"]*(llow|ermit)[^"]*"' | head
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
show "Descubrir"
tap "Buscar"; sleep 3
adb shell input tap 540 330; sleep 1
adb shell input text "Juanes"; adb shell input keyevent KEYCODE_ENTER; sleep 6
show "Buscar Juanes"
tap "Spotify"; sleep 8
echo "===== App en primer plano tras tocar Spotify ====="
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -3
adb shell input keyevent KEYCODE_BACK; sleep 3
echo "===== Nombre de la app ====="
adb shell dumpsys package app.mimusica | grep -E "versionName" | head -2
adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER app.mimusica | tail -1
anr "final"
adb root >/dev/null 2>&1; sleep 3
for f in $(adb shell ls /data/anr/ 2>/dev/null); do
  echo "--- traza $f"; adb shell cat /data/anr/$f | awk '/"main"/{p=1} p{print} /^$/{if(p) exit}' | head -40
done
echo "pid=$(adb shell pidof $PKG)"
exit 0
