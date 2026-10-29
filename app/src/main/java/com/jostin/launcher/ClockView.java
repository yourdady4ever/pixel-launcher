package com.jostin.launcher;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;

/** El reloj de inicio en sus tres estilos: espaciado, digital y apilado. */
class ClockView extends LinearLayout {
    private final Backdrop backdrop;
    private TextView day, date, greeting;
    private TextClock time, time2;
    private LinearLayout weatherRow;
    private ImageView weatherIcon;
    private TextView weatherText;
    private TextView studyChip;
    private int style = -1, align = -1, font = -1;
    /** Espacio que le dan (cuando vive dentro de un widget). 0 = ancho de la pantalla. */
    private int availW = 0, availH = 0;

    ClockView(Context c, Backdrop b) {
        super(c);
        backdrop = b;
        setOrientation(VERTICAL);
    }

    /** Rehace el reloj si cambió el estilo, la alineación o la letra. */
    void rebuild() {
        Context c = getContext();
        int s = Prefs.clockStyle(c), a = Prefs.clockAlign(c), f = Prefs.clockFont(c);
        if (s == style && a == align && f == font) {
            refresh();
            return;
        }
        style = s;
        align = a;
        font = f;
        removeAllViews();
        int g = a == 1 ? Gravity.START : Gravity.CENTER_HORIZONTAL;
        setGravity(g);
        Typeface tf = Ui.clockFont(f);
        int width = availW > 0 ? availW : getResources().getDisplayMetrics().widthPixels - Ui.dp(c, 40);

        if (s == 0) {
            day = Ui.text(c, "", 34, Ui.TEXT, tf);
            day.setLetterSpacing(f == 1 ? 0.18f : 0.3f);
            day.setMaxLines(1);
            day.setGravity(a == 1 ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
            day.setAutoSizeTextTypeUniformWithConfiguration(18, 38, 1, TypedValue.COMPLEX_UNIT_SP);
            addView(day, new LayoutParams(width, Ui.dp(c, 54)));
            date = small(c, 11, 0.42f, Ui.SEMIBOLD);
            addView(date, lp(10));
            time = clock(c, "'–   'HH:mm'   –'", "'–   'h:mm'   –'", 13, Ui.LIGHT);
            time.setLetterSpacing(0.32f);
            addView(time, lp(8));
            time2 = null;
        } else if (s == 1) {
            time = clock(c, "HH:mm", "h:mm", 86, tf);
            time.setLetterSpacing(f == 0 || f == 2 ? 0.02f : -0.02f);
            time.setIncludeFontPadding(false);
            addView(time, new LayoutParams(-2, -2));
            date = small(c, 15, 0.02f, Ui.MEDIUM);
            addView(date, lp(6));
            day = null;
            time2 = null;
        } else {
            time = clock(c, "HH", "hh", 112, tf);
            time.setIncludeFontPadding(false);
            time.setLineSpacing(0, 0.86f);
            addView(time, new LayoutParams(-2, -2));
            time2 = clock(c, "mm", "mm", 112, tf);
            time2.setIncludeFontPadding(false);
            LayoutParams l2 = new LayoutParams(-2, -2);
            l2.topMargin = -Ui.dp(c, 18);
            addView(time2, l2);
            date = small(c, 13, 0.3f, Ui.SEMIBOLD);
            addView(date, lp(8));
            day = null;
        }

        weatherRow = new LinearLayout(c);
        weatherRow.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(c);
        weatherRow.addView(weatherIcon, new LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
        weatherText = Ui.text(c, "", 13, Ui.TEXT, Ui.MEDIUM);
        LayoutParams wl = new LayoutParams(-2, -2);
        wl.leftMargin = Ui.dp(c, 7);
        weatherRow.addView(weatherText, wl);
        weatherRow.setVisibility(GONE);
        addView(weatherRow, lp(12));

        greeting = Ui.text(c, "", 14, Ui.TEXT, Ui.REGULAR);
        greeting.setGravity(a == 1 ? Gravity.START : Gravity.CENTER);
        greeting.setMaxWidth(width);
        greeting.setVisibility(GONE);
        addView(greeting, lp(10));

        studyChip = Ui.text(c, "", 11, Ui.TEXT, Ui.SEMIBOLD);
        studyChip.setLetterSpacing(0.25f);
        int h = Ui.dp(c, 12), v = Ui.dp(c, 7);
        studyChip.setPadding(h, v, h, v);
        Glass.apply(studyChip, backdrop, 14);
        studyChip.setVisibility(GONE);
        addView(studyChip, lp(14));
        refresh();
    }

    private LayoutParams lp(int topDp) {
        LayoutParams l = new LayoutParams(-2, -2);
        l.topMargin = Ui.dp(getContext(), topDp);
        return l;
    }

    private TextView small(Context c, float sp, float spacing, Typeface tf) {
        TextView t = Ui.text(c, "", sp, Ui.TEXT, tf);
        t.setLetterSpacing(spacing);
        return t;
    }

    private TextClock clock(Context c, String f24, String f12, float sp, Typeface tf) {
        TextClock t = new TextClock(c);
        t.setFormat24Hour(f24);
        t.setFormat12Hour(f12);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Actualiza textos y colores (llamar cada minuto o cuando cambie el fondo). */
    void refresh() {
        Context c = getContext();
        Date now = new Date();
        int col = Theme.clock(c);
        int soft = Ui.alpha(col, 0xCC);
        if (day != null) {
            String d = new SimpleDateFormat("EEEE", MainActivity.ES).format(now).toUpperCase(MainActivity.ES);
            if (!d.contentEquals(day.getText())) day.setText(d);
            day.setTextColor(col);
        }
        if (date != null) {
            String txt;
            if (style == 1) txt = cap(new SimpleDateFormat("EEEE d 'de' MMMM", MainActivity.ES).format(now));
            else txt = new SimpleDateFormat("d MMM yyyy", MainActivity.ES).format(now).replace(".", "").toUpperCase(MainActivity.ES);
            date.setText(txt);
            date.setTextColor(style == 1 ? col : soft);
        }
        if (time != null) time.setTextColor(style == 0 ? soft : col);
        if (time2 != null) time2.setTextColor(Ui.alpha(col, 0x99));
        float shadow = Theme.wallLight ? 0 : Ui.dp(c, 14);
        int sc = Theme.wallLight ? 0 : 0x66000000;
        if (day != null) day.setShadowLayer(shadow, 0, 0, sc);
        if (time != null && style != 0) time.setShadowLayer(shadow, 0, 0, sc);
        if (time2 != null) time2.setShadowLayer(shadow, 0, 0, sc);

        String g = Prefs.greeting(c);
        greeting.setVisibility(g.isEmpty() ? GONE : VISIBLE);
        greeting.setText(g);
        greeting.setTextColor(soft);

        Weather.Now w = Prefs.showWeather(c) ? Weather.cached(c) : null;
        if (w != null) {
            weatherRow.setVisibility(VISIBLE);
            Icons.Line ic = new Icons.Line(Weather.icon(w.code, w.day), soft, 1.7f);
            weatherIcon.setImageDrawable(ic);
            weatherText.setText(w.temp + "°  " + Weather.describe(w.code));
            weatherText.setTextColor(soft);
        } else {
            weatherRow.setVisibility(GONE);
        }
        fit();
    }

    /** Le dice al reloj cuánto espacio tiene; se rehace y se escala para caber completo. */
    void setAvail(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == availW && h == availH && style != -1) {
            fit();
            return;
        }
        availW = w;
        availH = h;
        style = -1;
        rebuild();
    }

    /** Si el reloj no cabe en su widget, se reduce parejo (nunca se corta). */
    private void fit() {
        if (availW <= 0 || availH <= 0) return;
        int u = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        measure(u, u);
        float mw = Math.max(1, getMeasuredWidth()), mh = Math.max(1, getMeasuredHeight());
        float s = Math.min(1f, Math.min(availW / mw, availH / mh));
        setPivotX(align == 1 ? 0 : mw / 2f);
        setPivotY(mh / 2f);
        setScaleX(s);
        setScaleY(s);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        if (availW > 0) {
            int u = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            super.onMeasure(u, u);
        } else {
            super.onMeasure(ws, hs);
        }
    }

    void setStudy(String text) {
        if (studyChip == null) return;
        studyChip.setVisibility(text == null ? GONE : VISIBLE);
        if (text != null) {
            studyChip.setText(text);
            studyChip.setTextColor(Ui.TEXT);
        }
        fit();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    void playEntrance() {
        float d = getResources().getDisplayMetrics().density;
        View first = getChildAt(0);
        if (first == null) return;
        first.setAlpha(0f);
        first.setTranslationY(-10 * d);
        first.animate().alpha(1f).translationY(0).setStartDelay(0).setDuration(620)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        if (style == 0 && day != null) {
            ObjectAnimator spread = ObjectAnimator.ofFloat(day, "letterSpacing", 0.75f, font == 1 ? 0.18f : 0.3f);
            spread.setDuration(900);
            spread.setInterpolator(new DecelerateInterpolator(2.6f));
            spread.start();
        }
        for (int k = 1; k < getChildCount(); k++) {
            View v = getChildAt(k);
            if (v.getVisibility() != VISIBLE) continue;
            v.setAlpha(0f);
            v.setTranslationY(6 * d);
            v.animate().alpha(1f).translationY(0).setStartDelay(220 + k * 90L).setDuration(500).start();
        }
    }
}
