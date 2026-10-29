package com.jostin.launcher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Fondos animados dibujados con código. Cada uno se adapta al tamaño de la pantalla. */
final class Scenes {
    static final String[] NAMES = {"Olas", "Niebla", "Noche", "Panal", "Rojo"};
    static final int COUNT = NAMES.length;

    private Scenes() {}

    static Scene create(int i) {
        switch (i) {
            case 1: return new Fog();
            case 2: return new Waves(false);
            case 3: return new Hex();
            case 4: return new Red();
            default: return new Waves(true);
        }
    }

    static int lerp(int a, int b, float f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * f), g = (int) (ag + (bg - ag) * f), bl = (int) (ab + (bb - ab) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    abstract static class Scene {
        int w, h;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        /** true si el fondo es claro (reloj oscuro). */
        abstract boolean light();

        void size(int w, int h) {
            this.w = w;
            this.h = h;
            prepare();
        }

        void prepare() {}

        abstract void draw(Canvas c, float t);

        int clockColor() {
            return light() ? 0xFF1A1A1A : 0xFFF0F0F0;
        }
    }

    /** Olas suaves en capas, claras o nocturnas. */
    static final class Waves extends Scene {
        private final boolean day;
        private final Path path = new Path();
        private final Paint crest = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient sky;
        private LinearGradient[] fills;
        private static final int LAYERS = 6;

        Waves(boolean day) {
            this.day = day;
            crest.setStyle(Paint.Style.STROKE);
        }

        @Override boolean light() { return day; }

        @Override
        void prepare() {
            int top = day ? 0xFFCFCFCF : 0xFF050505;
            int bottom = day ? 0xFF8A8A8A : 0xFF1C1C1E;
            sky = new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP);
            fills = new LinearGradient[LAYERS];
            for (int i = 0; i < LAYERS; i++) {
                float f = i / (float) (LAYERS - 1);
                int a = day ? lerp(0xFFBDBDBD, 0xFF1E1E1E, f) : lerp(0xFF2E2E30, 0xFF050505, f);
                int b = day ? lerp(0xFF8E8E8E, 0xFF050505, f) : lerp(0xFF141415, 0xFF000000, f);
                float y0 = h * (0.42f + 0.09f * i);
                fills[i] = new LinearGradient(0, y0 - h * 0.08f, 0, h, a, b, Shader.TileMode.CLAMP);
            }
            crest.setStrokeWidth(Math.max(1f, w / 600f));
        }

        @Override
        void draw(Canvas c, float t) {
            paint.setShader(sky);
            c.drawRect(0, 0, w, h, paint);
            int steps = 48;
            for (int i = 0; i < LAYERS; i++) {
                float base = h * (0.42f + 0.09f * i);
                float amp1 = h * (0.045f - i * 0.003f);
                float amp2 = h * 0.018f;
                float k1 = (float) (Math.PI * 2 / (w * (1.6f - i * 0.12f)));
                float k2 = (float) (Math.PI * 2 / (w * 0.7f));
                float s1 = 0.18f + i * 0.04f, s2 = 0.11f + i * 0.03f;
                path.reset();
                path.moveTo(0, h);
                for (int s = 0; s <= steps; s++) {
                    float x = w * s / (float) steps;
                    float y = base
                            + amp1 * (float) Math.sin(k1 * x + t * s1 + i * 1.9f)
                            + amp2 * (float) Math.sin(k2 * x - t * s2 + i);
                    path.lineTo(x, y);
                }
                path.lineTo(w, h);
                path.close();
                paint.setShader(fills[i]);
                c.drawPath(path, paint);
                crest.setColor(day ? 0x30FFFFFF : 0x1FFFFFFF);
                c.drawPath(path, crest);
            }
            paint.setShader(null);
        }
    }

    /** Montañas en capas con neblina que se mueve. */
    static final class Fog extends Scene {
        private final List<Path> ridges = new ArrayList<>();
        private LinearGradient sky;
        private final int[] ridgeColors = {0xFFAFAFAF, 0xFF8C8C8C, 0xFF5E5E5E, 0xFF2E2E2E};

        @Override boolean light() { return true; }

        @Override
        void prepare() {
            sky = new LinearGradient(0, 0, 0, h, 0xFFDADADA, 0xFFA2A2A2, Shader.TileMode.CLAMP);
            ridges.clear();
            Random r = new Random(7);
            for (int l = 0; l < ridgeColors.length; l++) {
                int n = 64;
                float[] ys = new float[n + 1];
                float base = h * (0.42f + l * 0.12f);
                float peak = h * (0.16f - l * 0.025f);
                // Ruido suave sumando senos con fases aleatorias
                float p1 = r.nextFloat() * 6, p2 = r.nextFloat() * 6, p3 = r.nextFloat() * 6;
                for (int i = 0; i <= n; i++) {
                    float x = i / (float) n;
                    float v = (float) (Math.sin(x * 3.1 + p1) * 0.55 + Math.sin(x * 7.3 + p2) * 0.3
                            + Math.sin(x * 17.0 + p3) * 0.15);
                    ys[i] = base - Math.abs(v) * peak;
                }
                Path p = new Path();
                p.moveTo(0, h);
                for (int i = 0; i <= n; i++) p.lineTo(w * i / (float) n, ys[i]);
                p.lineTo(w, h);
                p.close();
                ridges.add(p);
            }
        }

