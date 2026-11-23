package com.jostin.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Pequeños recordatorios del pez en el gadget (sin IA: el pez solo usa sus frases de siempre).
 * - once: algo que se dice una sola vez al día (p. ej. festejar los días juntos).
 * - levelUp: si subió de nivel desde la última vez que se vio.
 */
final class PezIA {
    private PezIA() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("pez_ia", Context.MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** true si hoy todavía no se usó ese motivo (y lo marca). */
    static boolean once(Context c, String what) {
        String d = today();
        if (d.equals(sp(c).getString("once_" + what, ""))) return false;
        sp(c).edit().putString("once_" + what, d).apply();
        return true;
    }

    /** ¿Subió de nivel desde la última vez? (la primera lectura solo se anota) */
    static boolean levelUp(Context c, int lv) {
        int old = sp(c).getInt("lvl", -1);
        if (old == lv) return false;
        sp(c).edit().putInt("lvl", lv).apply();
        return old >= 0 && lv > old;
    }
}
