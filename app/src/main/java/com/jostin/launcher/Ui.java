package com.jostin.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

/** Colores, tamaños, sonidos y animaciones compartidas. Estilo monocromático tipo vidrio. */
final class Ui {
    static final int BG = 0xFF0E0F11;
    static final int GLASS = 0xD91B1D21;
    static final int GLASS_LIGHT = 0x26FFFFFF;
    static final int TEXT = 0xFFF2F2F2;
    static final int MUTED = 0xFF9A9DA3;
    static final int ACCENT = 0xFFE8E8E8;
    static final int RED = 0xFFD7262E;

    static final Typeface THIN = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private static SoundPool pool;
    private static int clickId = 0, openId = 0;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Panel oscuro semitransparente con borde fino. */
    static GradientDrawable glass(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(GLASS);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 1), alpha(Color.WHITE, 0x1F));
        return g;
    }

    static GradientDrawable fill(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    static EditText input(Context c, String hint, boolean number) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(17);
        e.setSingleLine(true);
        e.setBackground(fill(c, GLASS_LIGHT, 14));
        int p = dp(c, 14);
        e.setPadding(p, p, p, p);
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    static TextView button(Context c, String s) {
        TextView b = text(c, s, 15, TEXT, MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setBackground(glass(c, 16));
        int p = dp(c, 14);
        b.setPadding(p, p, p, p);
        pressable(b);
        return b;
    }

    /** Botón de selección: blanco si está activo, vidrio si no. */
    static void chip(Context c, TextView t, boolean active) {
        t.setBackground(active ? fill(c, ACCENT, 16) : glass(c, 16));
        t.setTextColor(active ? 0xFF111111 : TEXT);
    }

    static void grayscale(ImageView v, boolean on) {
        if (on) {
            ColorMatrix m = new ColorMatrix();
            m.setSaturation(0f);
            v.setColorFilter(new ColorMatrixColorFilter(m));
        } else {
            v.clearColorFilter();
        }
    }

    /** Pantalla completa con barras transparentes. */
    static void edgeToEdge(Activity a, boolean lightBars) {
        Window w = a.getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.setBackgroundDrawable(new ColorDrawable(BG));
        setLightBars(a, lightBars);
    }

    static void setLightBars(Activity a, boolean light) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        a.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    /** Se hunde al tocar y rebota al soltar. */
    static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(280)
                            .setInterpolator(new OvershootInterpolator(3f)).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    static void initSounds(Context c) {
        if (pool != null) return;
        try {
            pool = new SoundPool.Builder()
                    .setMaxStreams(3)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .build();
            AssetFileDescriptor a = c.getAssets().openFd("click.wav");
            clickId = pool.load(a, 1);
            AssetFileDescriptor b = c.getAssets().openFd("open.wav");
            openId = pool.load(b, 1);
        } catch (Exception ignored) {
            // Sin sonido el launcher sigue funcionando igual
        }
    }

    static void click(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (pool != null && clickId != 0 && Prefs.sounds(v.getContext())) pool.play(clickId, 0.5f, 0.5f, 1, 0, 1f);
    }

    static void open(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        if (pool != null && openId != 0 && Prefs.sounds(v.getContext())) pool.play(openId, 0.6f, 0.6f, 1, 0, 1f);
    }
}
