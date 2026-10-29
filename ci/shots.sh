#!/bin/bash
# Instala el APK en el emulador, prueba cada función nueva y toma capturas para revisar el diseño.
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

# Foto de prueba para el fondo (se baja en el servidor de GitHub, no va dentro del APK)
curl -sSL -o test.jpg "https://raw.githubusercontent.com/opencv/opencv/4.x/samples/data/fruits.jpg" || true
adb shell mkdir -p /sdcard/Android/data/$PKG/files
adb push test.jpg /sdcard/Android/data/$PKG/files/test.jpg || true

start --ei theme 0; snap 01_inicio_olas 9
start --ei theme 5; snap 02_aurora 5
start --ei theme 6; snap 03_fluido 5
start --ei theme 7; snap 04_constelacion 5
start --ei theme 8; snap 05_plano 6
start --ei theme 0 --es shot studio --ei tab 0; snap 06_estudio_fondo 5
back
start --es shot studio --ei tab 2; snap 07_estudio_reloj 4
back
start --ei demo_clock 1 --ei demo_align 1 --ei demo_font 3 --ei theme 2; snap 08_reloj_digital 5
start --ei demo_clock 2 --ei demo_align 1 --ei demo_font 0 --ei theme 6; snap 09_reloj_apilado 5
start --ei demo_clock 1 --ei demo_align 0 --ei demo_font 1 --es demo_photo test.jpg; snap 10_foto 8
start --ei demo_effect 1 --es demo_photo test.jpg; snap 11_foto_puntos 8
start --ei demo_effect 2 --ei demo_overlay 1 --es demo_photo test.jpg; snap 12_foto_duotono_lluvia 8
start --es shot studio --ei tab 1; snap 13_estudio_color 4
back

adb shell cmd notification allow_listener $PKG/$PKG.NotifService || true
adb shell cmd notification post -S bigtext -t "Salón Jardín" cotizacion "¿Me mandas la cotización para el sábado?" || true
start --es demo_city "Cuautla" --es shot dashboard; snap 14_panel 9
back
adb shell input swipe 540 1400 540 450 300; snap 15_cajon 4
back
start --es open calc --es expr "'sin(30)+√(16)×2^3'"; snap 16_calc_color 5
back
start --es shot studio --ei tab 3; snap 17_estudio_inicio 4
back
start --ei demo_reset 1 --ei theme 0; sleep 3

adb logcat -d -b crash > "$OUT/crash.txt" || true
adb logcat -d | grep -iE "jostin|AndroidRuntime|FATAL" > "$OUT/log.txt" || true
find "$OUT" -type f -size 0 -delete
ls -la "$OUT"
