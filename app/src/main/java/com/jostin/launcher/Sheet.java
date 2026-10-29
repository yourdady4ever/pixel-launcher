package com.jostin.launcher;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Hojas de vidrio que suben desde abajo, en lugar de los diálogos grises de Android. */
final class Sheet {
    private static final String TAG = "jostin_sheet";
    static final int DANGER = 0xFFFF8A8A;

    interface Value {
        String get();
    }

    static final class Item {
        final String label;
        final Icons.Shape icon;
        final Runnable action;
        Value value;
        boolean keepOpen;
        boolean danger;

        Item(String label, Icons.Shape icon, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.action = action;
        }

        Item value(Value v) {
            value = v;
            keepOpen = true;
            return this;
        }

        Item danger() {
            danger = true;
            return this;
        }
    }

    private Sheet() {}

    static Item item(String label, Icons.Shape icon, Runnable action) {
        return new Item(label, icon, action);
    }

    /** Lista de opciones. */
    static void show(Activity a, Backdrop b, String title, List<Item> items) {
        LinearLayout card = card(a, b, title);
        for (Item it : items) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int px = Ui.dp(a, 10);
            row.setPadding(px, 0, Ui.dp(a, 14), 0);
            row.setBackground(Ui.fill(a, 0x00000000, 18));

            if (it.icon != null) {
                ImageView ic = new ImageView(a);
                Icons.Line d = Icons.of(it.icon);
                if (it.danger) d.setColor(DANGER);
                ic.setImageDrawable(d);
                int p = Ui.dp(a, 9);
                ic.setPadding(p, p, p, p);
                ic.setBackground(Ui.fill(a, Ui.SOFT, 14));
                row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 38)));
            }
            TextView label = Ui.text(a, it.label, 16, it.danger ? DANGER : Ui.TEXT, Ui.MEDIUM);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -2, 1f);
            llp.leftMargin = Ui.dp(a, 14);
            row.addView(label, llp);
            TextView value = Ui.text(a, it.value != null ? it.value.get() : "", 14, Ui.MUTED, Ui.REGULAR);
            row.addView(value);

            Ui.pressable(row, 0.97f);
            row.setOnClickListener(v -> {
                Sound.play(v, it.keepOpen ? Sound.TOGGLE : Sound.TAP);
                if (it.keepOpen) {
                    if (it.action != null) it.action.run();
                    if (it.value != null) value.setText(it.value.get());
                } else {
                    dismiss(a, () -> {
                        if (it.action != null) it.action.run();
                    });
                }
            });
            card.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(a, 58)));
        }
        present(a, card);
    }

    static void show(Activity a, Backdrop b, String title, Item... items) {
        List<Item> l = new ArrayList<>();
        for (Item i : items) l.add(i);
        show(a, b, title, l);
    }

    /** Pregunta de sí o no. */
    static void confirm(Activity a, Backdrop b, String title, String message, String ok, boolean danger, Runnable onOk) {
        LinearLayout card = card(a, b, null);
        int side = Ui.dp(a, 10);
        TextView t = Ui.text(a, title, 19, Ui.TEXT, Ui.SEMIBOLD);
        t.setPadding(side, Ui.dp(a, 6), side, 0);
        card.addView(t);
        if (message != null) {
            TextView m = Ui.text(a, message, 15, Ui.MUTED, Ui.REGULAR);
            m.setLineSpacing(0, 1.25f);
            m.setPadding(side, Ui.dp(a, 10), side, 0);
            card.addView(m);
        }
        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = button(a, "Cancelar", Ui.SOFT, Ui.TEXT);
        TextView yes = button(a, ok, danger ? 0xFFE5484D : 0xFFF5F6F8, danger ? 0xFFFFFFFF : Ui.INK);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1f);
        l1.rightMargin = Ui.dp(a, 5);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1f);
        l2.leftMargin = Ui.dp(a, 5);
        buttons.addView(cancel, l1);
        buttons.addView(yes, l2);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.dp(a, 22);
        card.addView(buttons, blp);
        cancel.setOnClickListener(v -> {
            Sound.play(v, Sound.BACK);
            dismiss(a, null);
        });
        yes.setOnClickListener(v -> {
            Sound.play(v, danger ? Sound.HOLD : Sound.TAP);
            dismiss(a, onOk);
        });
        present(a, card);
    }

    static TextView button(Activity a, String s, int fill, int color) {
        TextView b = Ui.text(a, s, 15, color, Ui.SEMIBOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.fill(a, fill, 18));
        Ui.pressable(b);
        return b;
    }

    /** Aviso corto que aparece arriba y se va solo. */
    static void pill(Activity a, Backdrop b, String text) {
        ViewGroup content = a.findViewById(android.R.id.content);
        TextView p = Ui.text(a, text, 14, Ui.TEXT, Ui.MEDIUM);
        p.setGravity(Gravity.CENTER);
        int ph = Ui.dp(a, 18), pv = Ui.dp(a, 12);
        p.setPadding(ph, pv, ph, pv);
        Glass.apply(p, b, 22, Glass.TINT_STRONG);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = topInset(content) + Ui.dp(a, 10);
        lp.leftMargin = lp.rightMargin = Ui.dp(a, 24);
        content.addView(p, lp);
        p.setAlpha(0f);
        p.setTranslationY(-Ui.dp(a, 30));
        p.animate().alpha(1f).translationY(0).setDuration(380).setInterpolator(new DecelerateInterpolator(2f))
                .withEndAction(() -> p.animate().alpha(0f).translationY(-Ui.dp(a, 16)).setStartDelay(2200)
                        .setDuration(300).withEndAction(() -> content.removeView(p)).start())
                .start();
    }

    /** Cierra la hoja abierta. Devuelve true si había una. */
    static boolean dismissTop(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content.findViewWithTag(TAG) == null) return false;
        dismiss(a, null);
        return true;
    }

    // ---------- interno ----------

    private static LinearLayout card(Activity a, Backdrop b, String title) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 12);
        card.setPadding(p, Ui.dp(a, 10), p, Ui.dp(a, 16));
        Glass.apply(card, b, 30, Glass.TINT_STRONG);
        card.setClickable(true);

        View handle = new View(a);
        handle.setBackground(Ui.fill(a, 0x40FFFFFF, 3));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = Ui.dp(a, 12);
        card.addView(handle, hlp);

        if (title != null) {
            TextView t = Ui.label(a, title);
            t.setPadding(Ui.dp(a, 10), 0, 0, Ui.dp(a, 8));
            card.addView(t);
        }
        return card;
    }

    private static void present(Activity a, LinearLayout card) {
        ViewGroup content = a.findViewById(android.R.id.content);
        View old = content.findViewWithTag(TAG);
        if (old != null) content.removeView(old);

        FrameLayout overlay = new FrameLayout(a);
        overlay.setTag(TAG);
        View dim = new View(a);
        dim.setBackgroundColor(0x80000000);
        dim.setOnClickListener(v -> dismiss(a, null));
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        int m = Ui.dp(a, 10);
        lp.leftMargin = lp.rightMargin = m;
        lp.bottomMargin = bottomInset(content) + m;
        overlay.addView(card, lp);
        content.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(240).start();
        card.setTranslationY(Ui.dp(a, 520));
        card.animate().translationY(0).setDuration(420).setInterpolator(new DecelerateInterpolator(2.6f)).start();
    }

    private static void dismiss(Activity a, Runnable after) {
        ViewGroup content = a.findViewById(android.R.id.content);
        View overlay = content.findViewWithTag(TAG);
        if (overlay == null) {
            if (after != null) after.run();
            return;
        }
        overlay.setTag(null);
        View dim = ((ViewGroup) overlay).getChildAt(0);
        View card = ((ViewGroup) overlay).getChildAt(1);
        dim.animate().alpha(0f).setDuration(220).start();
        card.animate().translationY(card.getHeight() + Ui.dp(a, 40)).setDuration(240)
                .setInterpolator(new AccelerateInterpolator(1.6f))
                .withEndAction(() -> content.removeView(overlay)).start();
        if (after != null) overlay.postDelayed(after, 90);
    }

    static int topInset(View v) {
        WindowInsets in = v.getRootWindowInsets();
        return in == null ? Ui.dp(v.getContext(), 28) : in.getSystemWindowInsetTop();
    }

    static int bottomInset(View v) {
        WindowInsets in = v.getRootWindowInsets();
        return in == null ? Ui.dp(v.getContext(), 24) : in.getSystemWindowInsetBottom();
    }
}
