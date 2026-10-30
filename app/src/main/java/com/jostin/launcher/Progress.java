package com.jostin.launcher;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Barra de progreso fina con el color de acento. */
final class Progress extends View {
    private float v;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    Progress(Activity a) {
        super(a);
        track.setColor(0x26FFFFFF);
    }

    void set(float f) {
        v = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight(), r = h / 2f;
        c.drawRoundRect(0, 0, getWidth(), h, r, r, track);
        fill.setColor(Theme.accent);
        c.drawRoundRect(0, 0, getWidth() * v, h, r, r, fill);
    }
}
