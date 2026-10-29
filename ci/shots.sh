#!/bin/bash
# Instala el APK en el emulador, abre cada pantalla y toma capturas para revisar el diseño.
set -x
PKG=com.jostin.launcher
OUT=shots
mkdir -p "$OUT"
adb install -r JostinOS.apk
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb logcat -c

start() { adb shell am start -n $PKG/.MainActivity "$@"; }
snap() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/$1.png"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 1.5; }

start --ei theme 0; snap 01_inicio_olas 8
start --ei theme 1; snap 02_inicio_niebla 4
start --ei theme 2; snap 03_inicio_noche 4
start --ei theme 3; snap 04_inicio_panal 4
start --ei theme 4; snap 05_inicio_rojo 4
start --ei theme 0 --es shot picker; snap 06_selector 4
back
start --es shot calendar; snap 07_calendario 4
back
start --es shot settings; snap 08_ajustes 4
back
start --es shot study; snap 09_estudio 3
start --es shot nostudy; sleep 1
start --es open apps; snap 10_cajon 6
back
start --es open calc --es expr "'sin(30)+√(16)×2^3'"; snap 11_calc 5
back
start --es open calc --es expr "'√(2)×π÷7'" --ez eq true; snap 12_calc_resultado 5
back
start --es open calc --ei tab 1 --ez demo true; snap 13_obra 5
back
start --es open tasks --ez demo true; snap 14_pendientes 5
back

# Video corto de la animación de entrada
adb shell screenrecord --time-limit 5 /sdcard/entrada.mp4 &
REC=$!
sleep 1
start --ei theme 2
sleep 6
wait $REC
adb pull /sdcard/entrada.mp4 "$OUT/entrada.mp4" || true

adb logcat -d -b crash > "$OUT/crash.txt" || true
adb logcat -d | grep -iE "jostin|AndroidRuntime|FATAL" > "$OUT/log.txt" || true
ls -la "$OUT"
