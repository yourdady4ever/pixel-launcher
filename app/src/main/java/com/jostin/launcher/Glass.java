package com.jostin.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Vidrio esmerilado: dibuja el fondo que está detrás muy difuminado, con un tinte que sale del
 * fondo (o neutro), un brillo suave arriba y un borde de luz.
 */
class Glass extends Drawable {
    /** Usa el tinte normal del tema. */
    static final int TINT = 1;
    /** Usa el tinte fuerte del tema (menús y hojas). */
    static final int TINT_STRONG = 2;

    private final Backdrop backdrop;
    private final float[] radii;
    private final int tint;
    private final Paint blurPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path borderPath = new Path();
    private final RectF rect = new RectF();
    private final int[] hostLoc = new int[2], backLoc = new int[2];
    private int alpha = 255;

    Glass(Backdrop backdrop, float radiusPx, int tint) {
        this(backdrop, new float[]{radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx}, tint);
    }

    Glass(Backdrop backdrop, float[] radii, int tint) {
        this.backdrop = backdrop;
        this.radii = radii;
        this.tint = tint;
        borderPaint.setStyle(Paint.Style.STROKE);
    }

    static Glass apply(View v, Backdrop b, float radiusDp) {
        return apply(v, b, radiusDp, TINT);
    }

    static Glass apply(View v, Backdrop b, float radiusDp, int tint) {
        Glass g = new Glass(b, Ui.dp(v.getContext(), radiusDp), tint);
        v.setBackground(g);
        if (b != null) b.register(v);
        return g;
    }

    private int tintColor() {
        if (tint == TINT) return backdrop == null ? Ui.alpha(Theme.glass, 0xF0) : Theme.glass;
        if (tint == TINT_STRONG) return backdrop == null ? Ui.alpha(Theme.glassStrong, 0xF5) : Theme.glassStrong;
        return tint;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        super.onBoundsChange(b);
        rect.set(b);
        path.reset();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        sheenPaint.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.5f,
                0x17FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        borderPaint.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                0x52FFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        borderPaint.setStrokeWidth(1.2f);
        rect.inset(0.6f, 0.6f);
        borderPath.reset();
        borderPath.addRoundRect(rect, radii, Path.Direction.CW);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        c.save();
        c.clipPath(path);
        Bitmap small = backdrop == null ? null : backdrop.small();
        Object cb = getCallback();
        if (small != null && !small.isRecycled() && cb instanceof View) {
            View host = (View) cb;
            host.getLocationInWindow(hostLoc);
            backdrop.getLocationInWindow(backLoc);
            float x = hostLoc[0] - backLoc[0], y = hostLoc[1] - backLoc[1];
            float sx = scaleOf(host);
            float s = backdrop.smallScale();
            c.save();
            c.translate((backdrop.sceneLeft() - x) / sx, (backdrop.sceneTop() - y) / sx);
            c.scale(s / sx, s / sx);
            blurPaint.setAlpha(alpha);
            c.drawBitmap(small, 0, 0, blurPaint);
            c.restore();
        }
        int tc = tintColor();
        tintPaint.setColor(tc);
        tintPaint.setAlpha((Ui.alphaOf(tc) * alpha) / 255);
        c.drawRect(b, tintPaint);
        sheenPaint.setAlpha(alpha);
        c.drawRect(b, sheenPaint);
        c.restore();
        borderPaint.setAlpha(alpha);
        c.drawPath(borderPath, borderPaint);
    }

    private static float scaleOf(View v) {
        float s = 1f;
        Object p = v;
        while (p instanceof View) {
            s *= ((View) p).getScaleX();
            p = ((View) p).getParent();
        }
        return s <= 0 ? 1f : s;
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
