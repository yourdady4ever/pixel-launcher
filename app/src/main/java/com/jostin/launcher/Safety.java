package com.jostin.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Que un diseño dañado nunca deje el teléfono sin pantalla de inicio:
 * copias automáticas del diseño, último diseño que funcionó y recuperación si se cierra al abrir.
 */
final class Safety {
    private static final String BOOT = "safe_boot", CRASHES = "safe_crashes", GOOD = "home_good";
    private static final String BACKUPS = "home_backups";
    private static final int KEEP = 4;

    private Safety() {}

    private static SharedPreferences sp(Context c) {
        return Prefs.sp(c);
    }

    /** Al abrir: si las dos últimas veces no llegó a estar estable, se restaura el último diseño bueno. */
    static boolean onCreate(Context c) {
        SharedPreferences p = sp(c);
        int crashes = p.getBoolean(BOOT, false) ? p.getInt(CRASHES, 0) + 1 : 0;
        boolean recovered = false;
        if (crashes >= 2) {
            String good = p.getString(GOOD, null);
            SharedPreferences.Editor e = p.edit();
            if (good != null) e.putString("home_v1", good);
            else e.remove("home_v1");
            // Lo más probable en fallar: una foto de fondo dañada
            e.putInt("wall_kind", 0);
            e.apply();
            crashes = 0;
            recovered = true;
        }
        p.edit().putBoolean(BOOT, true).putInt(CRASHES, crashes).apply();
        return recovered;
    }

    /** Llamar unos segundos después de mostrar inicio sin problemas. */
    static void stable(Context c) {
        SharedPreferences p = sp(c);
        String now = p.getString("home_v1", null);
        SharedPreferences.Editor e = p.edit().putBoolean(BOOT, false).putInt(CRASHES, 0);
        if (now != null) e.putString(GOOD, now);
        e.apply();
    }

    /** Una copia por día, se guardan las últimas cuatro. */
    static void maybeBackup(Context c) {
        SharedPreferences p = sp(c);
        String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        if (day.equals(p.getString("backup_day", ""))) return;
        backupNow(c, day);
        p.edit().putString("backup_day", day).apply();
    }

    static void backupNow(Context c, String tag) {
        SharedPreferences p = sp(c);
        String now = p.getString("home_v1", null);
        if (now == null) return;
        List<String> tags = tags(c);
        tags.remove(tag);
        tags.add(0, tag);
        while (tags.size() > KEEP) {
            String old = tags.remove(tags.size() - 1);
            p.edit().remove("home_bk_" + old).apply();
        }
        p.edit().putString("home_bk_" + tag, now).putString(BACKUPS, String.join(",", tags)).apply();
    }

    static List<String> tags(Context c) {
        String s = sp(c).getString(BACKUPS, "");
        List<String> out = new ArrayList<>();
        for (String t : s.split(",")) if (!t.isEmpty()) out.add(t);
        return out;
    }

    static String backup(Context c, String tag) {
        return sp(c).getString("home_bk_" + tag, null);
    }

    static String label(String tag) {
        try {
            if (tag.startsWith("antes-")) return "Antes de restablecer";
            Date d = new SimpleDateFormat("yyyyMMdd", Locale.US).parse(tag);
            return d == null ? tag : new SimpleDateFormat("EEEE d 'de' MMMM", MainActivity.ES).format(d);
        } catch (Exception e) {
            return tag;
        }
    }
}
