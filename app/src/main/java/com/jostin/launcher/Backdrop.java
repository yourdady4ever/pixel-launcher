package com.jostin.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.os.BatteryManager;
import android.os.PowerManager;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Fondo de pantalla completa: una escena animada o una foto tuya, con partículas encima.
 *
 * Para no gastar batería:
 *  - solo dibuja cuadros mientras se ve, y se detiene si nada se mueve (foto fija sin inclinar);
 *  - 30 cuadros por segundo salvo que pidas el modo fluido;
 *  - con ahorro de batería del teléfono apaga animaciones, partículas e inclinación solo.
 * Además mantiene una copia diminuta del fondo para que el vidrio se vea esmerilado.
 */
class Backdrop extends View implements SensorEventListener {
    static final float SMALL = 1f / 18f;
    private static final long FADE_MS = 650;

    interface Listener {
        /** El fondo cambió y ya se conocen sus colores. */
        void onWallpaperChanged();
    }

    private Scenes.Scene scene, previous, smallScene, smallPrevious;
    private Particles particles;
    private Bitmap small;
    private Canvas smallCanvas;
    private final Paint grainPaint = new Paint();
    private final Paint vignettePaint = new Paint();
    private long fadeStart = 0;
    private boolean running = false, wantAnim = true, wantParallax = true, eco = false, smooth = false;
    private float userDim = 0f, screenDim = 0f;
    private float t = 12f;
    private final long start = System.currentTimeMillis();
    private final List<WeakReference<View>> glass = new ArrayList<>();
    private Listener listener;
    private int loadToken = 0;
    private boolean ticking = false, smallDirty = true;

