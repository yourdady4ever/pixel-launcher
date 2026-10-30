package com.jostin.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/** Íconos de línea dibujados con código y los íconos de apps en versión monocroma. */
final class Icons {
    private Icons() {}

    interface Shape {
        void build(Path p);
    }

    /** Ícono de trazo fino en una cuadrícula de 24x24, como los de un sistema operativo. */
    static final class Line extends Drawable {
        private final Shape shape;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path scaled = new Path();
        private final android.graphics.Matrix m = new android.graphics.Matrix();

        Line(Shape shape, int color, float stroke) {
            this.shape = shape;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
            paint.setStrokeWidth(stroke);
            shape.build(path);
        }

        Line(Shape shape, int color) {
            this(shape, color, 1.6f);
        }

        void setColor(int color) {
            paint.setColor(color);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / 24f;
            c.save();
            c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
            c.scale(s, s);
            c.drawPath(path, paint);
            c.restore();
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return -1; }
    }

    static Line of(Shape s) {
        return new Line(s, Ui.TEXT);
    }

    static final Shape CALC = p -> {
        p.addRoundRect(new RectF(5.5f, 3f, 18.5f, 21f), 2.6f, 2.6f, Path.Direction.CW);
        p.addRoundRect(new RectF(8.5f, 6f, 15.5f, 9.2f), 0.8f, 0.8f, Path.Direction.CW);
        float[][] dots = {{9f, 13f}, {12f, 13f}, {15f, 13f}, {9f, 17f}, {12f, 17f}, {15f, 17f}};
        for (float[] d : dots) p.addCircle(d[0], d[1], 0.25f, Path.Direction.CW);
    };

    static final Shape FOCUS = p -> {
        p.addCircle(12, 13, 7.5f, Path.Direction.CW);
        p.moveTo(12, 9.5f);
        p.lineTo(12, 13);
        p.lineTo(14.5f, 14.8f);
        p.moveTo(9.8f, 2.8f);
        p.lineTo(14.2f, 2.8f);
    };

    static final Shape TASKS = p -> {
        p.moveTo(4.2f, 6.6f);
        p.lineTo(5.6f, 8f);
        p.lineTo(8.2f, 5.2f);
        p.moveTo(11.5f, 6.8f);
        p.lineTo(20f, 6.8f);
        p.addCircle(6.2f, 12.2f, 1.6f, Path.Direction.CW);
        p.moveTo(11.5f, 12.2f);
        p.lineTo(20f, 12.2f);
        p.addCircle(6.2f, 17.6f, 1.6f, Path.Direction.CW);
        p.moveTo(11.5f, 17.6f);
        p.lineTo(17f, 17.6f);
    };

    static final Shape SPARK = p -> {
        p.moveTo(11f, 3.5f);
        p.cubicTo(11.6f, 8.6f, 13.4f, 10.4f, 18.5f, 11f);
        p.cubicTo(13.4f, 11.6f, 11.6f, 13.4f, 11f, 18.5f);
        p.cubicTo(10.4f, 13.4f, 8.6f, 11.6f, 3.5f, 11f);
        p.cubicTo(8.6f, 10.4f, 10.4f, 8.6f, 11f, 3.5f);
        p.close();
        p.moveTo(18.5f, 16.5f);
        p.lineTo(18.5f, 20.5f);
        p.moveTo(16.5f, 18.5f);
        p.lineTo(20.5f, 18.5f);
    };

    static final Shape BACK = p -> {
        p.moveTo(14.5f, 5.5f);
        p.lineTo(8f, 12f);
        p.lineTo(14.5f, 18.5f);
    };

    static final Shape SEARCH = p -> {
        p.addCircle(10.8f, 10.8f, 6.3f, Path.Direction.CW);
        p.moveTo(15.4f, 15.4f);
        p.lineTo(19.8f, 19.8f);
    };

