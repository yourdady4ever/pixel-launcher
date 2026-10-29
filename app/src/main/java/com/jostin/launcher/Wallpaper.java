package com.jostin.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tus fotos como fondo. Cada foto se guarda una sola vez recortada al tamaño exacto de tu
 * pantalla y comprimida (unos 300 KB), con una miniatura aparte para el selector.
 * En memoria solo vive la foto que está puesta; todo lo pesado se hace en segundo plano.
 */
final class Wallpaper {
    static final String[] EFFECTS = {"Original", "Puntos", "Duotono", "Desenfoque"};
    static final int MAX_PHOTOS = 24;

    interface Done<T> {
        void done(T value);
    }

    static final class Loaded {
        final Bitmap bitmap;
        final Palette palette;

        Loaded(Bitmap b, Palette p) {
            bitmap = b;
            palette = p;
        }
    }

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static String cacheKey;
    private static Loaded cache;
    private static final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };

    private Wallpaper() {}

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "walls");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File file(Context c, String id) {
        return new File(dir(c), id + ".jpg");
    }

    static File thumbFile(Context c, String id) {
        return new File(dir(c), id + "_t.jpg");
    }

    /** Fotos guardadas, de la más nueva a la más vieja. */
    static List<String> photos(Context c) {
        File[] fs = dir(c).listFiles((d, n) -> n.endsWith(".jpg") && !n.endsWith("_t.jpg"));
        List<String> out = new ArrayList<>();
        if (fs == null) return out;
        Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : fs) out.add(f.getName().substring(0, f.getName().length() - 4));
        return out;
    }

    static void delete(Context c, String id) {
        file(c, id).delete();
        thumbFile(c, id).delete();
        thumbs.remove(id);
        if (id.equals(cacheKeyId())) {
            cache = null;
            cacheKey = null;
        }
    }

    private static String cacheKeyId() {
        return cacheKey == null ? "" : cacheKey.substring(0, cacheKey.indexOf('/'));
    }

    static void trim() {
        thumbs.evictAll();
    }

    /** Tamaño real de la pantalla (incluye barras), un poco más grande para el paralaje. */
    static int[] target(Context c) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) wm.getDefaultDisplay().getRealMetrics(dm);
        else dm = c.getResources().getDisplayMetrics();
        return new int[]{Math.round(dm.widthPixels * 1.06f), Math.round(dm.heightPixels * 1.06f)};
    }

    // ---------- Importar ----------

    interface Source {
        InputStream open() throws Exception;
    }

    static void importUri(Context c, Uri uri, Done<String> done) {
        Context app = c.getApplicationContext();
        importFrom(app, () -> app.getContentResolver().openInputStream(uri), done);
    }

    static void importFile(Context c, File f, Done<String> done) {
        importFrom(c.getApplicationContext(), () -> new java.io.FileInputStream(f), done);
    }

    private static void importFrom(Context c, Source src, Done<String> done) {
        worker.execute(() -> {
            String id = null;
            try {
                id = doImport(c, src);
            } catch (Throwable e) {
                id = null;
            }
            final String r = id;
            main.post(() -> done.done(r));
        });
    }

    private static String doImport(Context c, Source src) throws Exception {
        int[] t = target(c);
        int tw = t[0], th = t[1];
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = src.open()) {
            BitmapFactory.decodeStream(in, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int rotation = 0;
        try (InputStream in = src.open()) {
            ExifInterface ex = new ExifInterface(in);
            int or = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            rotation = or == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : or == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : or == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (Exception ignored) {
            rotation = 0;
        }
        int ow = rotation % 180 == 0 ? o.outWidth : o.outHeight;
        int oh = rotation % 180 == 0 ? o.outHeight : o.outWidth;
        // Cuánto podemos reducir al leer sin quedarnos cortos del tamaño de la pantalla
        float need = Math.max(tw / (float) ow, th / (float) oh);
        int sample = 1;
        while (need * sample * 2 <= 1f) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap raw;
        try (InputStream in = src.open()) {
            raw = BitmapFactory.decodeStream(in, null, d);
        }
        if (raw == null) return null;

        // Girar, escalar y recortar al centro en un solo paso
        int rw = rotation % 180 == 0 ? raw.getWidth() : raw.getHeight();
        int rh = rotation % 180 == 0 ? raw.getHeight() : raw.getWidth();
        float s = Math.max(tw / (float) rw, th / (float) rh);
        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Matrix m = new Matrix();
        m.postTranslate(-raw.getWidth() / 2f, -raw.getHeight() / 2f);
        m.postRotate(rotation);
        m.postScale(s, s);
        m.postTranslate(tw / 2f, th / 2f);
        cv.drawBitmap(raw, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        raw.recycle();

        List<String> existing = photos(c);
        for (int k = existing.size() - 1; k >= MAX_PHOTOS - 1; k--) delete(c, existing.get(k));

        String id = "p" + System.currentTimeMillis();
        try (FileOutputStream f = new FileOutputStream(file(c, id))) {
            out.compress(Bitmap.CompressFormat.JPEG, 88, f);
        }
        int thw = 240, thh = Math.round(240f * th / tw);
        Bitmap thumb = Bitmap.createScaledBitmap(out, thw, thh, true);
        try (FileOutputStream f = new FileOutputStream(thumbFile(c, id))) {
            thumb.compress(Bitmap.CompressFormat.JPEG, 82, f);
        }
        thumb.recycle();
        out.recycle();
        return id;
    }

    // ---------- Cargar con efecto ----------

    static Loaded cached(String id, int effect) {
        String key = id + "/" + effect;
        if (key.equals(cacheKey) && cache != null && !cache.bitmap.isRecycled()) return cache;
        return null;
    }

    static void load(Context c, String id, int effect, Done<Loaded> done) {
        Loaded hit = cached(id, effect);
        if (hit != null) {
            done.done(hit);
            return;
        }
        Context app = c.getApplicationContext();
        worker.execute(() -> {
            Loaded result = null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap base = BitmapFactory.decodeFile(file(app, id).getPath(), o);
                if (base != null) {
                    Palette pal = Palette.of(base);
                    Bitmap fx = effect == 0 ? base : apply(effect, base, pal);
                    if (fx != base) base.recycle();
                    result = new Loaded(fx, effect == 1 || effect == 2 ? Palette.of(fx) : pal);
                }
            } catch (Throwable e) {
                result = null;
            }
            final Loaded r = result;
            main.post(() -> {
                if (r != null) {
                    // La foto anterior no se recicla aquí: puede estar en pleno fundido
                    cache = r;
                    cacheKey = id + "/" + effect;
                }
                done.done(r);
            });
        });
    }

    static Bitmap thumb(Context c, String id) {
        Bitmap b = thumbs.get(id);
        if (b != null && !b.isRecycled()) return b;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        b = BitmapFactory.decodeFile(thumbFile(c, id).getPath(), o);
        if (b != null) thumbs.put(id, b);
        return b;
    }

    /** Miniatura de una escena animada, dibujada una vez y guardada en memoria. */
    static Bitmap sceneThumb(Context c, int scene) {
        String key = "scene_" + scene;
        Bitmap b = thumbs.get(key);
        if (b != null && !b.isRecycled()) return b;
        int[] t = target(c);
        int w = 240, h = Math.round(240f * t[1] / t[0]);
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Scenes.Scene s = Scenes.create(scene);
        s.size(w, h);
        s.draw(new Canvas(b), 14f);
        thumbs.put(key, b);
        return b;
    }

    // ---------- Efectos ----------

    private static Bitmap apply(int effect, Bitmap src, Palette pal) {
        switch (effect) {
            case 1: return halftone(src, pal);
            case 2: return duotone(src, pal);
            default: return blur(src);
        }
    }

    /** Tu foto hecha de puntos, como en los videos: más grandes donde hay más luz. */
    private static Bitmap halftone(Bitmap src, Palette pal) {
        int w = src.getWidth(), h = src.getHeight();
        int cols = 92;
        float cell = w / (float) cols;
        int rows = (int) Math.ceil(h / cell);
        Bitmap small = Bitmap.createScaledBitmap(src, cols, rows, true);
        int[] px = new int[cols * rows];
        small.getPixels(px, 0, cols, 0, 0, cols, rows);
        small.recycle();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float[] hsl = new float[3];
        Palette.toHsl((pal.dominant >> 16) & 0xFF, (pal.dominant >> 8) & 0xFF, pal.dominant & 0xFF, hsl);
        c.drawColor(Palette.fromHsl(hsl[0], Math.min(hsl[1], 0.3f), 0.04f));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFF0F1F3);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int v = px[y * cols + x];
                float lum = ((v >> 16 & 0xFF) * 0.2126f + (v >> 8 & 0xFF) * 0.7152f + (v & 0xFF) * 0.0722f) / 255f;
                lum = (float) Math.pow(lum, 1.15);
                float r = cell * 0.56f * lum;
                if (r < 0.35f) continue;
                c.drawCircle(x * cell + cell / 2f, y * cell + cell / 2f, r, p);
            }
        }
        return out;
    }

    /** Dos tonos sacados de la misma foto: sombras oscuras y luces de su color más vivo. */
    private static Bitmap duotone(Bitmap src, Palette pal) {
        int w = src.getWidth(), h = src.getHeight();
        float[] hd = new float[3], hv = new float[3];
        Palette.toHsl((pal.dominant >> 16) & 0xFF, (pal.dominant >> 8) & 0xFF, pal.dominant & 0xFF, hd);
        Palette.toHsl((pal.vibrant >> 16) & 0xFF, (pal.vibrant >> 8) & 0xFF, pal.vibrant & 0xFF, hv);
        int dark = Palette.fromHsl(hd[0], Math.min(0.6f, hd[1] + 0.2f), 0.06f);
        int light = hv[1] < 0.15f ? 0xFFEDEEF0 : Palette.fromHsl(hv[0], Math.min(0.8f, hv[1]), 0.8f);
        int[] lut = new int[256];
        for (int k = 0; k < 256; k++) {
            float f = k / 255f;
            f = f * f * (3 - 2 * f);  // más contraste en los medios tonos
            lut[k] = Scenes.lerp(dark, light, f);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int v = row[x];
                int lum = (int) ((v >> 16 & 0xFF) * 0.2126f + (v >> 8 & 0xFF) * 0.7152f + (v & 0xFF) * 0.0722f);
                row[x] = lut[Math.min(255, lum)];
            }
            out.setPixels(row, 0, w, 0, y, w, 1);
        }
        return out;
    }

    /** Desenfoque suave: se reduce, se difumina con caja triple y se vuelve a agrandar. */
    private static Bitmap blur(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int sw = Math.max(8, w / 8), sh = Math.max(8, h / 8);
        Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        small.recycle();
        for (int pass = 0; pass < 3; pass++) boxBlur(px, sw, sh, 5);
        Bitmap b = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888);
        Bitmap out = Bitmap.createScaledBitmap(b, w, h, true);
        b.recycle();
        return out;
    }

    static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int ra = 0, ga = 0, ba = 0;
            for (int i = -r; i <= r; i++) {
                int v = px[y * w + Math.max(0, Math.min(w - 1, i))];
                ra += v >> 16 & 0xFF;
                ga += v >> 8 & 0xFF;
                ba += v & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                tmp[y * w + x] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int out = px[y * w + Math.max(0, x - r)], in = px[y * w + Math.min(w - 1, x + r + 1)];
                ra += (in >> 16 & 0xFF) - (out >> 16 & 0xFF);
                ga += (in >> 8 & 0xFF) - (out >> 8 & 0xFF);
                ba += (in & 0xFF) - (out & 0xFF);
            }
        }
        for (int x = 0; x < w; x++) {
            int ra = 0, ga = 0, ba = 0;
            for (int i = -r; i <= r; i++) {
                int v = tmp[Math.max(0, Math.min(h - 1, i)) * w + x];
                ra += v >> 16 & 0xFF;
                ga += v >> 8 & 0xFF;
                ba += v & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                px[y * w + x] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int out = tmp[Math.max(0, y - r) * w + x], in = tmp[Math.min(h - 1, y + r + 1) * w + x];
                ra += (in >> 16 & 0xFF) - (out >> 16 & 0xFF);
                ga += (in >> 8 & 0xFF) - (out >> 8 & 0xFF);
                ba += (in & 0xFF) - (out & 0xFF);
            }
        }
    }
}
