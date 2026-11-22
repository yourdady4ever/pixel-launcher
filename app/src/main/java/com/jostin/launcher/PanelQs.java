package com.jostin.launcher;

import android.content.Context;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Acomoda los ajustes rápidos de Android (sysui_qs_tiles): cambia los de Google por los de Jostin.
 * Antes de tocar nada guarda cómo estaban, para regresarlos con un botón.
 */
final class PanelQs {
    private PanelQs() {}

    private static final String KEY = "sysui_qs_tiles", ORIG = "qs_orig";

    static String read(Context c) {
        try {
            return Settings.Secure.getString(c.getContentResolver(), KEY);
        } catch (Exception e) {
            return null;
        }
    }

    /** Con el permiso de ADB directo; si no, con Shizuku. Solo en un hilo de fondo. */
    private static boolean write(Context c, String v) {
        if (SystemColors.granted(c)) {
            try {
                if (Settings.Secure.putString(c.getContentResolver(), KEY, v)) return true;
            } catch (Exception ignored) {
                // se intenta con Shizuku
            }
        }
        Shizu.Res r = Shizu.runSync(c, "settings put secure " + KEY + " " + Shizu.q(v));
        return r != null && r.ok();
    }

    static boolean canWrite(Context c) {
        return SystemColors.granted(c) || Shizu.listo(c);
    }

    static boolean saved(Context c) {
        return Prefs.sp(c).getString(ORIG, null) != null;
    }

    /** Cambia Internet/Wi-Fi, Bluetooth y Modo avión por los de Jostin (en su mismo lugar) y agrega Limpiar. */
    static boolean arrange(Context c) {
        String cur = read(c);
        if (cur == null || cur.isEmpty()) return false;
        if (!saved(c)) Prefs.sp(c).edit().putString(ORIG, cur).commit();
        List<String> out = new ArrayList<>();
        boolean[] done = new boolean[QsTiles.REPLACED_BY.length];
        for (String t : cur.split(",")) {
            String tile = t.trim();
            if (tile.isEmpty()) continue;
            int hit = -1;
            for (int i = 0; i < QsTiles.REPLACES.length && hit < 0; i++) {
                for (String g : QsTiles.REPLACES[i]) if (g.equals(tile)) hit = i;
            }
            if (hit >= 0) {
                if (!done[hit]) out.add(QsTiles.spec(c, QsTiles.REPLACED_BY[hit]));
                done[hit] = true;
            } else if (!out.contains(tile)) {
                out.add(tile);
            }
        }
        for (int i = 0; i < done.length; i++) {
            String s = QsTiles.spec(c, QsTiles.REPLACED_BY[i]);
            if (!out.contains(s)) out.add(s);
        }
        String clean = QsTiles.spec(c, "Clean");
        if (!out.contains(clean)) out.add(clean);
        return write(c, String.join(",", out));
    }

    /** Regresa el panel exactamente como estaba antes de acomodarlo. */
    static boolean restore(Context c) {
        String o = Prefs.sp(c).getString(ORIG, null);
        if (o == null) return false;
        if (!write(c, o)) return false;
        Prefs.sp(c).edit().remove(ORIG).apply();
        return true;
    }
}