        @Override
        void draw(Canvas c, float t) {
            paint.setShader(sky);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            for (int l = 0; l < ridges.size(); l++) {
                paint.setColor(ridgeColors[l]);
                c.drawPath(ridges.get(l), paint);
                // Neblina entre capas
                for (int k = 0; k < 3; k++) {
                    float speed = 6f + k * 4f + l * 2f;
                    float span = w * 1.6f;
                    float cx = ((t * speed * w / 400f + k * span / 3f + l * 97) % span) - w * 0.3f;
                    float cy = h * (0.40f + l * 0.12f);
                    float rx = w * 0.55f;
                    paint.setShader(new RadialGradient(cx, cy, rx,
                            new int[]{0x55E6E6E6, 0x00E6E6E6}, null, Shader.TileMode.CLAMP));
                    c.save();
                    c.scale(1f, 0.35f, cx, cy);
                    c.drawCircle(cx, cy, rx, paint);
                    c.restore();
                    paint.setShader(null);
                }
            }
        }
    }

    /** Panal de hexágonos oscuros que se iluminan despacio. */
    static final class Hex extends Scene {
        private final Path grid = new Path();
        private final List<Path> cells = new ArrayList<>();
        private final List<Float> phases = new ArrayList<>();
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient shade;

        Hex() {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(0xFF2C2C2E);
        }

        @Override boolean light() { return false; }

        @Override
        void prepare() {
            grid.reset();
            cells.clear();
            phases.clear();
            float r = w / 7.5f;
            float hw = (float) (Math.sqrt(3) * r);
            Random rnd = new Random(3);
            int row = 0;
            for (float y = -r; y < h + r; y += r * 1.5f, row++) {
                float off = (row % 2) * hw / 2f;
                for (float x = -hw; x < w + hw; x += hw) {
                    Path p = hexPath(x + off, y, r * 0.94f);
                    grid.addPath(p);
                    if (rnd.nextFloat() < 0.18f) {
                        cells.add(p);
                        phases.add(rnd.nextFloat() * 6.28f);
                    }
                }
            }
            stroke.setStrokeWidth(Math.max(1.5f, w / 360f));
            shade = new LinearGradient(0, 0, 0, h, 0x00000000, 0xCC000000, Shader.TileMode.CLAMP);
        }

        private static Path hexPath(float cx, float cy, float r) {
            Path p = new Path();
            for (int i = 0; i < 6; i++) {
                double a = Math.PI / 6 + i * Math.PI / 3;
                float x = cx + (float) (r * Math.cos(a)), y = cy + (float) (r * Math.sin(a));
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            p.close();
            return p;
        }

        @Override
        void draw(Canvas c, float t) {
            c.drawColor(0xFF111113);
            for (int i = 0; i < cells.size(); i++) {
                float v = 0.5f + 0.5f * (float) Math.sin(t * 0.6f + phases.get(i));
                paint.setColor(lerp(0xFF161618, 0xFF3A3A3D, v));
                c.drawPath(cells.get(i), paint);
            }
            c.drawPath(grid, stroke);
            paint.setShader(shade);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    /** Franjas rojas inclinadas que se deslizan. */
    static final class Red extends Scene {
        private final Path stripe = new Path();
        private LinearGradient red, shade;

        @Override boolean light() { return false; }

        @Override
        void prepare() {
            red = new LinearGradient(0, h * 0.1f, 0, h * 0.8f, 0xFFE3141C, 0xFF3A0004, Shader.TileMode.CLAMP);
            shade = new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
        }

        @Override
        void draw(Canvas c, float t) {
            c.drawColor(0xFF070707);
            float sw = w / 3.2f;
            float slant = h * 0.18f;
            float shift = (t * w / 60f) % sw;
            paint.setShader(red);
            for (float x = -sw * 2 + shift; x < w + sw; x += sw) {
                stripe.reset();
                stripe.moveTo(x + slant, 0);
                stripe.lineTo(x + sw - 4 + slant, 0);
                stripe.lineTo(x + sw - 4, h);
                stripe.lineTo(x, h);
                stripe.close();
                c.drawPath(stripe, paint);
            }
            paint.setShader(shade);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }
}
