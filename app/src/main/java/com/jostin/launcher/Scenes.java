package com.jostin.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fondos dibujados con código. Cada escena prepara sus pinceles una sola vez y en cada cuadro
 * solo mueve matrices, para no crear objetos 30 veces por segundo (eso gasta batería).
 */
final class Scenes {
    static final String[] NAMES = {"Olas", "Niebla", "Noche", "Panal", "Rojo", "Aurora", "Fluido", "Constelación", "Plano"};
    static final int COUNT = NAMES.length;

    private Scenes() {}

    static Scene create(int i) {
        switch (i) {
            case 1: return new Fog();
            case 2: return new Waves(false);
            case 3: return new Hex();
            case 4: return new Red();
            case 5: return new Aurora();
            case 6: return new Fluid();
            case 7: return new Constellation();
            case 8: return new Blueprint();
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
        final Matrix mx = new Matrix();

        /** true si el fondo es claro arriba (reloj oscuro). */
        abstract boolean light();

        /** Color de acento que le queda a esta escena. */
        int accent() { return 0xFFF5F6F8; }

        /** Color para teñir el vidrio. */
        int tint() { return 0xFF0F1114; }

        /** Si se mueve con el tiempo. Si no, el fondo no se redibuja y no gasta. */
        boolean animated() { return true; }

        /** Toques del dedo (solo algunas escenas reaccionan). */
        void touch(float x, float y, boolean down) {}

        void size(int w, int h) {
            this.w = w;
            this.h = h;
            prepare();
        }

        void prepare() {}

        abstract void draw(Canvas c, float t);
    }

    // ==================== Olas ====================

    static final class Waves extends Scene {
        private final boolean day;
        private final Path path = new Path();
        private final Paint crest = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient sky;
        private final LinearGradient[] fills = new LinearGradient[LAYERS];
        private final LinearGradient[] rims = new LinearGradient[LAYERS];
        private static final int LAYERS = 6;

        Waves(boolean day) {
            this.day = day;
            crest.setStyle(Paint.Style.STROKE);
        }

        @Override boolean light() { return day; }

        @Override
        void prepare() {
            sky = new LinearGradient(0, 0, 0, h, day ? 0xFFD4D4D4 : 0xFF0A0A0B, day ? 0xFF9A9A9A : 0xFF1E1E21, Shader.TileMode.CLAMP);
            for (int i = 0; i < LAYERS; i++) {
                float f = i / (float) (LAYERS - 1);
                int a = day ? lerp(0xFFC4C4C4, 0xFF262626, f) : lerp(0xFF3C3C40, 0xFF0B0B0C, f);
                int b = day ? lerp(0xFF8A8A8A, 0xFF050505, f) : lerp(0xFF121214, 0xFF000000, f);
                float y0 = h * (0.42f + 0.09f * i);
                fills[i] = new LinearGradient(0, y0 - h * 0.06f, 0, y0 + h * 0.35f, a, b, Shader.TileMode.CLAMP);
                int peak = Math.min(0xA0, day ? (0x30 + i * 0x0C) : (0x22 + i * 0x08));
                rims[i] = new LinearGradient(-w * 0.7f, 0, w * 0.7f, 0,
                        new int[]{0x00FFFFFF, (peak << 24) | 0xFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            }
            crest.setStrokeWidth(Math.max(1f, w / 380f));
        }

        @Override
        void draw(Canvas c, float t) {
            paint.setShader(sky);
            c.drawRect(0, 0, w, h, paint);
            int steps = 48;
            for (int i = 0; i < LAYERS; i++) {
                float base = h * (0.42f + 0.09f * i);
                float amp1 = h * (0.05f - i * 0.003f), amp2 = h * 0.02f;
                float k1 = (float) (Math.PI * 2 / (w * (1.6f - i * 0.12f)));
                float k2 = (float) (Math.PI * 2 / (w * 0.7f));
                float s1 = 0.18f + i * 0.04f, s2 = 0.11f + i * 0.03f;
                path.reset();
                path.moveTo(0, h);
                for (int s = 0; s <= steps; s++) {
                    float x = w * s / (float) steps;
                    path.lineTo(x, base + amp1 * (float) Math.sin(k1 * x + t * s1 + i * 1.9f)
                            + amp2 * (float) Math.sin(k2 * x - t * s2 + i));
                }
                path.lineTo(w, h);
                path.close();
                paint.setShader(fills[i]);
                c.drawPath(path, paint);
                // Luz que recorre la cresta
                mx.setTranslate((float) ((Math.sin(t * 0.12f + i * 1.3f) * 0.5 + 0.5) * w), 0);
                rims[i].setLocalMatrix(mx);
                crest.setShader(rims[i]);
                c.drawPath(path, crest);
            }
            paint.setShader(null);
        }
    }

    // ==================== Niebla ====================

    static final class Fog extends Scene {
        private final List<Path> ridges = new ArrayList<>();
        private LinearGradient sky;
        private Bitmap blob;
        private final Paint blobPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.RectF dst = new android.graphics.RectF();
        private final int[] ridgeColors = {0xFFAFAFAF, 0xFF8C8C8C, 0xFF5E5E5E, 0xFF2E2E2E};

        @Override boolean light() { return true; }

        @Override
        void prepare() {
            sky = new LinearGradient(0, 0, 0, h, 0xFFDADADA, 0xFFA2A2A2, Shader.TileMode.CLAMP);
            ridges.clear();
            Random r = new Random(7);
            for (int l = 0; l < ridgeColors.length; l++) {
                int n = 64;
                float base = h * (0.42f + l * 0.12f), peak = h * (0.16f - l * 0.025f);
                float p1 = r.nextFloat() * 6, p2 = r.nextFloat() * 6, p3 = r.nextFloat() * 6;
                Path p = new Path();
                p.moveTo(0, h);
                for (int i = 0; i <= n; i++) {
                    float x = i / (float) n;
                    float v = (float) (Math.sin(x * 3.1 + p1) * 0.55 + Math.sin(x * 7.3 + p2) * 0.3 + Math.sin(x * 17.0 + p3) * 0.15);
                    p.lineTo(w * x, base - Math.abs(v) * peak);
                }
                p.lineTo(w, h);
                p.close();
                ridges.add(p);
            }
            // Una mancha de neblina pre-dibujada que se reutiliza en cada cuadro
            int bw = 128, bh = 48;
            blob = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas bc = new Canvas(blob);
            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            bp.setShader(new RadialGradient(bw / 2f, bh / 2f, bw / 2f, 0x66EEEEEE, 0x00EEEEEE, Shader.TileMode.CLAMP));
            bc.save();
            bc.scale(1f, bh / (float) bw, bw / 2f, bh / 2f);
            bc.drawCircle(bw / 2f, bh / 2f, bw / 2f, bp);
            bc.restore();
        }

        @Override
        void draw(Canvas c, float t) {
            paint.setShader(sky);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            for (int l = 0; l < ridges.size(); l++) {
                paint.setColor(ridgeColors[l]);
                c.drawPath(ridges.get(l), paint);
                for (int k = 0; k < 3; k++) {
                    float speed = 6f + k * 4f + l * 2f, span = w * 1.6f;
                    float cx = ((t * speed * w / 400f + k * span / 3f + l * 97) % span) - w * 0.3f;
                    float cy = h * (0.40f + l * 0.12f), rx = w * 0.55f;
                    dst.set(cx - rx, cy - rx * 0.36f, cx + rx, cy + rx * 0.36f);
                    c.drawBitmap(blob, null, dst, blobPaint);
                }
            }
        }
    }

    // ==================== Panal ====================

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
            float r = w / 7.5f, hw = (float) (Math.sqrt(3) * r);
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

    // ==================== Rojo ====================

    static final class Red extends Scene {
        private final Path slab = new Path();
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient red, red2, shade, sheen;

        Red() {
            edge.setStyle(Paint.Style.STROKE);
        }

        @Override boolean light() { return false; }
        @Override int accent() { return 0xFFFF5A62; }
        @Override int tint() { return 0xFF2A0508; }

        @Override
        void prepare() {
            red = new LinearGradient(0, 0, 0, h * 0.85f, 0xFFF0242C, 0xFF3D0005, Shader.TileMode.CLAMP);
            red2 = new LinearGradient(0, 0, 0, h * 0.85f, 0xFFC8101A, 0xFF2A0003, Shader.TileMode.CLAMP);
            shade = new LinearGradient(0, h * 0.5f, 0, h, 0x00000000, 0xF2000000, Shader.TileMode.CLAMP);
            float slant = h * 0.16f;
            sheen = new LinearGradient(slant, -h * 0.2f, 0, h * 0.2f,
                    new int[]{0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            sheenPaint.setShader(sheen);
            edge.setStrokeWidth(Math.max(1f, w / 420f));
            edge.setShader(new LinearGradient(0, 0, 0, h * 0.7f, 0x99FF7A7A, 0x00FF7A7A, Shader.TileMode.CLAMP));
        }

        @Override
        void draw(Canvas c, float t) {
            c.drawColor(0xFF050505);
            float sw = w / 3.6f, gap = w / 34f, slant = h * 0.16f;
            float drift = (t * w / 90f) % (sw + gap);
            int k = 0;
            for (float x = -(sw + gap) * 2 + drift; x < w + sw; x += sw + gap, k++) {
                slab.reset();
                slab.moveTo(x + slant, 0);
                slab.lineTo(x + sw + slant, 0);
                slab.lineTo(x + sw, h);
                slab.lineTo(x, h);
                slab.close();
                paint.setShader((k & 1) == 0 ? red : red2);
                c.drawPath(slab, paint);
                float gy = (float) ((Math.sin(t * 0.25f + k * 0.9f) * 0.5 + 0.5) * h * 0.6f);
                mx.setTranslate(x, gy);
                sheen.setLocalMatrix(mx);
                c.drawPath(slab, sheenPaint);
                c.drawLine(x + slant, 0, x, h, edge);
            }
            paint.setShader(shade);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    // ==================== Aurora ====================

    static final class Aurora extends Scene {
        private final Path ribbon = new Path();
        private final Path hills = new Path();
        private LinearGradient sky;
        private final Shader[] shaders = new Shader[3];
        private final int[] colors = {0xFF4DF2C3, 0xFF7CE0A3, 0xFF8C6BFF};
        private float[] stars;
        private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint add = new Paint(Paint.ANTI_ALIAS_FLAG);

        Aurora() {
            add.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            star.setColor(0xFFFFFFFF);
        }

        @Override boolean light() { return false; }
        @Override int accent() { return 0xFF4DF2C3; }
        @Override int tint() { return 0xFF04161A; }

        @Override
        void prepare() {
            sky = new LinearGradient(0, 0, 0, h, new int[]{0xFF01030A, 0xFF04101A, 0xFF0A1E26}, null, Shader.TileMode.CLAMP);
            // Estrías verticales (rayos de la aurora): un mosaico de 1 px de alto que se repite
            Random sr = new Random(13);
            int sw = 512;
            int[] sp = new int[sw];
            float p1 = sr.nextFloat() * 6, p2 = sr.nextFloat() * 6, p3 = sr.nextFloat() * 6;
            for (int k = 0; k < sw; k++) {
                double x = k / (double) sw * Math.PI * 2;
                double v = 0.62 + 0.18 * Math.sin(x * 7 + p1) + 0.12 * Math.sin(x * 23 + p2) + 0.08 * Math.sin(x * 61 + p3);
                int a = (int) (255 * Math.max(0.15, Math.min(1.0, v)));
                sp[k] = (a << 24) | 0xFFFFFF;
            }
            Bitmap stripes = Bitmap.createBitmap(sp, sw, 1, Bitmap.Config.ARGB_8888);
            for (int i = 0; i < 3; i++) {
                float base = h * (0.3f + 0.07f * i);
                LinearGradient lg = new LinearGradient(0, base - h * 0.24f, 0, base + h * 0.03f,
                        new int[]{0x00000000, Ui.alpha(colors[i], 0x38), Ui.alpha(colors[i], 0xA6), 0x00000000},
                        new float[]{0f, 0.5f, 0.88f, 1f}, Shader.TileMode.CLAMP);
                android.graphics.BitmapShader bs = new android.graphics.BitmapShader(stripes, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
                Matrix sm = new Matrix();
                sm.setScale(w / (float) sw * (1.1f + i * 0.35f), 1f);
                sm.postTranslate(i * w * 0.21f, 0);
                bs.setLocalMatrix(sm);
                shaders[i] = new android.graphics.ComposeShader(lg, bs, PorterDuff.Mode.DST_IN);
            }
            Random r = new Random(9);
            int n = 110;
            stars = new float[n * 4];
            for (int k = 0; k < n; k++) {
                stars[k * 4] = r.nextFloat() * w;
                stars[k * 4 + 1] = r.nextFloat() * h * 0.7f;
                stars[k * 4 + 2] = (0.4f + r.nextFloat() * 1.2f) * w / 1080f * 2.2f;
                stars[k * 4 + 3] = r.nextFloat() * 6.28f;
            }
            hills.reset();
            hills.moveTo(0, h);
            for (int i = 0; i <= 40; i++) {
                float x = w * i / 40f;
                float y = h * 0.82f - (float) (Math.abs(Math.sin(i * 0.37 + 1.2)) * h * 0.06 + Math.sin(i * 1.7) * h * 0.012);
                hills.lineTo(x, y);
            }
            hills.lineTo(w, h);
            hills.close();
        }

        @Override
        void draw(Canvas c, float t) {
            paint.setShader(sky);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            for (int k = 0; k < stars.length; k += 4) {
                float tw = 0.45f + 0.55f * (float) Math.sin(t * 1.3f + stars[k + 3]);
                star.setAlpha((int) (40 + 180 * tw));
                c.drawCircle(stars[k], stars[k + 1], stars[k + 2], star);
            }
            for (int i = 0; i < 3; i++) {
                float base = h * (0.3f + 0.07f * i);
                ribbon.reset();
                int steps = 48;
                for (int s = 0; s <= steps; s++) {
                    float x = w * s / (float) steps;
                    float y = base + h * 0.035f * (float) Math.sin(x / w * 5.2f + t * (0.16f + i * 0.05f) + i * 2)
                            + h * 0.015f * (float) Math.sin(x / w * 13f - t * 0.3f);
                    if (s == 0) ribbon.moveTo(x, y); else ribbon.lineTo(x, y);
                }
                for (int s = steps; s >= 0; s--) {
                    float x = w * s / (float) steps;
                    float len = h * (0.1f + 0.08f * (float) Math.sin(x / w * 7f + t * 0.22f + i)
                            + 0.025f * (float) Math.sin(x / w * 19f - t * 0.5f + i * 3));
                    float y = base - len + h * 0.03f * (float) Math.sin(x / w * 5.2f + t * (0.16f + i * 0.05f) + i * 2);
                    ribbon.lineTo(x, y);
                }
                ribbon.close();
                add.setShader(shaders[i]);
                c.drawPath(ribbon, add);
            }
            paint.setColor(0xFF020407);
            c.drawPath(hills, paint);
        }
    }

    // ==================== Fluido ====================

    static final class Fluid extends Scene {
        private final RadialGradient[] blobs = new RadialGradient[4];
        private final int[] colors = {0xFF6B3CFF, 0xFFFF4D9A, 0xFFFF8A3D, 0xFF2E6BFF};

        @Override boolean light() { return false; }
        @Override int accent() { return 0xFFC59BFF; }
        @Override int tint() { return 0xFF140A26; }

        @Override
        void prepare() {
            for (int i = 0; i < 4; i++) {
                blobs[i] = new RadialGradient(0, 0, 1f, new int[]{Ui.alpha(colors[i], 0xD0), Ui.alpha(colors[i], 0x55), 0x00000000},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            }
        }

        @Override
        void draw(Canvas c, float t) {
            c.drawColor(0xFF0B0618);
            float r = Math.max(w, h) * 0.55f;
            for (int i = 0; i < 4; i++) {
                float cx = w * (0.5f + 0.42f * (float) Math.sin(t * (0.07f + i * 0.013f) + i * 1.7f));
                float cy = h * (0.5f + 0.38f * (float) Math.cos(t * (0.05f + i * 0.011f) + i * 2.3f));
                float rr = r * (0.85f + 0.15f * (float) Math.sin(t * 0.2f + i));
                mx.setScale(rr, rr);
                mx.postTranslate(cx, cy);
                blobs[i].setLocalMatrix(mx);
                paint.setShader(blobs[i]);
                c.drawCircle(cx, cy, rr, paint);
            }
            paint.setShader(null);
        }
    }

    // ==================== Constelación ====================

    /** Puntos que flotan y se unen con líneas. Se apartan de tu dedo. */
    static final class Constellation extends Scene {
        private static final int N = 64;
        private final float[] base = new float[N * 4];
        private final float[] pos = new float[N * 2];
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient bg;
        private float tx = -1, ty = -1, force = 0;
        private boolean pressed = false;

        Constellation() {
            line.setStyle(Paint.Style.STROKE);
            dot.setColor(0xFFDCEBFF);
            Random r = new Random(21);
            for (int i = 0; i < N; i++) {
                base[i * 4] = r.nextFloat();
                base[i * 4 + 1] = r.nextFloat();
                base[i * 4 + 2] = (r.nextFloat() - 0.5f) * 0.016f;
                base[i * 4 + 3] = (r.nextFloat() - 0.5f) * 0.012f;
            }
        }

        @Override boolean light() { return false; }
        @Override int accent() { return 0xFF8EC9FF; }
        @Override int tint() { return 0xFF0A1322; }

        @Override
        void touch(float x, float y, boolean down) {
            tx = x;
            ty = y;
            pressed = down;
        }

        @Override
        void prepare() {
            bg = new LinearGradient(0, 0, w, h, 0xFF04060C, 0xFF0C1526, Shader.TileMode.CLAMP);
            line.setStrokeWidth(Math.max(1f, w / 640f));
        }

        private static float bounce(float v) {
            float m = v % 2f;
            if (m < 0) m += 2f;
            return m > 1f ? 2f - m : m;
        }

        @Override
        void draw(Canvas c, float t) {
            paint.setShader(bg);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            force += ((pressed ? 1f : 0f) - force) * 0.08f;
            float reach = w * 0.28f;
            for (int i = 0; i < N; i++) {
                float x = bounce(base[i * 4] + base[i * 4 + 2] * t) * w;
                float y = bounce(base[i * 4 + 1] + base[i * 4 + 3] * t) * h;
                if (force > 0.01f && tx >= 0) {
                    float dx = x - tx, dy = y - ty, d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < reach && d > 1) {
                        float push = (1 - d / reach) * reach * 0.45f * force;
                        x += dx / d * push;
                        y += dy / d * push;
                    }
                }
                pos[i * 2] = x;
                pos[i * 2 + 1] = y;
            }
            float max = w * 0.24f, max2 = max * max;
            for (int i = 0; i < N; i++) {
                float xi = pos[i * 2], yi = pos[i * 2 + 1];
                for (int j = i + 1; j < N; j++) {
                    float dx = xi - pos[j * 2], dy = yi - pos[j * 2 + 1];
                    float d2 = dx * dx + dy * dy;
                    if (d2 < max2) {
                        float a = 1f - (float) Math.sqrt(d2) / max;
                        line.setColor(Ui.alpha(0xFFA9D3FF, (int) (a * 205)));
                        c.drawLine(xi, yi, pos[j * 2], pos[j * 2 + 1], line);
                    }
                }
            }
            float rr = Math.max(1.5f, w / 420f);
            for (int i = 0; i < N; i++) {
                dot.setAlpha(i % 7 == 0 ? 255 : 170);
                c.drawCircle(pos[i * 2], pos[i * 2 + 1], i % 7 == 0 ? rr * 1.8f : rr, dot);
            }
        }
    }

    // ==================== Plano ====================

    /** Hoja de plano azul donde se dibuja sola una planta arquitectónica, con cotas. */
    static final class Blueprint extends Scene {
        private final Paint gridPaint = new Paint();
        private float[] segs;      // x1 y1 x2 y2 por tramo
        private float[] cum;       // longitud acumulada
        private float total;
        private final List<float[]> labels = new ArrayList<>();
        private final List<String> labelText = new ArrayList<>();
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient bg;
        private static final float CYCLE = 22f;

        Blueprint() {
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setColor(0xFFCFEAFF);
            pen.setColor(0xFFFFFFFF);
            text.setColor(0xCCCFEAFF);
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override boolean light() { return false; }
        @Override int accent() { return 0xFF7FD4FF; }
        @Override int tint() { return 0xFF061A2E; }

        @Override
        void prepare() {
            bg = new LinearGradient(0, 0, 0, h, 0xFF0B2D52, 0xFF061629, Shader.TileMode.CLAMP);
            if (Ui.REGULAR != null) text.setTypeface(Ui.REGULAR);
            text.setTextSize(w / 34f);
            ink.setStrokeWidth(Math.max(1f, w / 300f));
            // Cuadrícula: un mosaico pequeño que se repite (casi no ocupa memoria)
            float cell = w / 24f;
            int tile = Math.max(4, Math.round(cell * 4));
            float c1 = tile / 4f;
            Bitmap tb = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888);
            Canvas gc = new Canvas(tb);
            Paint g = new Paint();
            for (int i = 0; i < 4; i++) {
                g.setColor(i == 0 ? 0x2EBFE4FF : 0x14BFE4FF);
                g.setStrokeWidth(i == 0 ? Math.max(1f, w / 700f) : 1f);
                gc.drawLine(i * c1, 0, i * c1, tile, g);
                gc.drawLine(0, i * c1, tile, i * c1, g);
            }
            gridPaint.setShader(new android.graphics.BitmapShader(tb, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            buildPlan();
        }

        private void buildPlan() {
            List<float[]> s = new ArrayList<>();
            labels.clear();
            labelText.clear();
            float u = w / 10f;                 // 1 unidad = 1 m aprox
            float ox = w * 0.12f, oy = h * 0.3f;
            float W = 7.6f * u, H = 9.2f * u;
            // Muros exteriores
            rect(s, ox, oy, ox + W, oy + H);
            // Muros interiores
            s.add(new float[]{ox + 4.4f * u, oy, ox + 4.4f * u, oy + 3.2f * u});
            s.add(new float[]{ox + 4.4f * u, oy + 4.2f * u, ox + 4.4f * u, oy + 5.6f * u});
            s.add(new float[]{ox, oy + 5.6f * u, ox + 2.8f * u, oy + 5.6f * u});
            s.add(new float[]{ox + 3.8f * u, oy + 5.6f * u, ox + W, oy + 5.6f * u});
            s.add(new float[]{ox + 4.4f * u, oy + 5.6f * u, ox + 4.4f * u, oy + H});
            // Puertas (abatimiento en arcos)
            arc(s, ox + 4.4f * u, oy + 3.2f * u, u, 90, 180);
            arc(s, ox + 2.8f * u, oy + 5.6f * u, u, 0, 90);
            // Ventanas (doble línea)
            s.add(new float[]{ox + 1.2f * u, oy - 0.12f * u, ox + 3.2f * u, oy - 0.12f * u});
            s.add(new float[]{ox + 5.2f * u, oy - 0.12f * u, ox + 6.8f * u, oy - 0.12f * u});
            s.add(new float[]{ox + W + 0.12f * u, oy + 6.6f * u, ox + W + 0.12f * u, oy + 8.4f * u});
            // Cotas
            dim(s, ox, oy - 0.8f * u, ox + 4.4f * u, oy - 0.8f * u, "4.40");
            dim(s, ox + 4.4f * u, oy - 0.8f * u, ox + W, oy - 0.8f * u, "3.20");
            dim(s, ox - 0.7f * u, oy, ox - 0.7f * u, oy + 5.6f * u, "5.60");
            dim(s, ox - 0.7f * u, oy + 5.6f * u, ox - 0.7f * u, oy + H, "3.60");
            segs = new float[s.size() * 4];
            cum = new float[s.size()];
            total = 0;
            for (int i = 0; i < s.size(); i++) {
                float[] a = s.get(i);
                System.arraycopy(a, 0, segs, i * 4, 4);
                total += (float) Math.hypot(a[2] - a[0], a[3] - a[1]);
                cum[i] = total;
            }
        }

        private static void rect(List<float[]> s, float l, float t, float r, float b) {
            s.add(new float[]{l, t, r, t});
            s.add(new float[]{r, t, r, b});
            s.add(new float[]{r, b, l, b});
            s.add(new float[]{l, b, l, t});
        }

        private static void arc(List<float[]> s, float cx, float cy, float r, float a0, float a1) {
            int n = 10;
            for (int i = 0; i < n; i++) {
                double p = Math.toRadians(a0 + (a1 - a0) * i / n), q = Math.toRadians(a0 + (a1 - a0) * (i + 1) / n);
                s.add(new float[]{cx + (float) (r * Math.cos(p)), cy + (float) (r * Math.sin(p)),
                        cx + (float) (r * Math.cos(q)), cy + (float) (r * Math.sin(q))});
            }
        }

        private void dim(List<float[]> s, float x1, float y1, float x2, float y2, String label) {
            float tick = w / 90f;
            boolean horiz = Math.abs(y1 - y2) < 1;
            s.add(new float[]{x1, y1, x2, y2});
            if (horiz) {
                s.add(new float[]{x1, y1 - tick, x1, y1 + tick});
                s.add(new float[]{x2, y2 - tick, x2, y2 + tick});
                labels.add(new float[]{(x1 + x2) / 2, y1 - tick * 1.6f, s.size() - 1});
            } else {
                s.add(new float[]{x1 - tick, y1, x1 + tick, y1});
                s.add(new float[]{x2 - tick, y2, x2 + tick, y2});
                labels.add(new float[]{x1 - tick * 3.2f, (y1 + y2) / 2, s.size() - 1});
            }
            labelText.add(label);
        }

        @Override
        void draw(Canvas c, float t) {
            paint.setShader(bg);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            c.drawRect(0, 0, w, h, gridPaint);
            if (segs == null) return;
            float phase = t % CYCLE;
            float drawT = Math.min(1f, phase / (CYCLE * 0.62f));
            float fade = phase > CYCLE * 0.9f ? 1f - (phase - CYCLE * 0.9f) / (CYCLE * 0.1f) : 1f;
            float upto = total * (1 - (1 - drawT) * (1 - drawT));
            ink.setAlpha((int) (230 * fade));
            float tipX = -1, tipY = -1;
            int done = 0;
            for (int i = 0; i < cum.length; i++) {
                float start = i == 0 ? 0 : cum[i - 1];
                if (upto <= start) break;
                float x1 = segs[i * 4], y1 = segs[i * 4 + 1], x2 = segs[i * 4 + 2], y2 = segs[i * 4 + 3];
                if (upto >= cum[i]) {
                    c.drawLine(x1, y1, x2, y2, ink);
                    done = i;
                } else {
                    float f = (upto - start) / (cum[i] - start);
                    tipX = x1 + (x2 - x1) * f;
                    tipY = y1 + (y2 - y1) * f;
                    c.drawLine(x1, y1, tipX, tipY, ink);
                }
            }
            text.setAlpha((int) (200 * fade));
            for (int k = 0; k < labels.size(); k++) {
                float[] l = labels.get(k);
                if (done >= (int) l[2]) c.drawText(labelText.get(k), l[0], l[1], text);
            }
            if (tipX >= 0) {
                pen.setAlpha((int) (255 * fade));
                c.drawCircle(tipX, tipY, Math.max(2f, w / 260f), pen);
            }
        }
    }

    // ==================== Foto ====================

    /** Una foto tuya, con movimiento lento opcional (acercamiento suave). */
    static final class Photo extends Scene {
        final Bitmap bmp;
        private final boolean light;
        private final int accent, tint;
        private boolean motion;
        private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);

        Photo(Bitmap bmp, Palette p, boolean motion) {
            this.bmp = bmp;
            this.light = p != null && p.lightTop;
            this.accent = p != null ? p.vibrant : 0xFFF5F6F8;
            this.tint = p != null ? p.dominant : 0xFF0F1114;
            this.motion = motion;
        }

        void setMotion(boolean m) {
            motion = m;
        }

        @Override boolean light() { return light; }
        @Override int accent() { return accent; }
        @Override int tint() { return tint; }
        @Override boolean animated() { return motion; }

        @Override
        void draw(Canvas c, float t) {
            if (bmp == null || bmp.isRecycled()) {
                c.drawColor(0xFF000000);
                return;
            }
            float s = Math.max(w / (float) bmp.getWidth(), h / (float) bmp.getHeight());
            float zoom = 1f, dx = 0, dy = 0;
            if (motion) {
                zoom = 1f + 0.07f * (0.5f - 0.5f * (float) Math.cos(t * 2 * Math.PI / 48f));
                dx = (float) Math.sin(t * 2 * Math.PI / 70f) * w * 0.015f;
                dy = (float) Math.cos(t * 2 * Math.PI / 90f) * h * 0.01f;
            }
            float sc = s * zoom;
            mx.setScale(sc, sc);
            mx.postTranslate((w - bmp.getWidth() * sc) / 2f + dx, (h - bmp.getHeight() * sc) / 2f + dy);
            c.drawBitmap(bmp, mx, img);
        }
    }
}
