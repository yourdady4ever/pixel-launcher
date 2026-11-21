package com.jostin.launcher;

import android.app.Activity;
import android.content.Context;

import java.util.regex.Pattern;

/**
 * La clave secreta de tu pez en Firebase (/u/CLAVE/...). Llega por el botón «CONECTAR JOSTINOS» de
 * la página o se pega a mano. Nunca va en el repo, en el registro ni en el respaldo.
 */
final class PezKey {
    private static final String DB = "https://pez-estudio-default-rtdb.firebaseio.com/u/";
    private static final Pattern OK = Pattern.compile("[A-Za-z0-9_-]{24,}");
    private static volatile String cached;
    private static volatile boolean loaded;

    private PezKey() {}

    static boolean valid(String k) {
        return k != null && OK.matcher(k).matches();
    }

    static String get(Context c) {
        if (!loaded) {
            cached = Prefs.sp(c).getString("pez_key", null);
            loaded = true;
        }
        return cached;
    }

    static void set(Context c, String k) {
        Prefs.sp(c).edit().putString("pez_key", k).remove("pez_last").apply();
        cached = k;
        loaded = true;
        Gadgets.PezG.reset();
        AppUsage.kick(c);
    }

    static String pez(Context c) {
        String k = get(c);
        return k == null ? null : DB + k + "/pez.json";
    }

    static String apps(Context c) {
        String k = get(c);
        return k == null ? null : DB + k + "/apps.json";
    }

    static String appsCel(Context c) {
        String k = get(c);
        return k == null ? null : DB + k + "/apps/cel.json";
    }

    /** Quita la clave de cualquier texto (para el registro). */
    static String hide(String s) {
        String k = cached;
        return k == null || s == null ? s : s.replace(k, "•••");
    }

    /** Pegarla a mano desde ajustes. */
    static void ask(Activity a, Backdrop b) {
        Sheet.prompt(a, b, "Pega la clave de tu pez", "La ves en pez-estudio.web.app", "", v -> {
            String k = v == null ? "" : v.trim();
            if (!valid(k)) {
                Sheet.pill(a, b, "Esa clave no se ve bien (letras, números, - o _, mínimo 24)");
                return;
            }
            set(a, k);
            Sheet.pill(a, b, "Pez conectado ✅");
        });
    }
}
