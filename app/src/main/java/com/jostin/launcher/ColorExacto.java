package com.jostin.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

/**
 * Colores exactos de Material You con FabricatedOverlay (cmd overlay fabricate, con Shizuku).
 * Cada color es un overlay «com.android.shell:jostin_…» con prioridad más alta que los de Android;
 * regresar es apagarlos todos, y Android queda con sus colores de siempre.
 * Código propio: no viene de ColorBlendr ni de otro proyecto GPL.
 */
final class ColorExacto {
    private ColorExacto() {}

    private static final String ON = "fab_on", SEED = "fab_seed";
    private static final String PREFIX = "jostin_";
    /** Sombras de Material (system_accentN_XXX) y el tono que les toca (100 = blanco, 0 = negro). */
    static final int[] SHADES = {0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
    static final int[] TONES = {100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0};
    static final int[] PRESETS = {0xFF7FD4FF, 0xFF4F8CFF, 0xFF8E7CFF, 0xFFE46CFF, 0xFFFF6B8B, 0xFFFF8A3D, 0xFFFFC53D, 0xFF5BD68A, 0xFF2EC4B6, 0xFFB0B7C3};

    private static SharedPreferences sp(Context c) {
        return Prefs.sp(c);
    }

    static boolean on(Context c) {
        return sp(c).getBoolean(ON, false);
    }

    static int seed(Context c) {
        return sp(c).getInt(SEED, Prefs.accentColor(c));
    }

    static void setSeed(Context c, int color) {
        sp(c).edit().putInt(SEED, color).apply();
    }

    // ---------- Paleta (CIELAB: mismo tono y tinte, cambia solo la luz) ----------

    private static double lin(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double gam(double v) {
        return v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
    }

    private static double f(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    private static double fi(double t) {
        double t3 = t * t * t;
        return t3 > 216.0 / 24389 ? t3 : (116 * t - 16) / (24389.0 / 27);
    }

    /** color → {L, C, h°} */
    static double[] lch(int color) {
        double r = lin(((color >> 16) & 255) / 255.0), g = lin(((color >> 8) & 255) / 255.0), b = lin((color & 255) / 255.0);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        double L = 116 * fy - 16, A = 500 * (fx - fy), B = 200 * (fy - fz);
        double h = Math.toDegrees(Math.atan2(B, A));
        return new double[]{L, Math.hypot(A, B), h < 0 ? h + 360 : h};
    }

    /** {L, C, h} → color, o 0 si se sale de lo que la pantalla puede mostrar. */
    private static int fromLch(double L, double C, double h, boolean clip) {
        double A = C * Math.cos(Math.toRadians(h)), B = C * Math.sin(Math.toRadians(h));
        double fy = (L + 16) / 116, fx = fy + A / 500, fz = fy - B / 200;
        double x = fi(fx) * 0.95047, y = L > 8 ? fy * fy * fy : L / (24389.0 / 27), z = fi(fz) * 1.08883;
        double r = gam(3.2406 * x - 1.5372 * y - 0.4986 * z);
        double g = gam(-0.9689 * x + 1.8758 * y + 0.0415 * z);
        double b = gam(0.0557 * x - 0.2040 * y + 1.0570 * z);
        double e = 0.0005;
        if (!clip && (r < -e || r > 1 + e || g < -e || g > 1 + e || b < -e || b > 1 + e)) return 0;
        int R = (int) Math.round(Math.max(0, Math.min(1, r)) * 255);
        int G = (int) Math.round(Math.max(0, Math.min(1, g)) * 255);
        int Bl = (int) Math.round(Math.max(0, Math.min(1, b)) * 255);
        return 0xFF000000 | (R << 16) | (G << 8) | Bl;
    }

    /** El color con esa luz (0–100), bajando el tinte hasta que quepa en pantalla. */
    static int tone(double h, double chroma, int tone) {
        if (tone >= 100) return 0xFFFFFFFF;
        if (tone <= 0) return 0xFF000000;
        for (double c = chroma; c > 0; c -= 1) {
            int v = fromLch(tone, c, h, false);
            if (v != 0) return v;
        }
        return fromLch(tone, 0, h, true);
    }

    /** Las 13 sombras de una paleta; la 2 es el mismo tono con menos tinte (para fondos y chips). */
    static int[] palette(int seed, int which) {
        double[] l = lch(seed);
        double c = Math.max(28, Math.min(72, l[1]));
        if (which == 2) c = Math.max(8, c / 3);
        int[] out = new int[SHADES.length];
        for (int i = 0; i < SHADES.length; i++) out[i] = tone(l[2], c, TONES[i]);
        return out;
    }

    private static int at(int[] p, int shade) {
        for (int i = 0; i < SHADES.length; i++) if (SHADES[i] == shade) return p[i];
        return p[7];
    }

    // ---------- Overlay ----------

    private static final String NAME = "jostin_colors";
    static final String ID = "com.android.shell:" + NAME;

    private static void put(StringBuilder b, String res, int color) {
        if (b.length() > 0) b.append(';');
        b.append(res).append('=').append(String.format(Locale.US, "%08X", color));
    }

    /** Un solo overlay con todos los colores: las dos paletas y los de Material de Android 14+. */
    private static String spec(int seed) {
        int[] a1 = palette(seed, 1), a2 = palette(seed, 2);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < SHADES.length; i++) {
            put(b, "system_accent1_" + SHADES[i], a1[i]);
            put(b, "system_accent2_" + SHADES[i], a2[i]);
        }
        put(b, "system_primary_light", at(a1, 600));
        put(b, "system_on_primary_light", at(a1, 0));
        put(b, "system_primary_container_light", at(a1, 100));
        put(b, "system_on_primary_container_light", at(a1, 900));
        put(b, "system_primary_dark", at(a1, 200));
        put(b, "system_on_primary_dark", at(a1, 800));
        put(b, "system_primary_container_dark", at(a1, 700));
        put(b, "system_on_primary_container_dark", at(a1, 100));
        return b.toString();
    }

    /** Aplica la paleta. Regresa null si salió bien, o el motivo. En hilo de fondo. */
    static String apply(Context c, int seed) {
        String sp = spec(seed);
        String r = Shizu.call(c, s -> s.fabricate(NAME, sp));
        if (r == null) return "Shizuku no respondió";
        if (!r.startsWith("ok")) {
            Bitacora.error("Colores exactos", new RuntimeException(r));
            return "Android no aceptó los colores (" + r + ")";
        }
        // Encima de los colores que pone Android
        Shizu.runSync(c, "cmd overlay set-priority " + ID + " highest");
        sp(c).edit().putBoolean(ON, true).putInt(SEED, seed).apply();
        return null;
    }

    /** Regresa los colores originales: quita el overlay de Jostin. En hilo de fondo. */
    static String restore(Context c) {
        String r = Shizu.call(c, s -> s.unfabricate(NAME));
        if (r == null) return "Shizuku no respondió";
        if (!r.startsWith("ok")) {
            // Si no se pudo quitar, al menos se apaga
            Shizu.Res d = Shizu.runSync(c, "cmd overlay disable --user 0 " + ID);
            if (d == null || !d.ok()) return "No pude regresarlos (" + r + ")";
        }
        sp(c).edit().putBoolean(ON, false).apply();
        return null;
    }

    /** Cuando Shizuku conecta (p. ej. después de reiniciar): si los colores se perdieron, se vuelven a poner. */
    static void reapplyIfNeeded(Context c) {
        if (!on(c)) return;
        Shizu.Res r = Shizu.runSync(c, "cmd overlay list android | grep -cF '[x] " + ID + "'");
        if (r == null) return;
        if (r.out.trim().startsWith("0")) apply(c, seed(c));
    }

    /** Vista previa: las sombras y cómo se verían un botón y una tarjeta en claro y oscuro. */
    static final class Preview extends View {
        private int seed;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Preview(Context c, int seed) {
            super(c);
            this.seed = seed;
        }

        void setSeed(int s) {
            seed = s;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(getContext(), 150));
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), d = Ui.dp(getContext(), 1), rad = 12 * d;
            int[] a1 = palette(seed, 1), a2 = palette(seed, 2);
            float sw = w / SHADES.length;
            for (int i = 0; i < SHADES.length; i++) {
                p.setColor(a1[i]);
                cv.drawRect(i * sw, 0, (i + 1) * sw, 18 * d, p);
                p.setColor(a2[i]);
                cv.drawRect(i * sw, 18 * d, (i + 1) * sw, 30 * d, p);
            }
            float top = 42 * d, half = (w - 10 * d) / 2, hgt = 104 * d;
            card(cv, 0, top, half, hgt, rad, d, at(a2, 50), at(a1, 600), at(a1, 0), at(a1, 100), at(a1, 900), 0xFF1B1B1F);
            card(cv, half + 10 * d, top, half, hgt, rad, d, at(a2, 900), at(a1, 200), at(a1, 800), at(a1, 700), at(a1, 100), 0xFFE4E2E6);
        }

        private void card(Canvas cv, float x, float y, float w, float h, float rad, float d,
                          int bg, int primary, int onPrimary, int container, int onContainer, int text) {
            p.setColor(bg);
            r.set(x, y, x + w, y + h);
            cv.drawRoundRect(r, rad, rad, p);
            p.setColor(text);
            p.setTextSize(12 * d);
            cv.drawText("Ajustes", x + 12 * d, y + 22 * d, p);
            p.setColor(container);
            r.set(x + 10 * d, y + 32 * d, x + w - 10 * d, y + 60 * d);
            cv.drawRoundRect(r, 14 * d, 14 * d, p);
            p.setColor(onContainer);
            cv.drawText("Wi-Fi", x + 22 * d, y + 50 * d, p);
            p.setColor(primary);
            r.set(x + 10 * d, y + 68 * d, x + w - 10 * d, y + 94 * d);
            cv.drawRoundRect(r, 13 * d, 13 * d, p);
            p.setColor(onPrimary);
            cv.drawText("Botón", x + 22 * d, y + 85 * d, p);
        }
    }
}
