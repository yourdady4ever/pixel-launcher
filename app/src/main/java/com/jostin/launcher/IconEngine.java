package com.jostin.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Xml;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Íconos de apps: estilo, forma, paquetes de íconos, íconos y nombres personalizados. */
final class IconEngine {
    static final String[] SHAPES = {"Sistema", "Círculo", "Squircle", "Redondeado", "Gota"};
    static final String[] PACK_ACTIONS = {"org.adw.launcher.THEMES", "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme", "com.teslacoilsw.launcher.THEME", "com.anddoes.launcher.THEME"};

    private IconEngine() {}

    // ---------- Ícono final ----------

    /** El dibujo del ícono. Si devuelve forma propia, bind() le aplica el recorte. */
    static Drawable icon(Context c, String pkg) {
        Drawable custom = customIcon(c, pkg);
        if (custom != null) return custom;
        Drawable pack = Pack.get(c, pkg);
        if (pack != null) return pack;
        int style = Prefs.iconStyle(c), shape = Prefs.iconShape(c);
        if (style == 1) {
            Drawable d;
            try {
                d = c.getPackageManager().getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
            return shape == 0 ? d : new FullBleed(d);
        }
        return Icons.app(c, pkg, style, shape != 0);
    }

    /** ¿A este ícono hay que recortarlo con la forma elegida? */
    static boolean clipped(Context c, String pkg) {
        if (Prefs.iconShape(c) == 0) return customFile(c, pkg).exists();
        return Pack.get(c, pkg) == null || customFile(c, pkg).exists();
    }

    /** Pone el ícono en la vista con su forma. */
    static void bind(ImageView iv, String pkg) {
        iv.setImageDrawable(icon(iv.getContext(), pkg));
        clip(iv, pkg);
    }

    /** Solo aplica la forma (cuando el ícono ya está puesto). */
    static void clip(ImageView iv, String pkg) {
        Context c = iv.getContext();
        if (clipped(c, pkg)) {
            int shape = Prefs.iconShape(c);
            iv.setOutlineProvider(outline(shape == 0 ? 1 : shape));
            iv.setClipToOutline(true);
        } else {
            iv.setClipToOutline(false);
            iv.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }
    }

    static ViewOutlineProvider outline(int shape) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                int w = v.getWidth() - v.getPaddingLeft() - v.getPaddingRight();
                int h = v.getHeight() - v.getPaddingTop() - v.getPaddingBottom();
                int l = v.getPaddingLeft(), t = v.getPaddingTop();
                if (w <= 0 || h <= 0) return;
                switch (shape) {
                    case 1:
                        o.setOval(l, t, l + w, t + h);
                        break;
                    case 3:
                        o.setRoundRect(l, t, l + w, t + h, w * 0.24f);
                        break;
                    default:
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            Path p = shape == 2 ? squircle(w, h) : drop(w, h);
                            p.offset(l, t);
                            o.setPath(p);
                        } else {
                            o.setRoundRect(l, t, l + w, t + h, w * 0.32f);
                        }
                        break;
                }
            }
        };
    }

    static Path squircle(float w, float h) {
        Path p = new Path();
        int n = 72;
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            double cos = Math.cos(a), sin = Math.sin(a);
            float x = (float) (Math.signum(cos) * Math.pow(Math.abs(cos), 0.5)) * w / 2 + w / 2;
            float y = (float) (Math.signum(sin) * Math.pow(Math.abs(sin), 0.5)) * h / 2 + h / 2;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    static Path drop(float w, float h) {
        Path p = new Path();
        float r = w / 2f;
        p.moveTo(0, 0);
        p.lineTo(r, 0);
        p.arcTo(0, 0, w, h, 270, 270, false);
        p.close();
        return p;
    }

    // ---------- Nombres ----------

    static String label(Context c, String pkg) {
        String custom = Prefs.sp(c).getString("label_" + pkg, null);
        if (custom != null && !custom.isEmpty()) return custom;
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    static void setLabel(Context c, String pkg, String label) {
        if (label == null || label.trim().isEmpty()) Prefs.sp(c).edit().remove("label_" + pkg).apply();
        else Prefs.sp(c).edit().putString("label_" + pkg, label.trim()).apply();
    }

    // ---------- Ícono personalizado (de la galería) ----------

    private static final Map<String, Drawable> customCache = new HashMap<>();

    static File customFile(Context c, String pkg) {
        File d = new File(c.getFilesDir(), "icons");
        if (!d.exists()) d.mkdirs();
        return new File(d, pkg + ".png");
    }

    private static synchronized Drawable customIcon(Context c, String pkg) {
        if (customCache.containsKey(pkg)) return customCache.get(pkg);
        File f = customFile(c, pkg);
        Drawable d = null;
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) d = new BitmapDrawable(c.getResources(), b);
        }
        customCache.put(pkg, d);
        return d;
    }

    static synchronized void clearCustom(Context c, String pkg) {
        customFile(c, pkg).delete();
        customCache.remove(pkg);
    }

    private static synchronized void forget(String pkg) {
        customCache.remove(pkg);
    }

    /** Recorta la foto al centro en cuadrado de 192 px y la guarda como ícono de esa app. */
    static boolean importCustom(Context c, Uri uri, String pkg) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int s = 1;
            while (Math.min(o.outWidth, o.outHeight) / (s * 2) >= 192) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            Bitmap raw;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                raw = BitmapFactory.decodeStream(in, null, d);
            }
            if (raw == null) return false;
            int side = Math.min(raw.getWidth(), raw.getHeight());
            Bitmap out = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            Matrix m = new Matrix();
            float sc = 192f / side;
            m.postTranslate(-(raw.getWidth() - side) / 2f, -(raw.getHeight() - side) / 2f);
            m.postScale(sc, sc);
            cv.drawBitmap(raw, m, new Paint(Paint.FILTER_BITMAP_FLAG));
            raw.recycle();
            try (FileOutputStream f = new FileOutputStream(customFile(c, pkg))) {
                out.compress(Bitmap.CompressFormat.PNG, 100, f);
            }
            out.recycle();
            forget(pkg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- Ícono original a sangre (para recortarlo con otra forma) ----------

    static final class FullBleed extends Drawable {
        private final Drawable src;
        private final Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect r = new Rect();

        FullBleed(Drawable src) {
            this.src = src;
            plate.setColor(0xFFF2F3F5);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            if (src instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable a = (AdaptiveIconDrawable) src;
                int ex = Math.round(b.width() * 0.25f);
                r.set(b.left - ex, b.top - ex, b.right + ex, b.bottom + ex);
                Drawable bg = a.getBackground(), fg = a.getForeground();
                if (bg != null) {
                    bg.setBounds(r);
                    bg.draw(c);
                }
                if (fg != null) {
                    fg.setBounds(r);
                    fg.draw(c);
                }
            } else {
                c.drawRect(b, plate);
                int in = Math.round(b.width() * 0.16f);
                r.set(b.left + in, b.top + in, b.right - in, b.bottom - in);
                src.setBounds(r);
                src.draw(c);
            }
        }

        @Override public void setAlpha(int a) { src.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { src.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---------- Apps más usadas ----------

    static List<String> suggestions(Context c, int n, Set<String> exclude) {
        Map<String, Integer> counts = Prefs.useCounts(c);
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        for (Map.Entry<String, Integer> e : list) {
            if (out.size() >= n) break;
            String pkg = e.getKey();
            if (exclude.contains(pkg) || pm.getLaunchIntentForPackage(pkg) == null) continue;
            out.add(pkg);
        }
        return out;
    }

    // ---------- Paquetes de íconos (formato ADW / Nova) ----------

    static final class Pack {
        private static String loaded;
        private static Map<String, String> map;
        private static Resources res;
        private static final Map<String, Drawable> cache = new HashMap<>();

        private Pack() {}

        static List<String[]> installed(Context c) {
            PackageManager pm = c.getPackageManager();
            List<String[]> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String action : PACK_ACTIONS) {
                for (ResolveInfo ri : pm.queryIntentActivities(new Intent(action), PackageManager.GET_META_DATA)) {
                    String pkg = ri.activityInfo.packageName;
                    if (!seen.add(pkg)) continue;
                    out.add(new String[]{pkg, String.valueOf(ri.loadLabel(pm))});
                }
            }
            return out;
        }

        static synchronized void reset() {
            loaded = null;
            map = null;
            res = null;
            cache.clear();
        }

        static synchronized Drawable get(Context c, String pkg) {
            String pack = Prefs.iconPack(c);
            if (pack == null) return null;
            if (!pack.equals(loaded)) load(c, pack);
            if (map == null || res == null) return null;
            if (cache.containsKey(pkg)) return cache.get(pkg);
            Drawable d = null;
            try {
                Intent li = c.getPackageManager().getLaunchIntentForPackage(pkg);
                ComponentName cn = li == null ? null : li.getComponent();
                if (cn != null) {
                    String name = map.get("ComponentInfo{" + cn.getPackageName() + "/" + cn.getClassName() + "}");
                    if (name != null) {
                        int id = res.getIdentifier(name, "drawable", pack);
                        if (id != 0) d = res.getDrawable(id, null);
                    }
                }
            } catch (Exception ignored) {
                d = null;
            }
            cache.put(pkg, d);
            return d;
        }

        private static void load(Context c, String pack) {
            loaded = pack;
            map = new HashMap<>();
            cache.clear();
            try {
                res = c.getPackageManager().getResourcesForApplication(pack);
                XmlPullParser parser;
                int id = res.getIdentifier("appfilter", "xml", pack);
                InputStream in = null;
                if (id != 0) {
                    parser = res.getXml(id);
                } else {
                    in = res.getAssets().open("appfilter.xml");
                    parser = Xml.newPullParser();
                    parser.setInput(in, "UTF-8");
                }
                int ev = parser.getEventType();
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && "item".equals(parser.getName())) {
                        String comp = parser.getAttributeValue(null, "component");
                        String draw = parser.getAttributeValue(null, "drawable");
                        if (comp != null && draw != null) map.put(comp, draw);
                    }
                    ev = parser.next();
                }
                if (in != null) in.close();
            } catch (Exception e) {
                map = null;
                res = null;
            }
        }
    }
}
