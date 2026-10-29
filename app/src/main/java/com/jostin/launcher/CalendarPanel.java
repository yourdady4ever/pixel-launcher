package com.jostin.launcher;

import android.content.Context;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/** Calendario de vidrio que aparece al tocar el reloj. */
class CalendarPanel extends LinearLayout {
    private final Calendar shown = Calendar.getInstance();
    private final TextView title;
    private final GridLayout grid;

    CalendarPanel(Context c, Runnable onTasks) {
        super(c);
        setOrientation(VERTICAL);
        setBackground(Ui.glass(c, 24));
        int p = Ui.dp(c, 16);
        setPadding(p, p, p, p);
        shown.set(Calendar.DAY_OF_MONTH, 1);

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = arrow(c, "‹");
        TextView next = arrow(c, "›");
        title = Ui.text(c, "", 14, Ui.TEXT, Ui.MEDIUM);
        title.setLetterSpacing(0.25f);
        title.setGravity(Gravity.CENTER);
        head.addView(prev, new LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
        head.addView(title, new LayoutParams(0, -2, 1f));
        head.addView(next, new LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
        addView(head);
        prev.setOnClickListener(v -> {
            Ui.click(v);
            shown.add(Calendar.MONTH, -1);
            render();
        });
        next.setOnClickListener(v -> {
            Ui.click(v);
            shown.add(Calendar.MONTH, 1);
            render();
        });

        grid = new GridLayout(c);
        grid.setColumnCount(7);
        LayoutParams glp = new LayoutParams(-2, -2);
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = Ui.dp(c, 6);
        addView(grid, glp);

        TextView tasks = Ui.button(c, "Ver pendientes");
        LayoutParams tlp = new LayoutParams(-1, -2);
        tlp.topMargin = Ui.dp(c, 12);
        addView(tasks, tlp);
        tasks.setOnClickListener(v -> {
            Ui.click(v);
            onTasks.run();
        });

        render();
    }

    private TextView arrow(Context c, String s) {
        TextView t = Ui.text(c, s, 24, Ui.TEXT, Ui.LIGHT);
        t.setGravity(Gravity.CENTER);
        Ui.pressable(t);
        return t;
    }

    private void render() {
        Context c = getContext();
        title.setText(new SimpleDateFormat("MMMM yyyy", MainActivity.ES).format(shown.getTime()).toUpperCase(MainActivity.ES));
        grid.removeAllViews();
        int cell = Ui.dp(c, 40);
        String[] heads = {"L", "M", "M", "J", "V", "S", "D"};
        for (String h : heads) grid.addView(cell(c, h, Ui.MUTED, cell, false));

        Calendar today = Calendar.getInstance();
        Calendar d = (Calendar) shown.clone();
        // Lunes = 0 ... Domingo = 6
        int offset = (d.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        for (int i = 0; i < offset; i++) grid.addView(cell(c, "", Ui.TEXT, cell, false));
        int days = d.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= days; day++) {
            boolean isToday = today.get(Calendar.YEAR) == d.get(Calendar.YEAR)
                    && today.get(Calendar.MONTH) == d.get(Calendar.MONTH)
                    && today.get(Calendar.DAY_OF_MONTH) == day;
            grid.addView(cell(c, String.valueOf(day), isToday ? 0xFF111111 : Ui.TEXT, cell, isToday));
        }
    }

    private TextView cell(Context c, String s, int color, int size, boolean mark) {
        TextView t = Ui.text(c, s, 13, color, mark ? Ui.MEDIUM : null);
        t.setGravity(Gravity.CENTER);
        if (mark) t.setBackground(Ui.fill(c, Ui.ACCENT, size / 2f / c.getResources().getDisplayMetrics().density));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = size;
        lp.height = size;
        t.setLayoutParams(lp);
        return t;
    }
}