    static final Shape GRID = p -> {
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++) p.addCircle(6 + x * 6, 6 + y * 6, 0.35f, Path.Direction.CW);
    };

    static final Shape PLUS = p -> {
        p.moveTo(12, 5.5f);
        p.lineTo(12, 18.5f);
        p.moveTo(5.5f, 12);
        p.lineTo(18.5f, 12);
    };

    static final Shape CHECK = p -> {
        p.moveTo(5.5f, 12.5f);
        p.lineTo(10f, 17f);
        p.lineTo(18.5f, 7.5f);
    };

    static final Shape TRASH = p -> {
        p.moveTo(4.5f, 7f);
        p.lineTo(19.5f, 7f);
        p.moveTo(9.5f, 7f);
        p.lineTo(9.5f, 4.5f);
        p.lineTo(14.5f, 4.5f);
        p.lineTo(14.5f, 7f);
        p.moveTo(6.5f, 7f);
        p.lineTo(7.4f, 19.2f);
        p.quadTo(7.5f, 20f, 8.3f, 20f);
        p.lineTo(15.7f, 20f);
        p.quadTo(16.5f, 20f, 16.6f, 19.2f);
        p.lineTo(17.5f, 7f);
    };

    static final Shape HISTORY = p -> {
        p.addArc(new RectF(4f, 4f, 20f, 20f), 200, 290);
        p.moveTo(4.2f, 7.5f);
        p.lineTo(4.8f, 11.2f);
        p.lineTo(8.4f, 10.2f);
        p.moveTo(12, 8);
        p.lineTo(12, 12.2f);
        p.lineTo(14.8f, 14f);
    };

    static final Shape BACKSPACE = p -> {
        p.moveTo(9f, 5.5f);
        p.lineTo(19f, 5.5f);
        p.quadTo(20.5f, 5.5f, 20.5f, 7f);
        p.lineTo(20.5f, 17f);
        p.quadTo(20.5f, 18.5f, 19f, 18.5f);
        p.lineTo(9f, 18.5f);
        p.lineTo(3.2f, 12f);
        p.close();
        p.moveTo(11.5f, 9.5f);
        p.lineTo(16.5f, 14.5f);
        p.moveTo(16.5f, 9.5f);
        p.lineTo(11.5f, 14.5f);
    };

    static final Shape SOUND = p -> {
        p.moveTo(4f, 9.5f);
        p.lineTo(7.5f, 9.5f);
        p.lineTo(12f, 5.5f);
        p.lineTo(12f, 18.5f);
        p.lineTo(7.5f, 14.5f);
        p.lineTo(4f, 14.5f);
        p.close();
        p.moveTo(15.5f, 9f);
        p.quadTo(17.3f, 12f, 15.5f, 15f);
        p.moveTo(18.2f, 6.5f);
        p.quadTo(21.8f, 12f, 18.2f, 17.5f);
    };

    static final Shape WAVES = p -> {
        for (int i = 0; i < 3; i++) {
            float y = 7.5f + i * 4.5f;
            p.moveTo(3.5f, y);
            p.cubicTo(6.5f, y - 2.5f, 8.5f, y + 2.5f, 12f, y);
            p.cubicTo(15.5f, y - 2.5f, 17.5f, y + 2.5f, 20.5f, y);
        }
    };

    static final Shape IMAGE = p -> {
        p.addRoundRect(new RectF(3.5f, 5f, 20.5f, 19f), 2.4f, 2.4f, Path.Direction.CW);
        p.moveTo(4f, 17f);
        p.lineTo(9.2f, 11.6f);
        p.lineTo(12.6f, 15f);
        p.lineTo(15f, 12.8f);
        p.lineTo(20f, 17.5f);
        p.addCircle(15.5f, 9f, 1.3f, Path.Direction.CW);
    };

    static final Shape SHAPES = p -> {
        p.addRoundRect(new RectF(4f, 4f, 10.8f, 10.8f), 2f, 2f, Path.Direction.CW);
        p.addRoundRect(new RectF(13.2f, 4f, 20f, 10.8f), 2f, 2f, Path.Direction.CW);
        p.addRoundRect(new RectF(4f, 13.2f, 10.8f, 20f), 2f, 2f, Path.Direction.CW);
        p.addRoundRect(new RectF(13.2f, 13.2f, 20f, 20f), 2f, 2f, Path.Direction.CW);
    };

    static final Shape HOME = p -> {
        p.moveTo(4f, 11f);
        p.lineTo(12f, 4.5f);
        p.lineTo(20f, 11f);
        p.moveTo(6.5f, 9.5f);
        p.lineTo(6.5f, 19.5f);
        p.lineTo(17.5f, 19.5f);
        p.lineTo(17.5f, 9.5f);
    };

    static final Shape GEAR = p -> {
        p.moveTo(4f, 7f);
        p.lineTo(20f, 7f);
        p.moveTo(4f, 17f);
        p.lineTo(20f, 17f);
        p.addCircle(9f, 7f, 2.2f, Path.Direction.CW);
        p.addCircle(15f, 17f, 2.2f, Path.Direction.CW);
    };

    static final Shape INFO = p -> {
        p.addCircle(12, 12, 8.5f, Path.Direction.CW);
        p.moveTo(12, 11f);
        p.lineTo(12, 16.5f);
        p.addCircle(12, 7.8f, 0.2f, Path.Direction.CW);
    };

    static final Shape SWAP = p -> {
        p.moveTo(5f, 9f);
        p.lineTo(19f, 9f);
        p.moveTo(15.5f, 5.5f);
        p.lineTo(19f, 9f);
        p.lineTo(15.5f, 12.5f);
        p.moveTo(19f, 15f);
        p.lineTo(5f, 15f);
        p.moveTo(8.5f, 11.5f);
        p.lineTo(5f, 15f);
        p.lineTo(8.5f, 18.5f);
    };

    static final Shape CLOSE = p -> {
        p.moveTo(7f, 7f);
        p.lineTo(17f, 17f);
        p.moveTo(17f, 7f);
        p.lineTo(7f, 17f);
    };

    static final Shape BOX = p -> {
        p.moveTo(12f, 3.5f);
        p.lineTo(19.5f, 7.5f);
        p.lineTo(19.5f, 16.5f);
        p.lineTo(12f, 20.5f);
        p.lineTo(4.5f, 16.5f);
        p.lineTo(4.5f, 7.5f);
        p.close();
        p.moveTo(4.5f, 7.5f);
        p.lineTo(12f, 11.5f);
        p.lineTo(19.5f, 7.5f);
        p.moveTo(12f, 11.5f);
        p.lineTo(12f, 20.5f);
    };

    static final Shape WORK = p -> {
        p.addRoundRect(3.5f, 7.5f, 20.5f, 19.5f, 2.5f, 2.5f, Path.Direction.CW);
        p.moveTo(9f, 7.5f);
        p.lineTo(9f, 4.5f);
        p.lineTo(15f, 4.5f);
        p.lineTo(15f, 7.5f);
        p.moveTo(3.5f, 12.5f);
        p.lineTo(20.5f, 12.5f);
    };

    static final Shape LOCK = p -> {
        p.addRoundRect(5f, 10.5f, 19f, 20f, 2.5f, 2.5f, Path.Direction.CW);
        p.moveTo(8f, 10.5f);
        p.lineTo(8f, 8f);
        p.cubicTo(8f, 3.5f, 16f, 3.5f, 16f, 8f);
        p.lineTo(16f, 10.5f);
    };

    static final Shape WALL = p -> {
        p.addRect(3.5f, 5f, 20.5f, 19f, Path.Direction.CW);
        p.moveTo(3.5f, 9.7f);
        p.lineTo(20.5f, 9.7f);
        p.moveTo(3.5f, 14.3f);
        p.lineTo(20.5f, 14.3f);
        p.moveTo(9f, 5f);
        p.lineTo(9f, 9.7f);
        p.moveTo(15f, 5f);
        p.lineTo(15f, 9.7f);
        p.moveTo(12f, 9.7f);
        p.lineTo(12f, 14.3f);
        p.moveTo(9f, 14.3f);
        p.lineTo(9f, 19f);
        p.moveTo(15f, 14.3f);
        p.lineTo(15f, 19f);
    };

    // ---------- Íconos de apps ----------

    /**
     * Ícono de una app dentro de un círculo. En modo monocromo usa la capa monocroma de la app
     * (Android 13+) en blanco sobre vidrio; si la app no la tiene, usa su ícono en gris.
     */
    /** Estilos: 0 monocromo blanco, 1 originales, 2 en el color de acento. */
    static Drawable app(Context c, String pkg, int style) {
        return app(c, pkg, style, false);
    }

    /** fill: el ícono ocupa todo su cuadro porque la vista lo recorta con la forma elegida. */
    static Drawable app(Context c, String pkg, int style, boolean fill) {
        Drawable icon;
        try {
            icon = c.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        if (style == 1) return icon;
        int color = style == 2 ? Theme.accent : 0xFFF2F3F5;
        if (android.os.Build.VERSION.SDK_INT >= 33 && icon instanceof AdaptiveIconDrawable) {
            Drawable m = ((AdaptiveIconDrawable) icon).getMonochrome();
            if (m != null) return new Mono(m.mutate(), color, style == 2, fill);
        }
        Drawable g = icon.mutate();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        g.setColorFilter(new ColorMatrixColorFilter(cm));
        return fill ? new IconEngine.FullBleed(g) : g;
    }

    /** Capa monocroma en blanco sobre un círculo de vidrio claro. */
    static final class Mono extends Drawable {
        private final Drawable layer;
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();

        private final int bgAlpha;
        private final boolean fill;

        Mono(Drawable layer, int color, boolean tinted, boolean fill) {
            this.layer = layer;
            this.fill = fill;
            layer.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            bg.setColor(tinted ? Ui.alpha(color, 0x2E) : 0x24FFFFFF);
            bgAlpha = Ui.alphaOf(bg.getColor());
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(tinted ? Ui.alpha(color, 0x40) : 0x26FFFFFF);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            super.onBoundsChange(b);
            // La capa de un ícono adaptable mide 108 de los que solo se ven 72
            float extra = b.width() * 0.25f;
            layer.setBounds((int) (b.left - extra), (int) (b.top - extra), (int) (b.right + extra), (int) (b.bottom + extra));
            clip.reset();
            if (fill) clip.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
            else clip.addCircle(b.exactCenterX(), b.exactCenterY(), b.width() / 2f, Path.Direction.CW);
            ring.setStrokeWidth(Math.max(1f, b.width() / 60f));
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float r = b.width() / 2f;
            if (fill) c.drawRect(b, bg);
            else c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, bg);
            c.save();
            c.clipPath(clip);
            layer.draw(c);
            c.restore();
            if (!fill) c.drawCircle(b.exactCenterX(), b.exactCenterY(), r - ring.getStrokeWidth() / 2, ring);
        }

        @Override public void setAlpha(int a) { bg.setAlpha(a * bgAlpha / 255); layer.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
