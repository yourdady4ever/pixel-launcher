package com.jostin.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/** Efectos que se dibujan encima de cualquier fondo: lluvia, nieve, polvo de luz y estrellas. */
final class Particles {
    static final String[] NAMES = {"Nada", "Lluvia", "Nieve", "Polvo de luz", "Estrellas"};
    static final int NONE = 0, RAIN = 1, SNOW = 2, DUST = 3, STARS = 4;

    private final int type;
    private int w, h;
    private float[] p;          // datos por partícula, el significado depende del tipo
    private int n;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap glow;
    private final RectF dst = new RectF();
    private float last = -1;
    private final Random rnd = new Random(4);

    private Particles(int type) {
        this.type = type;
    }

    static Particles create(int type) {
        return type <= NONE || type > STARS ? null : new Particles(type);
    }

    void size(int w, int h) {
        this.w = w;
        this.h = h;
        float d = w / 1080f;
        switch (type) {
            case RAIN:
                n = 150;
                p = new float[n * 4];     // x, y, largo, velocidad
                for (int i = 0; i < n; i++) {
                    p[i * 4] = rnd.nextFloat() * w * 1.2f;
                    p[i * 4 + 1] = rnd.nextFloat() * h;
                    p[i * 4 + 2] = (30 + rnd.nextFloat() * 60) * d;
                    p[i * 4 + 3] = (1700 + rnd.nextFloat() * 1300) * d;
                }
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(Math.max(1f, 2.2f * d));
                break;
            case SNOW:
                n = 90;
                p = new float[n * 5];     // x, y, radio, velocidad, fase
                for (int i = 0; i < n; i++) {
                    p[i * 5] = rnd.nextFloat() * w;
                    p[i * 5 + 1] = rnd.nextFloat() * h;
                    p[i * 5 + 2] = (2 + rnd.nextFloat() * 5) * d;
                    p[i * 5 + 3] = (40 + rnd.nextFloat() * 90) * d;
                    p[i * 5 + 4] = rnd.nextFloat() * 6.28f;
                }
                break;
            case DUST:
                n = 42;
                p = new float[n * 6];     // x0, y0, radio, velocidad x, velocidad y, fase
                for (int i = 0; i < n; i++) {
                    p[i * 6] = rnd.nextFloat();
                    p[i * 6 + 1] = rnd.nextFloat();
                    p[i * 6 + 2] = (10 + rnd.nextFloat() * 26) * d;
                    p[i * 6 + 3] = (rnd.nextFloat() - 0.5f) * 0.012f;
                    p[i * 6 + 4] = -(0.004f + rnd.nextFloat() * 0.012f);
                    p[i * 6 + 5] = rnd.nextFloat() * 6.28f;
                }
                makeGlow();
                break;
            default:
                n = 140;
                p = new float[n * 4];     // x, y, radio, fase
                for (int i = 0; i < n; i++) {
                    p[i * 4] = rnd.nextFloat() * w;
                    p[i * 4 + 1] = rnd.nextFloat() * h;
                    p[i * 4 + 2] = (1 + rnd.nextFloat() * 2.4f) * d;
                    p[i * 4 + 3] = rnd.nextFloat() * 6.28f;
                }
                makeGlow();
                break;
        }
    }

    private void makeGlow() {
        int s = 48;
        glow = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(glow);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new RadialGradient(s / 2f, s / 2f, s / 2f, new int[]{0xFFFFFFFF, 0x55FFF4D6, 0x00FFF4D6},
                new float[]{0f, 0.25f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(s / 2f, s / 2f, s / 2f, g);
    }

    void draw(Canvas c, float t) {
        if (p == null) return;
        float dt = last < 0 ? 0 : Math.max(0f, Math.min(0.06f, t - last));
        last = t;
        switch (type) {
            case RAIN: {
                paint.setColor(0x40DCE6F0);
                float slant = 0.18f;
                for (int i = 0; i < n; i++) {
                    int k = i * 4;
                    p[k + 1] += p[k + 3] * dt;
                    p[k] -= p[k + 3] * dt * slant;
                    if (p[k + 1] > h + p[k + 2]) {
                        p[k + 1] = -p[k + 2];
                        p[k] = rnd.nextFloat() * w * 1.2f;
                    }
                    float len = p[k + 2];
                    c.drawLine(p[k], p[k + 1], p[k] + len * slant, p[k + 1] - len, paint);
                }
                break;
            }
            case SNOW: {
                paint.setColor(0xCCFFFFFF);
                for (int i = 0; i < n; i++) {
                    int k = i * 5;
                    p[k + 1] += p[k + 3] * dt;
                    float sway = (float) Math.sin(t * 0.8f + p[k + 4]) * p[k + 2] * 6f;
                    if (p[k + 1] > h + 10) {
                        p[k + 1] = -10;
                        p[k] = rnd.nextFloat() * w;
                    }
                    c.drawCircle(p[k] + sway, p[k + 1], p[k + 2], paint);
                }
                break;
            }
            case DUST: {
                for (int i = 0; i < n; i++) {
                    int k = i * 6;
                    float x = wrap(p[k] + p[k + 3] * t) * w;
                    float y = wrap(p[k + 1] + p[k + 4] * t) * h;
                    float r = p[k + 2] * (0.7f + 0.3f * (float) Math.sin(t * 0.9f + p[k + 5]));
                    sprite.setAlpha((int) (90 + 120 * (0.5f + 0.5f * (float) Math.sin(t * 0.6f + p[k + 5] * 2))));
                    dst.set(x - r, y - r, x + r, y + r);
                    c.drawBitmap(glow, null, dst, sprite);
                }
                break;
            }
            default: {
                for (int i = 0; i < n; i++) {
                    int k = i * 4;
                    float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.6f + p[k + 3]);
                    float r = p[k + 2] * (1.6f + 2.4f * tw);
                    sprite.setAlpha((int) (60 + 195 * tw));
                    dst.set(p[k] - r, p[k + 1] - r, p[k] + r, p[k + 1] + r);
                    c.drawBitmap(glow, null, dst, sprite);
                }
                break;
            }
        }
    }

    private static float wrap(float v) {
        float m = v % 1f;
        return m < 0 ? m + 1f : m;
    }
}
