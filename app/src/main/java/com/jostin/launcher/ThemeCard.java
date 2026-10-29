package com.jostin.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Vista previa de un fondo, recortada como tarjeta inclinada, con su nombre. */
class ThemeCard extends View {
    private final int theme;
    private Bitmap preview;
    private final Path clip = new Path();
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);
    private boolean selected = false;

    ThemeCard(Context c, int theme) {
        super(c);
        this.theme = theme;
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Ui.dp(c, 1.5f));
        border.setStrokeJoin(Paint.Join.ROUND);
        setCameraDistance(9000 * getResources().getDisplayMetrics().density);
    }

    int theme() {
        return theme;
    }

    void setSelectedCard(boolean s) {
        if (selected == s) return;
        selected = s;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        // La escena se dibuja a la proporción de la pantalla real y se recorta al centro
        int sw = getResources().getDisplayMetrics().widthPixels / 2;
        int sh = getResources().getDisplayMetrics().heightPixels / 2;
        Scenes.Scene s = Scenes.create(theme);
        s.size(sw, sh);
        Bitmap full = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        s.draw(new Canvas(full), 12f);
        int outW = Math.max(1, Math.round(h * sw / (float) sh));
        preview = Bitmap.createScaledBitmap(full, outW, h, true);
        if (preview != full) full.recycle();
        float slant = w * 0.16f;
        float r = Ui.dp(getContext(), 10);
        clip.reset();
        clip.moveTo(slant + r, 0);
        clip.lineTo(w - r, 0);
        clip.quadTo(w, 0, w - r * 0.3f, r);
        clip.lineTo(w - slant + r * 0.3f, h - r);
        clip.quadTo(w - slant, h, w - slant - r, h);
        clip.lineTo(r, h);
        clip.quadTo(0, h, r * 0.3f, h - r);
        clip.lineTo(slant - r * 0.3f, r);
        clip.quadTo(slant, 0, slant + r, 0);
        clip.close();
        shade.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0x8C000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (preview == null) return;
        c.save();
        c.clipPath(clip);
        c.drawBitmap(preview, (getWidth() - preview.getWidth()) / 2f, 0, img);
        c.drawRect(0, 0, getWidth(), getHeight(), shade);
        c.restore();
        border.setColor(selected ? 0xFFFFFFFF : 0x40FFFFFF);
        border.setStrokeWidth(Ui.dp(getContext(), selected ? 2f : 1f));
        c.drawPath(clip, border);
    }
}
