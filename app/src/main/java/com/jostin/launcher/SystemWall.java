package com.jostin.launcher;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pone el fondo de Jostin OS también como fondo del sistema y de la pantalla de bloqueo,
 * para que el fondo viejo no aparezca en apps recientes, al cambiar de app ni al bloquear.
 * Solo se hace cuando el fondo cambia de verdad (no cada cuadro) y en segundo plano.
 */
final class SystemWall {
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Runnable pending;

    private SystemWall() {}

    private static boolean visible = false, wanted = false;

    /**
     * Pide sincronizar. Si inicio está a la vista se deja para cuando salgas: al cambiar el fondo
     * del sistema, Android recalcula sus colores y vuelve a abrir las pantallas, y así no se nota.
     */
    static void sync(Context c) {
        if (visible) {
            wanted = true;
            return;
        }
        schedule(c.getApplicationContext(), 300);
    }

    /** MainActivity avisa cuando inicio se ve o se deja de ver. */
    static void setVisible(Context c, boolean v) {
        visible = v;
        if (v) {
            if (pending != null) {
                main.removeCallbacks(pending);
                pending = null;
                wanted = true;
            }
        } else if (wanted) {
            wanted = false;
            schedule(c.getApplicationContext(), 150);
        }
    }

    private static void schedule(Context app, long delay) {
        if (pending != null) main.removeCallbacks(pending);
        pending = () -> run(app);
        main.postDelayed(pending, delay);
    }

    private static String key(Context c) {
        return Prefs.wallKind(c) + "|" + Prefs.wallScene(c) + "|" + Prefs.wallPhoto(c) + "|"
                + Prefs.wallEffect(c) + "|" + Prefs.wallDim(c) + "|" + Prefs.syncLock(c);
    }

    private static void run(Context c) {
        pending = null;
        if (!Prefs.syncSystem(c)) {
            SystemColors.apply(c);
            return;
        }
        String key = key(c);
        if (key.equals(Prefs.syncedKey(c))) {
            SystemColors.apply(c);
            return;
        }
        if (Prefs.wallKind(c) == 1 && Prefs.wallPhoto(c) != null && Wallpaper.file(c, Prefs.wallPhoto(c)).exists()) {
            Wallpaper.load(c, Prefs.wallPhoto(c), Prefs.wallEffect(c), l -> {
                if (l == null) return;
                Scenes.Photo p = new Scenes.Photo(l.bitmap, l.palette, false);
                worker.execute(() -> apply(c, p, key));
            });
        } else {
            Scenes.Scene s = Scenes.create(Math.max(0, Math.min(Scenes.COUNT - 1, Prefs.wallScene(c))));
            worker.execute(() -> apply(c, s, key));
        }
    }

    private static void apply(Context c, Scenes.Scene scene, String key) {
        Bitmap out = null;
        try {
            DisplayMetrics dm = new DisplayMetrics();
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getRealMetrics(dm);
            int w = dm.widthPixels, h = dm.heightPixels;
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            scene.size(w, h);
            scene.draw(cv, 14f);
            int dim = Prefs.wallDim(c);
            if (dim > 0) cv.drawColor(Ui.alpha(0xFF000000, Math.round(255 * dim / 100f)));
            int which = WallpaperManager.FLAG_SYSTEM | (Prefs.syncLock(c) ? WallpaperManager.FLAG_LOCK : 0);
            WallpaperManager.getInstance(c).setBitmap(out, null, true, which);
            Prefs.setSyncedKey(c, key);
            // Después de que Android saque los colores del fondo nuevo, se ponen los de Jostin (si los pediste)
            main.postDelayed(() -> SystemColors.reapply(c), 2500);
        } catch (Throwable ignored) {
            // Si el sistema no lo permite, Jostin OS sigue funcionando igual
        } finally {
            if (out != null) out.recycle();
        }
    }
}
