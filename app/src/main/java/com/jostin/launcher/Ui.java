package com.jostin.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/** Colores, tipografías, tamaños y la animación de toque compartida por toda la app. */
final class Ui {
    static final int BG = 0xFF0C0D10;
    static final int TEXT = 0xFFF5F6F8;
    static final int MUTED = 0xFFA2A7B0;
    static final int FAINT = 0x66FFFFFF;
    static final int LINE = 0x1FFFFFFF;
    static final int SOFT = 0x14FFFFFF;
    static final int INK = 0xFF0E0F12;

    static Typeface DISPLAY, THIN, LIGHT, REGULAR, MEDIUM, SEMIBOLD;
    static Typeface[] CLOCK_FONTS;
    static final String[] CLOCK_FONT_NAMES = {"Michroma", "Inter", "Syncopate", "Bodoni", "Orbitron"};

    private Ui() {}

    static void init(Context c) {
        if (DISPLAY != null) return;
        DISPLAY = font(c, "michroma");
        THIN = font(c, "inter_display_thin");
        LIGHT = font(c, "inter_light");
        REGULAR = font(c, "inter_regular");
        MEDIUM = font(c, "inter_medium");
        SEMIBOLD = font(c, "inter_semibold");
        CLOCK_FONTS = new Typeface[]{DISPLAY, THIN, font(c, "syncopate"), font(c, "bodoni"), font(c, "orbitron")};
    }

    static Typeface clockFont(int i) {
        return CLOCK_FONTS[Math.max(0, Math.min(CLOCK_FONTS.length - 1, i))];
    }

    private static Typeface font(Context c, String name) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "fonts/" + name + ".ttf");
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    /** Qué tan largas son las animaciones según la preferencia: 1 normal, 0 apagadas. */
    static float motion(Context c) {
        switch (Prefs.motion(c)) {
            case 1: return 0.6f;
            case 2: return 0.5f;
            case 3: return 0f;
            default: return 1f;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static int alphaOf(int color) {
        return (color >>> 24) & 0xFF;
    }

    static GradientDrawable fill(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable outline(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = fill(c, fill, radiusDp);
        g.setStroke(Math.max(1, dp(c, 1)), stroke);
        return g;
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf != null ? tf : REGULAR);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Etiqueta pequeña en mayúsculas espaciadas, para secciones. */
    static TextView label(Context c, String s) {
        TextView t = text(c, s.toUpperCase(MainActivity.ES), 11, MUTED, SEMIBOLD);
        t.setLetterSpacing(0.18f);
        return t;
    }

    static void numericInput(android.widget.EditText e) {
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }

    /** Pantalla completa con barras transparentes. */
    static void edgeToEdge(Activity a, boolean lightBars) {
        Window w = a.getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
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

    /** Se hunde y se aclara al tocar; rebota al soltar. */
    static void pressable(View v) {
        pressable(v, 0.94f);
    }

    static void pressable(View v, float scale) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(scale).scaleY(scale).alpha(0.82f).setStartDelay(0).setDuration(110)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(0).setDuration(340)
                            .setInterpolator(new OvershootInterpolator(2.4f)).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Sacudida corta, para errores. */
    static void shake(View v) {
        float d = v.getResources().getDisplayMetrics().density;
        v.animate().cancel();
        android.animation.ObjectAnimator.ofFloat(v, "translationX", 0, 10 * d, -9 * d, 7 * d, -5 * d, 3 * d, 0)
                .setDuration(380).start();
    }

    /** Animación estándar de entrada: sube y aparece, con retraso escalonado. */
    static void rise(View v, int index, float fromDp) {
        float d = v.getResources().getDisplayMetrics().density;
        v.setAlpha(0f);
        v.setTranslationY(fromDp * d);
        v.animate().alpha(1f).translationY(0).setStartDelay(60L + index * 45L).setDuration(480)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
    }
}
