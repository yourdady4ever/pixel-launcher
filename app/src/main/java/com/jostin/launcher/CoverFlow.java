package com.jostin.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Carrusel 3D: la tarjeta del centro queda al frente y las de los lados giran hacia atrás.
 * Siempre se acomoda solo en una tarjeta.
 */
class CoverFlow extends HorizontalScrollView {
    interface Listener {
        /** La tarjeta del centro cambió mientras se desliza. */
        void onCentered(int index);

        /** El carrusel se detuvo en una tarjeta. */
        void onSettled(int index);
    }

    private final LinearLayout strip;
    private final List<View> cards = new ArrayList<>();
    private final int cardW, gap;
    private Listener listener;
    private boolean touching = false;
    private int centered = -1, settled = -1;
    private final Runnable settle = this::settle;

    CoverFlow(Context c, int cardWidth, int gapPx) {
        super(c);
        cardW = cardWidth;
        gap = gapPx;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipChildren(false);
        strip = new LinearLayout(c);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        addView(strip, new LayoutParams(-2, -1));
    }

    void setListener(Listener l) {
        listener = l;
    }

    void add(View card, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, height);
        if (!cards.isEmpty()) lp.leftMargin = gap;
        strip.addView(card, lp);
        cards.add(card);
    }

    private int step() {
        return cardW + gap;
    }

    void jumpTo(int index) {
        post(() -> {
            scrollTo(index * step(), 0);
            settled = index;
            transform();
        });
    }

    void smoothTo(int index) {
        smoothScrollTo(index * step(), 0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        int side = (w - cardW) / 2;
        strip.setPadding(side, 0, side, 0);
        post(this::transform);
    }

    @Override
    protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        transform();
        removeCallbacks(settle);
        postDelayed(settle, 90);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) touching = true;
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            touching = false;
            removeCallbacks(settle);
            postDelayed(settle, 60);
        }
        return super.onTouchEvent(e);
    }

    @Override
    public void fling(int velocityX) {
        // En lugar de un lanzamiento libre, avanza a la tarjeta que corresponde según la fuerza
        int target = Math.round((getScrollX() + velocityX * 0.12f) / step());
        target = Math.max(0, Math.min(cards.size() - 1, target));
        smoothTo(target);
    }

    private int nearest() {
        return Math.max(0, Math.min(cards.size() - 1, Math.round(getScrollX() / (float) step())));
    }

    private void settle() {
        if (touching || cards.isEmpty()) return;
        int n = nearest();
        int x = n * step();
        if (Math.abs(getScrollX() - x) > 2) {
            smoothScrollTo(x, 0);
            return;
        }
        if (n != settled) {
            settled = n;
            if (listener != null) listener.onSettled(n);
        }
    }

    private void transform() {
        float mid = getScrollX() + getWidth() / 2f;
        for (int i = 0; i < cards.size(); i++) {
            View c = cards.get(i);
            float center = c.getLeft() + c.getWidth() / 2f;
            float d = (center - mid) / step();
            float ad = Math.min(2.2f, Math.abs(d));
            c.setRotationY(Math.max(-55f, Math.min(55f, -d * 32f)));
            float s = 1f - ad * 0.13f;
            c.setScaleX(s);
            c.setScaleY(s);
            c.setAlpha(1f - Math.min(1f, ad) * 0.35f - Math.max(0f, ad - 1f) * 0.35f);
            c.setTranslationX(-d * cardW * 0.12f);
            c.setTranslationZ(-ad * 20f);
        }
        int n = nearest();
        if (n != centered) {
            centered = n;
            if (listener != null) listener.onCentered(n);
        }
    }
}
