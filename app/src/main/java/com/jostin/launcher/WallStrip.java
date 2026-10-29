package com.jostin.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tira de tarjetas inclinadas con tus fotos y las escenas, como en el video que mandaste. */
class WallStrip extends HorizontalScrollView {
    interface Listener {
        void onAdd();
        void onPick(int kind, int scene, String photo);
        void onDelete(String photo);
    }

    private static final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout row;
    private final List<Card> cards = new ArrayList<>();
    private final Listener listener;

    WallStrip(Activity a, Listener l) {
        super(a);
        listener = l;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        row = new LinearLayout(a);
        row.setClipChildren(false);
        row.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 30), Ui.dp(a, 10));
        addView(row);
        rebuild();
    }

    void rebuild() {
        Context c = getContext();
        row.removeAllViews();
        cards.clear();
        add(new Card(c, Card.ADD, -1, null));
        for (String id : Wallpaper.photos(c)) add(new Card(c, Card.PHOTO, -1, id));
        for (int k = 0; k < Scenes.COUNT; k++) add(new Card(c, Card.SCENE, k, null));
        markSelected();
    }

    private void add(Card card) {
        int w = Ui.dp(getContext(), 104), h = Ui.dp(getContext(), 196);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        if (!cards.isEmpty()) lp.leftMargin = -Ui.dp(getContext(), 14);
        row.addView(card, lp);
        cards.add(card);
        Ui.pressable(card, 0.94f);
        card.setOnClickListener(v -> {
            if (card.kind == Card.ADD) {
                Sound.play(v, Sound.TAP);
                listener.onAdd();
                return;
            }
            Sound.play(v, Sound.THEME);
            listener.onPick(card.kind == Card.PHOTO ? 1 : 0, card.scene, card.photo);
            markSelected();
        });
        if (card.kind == Card.PHOTO) {
            card.setOnLongClickListener(v -> {
                Sound.play(v, Sound.HOLD);
                listener.onDelete(card.photo);
                return true;
            });
        }
        card.load(loader, main);
    }

    void markSelected() {
        Context c = getContext();
        int kind = Prefs.wallKind(c);
        String photo = Prefs.wallPhoto(c);
        int scene = Prefs.wallScene(c);
        for (Card card : cards) {
            boolean sel = card.kind == Card.PHOTO ? kind == 1 && card.photo.equals(photo)
                    : card.kind == Card.SCENE && kind == 0 && card.scene == scene;
            card.setSelectedCard(sel);
        }
    }

    /** Lleva la tarjeta elegida a la vista. */
    void scrollToSelected() {
        post(() -> {
            for (Card card : cards) {
                if (card.selected) {
                    smoothScrollTo(Math.max(0, card.getLeft() - getWidth() / 2 + card.getWidth() / 2), 0);
                    return;
                }
            }
        });
    }

    static final class Card extends View {
        static final int ADD = 0, PHOTO = 1, SCENE = 2;
        final int kind, scene;
        final String photo;
        boolean selected;
        private Bitmap bmp;
        private final Path clip = new Path();
        private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint();
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint plus = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();

        Card(Context c, int kind, int scene, String photo) {
            super(c);
            this.kind = kind;
            this.scene = scene;
            this.photo = photo;
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeJoin(Paint.Join.ROUND);
            label.setColor(0xF2FFFFFF);
            label.setTypeface(Ui.SEMIBOLD);
            label.setTextSize(Ui.dp(c, 11));
            label.setLetterSpacing(0.08f);
            plus.setColor(0xE6FFFFFF);
            plus.setStrokeCap(Paint.Cap.ROUND);
            plus.setStrokeWidth(Ui.dp(c, 2));
        }

        void load(ExecutorService ex, Handler main) {
            if (kind == ADD) return;
            Context c = getContext().getApplicationContext();
            ex.execute(() -> {
                Bitmap b = kind == PHOTO ? Wallpaper.thumb(c, photo) : Wallpaper.sceneThumb(c, scene);
                main.post(() -> {
                    bmp = b;
                    invalidate();
                });
            });
        }

        void setSelectedCard(boolean s) {
            if (s == selected) return;
            selected = s;
            animate().scaleX(s ? 1.06f : 1f).scaleY(s ? 1.06f : 1f).setDuration(320)
                    .setInterpolator(new OvershootInterpolator(2f)).start();
            setTranslationZ(s ? 10 : 0);
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            float slant = w * 0.22f, r = Ui.dp(getContext(), 8);
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
            shade.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0xB3000000, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            c.save();
            c.clipPath(clip);
            if (kind == ADD) {
                c.drawColor(0x26FFFFFF);
                float cx = w / 2f, cy = h / 2f - Ui.dp(getContext(), 8), s = Ui.dp(getContext(), 12);
                c.drawLine(cx - s, cy, cx + s, cy, plus);
                c.drawLine(cx, cy - s, cx, cy + s, plus);
            } else if (bmp != null && !bmp.isRecycled()) {
                float sc = Math.max(w / bmp.getWidth(), h / bmp.getHeight());
                m.setScale(sc, sc);
                m.postTranslate((w - bmp.getWidth() * sc) / 2f, (h - bmp.getHeight() * sc) / 2f);
                c.drawBitmap(bmp, m, img);
            } else {
                c.drawColor(0x1AFFFFFF);
            }
            c.drawRect(0, 0, w, h, shade);
            c.restore();
            String text = kind == ADD ? "Tu foto" : kind == PHOTO ? "" : Scenes.NAMES[scene];
            if (!text.isEmpty()) {
                float tw = label.measureText(text);
                float x = Math.max(Ui.dp(getContext(), 6), (w * 0.78f - tw) / 2f);
                c.drawText(text, x, h - Ui.dp(getContext(), 12), label);
            }
            border.setColor(selected ? 0xFFFFFFFF : 0x33FFFFFF);
            border.setStrokeWidth(Ui.dp(getContext(), selected ? 2.2f : 1f));
            c.drawPath(clip, border);
        }
    }
}
