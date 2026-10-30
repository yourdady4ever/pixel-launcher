package com.jostin.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Todo lo que el launcher recuerda. */
final class Prefs {
    static final int SLOTS = 6;

    /** Apps que se pausan en modo estudio. */
    static final Set<String> DISTRACTIONS = new HashSet<>(Arrays.asList(
            "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.facebook.katana", "com.facebook.lite", "com.twitter.android", "com.snapchat.android",
            "com.google.android.youtube", "com.netflix.mediaclient", "com.reddit.frontpage",
            "com.discord", "com.roblox.client"));

    /** Si no has elegido apps, el dock se llena con estas (si las tienes). */
    static final String[] DEFAULT_APPS = {
            "com.whatsapp", "com.google.android.youtube", "com.android.chrome",
            "com.google.android.GoogleCamera", "com.spotify.music", "com.instagram.android",
            "com.google.android.apps.photos", "com.google.android.gm", "com.google.android.apps.maps",
            "com.roblox.client"};

    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("jostin_os", Context.MODE_PRIVATE);
    }

    private static int i(Context c, String k, int d) { return sp(c).getInt(k, d); }
    private static boolean b(Context c, String k, boolean d) { return sp(c).getBoolean(k, d); }
    private static String s(Context c, String k, String d) { return sp(c).getString(k, d); }
    private static void put(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }
    private static void put(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
    private static void put(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    // ---------- Dock ----------
    static String slot(Context c, int n) { return s(c, "slot_" + n, null); }
    static void setSlot(Context c, int n, String pkg) { put(c, "slot_" + n, pkg); }
    static boolean slotsInitialized(Context c) { return b(c, "slots_init", false); }
    static void markSlotsInitialized(Context c) { put(c, "slots_init", true); }
    static int dockCount(Context c) { return Math.max(4, Math.min(SLOTS, i(c, "dock_count", SLOTS))); }
    static void setDockCount(Context c, int v) { put(c, "dock_count", v); }
    /** 0 monocromo, 1 originales, 2 color de acento. */
    static int iconStyle(Context c) { return i(c, "icon_style", 0); }
    static void setIconStyle(Context c, int v) { put(c, "icon_style", v); }

    static Set<String> hidden(Context c) { return new HashSet<>(sp(c).getStringSet("hidden_apps", new HashSet<>())); }
    static void setHidden(Context c, Set<String> h) { sp(c).edit().putStringSet("hidden_apps", new HashSet<>(h)).apply(); }

    // ---------- Estudio y pendientes ----------
    static long studyEnd(Context c) { return sp(c).getLong("study_end", 0); }
    static void setStudyEnd(Context c, long t) { sp(c).edit().putLong("study_end", t).apply(); }
    static boolean studying(Context c) { return studyEnd(c) > System.currentTimeMillis(); }
    static String tasks(Context c) { return s(c, "tasks", "[]"); }
    static void setTasks(Context c, String json) { put(c, "tasks", json); }

    // ---------- Sonido ----------
    static boolean sounds(Context c) { return b(c, "sounds", true); }
    static void setSounds(Context c, boolean on) { put(c, "sounds", on); }
    static int volume(Context c) { return i(c, "sound_volume", 70); }
    static void setVolume(Context c, int v) { put(c, "sound_volume", v); }
    static boolean haptics(Context c) { return b(c, "haptics", true); }
    static void setHaptics(Context c, boolean on) { put(c, "haptics", on); }

    // ---------- Fondo ----------
    /** 0 escena animada, 1 foto propia. */
    static int wallKind(Context c) { return i(c, "wall_kind", 0); }
    static void setWallKind(Context c, int v) { put(c, "wall_kind", v); }
    static int wallScene(Context c) { return i(c, "wall_scene", i(c, "theme", 0)); }
    static void setWallScene(Context c, int v) { put(c, "wall_scene", v); }
    static String wallPhoto(Context c) { return s(c, "wall_photo", null); }
    static void setWallPhoto(Context c, String id) { put(c, "wall_photo", id); }
    /** 0 original, 1 puntos, 2 duotono, 3 desenfoque. */
    static int wallEffect(Context c) { return i(c, "wall_effect", 0); }
    static void setWallEffect(Context c, int v) { put(c, "wall_effect", v); }
    /** 0 nada, 1 lluvia, 2 nieve, 3 polvo de luz, 4 estrellas. */
    static int wallOverlay(Context c) { return i(c, "wall_overlay", 0); }
    static void setWallOverlay(Context c, int v) { put(c, "wall_overlay", v); }
    static int wallDim(Context c) { return i(c, "wall_dim", 0); }
    static void setWallDim(Context c, int v) { put(c, "wall_dim", v); }
    /** Usar el fondo de Jostin OS también en el sistema (recientes, transiciones). */
    static boolean syncSystem(Context c) { return b(c, "sync_system", true); }
    static void setSyncSystem(Context c, boolean on) { put(c, "sync_system", on); }
    static boolean syncLock(Context c) { return b(c, "sync_lock", true); }
    static void setSyncLock(Context c, boolean on) { put(c, "sync_lock", on); }
    static String syncedKey(Context c) { return s(c, "synced_key", ""); }
    static void setSyncedKey(Context c, String k) { put(c, "synced_key", k); }

    static boolean animated(Context c) { return b(c, "anim", true); }
    static void setAnimated(Context c, boolean on) { put(c, "anim", on); }
    static boolean parallax(Context c) { return b(c, "parallax", true); }
    static void setParallax(Context c, boolean on) { put(c, "parallax", on); }
    static boolean smooth(Context c) { return b(c, "fps60", false); }
    static void setSmooth(Context c, boolean on) { put(c, "fps60", on); }
    static boolean autoEco(Context c) { return b(c, "auto_eco", true); }
    static void setAutoEco(Context c, boolean on) { put(c, "auto_eco", on); }

    // ---------- Color ----------
    /** 0 del fondo, 1 monocromo, 2 elegido. */
    static int accentMode(Context c) { return i(c, "accent_mode", 0); }
    static void setAccentMode(Context c, int v) { put(c, "accent_mode", v); }
    static int accentColor(Context c) { return i(c, "accent_color", 0xFF7FD4FF); }
    static void setAccentColor(Context c, int v) { put(c, "accent_color", v); }
    static boolean tintFromWall(Context c) { return b(c, "tint_wall", true); }
    static void setTintFromWall(Context c, boolean on) { put(c, "tint_wall", on); }
    static int glassOpacity(Context c) { return i(c, "glass_opacity", 55); }
    static void setGlassOpacity(Context c, int v) { put(c, "glass_opacity", v); }

    // ---------- Reloj ----------
    /** 0 espaciado, 1 digital, 2 apilado. */
    static int clockStyle(Context c) { return i(c, "clock_style", 0); }
    static void setClockStyle(Context c, int v) { put(c, "clock_style", v); }
    /** 0 centro, 1 izquierda. */
    static int clockAlign(Context c) { return i(c, "clock_align", 0); }
    static void setClockAlign(Context c, int v) { put(c, "clock_align", v); }
    static int clockFont(Context c) { return i(c, "clock_font", 0); }
    static void setClockFont(Context c, int v) { put(c, "clock_font", v); }
    /** 0 automático, 1 color de acento. */
    static int clockColor(Context c) { return i(c, "clock_color", 0); }
    static void setClockColor(Context c, int v) { put(c, "clock_color", v); }
    static String greeting(Context c) { return s(c, "greeting", ""); }
    static void setGreeting(Context c, String v) { put(c, "greeting", v); }

    // ---------- Inicio ----------
    static boolean showPanels(Context c) { return b(c, "show_panels", true); }
    static void setShowPanels(Context c, boolean on) { put(c, "show_panels", on); }
    static boolean showMusic(Context c) { return b(c, "show_music", true); }
    static void setShowMusic(Context c, boolean on) { put(c, "show_music", on); }
    static boolean showWeather(Context c) { return b(c, "show_weather", true); }
    static void setShowWeather(Context c, boolean on) { put(c, "show_weather", on); }
    static boolean showDock(Context c) { return b(c, "show_dock", true); }
    static void setShowDock(Context c, boolean on) { put(c, "show_dock", on); }
    static boolean onboarded(Context c) { return b(c, "onboarded_v2", false); }
    static void setOnboarded(Context c) { put(c, "onboarded_v2", true); }

    // ---------- Gestos ----------
    /** Deslizar arriba: 0 cajón, 1 buscar, 2 nada. */
    static int gUp(Context c) { return i(c, "g_up", 0); }
    static void setGUp(Context c, int v) { put(c, "g_up", v); }
    /** Deslizar abajo: 2 nada; si no, la barra de Android. */
    static int gDown(Context c) { return i(c, "g_down", 0); }
    static void setGDown(Context c, int v) { put(c, "g_down", v); }
    /** Doble toque: 0 bloquear, 1 linterna, 2 siguiente fondo, 3 nada. */
    static int gDouble(Context c) { return i(c, "g_double", 0); }
    static void setGDouble(Context c, int v) { put(c, "g_double", v); }
    /** Mantener presionado: 0 personalizar, 1 fondos, 2 nada. */
    // Antes el 3 era "Nada"; sin el engrane del panel te dejaría sin forma de llegar a Personalizar
    static int gLong(Context c) { int v = i(c, "g_long", 0); return v > 2 ? 0 : v; }
    static void setGLong(Context c, int v) { put(c, "g_long", v); }

    // ---------- Clima ----------
    static String city(Context c) { return s(c, "w_city", ""); }
    static void setCity(Context c, String v) {
        sp(c).edit().putString("w_city", v).remove("w_lat").remove("w_lon").remove("w_cache").putLong("w_time", 0).apply();
    }
    static String weatherCache(Context c) { return s(c, "w_cache", null); }
    static long weatherTime(Context c) { return sp(c).getLong("w_time", 0); }
    static void setWeather(Context c, String json) {
        sp(c).edit().putString("w_cache", json).putLong("w_time", System.currentTimeMillis()).apply();
    }
    static float lat(Context c) { return sp(c).getFloat("w_lat", Float.NaN); }
    static float lon(Context c) { return sp(c).getFloat("w_lon", Float.NaN); }
    static void setCoords(Context c, float la, float lo, String name) {
        sp(c).edit().putFloat("w_lat", la).putFloat("w_lon", lo).putString("w_place", name).apply();
    }
    static String place(Context c) { return s(c, "w_place", city(c)); }

    // ---------- Calculadora ----------
    static String history(Context c) { return s(c, "calc_history", "[]"); }
    static void setHistory(Context c, String json) { put(c, "calc_history", json); }
    static boolean degrees(Context c) { return b(c, "calc_deg", true); }
    static void setDegrees(Context c, boolean on) { put(c, "calc_deg", on); }

    // ---------- Home Studio ----------
    static boolean homeFree(Context c) { return b(c, "home_free", false); }
    static void setHomeFree(Context c, boolean on) { put(c, "home_free", on); }
    static boolean homeLocked(Context c) { return b(c, "home_locked", false); }
    static void setHomeLocked(Context c, boolean on) { put(c, "home_locked", on); }
    /** 0 deslizar, 1 desvanecer, 2 zoom, 3 cubo, 4 voltear, 5 pila, 6 profundidad, 7 paralaje. */
    static int pageAnim(Context c) { return i(c, "page_anim", 0); }
    static void setPageAnim(Context c, int v) { put(c, "page_anim", v); }
    static boolean homeLabels(Context c) { return b(c, "home_labels", true); }
    static void setHomeLabels(Context c, boolean on) { put(c, "home_labels", on); }
    static boolean badges(Context c) { return b(c, "badges", true); }
    static void setBadges(Context c, boolean on) { put(c, "badges", on); }
    static String userName(Context c) { return s(c, "user_name", "Jostin"); }
    static void setUserName(Context c, String v) { put(c, "user_name", v); }

    // ---------- Íconos ----------
    static int iconShape(Context c) { return i(c, "icon_shape", 0); }
    static void setIconShape(Context c, int v) { put(c, "icon_shape", v); }
    static int iconScale(Context c) { return i(c, "icon_scale", 100); }
    static void setIconScale(Context c, int v) { put(c, "icon_scale", v); }
    static boolean iconGlow(Context c) { return b(c, "icon_glow", false); }
    static void setIconGlow(Context c, boolean on) { put(c, "icon_glow", on); }
    static String iconPack(Context c) { return s(c, "icon_pack", null); }
    static void setIconPack(Context c, String pkg) {
        if (pkg == null) sp(c).edit().remove("icon_pack").apply();
        else put(c, "icon_pack", pkg);
    }

    // ---------- Dock ----------
    static int dockIconSize(Context c) { return i(c, "dock_icon", 44); }
    static void setDockIconSize(Context c, int v) { put(c, "dock_icon", v); }
    static int dockSpacing(Context c) { return i(c, "dock_space", 3); }
    static void setDockSpacing(Context c, int v) { put(c, "dock_space", v); }
    static boolean dockGlass(Context c) { return b(c, "dock_glass", true); }
    static void setDockGlass(Context c, boolean on) { put(c, "dock_glass", on); }
    static boolean dockSuggest(Context c) { return b(c, "dock_suggest", false); }
    static void setDockSuggest(Context c, boolean on) { put(c, "dock_suggest", on); }
    static boolean drawerSuggest(Context c) { return b(c, "drawer_suggest", true); }
    static void setDrawerSuggest(Context c, boolean on) { put(c, "drawer_suggest", on); }

    // ---------- Notificaciones ----------

    // ---------- Android ----------
    static boolean androidColors(Context c) { return b(c, "android_colors", false); }
    static void setAndroidColors(Context c, boolean on) { put(c, "android_colors", on); }
    static int androidStyle(Context c) { return i(c, "android_style", 0); }
    static void setAndroidStyle(Context c, int v) { put(c, "android_style", v); }

    // ---------- Animaciones ----------
    /** 0 normal, 1 rápidas, 2 reducidas (casi sin movimiento), 3 apagadas. */
    static int motion(Context c) { return i(c, "motion", 0); }
    static void setMotion(Context c, int v) { put(c, "motion", v); }
    /** Cómo se abren las apps: 0 desde el ícono, 1 revelar, 2 suave, 3 la del sistema. */
    static int appAnim(Context c) { return i(c, "app_anim", 0); }
    static void setAppAnim(Context c, int v) { put(c, "app_anim", v); }

    // ---------- Uso de apps (para sugerencias; se queda en el teléfono) ----------
    static java.util.Map<String, Integer> useCounts(Context c) {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        try {
            org.json.JSONObject o = new org.json.JSONObject(s(c, "use_counts", "{}"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, o.optInt(k));
            }
        } catch (Exception ignored) {
            // vacío
        }
        return m;
    }

    static void countLaunch(Context c, String pkg) {
        java.util.Map<String, Integer> m = useCounts(c);
        m.put(pkg, (m.containsKey(pkg) ? m.get(pkg) : 0) + 1);
        put(c, "use_counts", new org.json.JSONObject(m).toString());
    }
}
