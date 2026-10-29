package com.jostin.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Selector de opciones con una pastilla blanca que se desliza a la opción elegida. */
class Segmented extends FrameLayout {
    interface Listener {
        void onSelect(int index);
    }

    private final View pill;
    private final TextView[] labels;
    private int selected = 0;
    private Listener listener;
    private final int pad;

    Segmented(Context c, Backdrop b, String... options) {
        super(c);
        pad = Ui.dp(c, 4);
        Glass.apply(this, b, 20);
        setPadding(pad, pad, pad, pad);

        pill = new View(c);
        pill.setBackground(Ui.fill(c, 0xFFF5F6F8, 16));
        addView(pill, new LayoutParams(0, -1));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        labels = new TextView[options.length];
        for (int i = 0; i < options.length; i++) {
            final int k = i;
            TextView t = Ui.text(c, options[i], 14, Ui.TEXT, Ui.SEMIBOLD);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> {
                if (k == selected) return;
                Sound.play(v, Sound.TOGGLE);
                select(k, true);
                if (listener != null) listener.onSelect(k);
            });
            row.addView(t, new LinearLayout.LayoutParams(0, -1, 1f));
            labels[i] = t;
        }
        addView(row, new LayoutParams(-1, -1));
        select(0, false);
    }

    void setListener(Listener l) {
        listener = l;
    }

    int selected() {
        return selected;
    }

    void select(int i, boolean animate) {
        selected = i;
        for (int k = 0; k < labels.length; k++) {
            labels[k].animate().cancel();
            labels[k].setTextColor(k == i ? Ui.INK : Ui.TEXT);
        }
        post(() -> {
            int inner = getWidth() - pad * 2;
            if (inner <= 0) return;
            int w = inner / labels.length;
            LayoutParams lp = (LayoutParams) pill.getLayoutParams();
            if (lp.width != w) {
                lp.width = w;
                pill.setLayoutParams(lp);
            }
            if (animate) {
                pill.animate().translationX(w * i).setDuration(380)
                        .setInterpolator(new OvershootInterpolator(1.3f)).start();
            } else {
                pill.setTranslationX(w * i);
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        select(selected, false);
    }
}
