package com.jostin.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Piezas de los widgets: portada giratoria, anillos y tarjeta de música. */
final class Widgets {
    private Widgets() {}

    // ---------- Íconos de reproducción ----------

    static final Icons.Shape PLAY = p -> {
        p.moveTo(8f, 5.5f);
        p.lineTo(18.5f, 12f);
        p.lineTo(8f, 18.5f);
        p.close();
    };
    static final Icons.Shape PAUSE = p -> {
        p.moveTo(9f, 6f);
        p.lineTo(9f, 18f);
        p.moveTo(15f, 6f);
        p.lineTo(15f, 18f);
    };
    static final Icons.Shape NEXT = p -> {
        p.moveTo(6.5f, 6.5f);
        p.lineTo(14.5f, 12f);
        p.lineTo(6.5f, 17.5f);
        p.close();
        p.moveTo(17.5f, 6.5f);
        p.lineTo(17.5f, 17.5f);
    };
    static final Icons.Shape PREV = p -> {
        p.moveTo(17.5f, 6.5f);
        p.lineTo(9.5f, 12f);
        p.lineTo(17.5f, 17.5f);
        p.close();
        p.moveTo(6.5f, 6.5f);
        p.lineTo(6.5f, 17.5f);
    };
    static final Icons.Shape NOTE = p -> {
        p.moveTo(9.5f, 17f);
        p.lineTo(9.5f, 5.5f);
        p.lineTo(18.5f, 4f);
        p.lineTo(18.5f, 15.5f);
        p.addCircle(7.3f, 17.2f, 2.3f, Path.Direction.CW);
        p.addCircle(16.3f, 15.7f, 2.3f, Path.Direction.CW);
    };
    static final Icons.Shape TORCH = p -> {
        p.moveTo(8f, 3.5f);
        p.lineTo(16f, 3.5f);
        p.lineTo(16f, 8f);
        p.lineTo(14f, 11f);
        p.lineTo(14f, 20.5f);
        p.lineTo(10f, 20.5f);
        p.lineTo(10f, 11f);
        p.lineTo(8f, 8f);
        p.close();
        p.moveTo(12f, 13.5f);
        p.lineTo(12f, 15.5f);
    };
    static final Icons.Shape LOCK = p -> {
        p.addRoundRect(new RectF(5.5f, 10.5f, 18.5f, 20.5f), 2.4f, 2.4f, Path.Direction.CW);
        p.moveTo(8.5f, 10.5f);
        p.lineTo(8.5f, 7.5f);
        p.cubicTo(8.5f, 3f, 15.5f, 3f, 15.5f, 7.5f);
        p.lineTo(15.5f, 10.5f);
    };
    static final Icons.Shape BELL = p -> {
        p.moveTo(6f, 16.5f);
        p.lineTo(6f, 11f);
        p.cubicTo(6f, 5f, 18f, 5f, 18f, 11f);
        p.lineTo(18f, 16.5f);
        p.lineTo(19.5f, 18f);
        p.lineTo(4.5f, 18f);
        p.close();
        p.moveTo(10f, 20.5f);
        p.lineTo(14f, 20.5f);
    };

