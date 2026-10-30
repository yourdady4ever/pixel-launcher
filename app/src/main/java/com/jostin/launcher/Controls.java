package com.jostin.launcher;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;

/** Los botones del centro de control; lo usan inicio, el panel y el panel de la orilla. */
final class Controls {
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Controls() {}

    static void start(Activity a, Backdrop b, Intent i, String fail) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            if (fail != null) Sheet.pill(a, b, fail);
        }
    }

    /** 0 linterna, 1 internet, 2 volumen, 3 no molestar. Siempre dice lo que de verdad pasó. */
    static void toggle(Activity a, Backdrop b, int k) {
        switch (k) {
            case 0: {
                boolean on = Device.toggleTorch(a);
                Sheet.pill(a, b, on ? "Linterna encendida" : "Linterna apagada");
                break;
            }
            case 1:
                if (Build.VERSION.SDK_INT >= 29) start(a, b, new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), null);
                else start(a, b, new Intent(Settings.ACTION_WIFI_SETTINGS), null);
                break;
            case 2:
                if (Build.VERSION.SDK_INT >= 29) {
                    start(a, b, new Intent(Settings.Panel.ACTION_VOLUME), null);
                } else {
                    AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                }
                break;
            default: {
                NotificationManager nm = (NotificationManager) a.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm == null || !nm.isNotificationPolicyAccessGranted()) {
                    Sheet.confirm(a, b, "No molestar",
                            "Para prenderlo desde aquí, Android pide que Jostin OS tenga acceso a «No molestar». Si ya le diste permiso de notificaciones, normalmente ya lo tiene.",
                            "Abrir ajustes", false, () -> start(a, b, new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), null));
                    return;
                }
                boolean want = !NotifService.dndOn(a);
                if (!NotifService.setDnd(a, want)) {
                    Sheet.pill(a, b, "Android no dejó cambiar «No molestar»");
                    return;
                }
                main.postDelayed(() -> Sheet.pill(a, b, NotifService.dndOn(a) ? "No molestar activado" : "No molestar desactivado"), 300);
                break;
            }
        }
    }

    /**
     * Los botones que antes solo tenía el panel de Android. Rotación, ubicación y ahorro se cambian
     * aquí mismo; modo avión y zona Wi-Fi Android no se los deja a ninguna app, así que abren su
     * pantalla. Siempre dice lo que de verdad pasó.
     */
    static void extra(Activity a, Backdrop b, int q) {
        ContentResolver cr = a.getContentResolver();
        switch (q) {
            case Dashboard.Q_ROTATE: {
                if (!SystemColors.granted(a) && !Settings.System.canWrite(a)) {
                    Sheet.confirm(a, b, "Rotación",
                            "Para cambiarla desde aquí, Android pide que Jostin OS pueda modificar ajustes del sistema (el mismo permiso del brillo).",
                            "Abrir ajustes", false, () -> start(a, b, new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    android.net.Uri.parse("package:" + a.getPackageName())), null));
                    return;
                }
                boolean on = rotationOn(a);
                try {
                    Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, on ? 0 : 1);
                } catch (Exception e) {
                    Sheet.pill(a, b, "Android no dejó cambiar la rotación");
                    return;
                }
                Sheet.pill(a, b, rotationOn(a) ? "Rotación automática" : "Rotación bloqueada");
                break;
            }
            case Dashboard.Q_LOCATION: {
                // Android la da por apagada solo con location_mode en 0; se puede escribir con el permiso de ADB
                if (!SystemColors.granted(a)) {
                    start(a, b, new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), null);
                    return;
                }
                boolean on = locationOn(a);
                try {
                    Settings.Secure.putInt(cr, Settings.Secure.LOCATION_MODE, on ? 0 : 3);
                } catch (Exception e) {
                    start(a, b, new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), null);
                    return;
                }
                main.postDelayed(() -> Sheet.pill(a, b, locationOn(a) ? "Ubicación activada" : "Ubicación desactivada"), 300);
                break;
            }
            case Dashboard.Q_SAVER: {
                if (!SystemColors.granted(a)) {
                    start(a, b, new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), null);
                    return;
                }
                boolean on = saverOn(a);
                // Igual que el botón de Android, que mientras carga no deja prenderlo
                if (!on && plugged(a)) {
                    Sheet.pill(a, b, "El ahorro de batería no se prende mientras carga");
                    return;
                }
                try {
                    // Android vuelve a leer "low_power" cada vez que cambia y prende o apaga el ahorro
                    Settings.Global.putInt(cr, "low_power", on ? 0 : 1);
                } catch (Exception e) {
                    start(a, b, new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), null);
                    return;
                }
                main.postDelayed(() -> Sheet.pill(a, b, saverOn(a) ? "Ahorro de batería activado" : "Ahorro de batería desactivado"), 400);
                break;
            }
            case Dashboard.Q_PLANE:
                start(a, b, new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), "No se pudo abrir el modo avión");
                break;
            case Dashboard.Q_HOTSPOT:
                if (!tryStart(a, new Intent("com.android.settings.WIFI_TETHER_SETTINGS"))
                        && !tryStart(a, new Intent("android.settings.TETHER_SETTINGS"))) {
                    start(a, b, new Intent(Settings.ACTION_WIRELESS_SETTINGS), "No se pudo abrir la zona Wi-Fi");
                }
                break;
            case Dashboard.Q_CAST:
                start(a, b, new Intent(Settings.ACTION_CAST_SETTINGS), "No se pudo abrir Transmitir");
                break;
            case Dashboard.Q_SHARE: {
                Intent qs = new Intent().setClassName("com.google.android.gms", "com.google.android.gms.nearby.sharing.main.MainActivity");
                if (!tryStart(a, qs)) Sheet.pill(a, b, "Quick Share no está en este teléfono");
                break;
            }
            default: {
                // Al detener, el servicio avisa cuando de verdad terminó de guardar
                boolean was = Recorder.recording;
                Recorder.toggle(a);
                if (was) Sheet.pill(a, b, "Guardando la grabación…");
                break;
            }
        }
    }

    private static boolean tryStart(Activity a, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean rotationOn(Context c) {
        return Settings.System.getInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    static boolean locationOn(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 28 && lm != null) return lm.isLocationEnabled();
        return Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.LOCATION_MODE, 0) != 0;
    }

    static boolean saverOn(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }

    static boolean plugged(Context c) {
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return i != null && i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    /**
     * Android solo apaga al cargar el ahorro que prendió su propio botón: el prendido escribiendo
     * "low_power" se quedaba prendido. Aquí se hace lo mismo que hace él: se apaga al conectar y,
     * si al desconectar la batería sigue debajo de 90%, se prende otra vez.
     */
    static void saverCharging(Context c, boolean plugged) {
        try {
            if (plugged) {
                if (!saverOn(c)) return;
                Settings.Global.putInt(c.getContentResolver(), "low_power", 0);
                Prefs.setSaverBack(c, true);
            } else if (Prefs.saverBack(c)) {
                Prefs.setSaverBack(c, false);
                BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
                if (bm != null && bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) < 90) {
                    Settings.Global.putInt(c.getContentResolver(), "low_power", 1);
                }
            }
        } catch (Exception ignored) {
            // sin el permiso de ADB no se puede tocar
        }
    }

    /** Empieza o termina el modo estudio (25 minutos) sin preguntar; para botones rápidos. */
    static boolean toggleStudy(Context c) {
        if (Prefs.studying(c)) {
            Prefs.setStudyEnd(c, 0);
            return false;
        }
        Prefs.setStudyEnd(c, System.currentTimeMillis() + 25 * 60_000L);
        return true;
    }
}