    private SensorManager sensors;
    private float gx = 0, gy = 7.5f, px = 0, py = 0, pageX = 0;

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            // La batería avisa a cada rato (voltaje, temperatura); solo se rehace si cambia el ahorro
            if (Intent.ACTION_BATTERY_CHANGED.equals(i.getAction()) && wantsEco(i) == eco) return;
            updateEco();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ticking = false;
            if (!running) return;
            step();
            if (needsFrames()) schedule();
        }
    };

    Backdrop(Context c) {
        super(c);
        scene = Scenes.create(0);
        smallScene = Scenes.create(0);
        grainPaint.setShader(new BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    void setListener(Listener l) {
        listener = l;
    }

    Scenes.Scene scene() {
        return scene;
    }

    /**
     * Mueve el fondo un poco al pasar de página en inicio. f: 0 primera página, 1 la última.
     * strength: qué tanto se mueve (1 = todo lo que sobra del fondo).
     */
    void setPageScroll(float f, float strength) {
        float sparePx = (scene.w - getWidth()) / 2f - 14 * getResources().getDisplayMetrics().density;
        float n = Math.max(0, sparePx) * (0.5f - f) * 2f * strength;
        if (Math.abs(n - pageX) < 0.5f) return;
        pageX = n;
        smallDirty = true;
        invalidate();
        kick();
    }

    /** Oscurece el fondo para pantallas secundarias (0 = normal). */
    void setScreenDim(float d) {
        screenDim = d;
        smallDirty = true;
        invalidate();
    }

    /** Lee de las preferencias qué fondo va y lo pone con un fundido. */
    void useCurrent() {
        Context c = getContext();
        userDim = Prefs.wallDim(c) / 100f;
        setOverlay(Prefs.wallOverlay(c));
        if (Prefs.wallKind(c) == 1 && Prefs.wallPhoto(c) != null && Wallpaper.file(c, Prefs.wallPhoto(c)).exists()) {
            String id = Prefs.wallPhoto(c);
            int fx = Prefs.wallEffect(c);
            int token = ++loadToken;
            Wallpaper.Loaded hit = Wallpaper.cached(id, fx);
            if (hit != null) {
                setPhoto(hit);
            } else {
                Wallpaper.load(c, id, fx, l -> {
                    if (token != loadToken) return;
                    if (l != null) setPhoto(l);
                    else setSceneIndex(Prefs.wallScene(getContext()));
                });
            }
        } else {
            ++loadToken;
            setSceneIndex(Math.max(0, Math.min(Scenes.COUNT - 1, Prefs.wallScene(c))));
        }
    }

    private void setSceneIndex(int i) {
        swap(Scenes.create(i), Scenes.create(i));
    }

    private void setPhoto(Wallpaper.Loaded l) {
        boolean motion = Prefs.animated(getContext());
        swap(new Scenes.Photo(l.bitmap, l.palette, motion), new Scenes.Photo(l.bitmap, l.palette, motion));
    }

    private void swap(Scenes.Scene main, Scenes.Scene mini) {
        boolean first = getWidth() == 0 || scene.w == 0 && previous == null;
        previous = first ? null : scene;
        smallPrevious = first ? null : smallScene;
        scene = main;
        smallScene = mini;
        sizeScenes();
        fadeStart = System.currentTimeMillis();
        smallDirty = true;
        Theme.setWallpaperColors(scene.accent(), scene.tint(), scene.light());
        Theme.recompute(getContext());
        if (listener != null) listener.onWallpaperChanged();
        kick();
    }

    private void setOverlay(int type) {
        particles = Particles.create(type);
        if (particles != null && getWidth() > 0) particles.size(getWidth(), getHeight());
    }

    void setUserDim(float d) {
        userDim = d;
        smallDirty = true;
        kick();
    }

    void refreshOverlay() {
        setOverlay(Prefs.wallOverlay(getContext()));
        kick();
    }

    void setAnimated(boolean on) {
        wantAnim = on;
        if (scene instanceof Scenes.Photo) ((Scenes.Photo) scene).setMotion(on);
        if (smallScene instanceof Scenes.Photo) ((Scenes.Photo) smallScene).setMotion(on);
        smallDirty = true;
        kick();
    }

    void setParallax(boolean on) {
        wantParallax = on;
        updateSensor();
        kick();
    }

    void setSmooth(boolean on) {
        smooth = on;
    }

    void setRunning(boolean on) {
        if (running == on) return;
        running = on;
        Context c = getContext();
        if (on) {
            try {
                IntentFilter f = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
                f.addAction(Intent.ACTION_BATTERY_CHANGED);
                c.registerReceiver(powerReceiver, f);
            } catch (Exception ignored) {
                // nada
            }
            updateEco();
        } else {
            try {
                c.unregisterReceiver(powerReceiver);
            } catch (Exception ignored) {
                // no estaba registrado
            }
        }
        updateSensor();
        kick();
    }

    private void updateEco() {
        eco = wantsEco(null);
        updateSensor();
        kick();
    }

    /** Con el ahorro de batería prendido, o con 20% o menos sin cargar. */
    private boolean wantsEco(Intent battery) {
        Context c = getContext();
        if (!Prefs.autoEco(c)) return false;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isPowerSaveMode()) return true;
        if (battery == null) {
            try {
                battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            } catch (Exception e) {
                return false;
            }
        }
        if (battery == null) return false;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        boolean plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        return !plugged && level >= 0 && level * 100 <= 20 * scale;
    }

    private boolean animOn() {
        return wantAnim && !eco;
    }

    private boolean parallaxOn() {
        return wantParallax && !eco;
    }

    /** Toque del dedo sobre el fondo (la constelación se aparta). */
    void touch(float x, float y, boolean down) {
        scene.touch(x + (scene.w - getWidth()) / 2f - px - pageX, y + (scene.h - getHeight()) / 2f - py, down);
        kick();
    }

    void register(View v) {
        glass.add(new WeakReference<>(v));
    }

    /** Redibuja todos los paneles de vidrio (cuando cambia su tinte u opacidad). */
    void invalidateGlass() {
        Iterator<WeakReference<View>> it = glass.iterator();
        while (it.hasNext()) {
            View v = it.next().get();
            if (v == null) it.remove();
            else if (v.isAttachedToWindow()) v.invalidate();
        }
    }

    Bitmap small() {
        return small;
    }

    float sceneLeft() {
        return -(scene.w - getWidth()) / 2f + px + pageX;
    }

    float sceneTop() {
        return -(scene.h - getHeight()) / 2f + py;
    }

    float smallScale() {
        return small == null ? 1f : scene.w / (float) small.getWidth();
    }

    // ---------- Ciclo de cuadros ----------

    private void kick() {
        if (running) {
            if (!ticking) {
                ticking = true;
                postOnAnimation(tick);
            }
        } else {
            step();
        }
    }

    private void schedule() {
        if (ticking) return;
        ticking = true;
        postOnAnimationDelayed(tick, smooth && !eco ? 14 : 31);
    }

    private boolean needsFrames() {
        if (!running) return false;
        if (previous != null) return true;
        if (animOn() && (scene.animated() || particles != null)) return true;
        float range = 14 * getResources().getDisplayMetrics().density;
        float tx = parallaxOn() ? -clamp(gx / 9.8f) * range : 0;
        float ty = parallaxOn() ? clamp((gy - 7.5f) / 9.8f) * range : 0;
        return Math.abs(tx - px) > 0.3f || Math.abs(ty - py) > 0.3f;
    }

    private void step() {
        boolean moving = animOn() && scene.animated();
        if (animOn()) t = (System.currentTimeMillis() - start) / 1000f;
        float range = 14 * getResources().getDisplayMetrics().density;
        float tx = parallaxOn() ? -clamp(gx / 9.8f) * range : 0;
        float ty = parallaxOn() ? clamp((gy - 7.5f) / 9.8f) * range : 0;
        float npx = px + (tx - px) * 0.12f, npy = py + (ty - py) * 0.12f;
        boolean shifted = Math.abs(npx - px) > 0.05f || Math.abs(npy - py) > 0.05f;
        px = npx;
        py = npy;
        boolean fading = previous != null;
        if (moving || fading || shifted || smallDirty) {
            renderSmall();
            smallDirty = false;
            Iterator<WeakReference<View>> it = glass.iterator();
            while (it.hasNext()) {
                View v = it.next().get();
                if (v == null) it.remove();
                else if (v.isAttachedToWindow()) v.invalidate();
            }
        }
        invalidate();
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private void updateSensor() {
        if (sensors == null) sensors = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensors == null) return;
        sensors.unregisterListener(this);
        if (running && parallaxOn()) {
            Sensor g = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (g != null) sensors.registerListener(this, g, SensorManager.SENSOR_DELAY_UI);
        } else {
            gx = 0;
            gy = 7.5f;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float ngx = e.values[0], ngy = e.values[1];
        boolean big = Math.abs(ngx - gx) > 0.08f || Math.abs(ngy - gy) > 0.08f;
        gx = ngx;
        gy = ngy;
        if (big) kick();
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {}

    // ---------- Dibujo ----------

    private void sizeScenes() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float over = 1.12f;
        int sw = Math.round(w * over), sh = Math.round(h * over);
        if (scene.w != sw || scene.h != sh) scene.size(sw, sh);
        if (previous != null && (previous.w != sw || previous.h != sh)) previous.size(sw, sh);
        int bw = Math.max(4, Math.round(sw * SMALL)), bh = Math.max(4, Math.round(sh * SMALL));
        if (smallScene.w != bw || smallScene.h != bh) smallScene.size(bw, bh);
        if (smallPrevious != null && (smallPrevious.w != bw || smallPrevious.h != bh)) smallPrevious.size(bw, bh);
        if (small == null || small.getWidth() != bw || small.getHeight() != bh) {
            small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            smallCanvas = new Canvas(small);
        }
        if (particles != null) particles.size(w, h);
    }

    private float fade() {
        if (previous == null) return 1f;
        float f = (System.currentTimeMillis() - fadeStart) / (float) FADE_MS;
        if (f >= 1f) {
            previous = null;
            smallPrevious = null;
            return 1f;
        }
        return f * f * (3 - 2 * f);
    }

    private float totalDim() {
        return 1f - (1f - userDim) * (1f - screenDim);
    }

    private void renderSmall() {
        if (small == null) return;
        float f = fade();
        if (smallPrevious != null && f < 1f) {
            smallPrevious.draw(smallCanvas, t);
            int s = smallCanvas.saveLayerAlpha(0, 0, small.getWidth(), small.getHeight(), (int) (255 * f));
            smallScene.draw(smallCanvas, t);
            smallCanvas.restoreToCount(s);
        } else {
            smallScene.draw(smallCanvas, t);
        }
        float d = totalDim();
        if (d > 0) smallCanvas.drawColor(Ui.alpha(0xFF000000, (int) (255 * d)));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sizeScenes();
        vignettePaint.setShader(new RadialGradient(w / 2f, h * 0.42f, Math.max(w, h) * 0.78f,
                new int[]{0x00000000, 0x00000000, 0x6B000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        smallDirty = true;
        renderSmall();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setRunning(false);
        removeCallbacks(tick);
        if (sensors != null) sensors.unregisterListener(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        if (scene.w == 0) sizeScenes();
        canvas.save();
        canvas.translate(-(scene.w - w) / 2f + px + pageX, -(scene.h - h) / 2f + py);
        float f = fade();
        if (previous != null && f < 1f) {
            previous.draw(canvas, t);
            int s = canvas.saveLayerAlpha(0, 0, scene.w, scene.h, (int) (255 * f));
            scene.draw(canvas, t);
            canvas.restoreToCount(s);
        } else {
            scene.draw(canvas, t);
        }
        canvas.restore();
        if (particles != null && animOn()) particles.draw(canvas, t);
        canvas.drawRect(0, 0, w, h, vignettePaint);
        canvas.drawRect(0, 0, w, h, grainPaint);
        float d = totalDim();
        if (d > 0) canvas.drawColor(Ui.alpha(0xFF000000, (int) (255 * d)));
    }

    private static Bitmap grain() {
        int s = 128;
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