    static ImageView iconButton(Context c, Icons.Shape s, int sizeDp, int padDp, View.OnClickListener l) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(Icons.of(s));
        int p = Ui.dp(c, padDp);
        b.setPadding(p, p, p, p);
        Ui.pressable(b, 0.84f);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)));
        return b;
    }

    // ---------- Portada ----------

    /** Portada redonda que gira lento como disco mientras suena, con barras alrededor. */
    static final class Art extends View {
        private Bitmap art;
        private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private final Icons.Line noteIcon = Icons.of(NOTE);
        private final boolean rays;
        private boolean playing;
        private float angle = 0;
        private long last = 0;
        private final long seed = System.nanoTime();

        Art(Context c, boolean rays) {
            super(c);
            this.rays = rays;
            bar.setStrokeCap(Paint.Cap.ROUND);
            hole.setColor(0xCC0B0C0E);
            bg.setColor(0x26FFFFFF);
        }

        void set(Bitmap b, boolean playing) {
            if (b != art) {
                art = b;
                img.setShader(b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            }
            boolean was = this.playing;
            this.playing = playing;
            if (playing && !was) last = 0;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float r = rays ? Math.min(w, h) * 0.34f : Math.min(w, h) / 2f;
            long now = System.currentTimeMillis();
            if (playing) {
                if (last != 0) angle = (angle + (now - last) * 0.012f) % 360f;
                last = now;
            }
            if (rays && playing) {
                // Barras decorativas que respiran mientras hay música
                int n = 48;
                bar.setColor(Ui.alpha(Theme.accent, 0xCC));
                bar.setStrokeWidth(Math.max(2f, r * 0.05f));
                float t = (now % 100000) / 1000f;
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    float v = (float) (0.5 + 0.25 * Math.sin(t * 3.1 + i * 0.7 + seed % 7)
                            + 0.25 * Math.sin(t * 5.3 + i * 1.9));
                    float r1 = r * 1.12f, r2 = r1 + r * 0.32f * v;
                    c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                            cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, bar);
                }
            }
            if (art != null) {
                float s = 2 * r / Math.min(art.getWidth(), art.getHeight());
                m.setScale(s, s);
                m.postTranslate(cx - art.getWidth() * s / 2f, cy - art.getHeight() * s / 2f);
                m.postRotate(angle, cx, cy);
                img.getShader().setLocalMatrix(m);
                c.drawCircle(cx, cy, r, img);
                if (rays) c.drawCircle(cx, cy, r * 0.12f, hole);
            } else {
                c.drawCircle(cx, cy, r, bg);
                int ip = (int) (r * 0.5f);
                noteIcon.setBounds((int) cx - ip, (int) cy - ip, (int) cx + ip, (int) cy + ip);
                noteIcon.draw(c);
            }
            if (playing && isShown()) postInvalidateDelayed(33);
        }
    }

    // ---------- Anillo ----------

    static final class Ring extends View {
        private float value;
        private String center = "", label = "";
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();

        Ring(Context c) {
            super(c);
            track.setStyle(Paint.Style.STROKE);
            track.setColor(0x26FFFFFF);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            text.setColor(Ui.TEXT);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Ui.MEDIUM);
            small.setColor(Ui.MUTED);
            small.setTextAlign(Paint.Align.CENTER);
            small.setTypeface(Ui.SEMIBOLD);
            small.setLetterSpacing(0.12f);
        }

        void set(float v, String center, String label) {
            value = Math.max(0f, Math.min(1f, v));
            this.center = center;
            this.label = label;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float sw = Ui.dp(getContext(), 4.5f);
            float d = Math.min(w, getHeight() - Ui.dp(getContext(), 20));
            float l = (w - d) / 2f + sw;
            oval.set(l, sw, l + d - 2 * sw, d - sw);
            track.setStrokeWidth(sw);
            arc.setStrokeWidth(sw);
            arc.setColor(Theme.accent);
            c.drawArc(oval, 0, 360, false, track);
            c.drawArc(oval, -90, 360 * value, false, arc);
            text.setTextSize(d * 0.24f);
            c.drawText(center, w / 2f, oval.centerY() + text.getTextSize() * 0.35f, text);
            small.setTextSize(Ui.dp(getContext(), 10));
            c.drawText(label, w / 2f, getHeight() - Ui.dp(getContext(), 4), small);
        }
    }

    // ---------- Tarjeta de música para inicio ----------

    static final class MusicCard extends LinearLayout implements Media.Listener {
        private final Art art;
        private final TextView title, artist;
        private final ImageView play;

        MusicCard(Activity a, Backdrop b) {
            super(a);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            int p = Ui.dp(a, 10);
            setPadding(p, p, Ui.dp(a, 8), p);
            Glass.apply(this, b, 24);
            art = new Art(a, false);
            addView(art, new LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48)));
            LinearLayout texts = new LinearLayout(a);
            texts.setOrientation(VERTICAL);
            title = Ui.text(a, "", 15, Ui.TEXT, Ui.SEMIBOLD);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            artist = Ui.text(a, "", 12, Ui.MUTED, Ui.REGULAR);
            artist.setMaxLines(1);
            artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(title);
            LayoutParams alp = new LayoutParams(-2, -2);
            alp.topMargin = Ui.dp(a, 4);
            texts.addView(artist, alp);
            LayoutParams tl = new LayoutParams(0, -2, 1f);
            tl.leftMargin = Ui.dp(a, 12);
            addView(texts, tl);
            addView(iconButton(a, PREV, 40, 10, v -> {
                Sound.play(v, Sound.TAP);
                Media.previous();
            }));
            play = iconButton(a, PLAY, 44, 11, v -> {
                Sound.play(v, Sound.TAP);
                Media.playPause();
            });
            addView(play);
            addView(iconButton(a, NEXT, 40, 10, v -> {
                Sound.play(v, Sound.TAP);
                Media.next();
            }));
            Ui.pressable(this, 0.97f);
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                Media.open(a);
            });
        }

        @Override
        public void onMediaChanged() {
            boolean show = Prefs.showMusic(getContext()) && Media.active();
            setVisibility(show ? VISIBLE : GONE);
            if (!show) {
                art.set(null, false);
                return;
            }
            title.setText(Media.title());
            artist.setText(Media.artist());
            art.set(Media.art(), Media.playing());
            play.setImageDrawable(Icons.of(Media.playing() ? PAUSE : PLAY));
        }
    }
}
