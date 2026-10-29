package com.jostin.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Pendientes del negocio. Toca para marcar, mantén presionado para borrar. */
public class PendientesActivity extends Screen {
    private JSONArray tasks;
    private LinearLayout list;
    private EditText input;
    private TextView counter;

    @Override
    String screenTitle() {
        return "Pendientes";
    }

    @Override
    String screenSubtitle() {
        return "Ponte en el Mapa";
    }

    @Override
    View createBody() {
        try {
            tasks = new JSONArray(Prefs.tasks(this));
        } catch (JSONException e) {
            tasks = new JSONArray();
        }
        if (getIntent().getBooleanExtra("demo", false) && tasks.length() == 0) {
            String[] demo = {"Mandar cotización al Salón Jardín", "Diseñar invitación XV años de Fernanda",
                    "Subir galería a la página de la boda", "Llamar a proveedor de block"};
            for (int k = 0; k < demo.length; k++) {
                JSONObject o = new JSONObject();
                try {
                    o.put("t", demo[k]);
                    o.put("d", k == 2);
                } catch (JSONException ignored) {
                    continue;
                }
                tasks.put(o);
            }
            save();
        }

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout add = new LinearLayout(this);
        add.setOrientation(LinearLayout.HORIZONTAL);
        add.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 6);
        add.setPadding(Ui.dp(this, 18), p, p, p);
        Glass.apply(add, backdrop, 28);
        input = new EditText(this);
        input.setHint("Agregar pendiente");
        input.setHintTextColor(0x66FFFFFF);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(16);
        input.setTypeface(Ui.REGULAR);
        input.setBackground(null);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        add.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView plus = new ImageView(this);
        Icons.Line pd = Icons.of(Icons.PLUS);
        pd.setColor(Theme.onAccent);
        plus.setImageDrawable(pd);
        int pp = Ui.dp(this, 12);
        plus.setPadding(pp, pp, pp, pp);
        plus.setBackground(Ui.fill(this, Theme.accent, 22));
        Ui.pressable(plus, 0.86f);
        add.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        body.addView(add, new LinearLayout.LayoutParams(-1, -2));

