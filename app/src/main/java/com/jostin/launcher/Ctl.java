package com.jostin.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Controles del estudio de personalización: chips, interruptores, deslizadores y colores. */
final class Ctl {
    interface IntListener {
        void on(int value);
    }

    interface BoolListener {
        void on(boolean value);
    }

    private Ctl() {}

    static TextView section(Context c, String title) {
        TextView t = Ui.label(c, title);
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 20), 0, Ui.dp(c, 10));
        return t;
    }

    // ---------- Chips ----------

    static View chips(Context c, String[] options, int selected, IntListener l) {
        return chips(c, options, null, selected, l);
    }

    static View chips(Context c, String[] options, Typeface[] fonts, int selected, IntListener l) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] all = new TextView[options.length];
        final int[] sel = {selected};
        for (int i = 0; i < options.length; i++) {
            final int k = i;
            TextView t = Ui.text(c, options[i], 14, Ui.TEXT, fonts != null ? fonts[i] : Ui.MEDIUM);
            t.setGravity(Gravity.CENTER);
            int ph = Ui.dp(c, 16), pv = Ui.dp(c, 10);
            t.setPadding(ph, pv, ph, pv);
            Ui.pressable(t, 0.93f);
            t.setOnClickListener(v -> {
                if (sel[0] == k) return;
                Sound.play(v, Sound.TOGGLE);
                sel[0] = k;
                for (int j = 0; j < all.length; j++) paintChip(c, all[j], j == k);
                l.on(k);
            });
            all[i] = t;
            paintChip(c, t, i == selected);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = Ui.dp(c, 8);
            row.addView(t, lp);
        }
        hs.addView(row);
        return hs;
    }

    private static void paintChip(Context c, TextView t, boolean on) {
        t.setBackground(on ? Ui.fill(c, Theme.accent, 16) : Ui.outline(c, Ui.SOFT, 0x14FFFFFF, 16));
        t.setTextColor(on ? Theme.onAccent : Ui.TEXT);
    }

    // ---------- Interruptor ----------

    static View toggle(Context c, String label, String sub, boolean on, BoolListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 10));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(c, label, 16, Ui.TEXT, Ui.MEDIUM));
        if (sub != null) {
            TextView s = Ui.text(c, sub, 13, Ui.MUTED, Ui.REGULAR);
            s.setLineSpacing(0, 1.15f);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.dp(c, 4);
            texts.addView(s, slp);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(c, on);
        LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 30));
        swl.leftMargin = Ui.dp(c, 12);
        row.addView(sw, swl);
        row.setOnClickListener(v -> {
            boolean now = !sw.on;
            Sound.play(v, Sound.TOGGLE);
            sw.set(now);
            l.on(now);
        });
        return row;
    }

    static final class Switch extends View {
        boolean on;
        private float p;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);

        Switch(Context c, boolean on) {
            super(c);
            this.on = on;
            p = on ? 1f : 0f;
        }

        void set(boolean v) {
            on = v;
            ValueAnimator a = ValueAnimator.ofFloat(p, v ? 1f : 0f);
            a.setDuration(320);
            a.setInterpolator(new OvershootInterpolator(1.6f));
            a.addUpdateListener(x -> {
                p = (float) x.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            float f = Math.max(0f, Math.min(1f, p));
            track.setColor(Scenes.lerp(0xFF3A3D44, Theme.accent, f));
            c.drawRoundRect(0, 0, w, h, r, r, track);
            float kr = r - Ui.dp(getContext(), 3);
            float cx = r + (w - 2 * r) * p;
            knob.setColor(Scenes.lerp(0xFFE6E7EA, Theme.onAccent == Ui.INK ? 0xFF0E0F12 : 0xFFFFFFFF, f));
            c.drawCircle(cx, r, kr, knob);
        }
    }

    // ---------- Deslizador ----------

    interface SliderListener {
        void on(int value, boolean done);
    }

    static View slider(Context c, String label, int min, int max, int value, String unit, SliderListener l) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 4));
        LinearLayout head = new LinearLayout(c);
        head.addView(Ui.text(c, label, 16, Ui.TEXT, Ui.MEDIUM), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = Ui.text(c, value + unit, 14, Ui.MUTED, Ui.MEDIUM);
        head.addView(val);
        col.addView(head);
        Slider s = new Slider(c, min, max, value, (v, done) -> {
            val.setText(v + unit);
            l.on(v, done);
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 34));
        slp.topMargin = Ui.dp(c, 4);
        col.addView(s, slp);
        return col;
    }

    static final class Slider extends View {
        private final int min, max;
        private int value;
        private final SliderListener l;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int lastTick = -1;

        Slider(Context c, int min, int max, int value, SliderListener l) {
            super(c);
            this.min = min;
            this.max = max;
            this.value = value;
            this.l = l;
            track.setColor(0x33FFFFFF);
            knob.setColor(0xFFF5F6F8);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            float pad = getHeight() / 2f;
            float f = Math.max(0f, Math.min(1f, (e.getX() - pad) / Math.max(1f, getWidth() - 2 * pad)));
            int v = Math.round(min + f * (max - min));
            boolean done = a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL;
            if (v != value || done) {
                value = v;
                // Un tic cada 10% para sentir el recorrido
                int tick = (int) (f * 10);
                if (tick != lastTick) {
                    lastTick = tick;
                    if (Prefs.haptics(getContext())) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                }
                l.on(value, done);
                invalidate();
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), w = getWidth(), cy = h / 2f, pad = h / 2f;
            float th = Ui.dp(getContext(), 5);
            float f = (value - min) / (float) Math.max(1, max - min);
            float x = pad + (w - 2 * pad) * f;
            c.drawRoundRect(pad, cy - th / 2, w - pad, cy + th / 2, th / 2, th / 2, track);
            fill.setColor(Theme.accent);
            c.drawRoundRect(pad, cy - th / 2, x, cy + th / 2, th / 2, th / 2, fill);
            c.drawCircle(x, cy, Ui.dp(getContext(), 11), knob);
        }
    }

    // ---------- Colores ----------

    static View swatches(Context c, int[] colors, int selected, IntListener l) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        Dot[] dots = new Dot[colors.length];
        for (int i = 0; i < colors.length; i++) {
            final int k = i;
            Dot d = new Dot(c, colors[i], colors[i] == selected);
            dots[i] = d;
            Ui.pressable(d, 0.85f);
            d.setOnClickListener(v -> {
                Sound.play(v, Sound.TOGGLE);
                for (Dot o : dots) o.setSel(o == d);
                l.on(colors[k]);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40));
            if (i > 0) lp.leftMargin = Ui.dp(c, 8);
            row.addView(d, lp);
        }
        hs.addView(row);
        return hs;
    }

    static final class Dot extends View {
        private final int color;
        private boolean sel;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Dot(Context c, int color, boolean sel) {
            super(c);
            this.color = color;
            this.sel = sel;
        }

        void setSel(boolean s) {
            sel = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = getWidth() / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawCircle(r, r, sel ? r - Ui.dp(getContext(), 5) : r - Ui.dp(getContext(), 2), p);
            if (sel) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 2));
                p.setColor(0xFFFFFFFF);
                c.drawCircle(r, r, r - Ui.dp(getContext(), 1.2f), p);
            }
        }
    }

    // ---------- Fila con acción ----------

    static View action(Context c, Icons.Shape icon, String label, String value, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(c, 12);
        row.setPadding(p, p, Ui.dp(c, 16), p);
        row.setBackground(Ui.outline(c, Ui.SOFT, 0x14FFFFFF, 18));
        if (icon != null) {
            android.widget.ImageView iv = new android.widget.ImageView(c);
            iv.setImageDrawable(Icons.of(icon));
            row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        }
        TextView t = Ui.text(c, label, 15, Ui.TEXT, Ui.MEDIUM);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(c, 12);
        row.addView(t, tl);
        if (value != null) row.addView(Ui.text(c, value, 14, Ui.MUTED, Ui.REGULAR));
        Ui.pressable(row, 0.97f);
        row.setOnClickListener(v -> {
            Sound.play(v, Sound.TAP);
            l.onClick(v);
        });
        return row;
    }
}
