package com.jostin.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Fondo animado de pantalla completa.
 * Además mantiene una copia diminuta del fondo que usan los paneles de vidrio para verse
 * esmerilados, se mueve un poco al inclinar el teléfono y lleva grano y viñeta encima.
 */
class Backdrop extends View implements SensorEventListener {
    static final float SMALL = 1f / 18f;
    private static final int FRAME_MS = 33;
    private static final long FADE_MS = 600;

    private Scenes.Scene scene, previous;
    private Scenes.Scene smallScene, smallPrevious;
    private Bitmap small;
    private Canvas smallCanvas;
    private final Paint fadePaint = new Paint();
    private final Paint grainPaint = new Paint();
    private final Paint vignettePaint = new Paint();
    private long fadeStart = 0;
    private boolean running = false, animated = true, parallax = true;
    private float dim = 0f;
    private float t = 12f;
    private final long start = System.currentTimeMillis();
    private final List<WeakReference<View>> glass = new ArrayList<>();

    // Paralaje
    private SensorManager sensors;
    private float gx = 0, gy = 0, px = 0, py = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            step();
            if (animated || previous != null || parallax) postDelayed(this, FRAME_MS);
        }
    };

    Backdrop(Context c, int theme) {
        super(c);
        scene = Scenes.create(theme);
        smallScene = Scenes.create(theme);
        Bitmap noise = grain(c);
        grainPaint.setShader(new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    Scenes.Scene scene() {
        return scene;
    }

    /** Oscurece el fondo (0 = normal, 1 = negro). Para pantallas secundarias. */
    void setDim(float d) {
        dim = d;
        invalidate();
    }

    void setTheme(int theme) {
        previous = scene;
        smallPrevious = smallScene;
        scene = Scenes.create(theme);
        smallScene = Scenes.create(theme);
        sizeScenes();
        fadeStart = System.currentTimeMillis();
        kick();
    }

    void setAnimated(boolean on) {
        animated = on;
        kick();
    }

    void setParallax(boolean on) {
        parallax = on;
        updateSensor();
        if (!on) {
            px = py = gx = gy = 0;
        }
        kick();
    }

    void setRunning(boolean on) {
        running = on;
        updateSensor();
        kick();
    }

    /** Registra una vista de vidrio para que se redibuje junto con el fondo. */
    void register(View v) {
        glass.add(new WeakReference<>(v));
    }

    Bitmap small() {
        return small;
    }

    /** Dónde empieza la escena respecto a esta vista (incluye el paralaje). */
    float sceneLeft() {
        return -(scene.w - getWidth()) / 2f + px;
    }

    float sceneTop() {
        return -(scene.h - getHeight()) / 2f + py;
    }

    /** Cuántos píxeles de pantalla mide cada píxel de la copia diminuta. */
    float smallScale() {
        return small == null ? 1f : scene.w / (float) small.getWidth();
    }

    private void kick() {
        removeCallbacks(tick);
        if (running) post(tick);
        else step();
    }

    private void updateSensor() {
        if (sensors == null) sensors = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensors == null) return;
        sensors.unregisterListener(this);
        if (running && parallax) {
            Sensor g = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (g != null) sensors.registerListener(this, g, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        gx = e.values[0];
        gy = e.values[1];
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {}

    private void step() {
        if (animated) t = (System.currentTimeMillis() - start) / 1000f;
        float range = 14 * getResources().getDisplayMetrics().density;
        float tx = parallax ? -clamp(gx / 9.8f) * range : 0;
        float ty = parallax ? clamp((gy - 7.5f) / 9.8f) * range : 0;
        px += (tx - px) * 0.12f;
        py += (ty - py) * 0.12f;
        renderSmall();
        invalidate();
        Iterator<WeakReference<View>> it = glass.iterator();
        while (it.hasNext()) {
            View v = it.next().get();
            if (v == null) it.remove();
            else v.invalidate();
        }
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private void sizeScenes() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        // Un poco más grande que la pantalla, para que el paralaje no deje ver bordes
        float over = 1.06f;
        int sw = Math.round(w * over), sh = Math.round(h * over);
        scene.size(sw, sh);
        if (previous != null) previous.size(sw, sh);
        int bw = Math.max(4, Math.round(sw * SMALL)), bh = Math.max(4, Math.round(sh * SMALL));
        smallScene.size(bw, bh);
        if (smallPrevious != null) smallPrevious.size(bw, bh);
        if (small == null || small.getWidth() != bw || small.getHeight() != bh) {
            small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            smallCanvas = new Canvas(small);
        }
    }

    private float fadeProgress() {
        if (previous == null) return 1f;
        float f = (System.currentTimeMillis() - fadeStart) / (float) FADE_MS;
        if (f >= 1f) {
            previous = null;
            smallPrevious = null;
            return 1f;
        }
        return f * f * (3 - 2 * f);
    }

    private void renderSmall() {
        if (small == null) return;
        float f = fadeProgress();
        if (smallPrevious != null && f < 1f) {
            smallPrevious.draw(smallCanvas, t);
            int s = smallCanvas.saveLayerAlpha(0, 0, small.getWidth(), small.getHeight(), (int) (255 * f));
            smallScene.draw(smallCanvas, t);
            smallCanvas.restoreToCount(s);
        } else {
            smallScene.draw(smallCanvas, t);
        }
        if (dim > 0) smallCanvas.drawColor(Ui.alpha(0xFF000000, (int) (255 * dim)));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sizeScenes();
        vignettePaint.setShader(new RadialGradient(w / 2f, h * 0.42f, Math.max(w, h) * 0.78f,
                new int[]{0x00000000, 0x00000000, 0x73000000}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        renderSmall();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        removeCallbacks(tick);
        if (sensors != null) sensors.unregisterListener(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        if (scene.w == 0) sizeScenes();
        float extraX = (scene.w - w) / 2f, extraY = (scene.h - h) / 2f;
        canvas.save();
        canvas.translate(-extraX + px, -extraY + py);
        float f = fadeProgress();
        if (previous != null && f < 1f) {
            previous.draw(canvas, t);
            int s = canvas.saveLayerAlpha(0, 0, scene.w, scene.h, (int) (255 * f));
            scene.draw(canvas, t);
            canvas.restoreToCount(s);
        } else {
            scene.draw(canvas, t);
        }
        canvas.restore();
        canvas.drawRect(0, 0, w, h, vignettePaint);
        canvas.drawRect(0, 0, w, h, grainPaint);
        if (dim > 0) canvas.drawColor(Ui.alpha(0xFF000000, (int) (255 * dim)));
    }

    /** Textura de grano fino, casi invisible, que quita el aspecto "plano" de los degradados. */
    private static Bitmap grain(Context c) {
        int s = 160;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Random r = new Random(5);
        int[] px = new int[s * s];
        for (int i = 0; i < px.length; i++) {
            int v = r.nextInt(256);
            int a = 6 + r.nextInt(10);
            px[i] = (a << 24) | (v << 16) | (v << 8) | v;
        }
        b.setPixels(px, 0, s, 0, 0, s, s);
        return b;
    }
}
