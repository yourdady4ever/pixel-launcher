package com.jostin.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Todo lo que el launcher recuerda. */
final class Prefs {
    static final int SLOTS = 6;

    /** Apps que se bloquean en modo estudio. */
    static final Set<String> DISTRACTIONS = new HashSet<>(Arrays.asList(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.twitter.android",
            "com.snapchat.android",
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.reddit.frontpage",
            "com.discord",
            "com.roblox.client"
    ));

    /** Si no has elegido apps, se llenan los canales con estas (si las tienes). */
    static final String[] DEFAULT_APPS = {
            "com.whatsapp",
            "com.google.android.youtube",
            "com.android.chrome",
            "com.google.android.GoogleCamera",
            "com.spotify.music",
            "com.instagram.android",
            "com.google.android.apps.photos",
            "com.google.android.gm",
            "com.google.android.apps.maps",
            "com.roblox.client"
    };

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("jostin_os", Context.MODE_PRIVATE);
    }

    static String slot(Context c, int i) {
        return sp(c).getString("slot_" + i, null);
    }

    static void setSlot(Context c, int i, String pkg) {
        sp(c).edit().putString("slot_" + i, pkg).apply();
    }

    static boolean slotsInitialized(Context c) {
        return sp(c).getBoolean("slots_init", false);
    }

    static void markSlotsInitialized(Context c) {
        sp(c).edit().putBoolean("slots_init", true).apply();
    }

    static long studyEnd(Context c) {
        return sp(c).getLong("study_end", 0);
    }

    static void setStudyEnd(Context c, long t) {
        sp(c).edit().putLong("study_end", t).apply();
    }

    static boolean studying(Context c) {
        return studyEnd(c) > System.currentTimeMillis();
    }

    static String tasks(Context c) {
        return sp(c).getString("tasks", "[]");
    }

    static void setTasks(Context c, String json) {
        sp(c).edit().putString("tasks", json).apply();
    }

    static boolean sounds(Context c) {
        return sp(c).getBoolean("sounds", true);
    }

    static void setSounds(Context c, boolean on) {
        sp(c).edit().putBoolean("sounds", on).apply();
    }

    static int theme(Context c) {
        return sp(c).getInt("theme", 0);
    }

    static void setTheme(Context c, int t) {
        sp(c).edit().putInt("theme", t).apply();
    }

    /** Íconos de apps en estilo monocromo (blanco sobre vidrio). */
    static boolean monoIcons(Context c) {
        return sp(c).getBoolean("mono_icons", true);
    }

    static void setMonoIcons(Context c, boolean on) {
        sp(c).edit().putBoolean("mono_icons", on).apply();
    }

    static boolean parallax(Context c) {
        return sp(c).getBoolean("parallax", true);
    }

    static void setParallax(Context c, boolean on) {
        sp(c).edit().putBoolean("parallax", on).apply();
    }

    static String history(Context c) {
        return sp(c).getString("calc_history", "[]");
    }

    static void setHistory(Context c, String json) {
        sp(c).edit().putString("calc_history", json).apply();
    }

    static boolean degrees(Context c) {
        return sp(c).getBoolean("calc_deg", true);
    }

    static void setDegrees(Context c, boolean on) {
        sp(c).edit().putBoolean("calc_deg", on).apply();
    }

    static boolean animated(Context c) {
        return sp(c).getBoolean("anim", true);
    }

    static void setAnimated(Context c, boolean on) {
        sp(c).edit().putBoolean("anim", on).apply();
    }
}
