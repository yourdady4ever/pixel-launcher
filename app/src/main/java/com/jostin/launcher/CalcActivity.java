package com.jostin.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Calculadora científica y calculadora de materiales de obra, en dos pestañas. */
public class CalcActivity extends Screen {
    private static final String[] MULTI = {"asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "ln(", "log(",
            "e^(", "10^(", "√(", "∛(", "Ans"};
    private static final String OPS = "+−×÷";

    private Segmented tabs;
    private View sciView, obraView;
    private ImageView historyButton;

    // ---- Científica ----
    private final List<String> tokens = new ArrayList<>();
    private boolean evaluated = false, second = false;
    private double ans = 0;
    private TextView exprView, previewView, modeView, secondView;
    private View display;
    private final List<Key> secondKeys = new ArrayList<>();
    private Key degKey, secondKey;

    @Override
    String screenTitle() {
        return "Calculadora";
    }

    @Override
    View createBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        tabs = new Segmented(this, backdrop, "Científica", "Obra");
        body.addView(tabs, new LinearLayout.LayoutParams(-1, Ui.dp(this, 46)));

        FrameLayout stack = new FrameLayout(this);
        sciView = buildScientific();
        obraView = buildObra();
        stack.addView(sciView, new FrameLayout.LayoutParams(-1, -1));
        stack.addView(obraView, new FrameLayout.LayoutParams(-1, -1));
        obraView.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
        slp.topMargin = Ui.dp(this, 12);
        body.addView(stack, slp);

        tabs.setListener(this::showTab);
        historyButton = headerAction(Icons.HISTORY, v -> {
            Sound.play(v, Sound.TAP);
            showHistory();
        });

        loadHistoryAns();
        handleDemo();
        Ui.rise(tabs, 1, 16);
        Ui.rise(stack, 2, 24);
        return body;
    }

    private void showTab(int t) {
        View in = t == 0 ? sciView : obraView, out = t == 0 ? obraView : sciView;
        float d = getResources().getDisplayMetrics().density;
        out.animate().alpha(0f).translationX((t == 0 ? 1 : -1) * 24 * d).setDuration(160).withEndAction(() -> out.setVisibility(View.GONE)).start();
        in.setVisibility(View.VISIBLE);
        in.setAlpha(0f);
        in.setTranslationX((t == 0 ? -1 : 1) * 24 * d);
        in.animate().alpha(1f).translationX(0).setStartDelay(90).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
        historyButton.animate().alpha(t == 0 ? 1f : 0f).setDuration(200).start();
        historyButton.setEnabled(t == 0);
    }

    private void handleDemo() {
        int tab = getIntent().getIntExtra("tab", 0);
        if (tab == 1) {
            tabs.select(1, false);
            sciView.setVisibility(View.GONE);
            obraView.setVisibility(View.VISIBLE);
            historyButton.setAlpha(0f);
        }
        String expr = getIntent().getStringExtra("expr");
        if (expr != null) {
            tokens.clear();
            tokens.addAll(tokenize(expr));
            refresh();
            if (getIntent().getBooleanExtra("eq", false)) exprView.post(() -> equals(exprView));
        }
    }

    // ==================== Científica ====================

    private View buildScientific() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);

        LinearLayout disp = new LinearLayout(this);
        disp.setOrientation(LinearLayout.VERTICAL);
        disp.setGravity(Gravity.BOTTOM);
        int p = Ui.dp(this, 20);
        disp.setPadding(p, Ui.dp(this, 16), p, Ui.dp(this, 18));
        Glass.apply(disp, backdrop, 30);
        display = disp;

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        modeView = tag("DEG");
        secondView = tag("2ND");
        secondView.setAlpha(0f);
        top.addView(modeView);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = Ui.dp(this, 6);
        top.addView(secondView, sl);
        disp.addView(top);

        View spacer = new View(this);
        disp.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        previewView = Ui.text(this, "", 20, Ui.MUTED, Ui.REGULAR);
        previewView.setGravity(Gravity.END);
        previewView.setMaxLines(1);
        disp.addView(previewView, new LinearLayout.LayoutParams(-1, -2));

