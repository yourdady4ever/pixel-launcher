package com.jostin.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/**
 * Fondo de pantalla animado. Dibuja ~30 cuadros por segundo solo mientras se ve,
 * y cambia de escena con un fundido suave.
 */
class Backdrop extends View {
    private static final int FRAME_MS = 33;
    private static final long FADE_MS = 550;

    private Scenes.Scene scene;
    private Scenes.Scene previous;
    private long fadeStart = 0;
    private boolean running = false;
    private boolean animated = true;
    private final long start = System.currentTimeMillis();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            if (animated || previous != null) postDelayed(this, FRAME_MS);
        }
    };

    Backdrop(Context c, int theme) {
        super(c);
        scene = Scenes.create(theme);
    }

    Scenes.Scene scene() {
        return scene;
    }

    void setTheme(int theme) {
        Scenes.Scene next = Scenes.create(theme);
        if (getWidth() > 0) next.size(getWidth(), getHeight());
        previous = scene;
        scene = next;
        fadeStart = System.currentTimeMillis();
        kick();
    }

    void setAnimated(boolean on) {
        animated = on;
        kick();
    }

    void setRunning(boolean on) {
        running = on;
        kick();
    }

    private void kick() {
        removeCallbacks(tick);
        if (running) post(tick);
        else invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        scene.size(w, h);
        if (previous != null) previous.size(w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        removeCallbacks(tick);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() == 0) return;
        if (scene.w != getWidth() || scene.h != getHeight()) scene.size(getWidth(), getHeight());
        if (previous != null && previous.w != getWidth()) previous.size(getWidth(), getHeight());
        float t = animated ? (System.currentTimeMillis() - start) / 1000f : 12f;
        if (previous != null) {
            float f = (System.currentTimeMillis() - fadeStart) / (float) FADE_MS;
            if (f >= 1f) {
                previous = null;
                scene.draw(canvas, t);
            } else {
                previous.draw(canvas, t);
                int save = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (255 * f));
                scene.draw(canvas, t);
                canvas.restoreToCount(save);
            }
        } else {
            scene.draw(canvas, t);
        }
    }
}
