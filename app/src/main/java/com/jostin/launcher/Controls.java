package com.jostin.launcher;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/** Los botones rápidos de inicio. */
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
