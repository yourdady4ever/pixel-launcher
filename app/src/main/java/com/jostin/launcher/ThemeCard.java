package com.jostin.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Vista previa de un fondo, recortada como tarjeta inclinada. */
class ThemeCard extends View {
    private final int theme;
    private Bitmap preview;
    private final Path clip = new Path();
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean selected = false;

    ThemeCard(Context c, int theme) {
        super(c);
        this.theme = theme;
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Ui.dp(c, 2));
    }

    int theme() {
        return theme;
    }

    void setSelectedCard(boolean s) {
        selected = s;
        animate().scaleX(s ? 1.08f : 0.94f).scaleY(s ? 1.08f : 0.94f).setDuration(220).start();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        // La escena se dibuja a tamaño de pantalla reducido para que se parezca al fondo real
        int sw = getResources().getDisplayMetrics().widthPixels / 3;
        int sh = getResources().getDisplayMetrics().heightPixels / 3;
        Scenes.Scene s = Scenes.create(theme);
        s.size(sw, sh);
        Bitmap full = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        s.draw(new Canvas(full), 12f);
        preview = Bitmap.createScaledBitmap(full, Math.max(1, (int) (h * sw / (float) sh)), h, true);
        if (preview != full) full.recycle();
        float slant = w * 0.22f;
        clip.reset();
        clip.moveTo(slant, 0);
        clip.lineTo(w, 0);
        clip.lineTo(w - slant, h);
        clip.lineTo(0, h);
        clip.close();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (preview == null) return;
        c.save();
        c.clipPath(clip);
        float left = (getWidth() - preview.getWidth()) / 2f;
        c.drawBitmap(preview, left, 0, null);
        c.restore();
        border.setColor(selected ? 0xFFFFFFFF : 0x44FFFFFF);
        c.drawPath(clip, border);
    }
}
