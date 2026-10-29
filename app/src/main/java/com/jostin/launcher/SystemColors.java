package com.jostin.launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

/**
 * Pinta Android (la barra de notificaciones, los ajustes rápidos, el teclado y las apps de Google)
 * con el color de Jostin OS. Usa el mismo ajuste que «Fondo y estilo» de Android, así que no rompe
 * nada y se quita regresando a los colores del fondo. Necesita un permiso que solo se da una vez
 * desde la computadora con ADB.
 */
final class SystemColors {
    static final String KEY = "theme_customization_overlay_packages";
    static final String ADB = "adb shell pm grant com.jostin.launcher android.permission.WRITE_SECURE_SETTINGS";
    static final String[] STYLES = {"Suave", "Vibrante", "Expresivo", "Pastel", "Monocromo"};
    private static final String[] STYLE_IDS = {"TONAL_SPOT", "VIBRANT", "EXPRESSIVE", "SPRITZ", "MONOCHROMATIC"};

    private SystemColors() {}

    static boolean granted(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean enabled(Context c) {
        return Prefs.androidColors(c) && granted(c);
    }

    private static String wanted(Context c) {
        int style = Math.max(0, Math.min(STYLES.length - 1, Prefs.androidStyle(c)));
        // Monocromo existe desde Android 13 en Pixel; antes se usa el suave
        if (style == 4 && Build.VERSION.SDK_INT < 33) style = 0;
        return String.format("%06X", Theme.accent & 0xFFFFFF) + "|" + STYLE_IDS[style];
    }

    /** Escribe la paleta si cambió. Se llama al salir de inicio (Android rehace las pantallas al aplicarla). */
    static void apply(Context c) {
        if (!Prefs.androidColors(c)) {
            // Lo apagaste: Android regresa a los colores del fondo
            if (granted(c) && Prefs.sp(c).contains("android_colors_key")) restore(c);
            return;
        }
        if (!granted(c)) return;
        String want = wanted(c);
        if (want.equals(Prefs.sp(c).getString("android_colors_key", ""))) return;
        try {
            String cur = Settings.Secure.getString(c.getContentResolver(), KEY);
            if (!Prefs.sp(c).contains("android_colors_backup")) {
                Prefs.sp(c).edit().putString("android_colors_backup", cur == null ? "" : cur).apply();
            }
            JSONObject o = cur == null || cur.isEmpty() ? new JSONObject() : new JSONObject(cur);
            String[] parts = want.split("\\|");
            o.put("android.theme.customization.system_palette", parts[0]);
            o.put("android.theme.customization.accent_color", parts[0]);
            o.put("android.theme.customization.theme_style", parts[1]);
            o.put("android.theme.customization.color_source", "preset");
            o.put("android.theme.customization.color_both", "1");
            o.put("_applied_timestamp", System.currentTimeMillis());
            Settings.Secure.putString(c.getContentResolver(), KEY, o.toString());
            Prefs.sp(c).edit().putString("android_colors_key", want).apply();
        } catch (Exception ignored) {
            // sin permiso o Android no lo aceptó: se queda como estaba
        }
    }

    /** Después de cambiar el fondo del sistema Android recalcula sus colores; se vuelven a poner los de Jostin. */
    static void reapply(Context c) {
        if (!enabled(c)) return;
        Prefs.sp(c).edit().remove("android_colors_key").apply();
        apply(c);
    }

    /** Regresa Android a los colores que tenía antes (los del fondo). */
    static void restore(Context c) {
        if (!granted(c)) return;
        try {
            String bk = Prefs.sp(c).getString("android_colors_backup", null);
            JSONObject o;
            if (bk != null && !bk.isEmpty()) {
                o = new JSONObject(bk);
            } else {
                o = new JSONObject();
                o.put("android.theme.customization.color_source", "home_wallpaper");
            }
            o.put("_applied_timestamp", System.currentTimeMillis());
            Settings.Secure.putString(c.getContentResolver(), KEY, o.toString());
        } catch (Exception ignored) {
            // nada
        }
        Prefs.sp(c).edit().remove("android_colors_key").remove("android_colors_backup").apply();
    }

    /** Agrega los botones de Jostin al final de los ajustes rápidos de Android. */
    static boolean addTiles(Context c, String[] specs) {
        if (!granted(c)) return false;
        try {
            String cur = Settings.Secure.getString(c.getContentResolver(), "sysui_qs_tiles");
            if (cur == null || cur.isEmpty()) return false;
            StringBuilder b = new StringBuilder(cur);
            for (String s : specs) if (!cur.contains(s)) b.append(',').append(s);
            Settings.Secure.putString(c.getContentResolver(), "sysui_qs_tiles", b.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
