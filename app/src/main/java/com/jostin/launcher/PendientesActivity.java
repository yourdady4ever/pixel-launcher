package com.jostin.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Lista sencilla de pendientes del negocio. Toca para marcar, mantén presionado para borrar. */
public class PendientesActivity extends Activity {
    private JSONArray tasks;
    private LinearLayout list;
    private EditText input;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.edgeToEdge(this, false);
        try {
            tasks = new JSONArray(Prefs.tasks(this));
        } catch (JSONException e) {
            tasks = new JSONArray();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 18);

        TextView title = Ui.text(this, "PENDIENTES", 26, Ui.TEXT, Ui.THIN);
        title.setLetterSpacing(0.4f);
        root.addView(title);
        root.addView(Ui.text(this, "Ponte en el Mapa", 14, Ui.MUTED, null));

        LinearLayout add = new LinearLayout(this);
        add.setOrientation(LinearLayout.HORIZONTAL);
        add.setGravity(Gravity.CENTER_VERTICAL);
        input = Ui.input(this, "Nuevo pendiente", false);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        add.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView plus = Ui.button(this, "Agregar");
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.leftMargin = Ui.dp(this, 8);
        add.addView(plus, plp);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.dp(this, 18);
        root.addView(add, alp);

        plus.setOnClickListener(v -> {
            Ui.click(v);
            addTask();
        });
        input.setOnEditorActionListener((v, a, e) -> {
            addTask();
            return true;
        });

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
        slp.topMargin = Ui.dp(this, 12);
        root.addView(scroll, slp);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(side, insets.getSystemWindowInsetTop() + Ui.dp(this, 20), side,
                    insets.getSystemWindowInsetBottom() + Ui.dp(this, 16));
            return insets;
        });
        setContentView(root);
        render();
    }

    private void addTask() {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) return;
        try {
            JSONObject o = new JSONObject();
            o.put("t", t);
            o.put("d", false);
            tasks.put(o);
        } catch (JSONException ignored) {
            return;
        }
        input.setText("");
        save();
        render();
    }

    private void save() {
        Prefs.setTasks(this, tasks.toString());
    }

    private void render() {
        list.removeAllViews();
        if (tasks.length() == 0) {
            TextView e = Ui.text(this, "Sin pendientes por ahora.", 15, Ui.MUTED, null);
            e.setPadding(0, Ui.dp(this, 20), 0, 0);
            list.addView(e);
            return;
        }
        for (int i = 0; i < tasks.length(); i++) {
            final int idx = i;
            JSONObject o = tasks.optJSONObject(i);
            if (o == null) continue;
            boolean done = o.optBoolean("d");
            TextView row = Ui.text(this, (done ? "✓   " : "○   ") + o.optString("t"), 16,
                    done ? Ui.MUTED : Ui.TEXT, null);
            if (done) row.setPaintFlags(row.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            row.setBackground(Ui.glass(this, 16));
            int p = Ui.dp(this, 16);
            row.setPadding(p, p, p, p);
            Ui.pressable(row);
            row.setOnClickListener(v -> {
                Ui.click(v);
                try {
                    o.put("d", !o.optBoolean("d"));
                } catch (JSONException ignored) {
                    return;
                }
                save();
                render();
            });
            row.setOnLongClickListener(v -> {
                Ui.open(v);
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("¿Borrar este pendiente?")
                        .setMessage(o.optString("t"))
                        .setPositiveButton("Borrar", (d, w) -> {
                            tasks.remove(idx);
                            save();
                            render();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(this, 8);
            list.addView(row, lp);
        }
    }
}
