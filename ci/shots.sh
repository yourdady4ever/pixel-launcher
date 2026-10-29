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
# El launcher de Google del emulador se congela con tanta carga y su aviso tapa las capturas:
# se pone Jostin OS como inicio y se detiene el otro
adb shell cmd package set-home-activity $PKG/$PKG.MainActivity || true
adb shell am force-stop com.google.android.apps.nexuslauncher || true
# Permisos que en el teléfono se dan con un toque
adb shell appwidget grantbind --package $PKG --user 0 || true
adb shell pm grant $PKG android.permission.READ_CALENDAR || true
adb shell cmd notification allow_listener $PKG/$PKG.NotifService || true
adb logcat -c

start() { adb shell am start -n $PKG/.MainActivity "$@"; }
snap() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/$1.png"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 1.5; }

# Foto de prueba para el fondo (se baja en el servidor de GitHub, no va dentro del APK)
curl -sSL -o test.jpg "https://raw.githubusercontent.com/opencv/opencv/4.x/samples/data/fruits.jpg" || true
adb shell mkdir -p /sdcard/Android/data/$PKG/files
adb push test.jpg /sdcard/Android/data/$PKG/files/test.jpg || true
adb shell cmd notification post -S bigtext -t "Salón Jardín" cotizacion "¿Me mandas la cotización para el sábado?" || true
adb shell cmd notification post -S bigtext -t "Mamá" recado "No se te olvide pasar por las tortillas" || true

# Inicio y sus páginas
start --ei theme 0 --es demo_city "Cuautla"; snap 01_inicio 10
start --es shot page --ei tab 1; snap 02_pagina2 4
start --es shot page --ei tab 0; sleep 2
start --ei theme 5; snap 03_aurora 5
start --ei theme 7; snap 04_constelacion 5

# Modo edición, selección y catálogo de widgets
start --es shot edit; snap 05_editar 4
start --es shot select; snap 06_seleccion 3
start --es shot picker; snap 07_catalogo 5
back
start --es shot picker_apps; snap 08_catalogo_apps 6
back
start --es shot aw; snap 09_widget_android 9
start --es shot done; snap 10_con_widget 4
start --es shot folder; snap 11_carpeta 7
back
start --es shot menu --es arg reloj; snap 12_menu_widget 4
back

# Modo libre: decorativos encimados
start --ez demo_free true --es shot add --es arg forma; snap 13_libre 5
start --es shot add --es arg analogico; snap 14_libre_reloj 5
start --es shot done; sleep 2
start --ez demo_free false; sleep 3

# Estudio de personalización
start --es shot studio --ei tab 2; snap 15_estudio_inicio 4
back
start --es shot studio --ei tab 3; snap 16_estudio_iconos 4
back
start --es shot studio --ei tab 4; snap 17_estudio_dock 4
back

# Panel con notificaciones que se pueden quitar
start --es shot dashboard; snap 18_panel 8
adb shell input swipe 540 1900 540 700 400; snap 19_panel_avisos 3
back
adb shell input swipe 540 1700 540 600 300; snap 20_cajon 4
back
start --es open calc --es expr "'sin(30)+√(16)×2^3'"; snap 21_calc 5
back

# Fondo del sistema: recientes y pantalla de bloqueo deben verse con el fondo de Jostin OS
start --ei theme 5; sleep 6
adb shell input keyevent KEYCODE_APP_SWITCH; snap 22_recientes 3
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; snap 23_bloqueo 3
adb shell wm dismiss-keyguard || true
start --ei demo_reset 1 --ei theme 0; sleep 3

# Rendimiento y memoria
adb shell dumpsys meminfo $PKG | head -45 > "$OUT/memoria.txt" || true
adb shell dumpsys gfxinfo $PKG | head -40 > "$OUT/cuadros.txt" || true
adb shell dumpsys wallpaper | head -30 > "$OUT/wallpaper.txt" || true
adb logcat -d -b crash > "$OUT/crash.txt" || true
adb logcat -d | grep -iE "jostin|AndroidRuntime|FATAL" > "$OUT/log.txt" || true
find "$OUT" -type f -size 0 -delete
ls -la "$OUT"