        exprView = Ui.text(this, "0", 52, Ui.TEXT, Ui.LIGHT);
        exprView.setGravity(Gravity.END | Gravity.BOTTOM);
        exprView.setMaxLines(2);
        exprView.setAutoSizeTextTypeUniformWithConfiguration(22, 56, 2, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 112));
        elp.topMargin = Ui.dp(this, 4);
        disp.addView(exprView, elp);

        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        v.addView(disp, dlp);

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        String[][] sci = {
                {"2nd", "DEG", "sin", "cos", "tan"},
                {"xʸ", "√", "ln", "log", "Ans"},
                {"x²", "π", "e", "(", ")"}
        };
        String[][] num = {
                {"AC", "⌫", "%", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"±", "0", ".", "="}
        };
        int gap = Ui.dp(this, 7);
        for (String[] row : sci) pad.addView(keyRow(row, Ui.dp(this, 44), gap, true), rowParams(Ui.dp(this, 44), gap));
        for (String[] row : num) pad.addView(keyRow(row, Ui.dp(this, 60), gap, false), rowParams(Ui.dp(this, 60), gap));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = Ui.dp(this, 5);
        v.addView(pad, plp);

        modeView.setText(Prefs.degrees(this) ? "DEG" : "RAD");
        degKey.setLabel(Prefs.degrees(this) ? "DEG" : "RAD");
        refresh();
        return v;
    }

    private TextView tag(String s) {
        TextView t = Ui.text(this, s, 11, Ui.TEXT, Ui.SEMIBOLD);
        t.setLetterSpacing(0.15f);
        int h = Ui.dp(this, 8), vv = Ui.dp(this, 4);
        t.setPadding(h, vv, h, vv);
        t.setBackground(Ui.fill(this, 0x1FFFFFFF, 8));
        return t;
    }

    private LinearLayout.LayoutParams rowParams(int h, int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, h);
        lp.topMargin = gap;
        return lp;
    }

    private LinearLayout keyRow(String[] labels, int h, int gap, boolean sci) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            Key k = new Key(this, labels[i], sci);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = gap;
            r.addView(k, lp);
            String id = labels[i];
            if (id.equals("DEG")) degKey = k;
            if (id.equals("2nd")) secondKey = k;
            if (secondLabel(id) != null) secondKeys.add(k);
            k.setOnClickListener(view -> press(k.id, view));
            if (id.equals("⌫")) k.setOnLongClickListener(view -> {
                Sound.play(view, Sound.HOLD);
                clearAll();
                return true;
            });
        }
        return r;
    }

    private static String secondLabel(String id) {
        switch (id) {
            case "sin": return "sin⁻¹";
            case "cos": return "cos⁻¹";
            case "tan": return "tan⁻¹";
            case "ln": return "eˣ";
            case "log": return "10ˣ";
            case "√": return "∛";
            case "x²": return "x!";
            default: return null;
        }
    }

    /** Tecla con su estilo según el tipo: número, operación, igual o científica. */
    final class Key extends FrameLayout {
        final String id;
        private final TextView label;

        Key(Context c, String id, boolean sci) {
            super(c);
            this.id = id;
            int fill;
            int color = Ui.TEXT;
            float size;
            android.graphics.Typeface tf;
            if (sci) {
                fill = 0x0FFFFFFF;
                size = 15;
                tf = Ui.MEDIUM;
                color = 0xFFD9DCE1;
            } else if (id.equals("=")) {
                fill = 0xFFF5F6F8;
                color = Ui.INK;
                size = 30;
                tf = Ui.LIGHT;
            } else if (OPS.contains(id)) {
                fill = 0x2EFFFFFF;
                size = 30;
                tf = Ui.LIGHT;
            } else if (id.equals("AC") || id.equals("⌫") || id.equals("%")) {
                fill = 0x0DFFFFFF;
                size = 18;
                tf = Ui.MEDIUM;
                color = 0xFFD9DCE1;
            } else {
                fill = 0x1AFFFFFF;
                size = 26;
                tf = Ui.REGULAR;
            }
            setBackground(sci ? Ui.outline(c, fill, 0x14FFFFFF, 16) : Ui.fill(c, fill, 22));
            label = Ui.text(c, id, size, color, tf);
            label.setGravity(Gravity.CENTER);
            if (id.equals("⌫")) {
                ImageView ic = new ImageView(c);
                Icons.Line d = Icons.of(Icons.BACKSPACE);
                d.setColor(0xFFD9DCE1);
                ic.setImageDrawable(d);
                int p = Ui.dp(c, 18);
                ic.setPadding(p, p, p, p);
                addView(ic, new LayoutParams(-1, -1));
            } else {
                addView(label, new LayoutParams(-1, -1));
            }
            Ui.pressable(this, 0.9f);
        }

        void setLabel(String s) {
            label.setText(s);
        }

        void setActive(boolean on) {
            setBackground(on ? Ui.fill(getContext(), 0xFFF5F6F8, 16) : Ui.outline(getContext(), 0x0FFFFFFF, 0x14FFFFFF, 16));
            label.setTextColor(on ? Ui.INK : 0xFFD9DCE1);
        }

        /** Cambia la etiqueta con un fundido rápido (para 2nd). */
        void swapLabel(String s) {
            label.animate().alpha(0f).setDuration(80).withEndAction(() -> {
                label.setText(s);
                label.animate().alpha(1f).setDuration(140).start();
            }).start();
        }
    }

    private void press(String id, View v) {
        switch (id) {
            case "2nd":
                Sound.play(v, Sound.TOGGLE);
                second = !second;
                secondKey.setActive(second);
                secondView.animate().alpha(second ? 1f : 0f).setDuration(200).start();
                for (Key k : secondKeys) k.swapLabel(second ? secondLabel(k.id) : k.id);
                return;
            case "DEG":
                Sound.play(v, Sound.TOGGLE);
                boolean deg = !Prefs.degrees(this);
                Prefs.setDegrees(this, deg);
                degKey.setLabel(deg ? "DEG" : "RAD");
                modeView.setText(deg ? "DEG" : "RAD");
                refresh();
                return;
            case "AC":
                Sound.play(v, Sound.BACK);
                clearAll();
                return;
            case "⌫":
                Sound.play(v, Sound.KEY);
                if (evaluated) clearAll();
                else if (!tokens.isEmpty()) tokens.remove(tokens.size() - 1);
                refresh();
                return;
            case "=":
                equals(v);
                return;
            default:
                break;
        }
        Sound.play(v, Sound.KEY);
        String tok = tokenFor(id);
        if (second && secondLabel(id) != null) {
            second = false;
            secondKey.setActive(false);
            secondView.animate().alpha(0f).setDuration(200).start();
            for (Key k : secondKeys) k.swapLabel(k.id);
        }
        if (id.equals("±")) {
            if (evaluated) evaluated = false;
            toggleSign();
            refresh();
            return;
        }
        insert(tok);
    }

    private String tokenFor(String id) {
        boolean s = second && secondLabel(id) != null;
        switch (id) {
            case "sin": return s ? "asin(" : "sin(";
            case "cos": return s ? "acos(" : "cos(";
            case "tan": return s ? "atan(" : "tan(";
            case "ln": return s ? "e^(" : "ln(";
            case "log": return s ? "10^(" : "log(";
            case "√": return s ? "∛(" : "√(";
            case "x²": return s ? "!" : "²";
            case "xʸ": return "^";
            default: return id;
        }
    }

    private static boolean isOp(String t) {
        return t.length() == 1 && OPS.contains(t);
    }

    private static boolean isPostfix(String t) {
        return t.equals("²") || t.equals("!") || t.equals("%") || t.equals("^");
    }

    private void insert(String tok) {
        boolean continuing = isOp(tok) || isPostfix(tok);
        if (evaluated) {
            evaluated = false;
            if (!continuing) tokens.clear();
        }
        String last = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
        if (isOp(tok)) {
            if (last == null) {
                if (!tok.equals("−")) tokens.add(ans != 0 ? "Ans" : "0");
            } else if (isOp(last) || last.equals("^")) {
                boolean unaryMinus = tok.equals("−") && (last.equals("×") || last.equals("÷") || last.equals("^"));
                if (!unaryMinus) tokens.remove(tokens.size() - 1);
            }
        } else if (isPostfix(tok)) {
            if (last == null) tokens.add(ans != 0 ? "Ans" : "0");
            else if (isOp(last)) return;
        } else if (tok.equals(".")) {
            // Evitar dos puntos en el mismo número
            for (int k = tokens.size() - 1; k >= 0; k--) {
                String t = tokens.get(k);
                if (t.equals(".")) return;
                if (!Character.isDigit(t.charAt(0))) break;
            }
            if (last == null || !Character.isDigit(last.charAt(0))) tokens.add("0");
        }
        if (tokens.size() > 120) return;
        tokens.add(tok);
        refresh();
    }

    private void toggleSign() {
        int j = tokens.size();
        while (j > 0) {
            String t = tokens.get(j - 1);
            if (Character.isDigit(t.charAt(0)) || t.equals(".") || t.equals("π") || t.equals("e") || t.equals("Ans")) j--;
            else break;
        }
        if (j > 0 && tokens.get(j - 1).equals("−") && (j - 2 < 0 || isOp(tokens.get(j - 2)) || tokens.get(j - 2).endsWith("("))) {
            tokens.remove(j - 1);
        } else {
            tokens.add(j, "−");
        }
    }

    private void clearAll() {
        tokens.clear();
        evaluated = false;
        previewView.setTextColor(Ui.MUTED);
        refresh();
    }

    private String raw() {
        StringBuilder b = new StringBuilder();
        for (String t : tokens) b.append(t);
        return b.toString();
    }

    /** Texto bonito para la pantalla: espacios alrededor de las operaciones y miles separados. */
    private String pretty() {
        StringBuilder b = new StringBuilder();
        StringBuilder num = new StringBuilder();
        for (int k = 0; k < tokens.size(); k++) {
            String t = tokens.get(k);
            boolean digit = Character.isDigit(t.charAt(0)) || t.equals(".");
            if (digit) {
                num.append(t);
                continue;
            }
            b.append(group(num.toString()));
            num.setLength(0);
            boolean unary = t.equals("−") && (k == 0 || isOp(tokens.get(k - 1)) || tokens.get(k - 1).endsWith("(") || tokens.get(k - 1).equals("^"));
            if (isOp(t) && !unary) b.append(' ').append(t).append(' ');
            else b.append(t);
        }
        b.append(group(num.toString()));
        return b.toString();
    }

    private static String group(String n) {
        if (n.isEmpty()) return n;
        int dot = n.indexOf('.');
        String ip = dot >= 0 ? n.substring(0, dot) : n;
        String fp = dot >= 0 ? n.substring(dot) : "";
        StringBuilder r = new StringBuilder();
        for (int k = 0; k < ip.length(); k++) {
            if (k > 0 && (ip.length() - k) % 3 == 0) r.append(',');
            r.append(ip.charAt(k));
        }
        return r + fp;
    }

    private void refresh() {
        if (exprView == null) return;
        if (tokens.isEmpty()) {
            exprView.setText("0");
            exprView.setTextColor(Ui.FAINT);
            if (!evaluated) previewView.setText("");
            return;
        }
        exprView.setTextColor(Ui.TEXT);
        exprView.setText(pretty());
        if (evaluated) return;
        previewView.setTextColor(Ui.MUTED);
        boolean plainNumber = true;
        for (String t : tokens) {
            if (!(Character.isDigit(t.charAt(0)) || t.equals("."))) {
                plainNumber = false;
                break;
            }
        }
        if (plainNumber) {
            previewView.setText("");
            return;
        }
        try {
            double v = Expr.eval(raw(), Prefs.degrees(this), ans);
            previewView.setText("= " + Expr.format(v));
        } catch (Expr.Error e) {
            previewView.setText("");
        }
    }

    private void equals(View v) {
        if (tokens.isEmpty() || evaluated) {
            Sound.play(v, Sound.TAP);
            return;
        }
        String shown = pretty();
        try {
            double r = Expr.eval(raw(), Prefs.degrees(this), ans);
            Sound.play(v, Sound.EQUALS);
            ans = r;
            addHistory(shown, r);
            previewView.setTextColor(Ui.MUTED);
            previewView.setText(shown + " =");
            tokens.clear();
            tokens.addAll(tokenize(Expr.plain(r)));
            evaluated = true;
            exprView.setTextColor(Ui.TEXT);
            exprView.setText(Expr.format(r));
            exprView.setScaleX(0.86f);
            exprView.setScaleY(0.86f);
            exprView.setAlpha(0.3f);
            exprView.setPivotX(exprView.getWidth());
            exprView.setPivotY(exprView.getHeight());
            exprView.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(0).setDuration(420)
                    .setInterpolator(new OvershootInterpolator(1.6f)).start();
            previewView.setAlpha(0f);
            previewView.animate().alpha(1f).setDuration(300).start();
        } catch (Expr.Error e) {
            Sound.play(v, Sound.ERROR);
            Ui.shake(display);
            previewView.setTextColor(Sheet.DANGER);
            String m = e.getMessage();
            previewView.setText(m == null ? "Revisa la operación" : Character.toUpperCase(m.charAt(0)) + m.substring(1));
        }
    }

    static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        outer:
        while (i < s.length()) {
            for (String m : MULTI) {
                if (s.startsWith(m, i)) {
                    out.add(m);
                    i += m.length();
                    continue outer;
                }
            }
            char c = s.charAt(i);
            out.add(c == '-' ? "−" : c == '*' ? "×" : c == '/' ? "÷" : String.valueOf(c));
            i++;
        }
        return out;
    }

    // ---- Historial ----

    private void loadHistoryAns() {
        try {
            JSONArray h = new JSONArray(Prefs.history(this));
            if (h.length() > 0) ans = h.getJSONObject(0).optDouble("v", 0);
        } catch (Exception ignored) {
            ans = 0;
        }
    }

    private void addHistory(String expr, double v) {
        try {
            JSONArray old = new JSONArray(Prefs.history(this));
            JSONArray h = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("e", expr);
            o.put("r", Expr.format(v));
            o.put("v", v);
            h.put(o);
            for (int k = 0; k < old.length() && k < 29; k++) h.put(old.get(k));
            Prefs.setHistory(this, h.toString());
        } catch (Exception ignored) {
            // sin historial no pasa nada
        }
    }

    private void showHistory() {
        List<Sheet.Item> items = new ArrayList<>();
        try {
            JSONArray h = new JSONArray(Prefs.history(this));
            for (int k = 0; k < h.length() && k < 8; k++) {
                JSONObject o = h.getJSONObject(k);
                double v = o.optDouble("v", 0);
                String e = o.optString("e");
                if (e.length() > 22) e = e.substring(0, 21) + "…";
                Sheet.Item it = Sheet.item(e, null, () -> useValue(v));
                final String r = "= " + o.optString("r");
                it.value = () -> r;
                items.add(it);
            }
        } catch (Exception ignored) {
            // nada
        }
        if (items.isEmpty()) {
            Sheet.pill(this, backdrop, "Todavía no hay operaciones");
            return;
        }
        items.add(Sheet.item("Borrar historial", Icons.TRASH, () -> {
            Prefs.setHistory(this, "[]");
            Sheet.pill(this, backdrop, "Historial borrado");
        }).danger());
        Sheet.show(this, backdrop, "Historial", items);
    }

    private void useValue(double v) {
        if (evaluated || tokens.isEmpty()) tokens.clear();
        evaluated = false;
        String last = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
        if (last != null && !isOp(last) && !last.endsWith("(") && !last.equals("^")) tokens.add("×");
        tokens.addAll(tokenize(Expr.plain(v)));
        refresh();
    }

    // ==================== Obra ====================

    // Por m³ de concreto, cemento gris en bultos de 50 kg: {kg cemento, m³ arena, m³ grava, litros agua}
    private static final String[] FC = {"f'c 150", "f'c 200", "f'c 250"};
    private static final double[][] MIX = {
            {290, 0.55, 0.70, 205},
            {350, 0.50, 0.66, 210},
            {410, 0.46, 0.63, 215}
    };
    private static final double WASTE = 1.05;
    private static final double BLOCKS_PER_M2 = 12.5;  // block 15x20x40 con junta de 1 cm

    private int obraMode = 0, fc = 1;
    private LinearLayout obraFields, obraResults;
    private EditText fLargo, fAncho, fEspesor, fAlto, fHuecos;
    private View fcRow;
    private TextView headline, headlineSub;
    private final List<Row> rows = new ArrayList<>();

    private View buildObra() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);

        Segmented mode = new Segmented(this, backdrop, "Concreto", "Muro de block");
        col.addView(mode, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));

        obraFields = new LinearLayout(this);
        obraFields.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        obraFields.setPadding(p, p, p, p);
        Glass.apply(obraFields, backdrop, 26);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = Ui.dp(this, 12);
        col.addView(obraFields, flp);

        obraResults = new LinearLayout(this);
        obraResults.setOrientation(LinearLayout.VERTICAL);
        obraResults.setPadding(p, Ui.dp(this, 18), p, Ui.dp(this, 10));
        Glass.apply(obraResults, backdrop, 26);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(this, 12);
        col.addView(obraResults, rlp);

        TextView note = Ui.text(this, "Cantidades aproximadas, con 5% de desperdicio. Confirma la dosificación con tu proveedor.", 12, Ui.MUTED, Ui.REGULAR);
        note.setLineSpacing(0, 1.25f);
        note.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 24));
        col.addView(note);

        mode.setListener(m -> {
            obraMode = m;
            buildObraMode(true);
        });
        buildObraMode(false);
        if (getIntent().getBooleanExtra("demo", false)) {
            fLargo.setText("6");
            fAncho.setText("4");
            fEspesor.setText("10");
        }
        return sv;
    }

    private void buildObraMode(boolean animate) {
        obraFields.removeAllViews();
        obraResults.removeAllViews();
        rows.clear();
        if (obraMode == 0) {
            LinearLayout r1 = new LinearLayout(this);
            fLargo = field(r1, "Largo", "m", 0);
            fAncho = field(r1, "Ancho", "m", 1);
            obraFields.addView(r1);
            LinearLayout r2 = new LinearLayout(this);
            fEspesor = field(r2, "Espesor", "cm", 0);
            fcRow = fcSelector(r2);
            LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(-1, -2);
            r2lp.topMargin = Ui.dp(this, 12);
            obraFields.addView(r2, r2lp);

            headline = Ui.text(this, "0 m³", 40, Ui.TEXT, Ui.THIN);
            headlineSub = Ui.text(this, "Volumen de concreto", 13, Ui.MUTED, Ui.REGULAR);
            obraResults.addView(headline);
            obraResults.addView(headlineSub);
            rows.add(row(Icons.BOX, "Cemento", "bultos de 50 kg", 0));
            rows.add(row(Icons.WAVES, "Arena", "m³", 2));
            rows.add(row(Icons.SHAPES, "Grava", "m³", 2));
            rows.add(row(Icons.WAVES, "Agua", "litros", 0));
        } else {
            LinearLayout r1 = new LinearLayout(this);
            fLargo = field(r1, "Largo", "m", 0);
            fAlto = field(r1, "Alto", "m", 1);
            obraFields.addView(r1);
            LinearLayout r2 = new LinearLayout(this);
            fHuecos = field(r2, "Puertas y ventanas", "m²", 0);
            LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(-1, -2);
            r2lp.topMargin = Ui.dp(this, 12);
            obraFields.addView(r2, r2lp);

            headline = Ui.text(this, "0 m²", 40, Ui.TEXT, Ui.THIN);
            headlineSub = Ui.text(this, "Área del muro", 13, Ui.MUTED, Ui.REGULAR);
            obraResults.addView(headline);
            obraResults.addView(headlineSub);
            rows.add(row(Icons.WALL, "Block 15×20×40", "piezas", 0));
        }
        if (animate) {
            Ui.rise(obraFields, 0, 14);
            Ui.rise(obraResults, 1, 18);
        }
        recompute();
    }

    private EditText field(LinearLayout parent, String label, String unit, int index) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 14);
        box.setPadding(p, Ui.dp(this, 11), p, Ui.dp(this, 8));
        box.setBackground(Ui.fill(this, Ui.SOFT, 18));
        box.addView(Ui.label(this, label));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.BOTTOM);
        EditText e = new EditText(this);
        Ui.numericInput(e);
        e.setBackground(null);
        e.setPadding(0, Ui.dp(this, 4), 0, 0);
        e.setTextSize(24);
        e.setTypeface(Ui.LIGHT);
        e.setTextColor(Ui.TEXT);
        e.setHint("0");
        e.setHintTextColor(0x40FFFFFF);
        e.setSingleLine(true);
        line.addView(e, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView u = Ui.text(this, unit, 14, Ui.MUTED, Ui.MEDIUM);
        u.setPadding(Ui.dp(this, 6), 0, 0, Ui.dp(this, 8));
        line.addView(u);
        box.addView(line);

        e.setOnFocusChangeListener((v, has) ->
                box.setBackground(has ? Ui.outline(this, 0x24FFFFFF, 0x59FFFFFF, 18) : Ui.fill(this, Ui.SOFT, 18)));
        box.setOnClickListener(v -> {
            e.requestFocus();
            android.view.inputmethod.InputMethodManager im =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(e, 0);
        });
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                recompute();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (index > 0) lp.leftMargin = Ui.dp(this, 10);
        parent.addView(box, lp);
        return e;
    }

    private View fcSelector(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 14);
        box.setPadding(p, Ui.dp(this, 11), Ui.dp(this, 12), Ui.dp(this, 11));
        box.setBackground(Ui.fill(this, Ui.SOFT, 18));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.label(this, "Resistencia"), new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView sw = new ImageView(this);
        Icons.Line sd = Icons.of(Icons.SWAP);
        sd.setColor(Ui.MUTED);
        sw.setImageDrawable(sd);
        head.addView(sw, new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        box.addView(head);
        TextView val = Ui.text(this, FC[fc], 22, Ui.TEXT, Ui.LIGHT);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.topMargin = Ui.dp(this, 8);
        box.addView(val, vlp);
        Ui.pressable(box, 0.96f);
        box.setOnClickListener(v -> {
            Sound.play(v, Sound.TOGGLE);
            fc = (fc + 1) % FC.length;
            val.setText(FC[fc]);
            val.setAlpha(0f);
            val.setTranslationY(Ui.dp(this, 6));
            val.animate().alpha(1f).translationY(0).setDuration(240).start();
            recompute();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.leftMargin = Ui.dp(this, 10);
        parent.addView(box, lp);
        return box;
    }

    /** Fila de resultado con número animado. */
    final class Row {
        final TextView value;
        final int decimals;
        double shown = 0;
        ValueAnimator anim;

        Row(TextView value, int decimals) {
            this.value = value;
            this.decimals = decimals;
        }

        void set(double target) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat((float) shown, (float) target);
            anim.setDuration(420);
            anim.setInterpolator(new DecelerateInterpolator(2f));
            anim.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                value.setText(fmt(shown, decimals));
            });
            anim.start();
        }
    }

    private Row row(Icons.Shape icon, String name, String unit, int decimals) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(Icons.of(icon));
        int p = Ui.dp(this, 9);
        ic.setPadding(p, p, p, p);
        ic.setBackground(Ui.fill(this, Ui.SOFT, 14));
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, name, 15, Ui.TEXT, Ui.MEDIUM));
        TextView u = Ui.text(this, unit, 12, Ui.MUTED, Ui.REGULAR);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(-2, -2);
        ulp.topMargin = Ui.dp(this, 3);
        texts.addView(u, ulp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(this, 12);
        r.addView(texts, tlp);
        TextView v = Ui.text(this, "0", 26, Ui.TEXT, Ui.LIGHT);
        r.addView(v);

        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, 1) / 2));
        llp.topMargin = Ui.dp(this, 14);
        obraResults.addView(line, llp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(this, 12);
        obraResults.addView(r, rlp);
        return new Row(v, decimals);
    }

    private double headlineShown = 0;
    private ValueAnimator headlineAnim;

    private void recompute() {
        if (rows.isEmpty()) return;
        double base;
        double[] values;
        String unit;
        if (obraMode == 0) {
            double vol = num(fLargo) * num(fAncho) * num(fEspesor) / 100.0;
            base = vol;
            unit = " m³";
            double v5 = vol * WASTE;
            double[] m = MIX[fc];
            values = new double[]{Math.ceil(v5 * m[0] / 50.0), v5 * m[1], v5 * m[2], v5 * m[3]};
        } else {
            double area = Math.max(0, num(fLargo) * num(fAlto) - num(fHuecos));
            base = area;
            unit = " m²";
            values = new double[]{Math.ceil(area * BLOCKS_PER_M2 * WASTE)};
        }
        for (int k = 0; k < rows.size(); k++) rows.get(k).set(values[k]);
        if (headlineAnim != null) headlineAnim.cancel();
        headlineAnim = ValueAnimator.ofFloat((float) headlineShown, (float) base);
        headlineAnim.setDuration(420);
        headlineAnim.setInterpolator(new DecelerateInterpolator(2f));
        final String u = unit;
        headlineAnim.addUpdateListener(a -> {
            headlineShown = (float) a.getAnimatedValue();
            headline.setText(fmt(headlineShown, 2) + u);
        });
        headlineAnim.start();
    }

    private static String fmt(double v, int decimals) {
        String pattern = decimals == 0 ? "%,.0f" : "%,." + decimals + "f";
        String s = String.format(Locale.US, pattern, v);
        if (decimals > 0 && s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private static double num(EditText e) {
        if (e == null) return 0;
        try {
            return Double.parseDouble(e.getText().toString().trim().replace(",", "."));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
