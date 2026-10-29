package com.jostin.launcher;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Deslizar una fila hacia un lado la quita (como las notificaciones de Android). Tocarla sigue funcionando. */
final class SwipeDismiss {
    private SwipeDismiss() {}

    static void attach(View row, Runnable onDismiss) {
        int slop = ViewConfiguration.get(row.getContext()).getScaledTouchSlop();
        final float[] down = new float[2];
        final boolean[] swiping = {false};
        final VelocityTracker[] vt = {null};
        row.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    swiping[0] = false;
                    if (vt[0] != null) vt[0].recycle();
                    vt[0] = VelocityTracker.obtain();
                    vt[0].addMovement(e);
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(110).start();
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    if (vt[0] != null) vt[0].addMovement(e);
                    float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
                    if (!swiping[0] && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                        swiping[0] = true;
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                        // Cancela el toque normal para que no se abra al soltar
                        MotionEvent c = MotionEvent.obtain(e);
                        c.setAction(MotionEvent.ACTION_CANCEL);
                        v.onTouchEvent(c);
                        c.recycle();
                    }
                    if (swiping[0]) {
                        v.setTranslationX(dx);
                        v.setAlpha(Math.max(0.15f, 1f - Math.abs(dx) / Math.max(1f, v.getWidth())));
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2f)).start();
                    if (!swiping[0]) return false;
                    swiping[0] = false;
                    float dx = e.getRawX() - down[0];
                    float vx = 0;
                    if (vt[0] != null) {
                        vt[0].addMovement(e);
                        vt[0].computeCurrentVelocity(1000);
                        vx = vt[0].getXVelocity();
                        vt[0].recycle();
                        vt[0] = null;
                    }
                    boolean gone = Math.abs(dx) > v.getWidth() * 0.35f || (Math.abs(vx) > 1400 && Math.signum(vx) == Math.signum(dx));
                    if (gone) {
                        Sound.play(v, Sound.BACK);
                        float to = Math.signum(dx == 0 ? vx : dx) * v.getWidth() * 1.1f;
                        v.animate().translationX(to).alpha(0f).setDuration(200).withEndAction(() -> collapse(v, onDismiss)).start();
                    } else {
                        v.animate().translationX(0).alpha(1f).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
                    }
                    return true;
                }
                default:
                    return false;
            }
        });
    }

    /** Cierra el hueco que deja la fila. */
    static void collapse(View v, Runnable after) {
        int h = v.getHeight();
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        int m = lp.topMargin;
        ValueAnimator a = ValueAnimator.ofFloat(1f, 0f);
        a.setDuration(200);
        a.addUpdateListener(x -> {
            float f = (float) x.getAnimatedValue();
            lp.height = Math.max(0, Math.round(h * f));
            lp.topMargin = Math.round(m * f);
            v.setLayoutParams(lp);
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator an) {
                v.setVisibility(View.GONE);
                if (after != null) after.run();
            }
        });
        a.start();
    }
}
