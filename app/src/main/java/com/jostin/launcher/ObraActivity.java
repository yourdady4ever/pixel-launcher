package com.jostin.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Calculadora rápida de materiales. Las cantidades son aproximadas para estimar en obra;
 * la dosificación real depende del proveedor, el agregado y el tipo de cemento.
 */
public class ObraActivity extends Activity {
    // Por m³ de concreto, cemento gris en bultos de 50 kg: {kg cemento, m³ arena, m³ grava, litros agua}
    private static final String[] FC = {"f'c 150", "f'c 200", "f'c 250"};
    private static final double[][] MIX = {
            {290, 0.55, 0.70, 205},
            {350, 0.50, 0.66, 210},
            {410, 0.46, 0.63, 215}
    };
    private static final double WASTE = 1.05;
    private static final double BLOCKS_PER_M2 = 12.5; // block 15x20x40 con junta de 1 cm

    private LinearLayout body;
    private int mode = 0, fc = 1;
    private final TextView[] modeChips = new TextView[2];

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.edgeToEdge(this, false);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 18);
        root.setPadding(side, 0, side, Ui.dp(this, 24));
        scroll.addView(root);

        TextView title = Ui.text(this, "OBRA", 26, Ui.TEXT, Ui.THIN);
        title.setLetterSpacing(0.4f);
        root.addView(title);
        TextView sub = Ui.text(this, "Calculadora de materiales", 14, Ui.MUTED, null);
        root.addView(sub);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Concreto", "Muro de block"};
        for (int i = 0; i < 2; i++) {
            final int m = i;
            TextView chip = Ui.button(this, names[i]);
            chip.setOnClickListener(v -> {
                Ui.click(v);
                mode = m;
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(i == 0 ? 0 : Ui.dp(this, 6), Ui.dp(this, 18), i == 0 ? Ui.dp(this, 6) : 0, 0);
            chips.addView(chip, lp);
            modeChips[i] = chip;
        }
        root.addView(chips);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body);

        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(side, insets.getSystemWindowInsetTop() + Ui.dp(this, 20), side,
                    insets.getSystemWindowInsetBottom() + Ui.dp(this, 24));
            return insets;
        });
        setContentView(scroll);
        render();
    }

    private void render() {
        for (int i = 0; i < 2; i++) Ui.chip(this, modeChips[i], i == mode);
        body.removeAllViews();
        if (mode == 0) concrete();
        else wall();
    }

    private void concrete() {
        EditText l = field("Largo (m)");
        EditText a = field("Ancho (m)");
        EditText e = field("Espesor (cm)");

        LinearLayout chips = new LinearLayout(this);
        TextView[] fcChips = new TextView[FC.length];
        for (int i = 0; i < FC.length; i++) {
            final int k = i;
            TextView chip = Ui.button(this, FC[i]);
            fcChips[i] = chip;
            chip.setOnClickListener(v -> {
                Ui.click(v);
                fc = k;
                for (int j = 0; j < fcChips.length; j++) Ui.chip(this, fcChips[j], j == fc);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(i == 0 ? 0 : Ui.dp(this, 4), Ui.dp(this, 10), i == FC.length - 1 ? 0 : Ui.dp(this, 4), 0);
            chips.addView(chip, lp);
        }
        for (int j = 0; j < fcChips.length; j++) Ui.chip(this, fcChips[j], j == fc);
        body.addView(chips);

        TextView result = resultBox();
        calcButton(v -> {
            double vol = num(l) * num(a) * num(e) / 100.0;
            if (vol <= 0) {
                result.setText("Escribe largo, ancho y espesor.");
                return;
            }
            double v5 = vol * WASTE;
            double[] m = MIX[fc];
            int bultos = (int) Math.ceil(v5 * m[0] / 50.0);
            result.setText(String.format(Locale.US,
                    "Volumen: %.2f m³ (%.2f m³ con 5%% de desperdicio)\n\n"
                            + "Cemento: %d bultos de 50 kg\n"
                            + "Arena: %.2f m³\n"
                            + "Grava: %.2f m³\n"
                            + "Agua: %.0f litros\n\n"
                            + "Aproximado para %s. Confirma con la dosificación de tu proveedor.",
                    vol, v5, bultos, v5 * m[1], v5 * m[2], v5 * m[3], FC[fc]));
        });
        body.addView(result);
    }

    private void wall() {
        EditText l = field("Largo del muro (m)");
        EditText h = field("Alto del muro (m)");
        EditText holes = field("Huecos de puertas y ventanas (m²), opcional");
        TextView result = resultBox();
        calcButton(v -> {
            double area = num(l) * num(h) - num(holes);
            if (area <= 0) {
                result.setText("Escribe largo y alto del muro.");
                return;
            }
            int blocks = (int) Math.ceil(area * BLOCKS_PER_M2 * WASTE);
            result.setText(String.format(Locale.US,
                    "Área: %.2f m²\n\nBlock 15x20x40: %d piezas (con 5%% de desperdicio)\n\n"
                            + "Aproximado con junta de 1 cm.",
                    area, blocks));
        });
        body.addView(result);
    }

    private EditText field(String hint) {
        EditText e = Ui.input(this, hint, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 10);
        body.addView(e, lp);
        return e;
    }

    private void calcButton(View.OnClickListener action) {
        TextView b = Ui.button(this, "Calcular");
        b.setBackground(Ui.fill(this, Ui.ACCENT, 16));
        b.setTextColor(0xFF111111);
        b.setOnClickListener(v -> {
            Ui.click(v);
            action.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 16);
        body.addView(b, lp);
    }

    private TextView resultBox() {
        TextView r = Ui.text(this, "", 16, Ui.TEXT, null);
        r.setLineSpacing(0, 1.2f);
        r.setBackground(Ui.glass(this, 18));
        int p = Ui.dp(this, 16);
        r.setPadding(p, p, p, p);
        r.setGravity(Gravity.START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 14);
        r.setLayoutParams(lp);
        r.setText("El resultado aparece aquí.");
        return r;
    }

    private static double num(EditText e) {
        try {
            return Double.parseDouble(e.getText().toString().trim().replace(",", "."));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
