package com.jostin.launcher;

import android.content.Context;
import android.graphics.Color;

/**
 * Colores vivos de la interfaz. Salen del fondo actual (o de lo que elijas) y todo lo demás
 * los consulta: el vidrio, los selectores, los anillos, la tecla de igual, etc.
 */
final class Theme {
    static final int[] SWATCHES = {0xFFF5F6F8, 0xFF7FD4FF, 0xFF8C9EFF, 0xFFB48CFF, 0xFFFF7EB6,
            0xFFFF5A5F, 0xFFFFA45B, 0xFFFFD66B, 0xFF7CE0A3, 0xFF4DF2C3};

    /** Color para resaltar (selección, anillos, botón principal). */
    static int accent = 0xFFF5F6F8;
    /** Texto encima del color de acento. */
    static int onAccent = Ui.INK;
    /** Tinte del vidrio normal y del vidrio fuerte (hojas, menús). */
    static int glass = 0x8C0F1114;
    static int glassStrong = 0xB80D0F12;

    // Lo que aporta el fondo actual
    private static int wallAccent = 0xFFF5F6F8;
    private static int wallTint = 0xFF0F1114;
    static boolean wallLight = false;

    private Theme() {}

    static void setWallpaperColors(int accent, int tint, boolean light) {
        wallAccent = accent;
        wallTint = tint;
        wallLight = light;
    }

    static void recompute(Context c) {
        int mode = Prefs.accentMode(c);
        int base = mode == 0 ? wallAccent : mode == 1 ? 0xFFF5F6F8 : Prefs.accentColor(c);
        accent = readable(base);
        onAccent = luminance(accent) > 0.45 ? Ui.INK : 0xFFFFFFFF;

        int rgb = 0x0F1114;
        if (Prefs.tintFromWall(c)) {
            float[] hsl = new float[3];
            Palette.toHsl(Color.red(wallTint), Color.green(wallTint), Color.blue(wallTint), hsl);
            rgb = Palette.fromHsl(hsl[0], Math.min(hsl[1], 0.45f), 0.085f) & 0xFFFFFF;
        }
        int a = Math.round(255 * Prefs.glassOpacity(c) / 100f);
        glass = (Math.max(40, Math.min(235, a)) << 24) | rgb;
        glassStrong = (Math.max(150, Math.min(245, a + 50)) << 24) | rgb;
    }

    /** Aclara el color lo necesario para que se lea bien sobre vidrio oscuro. */
    static int readable(int color) {
        float[] hsl = new float[3];
        Palette.toHsl(Color.red(color), Color.green(color), Color.blue(color), hsl);
        if (hsl[1] < 0.12f) return 0xFFF5F6F8;
        float l = Math.max(hsl[2], 0.64f);
        float s = Math.min(1f, hsl[1] * 1.1f + 0.05f);
        return Palette.fromHsl(hsl[0], s, Math.min(l, 0.8f));
    }

    static double luminance(int c) {
        return (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0;
    }

    /** Color del reloj según el fondo y lo que elegiste. */
    static int clock(Context c) {
        if (Prefs.clockColor(c) == 1) return accent;
        return wallLight ? 0xFF16171A : 0xFFF2F3F5;
    }
}
