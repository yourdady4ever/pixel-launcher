package com.jostin.launcher;

import android.content.Context;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/** Calendario de vidrio que aparece al tocar el reloj. */
class CalendarPanel extends LinearLayout {
    private final Calendar shown = Calendar.getInstance();
    private final TextView title;
    private final GridLayout grid;

    CalendarPanel(Context c, Backdrop b, String footer, Runnable onFooter) {
        super(c);
        setOrientation(VERTICAL);
        Glass.apply(this, b, 30, Glass.TINT_STRONG);
        int p = Ui.dp(c, 18);
        setPadding(p, p, p, p);
        setClickable(true);
        shown.set(Calendar.DAY_OF_MONTH, 1);

        Calendar now = Calendar.getInstance();
        TextView big = Ui.text(c, String.valueOf(now.get(Calendar.DAY_OF_MONTH)), 44, Ui.TEXT, Ui.THIN);
        TextView longDate = Ui.text(c, cap(new SimpleDateFormat("EEEE", MainActivity.ES).format(now.getTime())), 16, Ui.TEXT, Ui.MEDIUM);
        TextView month = Ui.text(c, cap(new SimpleDateFormat("MMMM 'de' yyyy", MainActivity.ES).format(now.getTime())), 13, Ui.MUTED, Ui.REGULAR);
        LinearLayout today = new LinearLayout(c);
        today.setGravity(Gravity.CENTER_VERTICAL);
        today.addView(big);
        LinearLayout td = new LinearLayout(c);
        td.setOrientation(VERTICAL);
        td.setPadding(Ui.dp(c, 14), 0, 0, 0);
        td.addView(longDate);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.topMargin = Ui.dp(c, 4);
        td.addView(month, mlp);
        today.addView(td);
        addView(today);

        View0 line = new View0(c);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, 1);
        llp.topMargin = Ui.dp(c, 16);
        llp.bottomMargin = Ui.dp(c, 8);
        addView(line, llp);

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        title = Ui.label(c, "");
        head.addView(title, new LayoutParams(0, -2, 1f));
        ImageView prev = arrow(c, true);
        ImageView next = arrow(c, false);
        head.addView(prev, new LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        head.addView(next, new LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        addView(head);
        prev.setOnClickListener(v -> {
            Sound.play(v, Sound.TAP);
            shown.add(Calendar.MONTH, -1);
            render(true, -1);
        });
        next.setOnClickListener(v -> {
            Sound.play(v, Sound.TAP);
            shown.add(Calendar.MONTH, 1);
            render(true, 1);
        });

        grid = new GridLayout(c);
        grid.setColumnCount(7);
        LayoutParams glp = new LayoutParams(-2, -2);
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = Ui.dp(c, 6);
        addView(grid, glp);

        TextView foot = Sheet.button((android.app.Activity) c, footer, Ui.SOFT, Ui.TEXT);
        LayoutParams flp = new LayoutParams(-1, Ui.dp(c, 48));
        flp.topMargin = Ui.dp(c, 14);
        addView(foot, flp);
        foot.setOnClickListener(v -> {
            Sound.play(v, Sound.OPEN);
            onFooter.run();
        });
        render(false, 0);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Línea divisoria fina. */
    private static final class View0 extends android.view.View {
        View0(Context c) {
            super(c);
            setBackgroundColor(Ui.LINE);
        }
    }

    private ImageView arrow(Context c, boolean left) {
        ImageView v = new ImageView(c);
        Icons.Line d = new Icons.Line(left ? Icons.BACK : p -> {
            p.moveTo(9.5f, 5.5f);
            p.lineTo(16f, 12f);
            p.lineTo(9.5f, 18.5f);
        }, Ui.TEXT);
        v.setImageDrawable(d);
        int p = Ui.dp(c, 8);
        v.setPadding(p, p, p, p);
        Ui.pressable(v, 0.85f);
        return v;
    }

    private void render(boolean animate, int dir) {
        Context c = getContext();
        title.setText(new SimpleDateFormat("MMMM yyyy", MainActivity.ES).format(shown.getTime()).toUpperCase(MainActivity.ES));
        grid.removeAllViews();
        int cell = Ui.dp(c, 40);
        String[] heads = {"L", "M", "M", "J", "V", "S", "D"};
        for (String h : heads) grid.addView(cell(c, h, Ui.MUTED, cell, false, Ui.SEMIBOLD));

        Calendar today = Calendar.getInstance();
        Calendar d = (Calendar) shown.clone();
        int offset = (d.get(Calendar.DAY_OF_WEEK) + 5) % 7;  // lunes primero
        for (int i = 0; i < offset; i++) grid.addView(cell(c, "", Ui.TEXT, cell, false, null));
        int days = d.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= days; day++) {
            boolean isToday = today.get(Calendar.YEAR) == d.get(Calendar.YEAR)
                    && today.get(Calendar.MONTH) == d.get(Calendar.MONTH)
                    && today.get(Calendar.DAY_OF_MONTH) == day;
            int col = (offset + day - 1) % 7;
            int color = isToday ? Ui.INK : (col >= 5 ? 0xFF8C9099 : Ui.TEXT);
            grid.addView(cell(c, String.valueOf(day), color, cell, isToday, isToday ? Ui.SEMIBOLD : Ui.REGULAR));
        }
        if (animate) {
            float dd = getResources().getDisplayMetrics().density;
            grid.setAlpha(0f);
            grid.setTranslationX(dir * 24 * dd);
            grid.animate().alpha(1f).translationX(0).setDuration(260).start();
        }
    }

    private TextView cell(Context c, String s, int color, int size, boolean mark, android.graphics.Typeface tf) {
        TextView t = Ui.text(c, s, 14, color, tf);
        t.setGravity(Gravity.CENTER);
        if (mark) {
            t.setBackground(Ui.fill(c, 0xFFF5F6F8, size / 2f / c.getResources().getDisplayMetrics().density));
        }
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = size;
        lp.height = size;
        t.setLayoutParams(lp);
        return t;
    }
}
