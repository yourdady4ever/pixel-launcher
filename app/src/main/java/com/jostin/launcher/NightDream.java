package com.jostin.launcher;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;

/**
 * Protector de pantalla de Android (se ve mientras el teléfono carga): tu fondo de Jostin OS con
 * el reloj. El reloj se mueve un poco cada minuto para no marcar la pantalla.
 */
public class NightDream extends DreamService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Backdrop backdrop;
    private ClockView clock;
    private int step = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (clock == null) return;
            clock.refresh();
            step++;
            float d = getResources().getDisplayMetrics().density;
            float dx = (float) Math.sin(step * 1.7) * 28 * d, dy = (float) Math.cos(step * 1.3) * 40 * d;
            clock.animate().translationX(dx).translationY(dy).setDuration(2000).start();
            handler.postDelayed(this, 60_000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        Ui.init(this);
        Theme.recompute(this);
        FrameLayout root = new FrameLayout(this);
        backdrop = new Backdrop(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        clock = new ClockView(this, backdrop);
        root.addView(clock, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);
        backdrop.setParallax(false);
        backdrop.setSmooth(false);
        backdrop.useCurrent();
        backdrop.setScreenDim(0.35f);
        clock.rebuild();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (backdrop != null) backdrop.setRunning(true);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        handler.removeCallbacks(tick);
        if (backdrop != null) backdrop.setRunning(false);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tick);
        if (backdrop != null) backdrop.setRunning(false);
    }
}