        plus.setOnClickListener(v -> addTask(v));
        input.setOnEditorActionListener((v, a, e) -> {
            addTask(v);
            return true;
        });

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(Ui.dp(this, 6), Ui.dp(this, 18), Ui.dp(this, 2), Ui.dp(this, 6));
        counter = Ui.label(this, "");
        meta.addView(counter, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clean = Ui.text(this, "Quitar hechos", 13, Ui.MUTED, Ui.MEDIUM);
        clean.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        clean.setBackground(Ui.fill(this, Ui.SOFT, 14));
        Ui.pressable(clean);
        clean.setOnClickListener(v -> clearDone(v));
        meta.addView(clean);
        body.addView(meta);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, Ui.dp(this, 24));
        scroll.addView(list);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Ui.rise(add, 1, 16);
        Ui.rise(meta, 2, 16);
        render(true);
        return body;
    }

    private void addTask(View from) {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) {
            Sound.play(from, Sound.ERROR);
            Ui.shake(input);
            return;
        }
        Sound.play(from, Sound.TAP);
        try {
            JSONObject o = new JSONObject();
            o.put("t", t);
            o.put("d", false);
            // Lo nuevo va hasta arriba
            JSONArray n = new JSONArray();
            n.put(o);
            for (int k = 0; k < tasks.length(); k++) n.put(tasks.get(k));
            tasks = n;
        } catch (JSONException ignored) {
            return;
        }
        input.setText("");
        save();
        render(false);
        if (list.getChildCount() > 0) {
            View first = list.getChildAt(0);
            first.setAlpha(0f);
            first.setTranslationY(-Ui.dp(this, 16));
            first.animate().alpha(1f).translationY(0).setDuration(380).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    private void clearDone(View v) {
        JSONArray n = new JSONArray();
        int removed = 0;
        for (int k = 0; k < tasks.length(); k++) {
            JSONObject o = tasks.optJSONObject(k);
            if (o == null) continue;
            if (o.optBoolean("d")) removed++;
            else n.put(o);
        }
        if (removed == 0) {
            Sound.play(v, Sound.TAP);
            Sheet.pill(this, backdrop, "No hay pendientes terminados");
            return;
        }
        Sound.play(v, Sound.BACK);
        tasks = n;
        save();
        render(false);
        Sheet.pill(this, backdrop, removed == 1 ? "Se quitó 1 pendiente" : "Se quitaron " + removed + " pendientes");
    }

    private void save() {
        Prefs.setTasks(this, tasks.toString());
    }

    private void updateCounter() {
        int pending = 0, done = 0;
        for (int k = 0; k < tasks.length(); k++) {
            JSONObject o = tasks.optJSONObject(k);
            if (o == null) continue;
            if (o.optBoolean("d")) done++;
            else pending++;
        }
        counter.setText((pending + (pending == 1 ? " pendiente" : " pendientes") + "  ·  " + done + (done == 1 ? " hecho" : " hechos")).toUpperCase(MainActivity.ES));
    }

    private void render(boolean entrance) {
        list.removeAllViews();
        updateCounter();
        if (tasks.length() == 0) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(0, Ui.dp(this, 60), 0, 0);
            ImageView ic = new ImageView(this);
            Icons.Line d = Icons.of(Icons.CHECK);
            d.setColor(0x66FFFFFF);
            ic.setImageDrawable(d);
            empty.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
            TextView t = Ui.text(this, "Todo al día", 18, Ui.TEXT, Ui.MEDIUM);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.topMargin = Ui.dp(this, 12);
            empty.addView(t, tlp);
            TextView s = Ui.text(this, "Escribe arriba lo que tengas que hacer", 14, Ui.MUTED, Ui.REGULAR);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.dp(this, 6);
            empty.addView(s, slp);
            list.addView(empty);
            if (entrance) Ui.rise(empty, 3, 20);
            return;
        }
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject o = tasks.optJSONObject(i);
            if (o == null) continue;
            View row = taskRow(o);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(this, 8);
            list.addView(row, lp);
            if (entrance) Ui.rise(row, 3 + i, 18);
        }
    }

    private View taskRow(JSONObject o) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 14);
        row.setPadding(p, p, Ui.dp(this, 18), p);
        Glass.apply(row, backdrop, 22);

        boolean done = o.optBoolean("d");
        CheckView check = new CheckView(this, done);
        row.addView(check, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        TextView text = Ui.text(this, o.optString("t"), 16, done ? Ui.MUTED : Ui.TEXT, Ui.REGULAR);
        text.setLineSpacing(0, 1.2f);
        if (done) text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(this, 14);
        row.addView(text, tlp);

        Ui.pressable(row, 0.97f);
        row.setOnClickListener(v -> {
            boolean now = !o.optBoolean("d");
            try {
                o.put("d", now);
            } catch (JSONException ignored) {
                return;
            }
            Sound.play(v, now ? Sound.CHECK : Sound.TAP);
            check.setChecked(now, true);
            text.setTextColor(now ? Ui.MUTED : Ui.TEXT);
            if (now) text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            else text.setPaintFlags(text.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            save();
            updateCounter();
        });
        row.setOnLongClickListener(v -> {
            Sound.play(v, Sound.HOLD);
            Sheet.confirm(this, backdrop, "¿Borrar este pendiente?", o.optString("t"), "Borrar", true, () -> remove(o, row));
            return true;
        });
        return row;
    }

    private void remove(JSONObject target, View row) {
        JSONArray n = new JSONArray();
        for (int k = 0; k < tasks.length(); k++) {
            JSONObject o = tasks.optJSONObject(k);
            if (o != null && o != target) n.put(o);
        }
        tasks = n;
        save();
        // Se desliza a la derecha y la lista se cierra
        int h = row.getHeight();
        row.animate().translationX(row.getWidth()).alpha(0f).setDuration(260).withEndAction(() -> {
            ValueAnimator a = ValueAnimator.ofInt(h, 0);
            a.setDuration(220);
            a.addUpdateListener(x -> {
                ViewGroup.LayoutParams lp = row.getLayoutParams();
                lp.height = (int) x.getAnimatedValue();
                row.setLayoutParams(lp);
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator an) {
                    render(false);
                }
            });
            a.start();
        }).start();
    }

    /** Círculo que se llena y dibuja una palomita animada. */
    static final class CheckView extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tickPath = new Path();
        private final Path partial = new Path();
        private float progress;

        CheckView(Context c, boolean checked) {
            super(c);
            progress = checked ? 1f : 0f;
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(0x80FFFFFF);
            fill.setColor(Theme.accent);
            tick.setStyle(Paint.Style.STROKE);
            tick.setColor(Theme.onAccent);
            tick.setStrokeCap(Paint.Cap.ROUND);
            tick.setStrokeJoin(Paint.Join.ROUND);
        }

        void setChecked(boolean on, boolean animate) {
            float target = on ? 1f : 0f;
            if (!animate) {
                progress = target;
                invalidate();
                return;
            }
            ValueAnimator a = ValueAnimator.ofFloat(progress, target);
            a.setDuration(on ? 380 : 200);
            a.setInterpolator(new DecelerateInterpolator(1.6f));
            a.addUpdateListener(x -> {
                progress = (float) x.getAnimatedValue();
                invalidate();
            });
            a.start();
            if (on) {
                setScaleX(0.8f);
                setScaleY(0.8f);
                animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            float s = w / 24f;
            tickPath.reset();
            tickPath.moveTo(7 * s, 12.5f * s);
            tickPath.lineTo(10.5f * s, 16f * s);
            tickPath.lineTo(17.2f * s, 8.6f * s);
            ring.setStrokeWidth(1.6f * s);
            tick.setStrokeWidth(2.2f * s);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = getWidth() / 2f - ring.getStrokeWidth();
            c.drawCircle(cx, cy, r, ring);
            if (progress <= 0) return;
            float fp = Math.min(1f, progress * 1.6f);
            fill.setAlpha((int) (255 * fp));
            c.drawCircle(cx, cy, r * (0.4f + 0.6f * fp) + ring.getStrokeWidth() / 2, fill);
            float tp = Math.max(0f, (progress - 0.35f) / 0.65f);
            if (tp > 0) {
                PathMeasure pm = new PathMeasure(tickPath, false);
                partial.reset();
                float len = 0;
                // La palomita tiene dos tramos; se recorren en orden
                do {
                    len += pm.getLength();
                } while (pm.nextContour());
                pm = new PathMeasure(tickPath, false);
                pm.getSegment(0, len * tp, partial, true);
                c.drawPath(partial, tick);
            }
        }
    }
}
