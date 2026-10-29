package com.jostin.launcher;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Hojas de vidrio que suben desde abajo. Se pueden cerrar arrastrándolas hacia abajo. */
final class Sheet {
    private static final String TAG = "jostin_sheet";
    static final int DANGER = 0xFFFF8A8A;

    interface Value {
        String get();
    }

    interface TextDone {
        void done(String text);
    }

    static final class Item {
        final String label;
        final Icons.Shape icon;
        final Runnable action;
        Value value;
        boolean keepOpen;
        boolean danger;
        android.graphics.drawable.Drawable drawable;

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

        /** Usa una imagen (el ícono de una app, por ejemplo) en lugar del ícono de línea. */
        Item drawable(android.graphics.drawable.Drawable d) {
            drawable = d;
            return this;
        }
    }

    private Sheet() {}

    static Item item(String label, Icons.Shape icon, Runnable action) {
        return new Item(label, icon, action);
    }

    static void show(Activity a, Backdrop b, String title, Item... items) {
        List<Item> l = new ArrayList<>();
        for (Item i : items) l.add(i);
        show(a, b, title, l);
    }

    static void show(Activity a, Backdrop b, String title, List<Item> items) {
        show(a, b, title, null, items);
    }

    /** Lista de opciones; si son muchas se puede desplazar. header va arriba de la lista (puede ser null). */
    static void show(Activity a, Backdrop b, String title, View header, List<Item> items) {
        LinearLayout card = card(a, b, title);
        if (header != null) card.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        for (Item it : items) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 14), 0);
            if (it.drawable != null) {
                ImageView ic = new ImageView(a);
                ic.setImageDrawable(it.drawable);
                int p = Ui.dp(a, 3);
                ic.setPadding(p, p, p, p);
                row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 38)));
            } else if (it.icon != null) {
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
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -2, 1f);
            llp.leftMargin = Ui.dp(a, it.icon != null || it.drawable != null ? 14 : 4);
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
                    dismiss(a, it.action);
                }
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(a, 58)));
        }
        ScrollView sv = new ScrollView(a) {
            @Override
            protected void onMeasure(int w, int h) {
                int max = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.62f);
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
            }
        };
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list);
        card.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        present(a, card, 0x80000000);
    }

    /** Hoja con contenido propio y desplazable, alta hasta la fracción de pantalla indicada. */
    static void panel(Activity a, Backdrop b, View header, View content, float maxFraction, int dim) {
        LinearLayout card = card(a, b, null);
        if (header != null) card.addView(header, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(a) {
            @Override
            protected void onMeasure(int w, int h) {
                int max = (int) (a.getResources().getDisplayMetrics().heightPixels * maxFraction);
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
            }
        };
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(content);
        card.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        present(a, card, dim);
    }

    /** Pregunta de sí o no. */
    static void confirm(Activity a, Backdrop b, String title, String message, String ok, boolean danger, Runnable onOk) {
        confirm(a, b, title, message, ok, danger, onOk, null);
    }

    /** Igual, y onCancel corre si eligen «Cancelar» (o el botón que diga cancelText). */
    static void confirm(Activity a, Backdrop b, String title, String message, String ok, boolean danger, Runnable onOk, Runnable onCancel) {
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
        TextView cancel = button(a, onCancel != null ? "Deshacer" : "Cancelar", Ui.SOFT, Ui.TEXT);
        TextView yes = button(a, ok, danger ? 0xFFE5484D : Theme.accent, danger ? 0xFFFFFFFF : Theme.onAccent);
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
            dismiss(a, onCancel);
        });
        yes.setOnClickListener(v -> {
            Sound.play(v, danger ? Sound.HOLD : Sound.TAP);
            dismiss(a, onOk);
        });
        present(a, card, 0x80000000);
    }

    /** Pide un texto (tu ciudad, un saludo...). */
    static void prompt(Activity a, Backdrop b, String title, String hint, String initial, TextDone done) {
        prompt(a, b, title, hint, initial, false, done);
    }

    /** Igual, pero puede ser de varias líneas (para notas). */
    static void prompt(Activity a, Backdrop b, String title, String hint, String initial, boolean multi, TextDone done) {
        LinearLayout card = card(a, b, null);
        int side = Ui.dp(a, 10);
        TextView t = Ui.text(a, title, 19, Ui.TEXT, Ui.SEMIBOLD);
        t.setPadding(side, Ui.dp(a, 6), side, Ui.dp(a, 14));
        card.addView(t);
        EditText e = new EditText(a);
        e.setText(initial);
        e.setSelection(e.getText().length());
        e.setHint(hint);
        e.setHintTextColor(0x66FFFFFF);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(17);
        e.setTypeface(Ui.REGULAR);
        if (multi) {
            e.setSingleLine(false);
            e.setMinLines(3);
            e.setMaxLines(7);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        } else {
            e.setSingleLine(true);
            e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        }
        int p = Ui.dp(a, 16);
        e.setPadding(p, p, p, p);
        e.setBackground(Ui.outline(a, Ui.SOFT, 0x33FFFFFF, 16));
        card.addView(e, new LinearLayout.LayoutParams(-1, -2));
        TextView ok = button(a, "Guardar", Theme.accent, Theme.onAccent);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 52));
        olp.topMargin = Ui.dp(a, 14);
        card.addView(ok, olp);
        Runnable save = () -> {
            hideKeyboard(a, e);
            String v = e.getText().toString().trim();
            dismiss(a, () -> done.done(v));
        };
        ok.setOnClickListener(v -> {
            Sound.play(v, Sound.TAP);
            save.run();
        });
        if (!multi) e.setOnEditorActionListener((v, act, ev) -> {
            save.run();
            return true;
        });
        present(a, card, 0x80000000);
        e.postDelayed(() -> {
            e.requestFocus();
            InputMethodManager im = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(e, 0);
        }, 250);
    }

    static void hideKeyboard(Activity a, View v) {
        InputMethodManager im = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
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
                .withEndAction(() -> p.animate().alpha(0f).translationY(-Ui.dp(a, 16)).setStartDelay(2400)
                        .setDuration(300).withEndAction(() -> content.removeView(p)).start())
                .start();
    }

    static boolean isOpen(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        return content.findViewWithTag(TAG) != null;
    }

    static boolean dismissTop(Activity a) {
        if (!isOpen(a)) return false;
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

    private static void present(Activity a, LinearLayout card, int dimColor) {
        ViewGroup content = a.findViewById(android.R.id.content);
        View old = content.findViewWithTag(TAG);
        if (old != null) content.removeView(old);

        FrameLayout overlay = new FrameLayout(a);
        overlay.setTag(TAG);
        View dim = new View(a);
        dim.setBackgroundColor(dimColor);
        dim.setOnClickListener(v -> {
            Sound.play(v, Sound.BACK);
            hideKeyboard(a, v);
            dismiss(a, null);
        });
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        int m = Ui.dp(a, 10);
        lp.leftMargin = lp.rightMargin = m;
        lp.bottomMargin = bottomInset(content) + m;
        overlay.addView(card, lp);
        // Si sale el teclado, la hoja sube con él
        overlay.setOnApplyWindowInsetsListener((v, in) -> {
            int bottom = in.getSystemWindowInsetBottom();
            ((FrameLayout.LayoutParams) card.getLayoutParams()).bottomMargin = bottom + m;
            card.requestLayout();
            return in;
        });
        content.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.requestApplyInsets();
        dragToDismiss(a, card);

        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(240).start();
        card.setTranslationY(Ui.dp(a, 600));
        card.animate().translationY(0).setDuration(440).setInterpolator(new DecelerateInterpolator(2.6f)).start();
    }

    /** Arrastrar la parte de arriba de la hoja hacia abajo la cierra. */
    private static void dragToDismiss(Activity a, LinearLayout card) {
        final float[] start = new float[2];
        final boolean[] dragging = {false};
        final VelocityTracker[] vt = {null};
        int zone = Ui.dp(a, 56);
        card.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (e.getY() > zone) return false;
                    start[0] = e.getRawY();
                    start[1] = card.getTranslationY();
                    dragging[0] = true;
                    vt[0] = VelocityTracker.obtain();
                    vt[0].addMovement(e);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) return false;
                    vt[0].addMovement(e);
                    card.setTranslationY(Math.max(0, start[1] + e.getRawY() - start[0]));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging[0]) return false;
                    dragging[0] = false;
                    vt[0].addMovement(e);
                    vt[0].computeCurrentVelocity(1000);
                    float vy = vt[0].getYVelocity();
                    vt[0].recycle();
                    if (card.getTranslationY() > card.getHeight() * 0.25f || vy > 1400) {
                        Sound.play(card, Sound.BACK);
                        dismiss(a, null);
                    } else {
                        card.animate().translationY(0).setDuration(280).setInterpolator(new DecelerateInterpolator(2f)).start();
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    static void dismiss(Activity a, Runnable after) {
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
