package com.jostin.launcher;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * Ajustes de Android que solo se pueden cambiar con el permiso WRITE_SECURE_SETTINGS (que tú das una
 * vez por ADB): blanco y negro, animaciones más rápidas y el acceso a notificaciones de Jostin.
 * Antes de tocar algo se guarda su valor original, para que «Deshacer todo» lo deje como estaba.
 */
final class Poderes {
    private static final String DALTON_ON = "accessibility_display_daltonizer_enabled", DALTON = "accessibility_display_daltonizer";
    private static final String[] ANIMS = {"window_animation_scale", "transition_animation_scale", "animator_duration_scale"};
    private static final String NOTIF = "enabled_notification_listeners";

    private Poderes() {}

    private static SharedPreferences orig(Context c) {
        return c.getSharedPreferences("poderes_orig", Context.MODE_PRIVATE);
    }

    /** Guarda el valor original una sola vez (secure: s_, global: g_). */
    private static void remember(Context c, boolean global, String key) {
        SharedPreferences o = orig(c);
        String k = (global ? "g_" : "s_") + key;
        if (o.contains(k)) return;
        ContentResolver r = c.getContentResolver();
        String v = global ? Settings.Global.getString(r, key) : Settings.Secure.getString(r, key);
        o.edit().putString(k, v == null ? "\u0000" : v).apply();
    }

    private static boolean putSecure(Context c, String key, String v) {
        try {
            remember(c, false, key);
            return Settings.Secure.putString(c.getContentResolver(), key, v);
        } catch (Exception e) {
            Bitacora.error("ajuste " + key, e);
            return false;
        }
    }

    private static boolean putGlobal(Context c, String key, String v) {
        try {
            remember(c, true, key);
            return Settings.Global.putString(c.getContentResolver(), key, v);
        } catch (Exception e) {
            Bitacora.error("ajuste " + key, e);
            return false;
        }
    }

    // ---------- Blanco y negro ----------

    static boolean grayOn(Context c) {
        return Settings.Secure.getInt(c.getContentResolver(), DALTON_ON, 0) == 1
                && Settings.Secure.getInt(c.getContentResolver(), DALTON, -1) == 0;
    }

    static boolean setGray(Context c, boolean on) {
        if (!SystemColors.granted(c)) return false;
        boolean ok = putSecure(c, DALTON_ON, on ? "1" : "0");
        if (on) ok &= putSecure(c, DALTON, "0");
        return ok;
    }

    static boolean grayInStudy(Context c) {
        return Prefs.sp(c).getBoolean("gray_study", false);
    }

    static void setGrayInStudy(Context c, boolean on) {
        Prefs.sp(c).edit().putBoolean("gray_study", on).apply();
    }

    /** Al empezar o terminar el modo estudio; solo apaga el gris si lo prendió el modo estudio. */
    static void onStudy(Context c, boolean studying) {
        if (!grayInStudy(c) || !SystemColors.granted(c)) return;
        SharedPreferences p = Prefs.sp(c);
        if (studying && !grayOn(c)) {
            setGray(c, true);
            p.edit().putBoolean("gray_by_study", true).apply();
        } else if (!studying && p.getBoolean("gray_by_study", false)) {
            setGray(c, false);
            p.edit().remove("gray_by_study").apply();
        }
    }

    // ---------- Animaciones ----------

    static boolean fastOn(Context c) {
        return "0.5".equals(Settings.Global.getString(c.getContentResolver(), ANIMS[0]));
    }

    static boolean setFast(Context c, boolean on) {
        if (!SystemColors.granted(c)) return false;
        boolean ok = true;
        for (String k : ANIMS) ok &= putGlobal(c, k, on ? "0.5" : "1.0");
        return ok;
    }

    // ---------- Accesos que le faltan ----------

    /** Activa el acceso a notificaciones de Jostin (música, puntos en íconos y avisos del pez). */
    static boolean grantNotifAccess(Context c) {
        if (!SystemColors.granted(c) || Media.hasAccess(c)) return false;
        String me = new ComponentName(c, NotifService.class).flattenToString();
        String cur = Settings.Secure.getString(c.getContentResolver(), NOTIF);
        return putSecure(c, NOTIF, cur == null || cur.isEmpty() ? me : cur + ":" + me);
    }

    // ---------- Deshacer todo ----------

    /** Deja como estaban todos los ajustes que Jostin cambió y apaga sus opciones. Devuelve cuántos restauró. */
    static int undoAll(Context c) {
        int n = 0;
        ContentResolver r = c.getContentResolver();
        SharedPreferences o = orig(c);
        for (String k : o.getAll().keySet()) {
            String v = o.getString(k, null);
            String key = k.substring(2);
            String val = "\u0000".equals(v) ? null : v;
            try {
                if (k.startsWith("g_")) Settings.Global.putString(r, key, val);
                else Settings.Secure.putString(r, key, val);
                n++;
            } catch (Exception e) {
                Bitacora.error("deshacer " + key, e);
            }
        }
        o.edit().clear().apply();
        Prefs.sp(c).edit().putBoolean("gray_study", false).remove("gray_by_study").apply();
        // Los colores de Android regresan a los de tu fondo
        if (Prefs.androidColors(c)) {
            Prefs.setAndroidColors(c, false);
            SystemColors.restore(c);
            n++;
        }
        return n;
    }

    /** Lo que tienes que escribir en la PC para quitarle a Jostin el permiso (eso no lo puede hacer solo). */
    static final String REVOKE = "adb shell pm revoke com.jostin.launcher android.permission.WRITE_SECURE_SETTINGS";
}
