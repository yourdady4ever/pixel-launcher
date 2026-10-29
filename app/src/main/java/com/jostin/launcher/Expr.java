package com.jostin.launcher;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Evalúa expresiones de la calculadora científica.
 * Entiende + − × ÷ ^, paréntesis (cierra solos los que falten), multiplicación implícita (2π, 3(4)),
 * % ! ² ³, √ ∛, π e, Ans, sin cos tan y sus inversas, ln log, eˣ (e^) y 10ˣ (10^).
 */
final class Expr {
    static final class Error extends Exception {
        Error(String m) {
            super(m);
        }
    }

    private final String s;
    private final boolean degrees;
    private final double ans;
    private int i = 0;

    private Expr(String s, boolean degrees, double ans) {
        this.s = s;
        this.degrees = degrees;
        this.ans = ans;
    }

    static double eval(String text, boolean degrees, double ans) throws Error {
        if (text.trim().isEmpty()) throw new Error("vacío");
        Expr e = new Expr(text.replace(" ", ""), degrees, ans);
        double v = e.expr();
        if (e.i < e.s.length()) throw new Error("sobra " + e.s.substring(e.i));
        if (Double.isNaN(v)) throw new Error("indefinido");
        return clean(v);
    }

    /** Quita el ruido de punto flotante (0.1 + 0.2 = 0.3). */
    static double clean(double v) {
        if (Double.isInfinite(v) || Double.isNaN(v) || v == 0) return v;
        if (Math.abs(v) < 1e-13) return 0;
        return new BigDecimal(v).round(new MathContext(13)).doubleValue();
    }

    private boolean eat(String tok) {
        if (s.startsWith(tok, i)) {
            i += tok.length();
            return true;
        }
        return false;
    }

    private boolean atEnd() {
        return i >= s.length();
    }

    private double expr() throws Error {
        double v = term();
        while (true) {
            if (eat("+")) v += term();
            else if (eat("−") || eat("-")) v -= term();
            else return v;
        }
    }

    private double term() throws Error {
        double v = unary();
        while (true) {
            if (eat("×") || eat("*")) v *= unary();
            else if (eat("÷") || eat("/")) {
                double d = unary();
                if (d == 0) throw new Error("división entre cero");
                v /= d;
            } else if (startsPrimary()) v *= power();  // multiplicación implícita
            else return v;
        }
    }

    private double unary() throws Error {
        if (eat("−") || eat("-")) return -unary();
        if (eat("+")) return unary();
        return power();
    }

    private double power() throws Error {
        double base = postfix();
        if (eat("^")) {
            double ex = unary();  // asociativo a la derecha: 2^3^2 = 2^9
            return Math.pow(base, ex);
        }
        return base;
    }

    private double postfix() throws Error {
        double v = primary();
        while (true) {
            if (eat("!")) v = factorial(v);
            else if (eat("%")) v = v / 100.0;
            else if (eat("²")) v = v * v;
            else if (eat("³")) v = v * v * v;
            else return v;
        }
    }

    private boolean startsPrimary() {
        if (atEnd()) return false;
        char c = s.charAt(i);
        if (Character.isDigit(c) || c == '.' || c == '(' || c == 'π' || c == '√' || c == '∛') return true;
        for (String f : FUNCS) if (s.startsWith(f, i)) return true;
        return s.startsWith("Ans", i) || s.startsWith("e", i);
    }

    private static final String[] FUNCS = {"asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "ln(", "log("};

    private double primary() throws Error {
        if (atEnd()) throw new Error("incompleto");
        if (eat("(")) {
            double v = expr();
            eat(")");  // si falta, se cierra solo al final
            return v;
        }
        if (eat("√")) {
            double v = postfixOperand();
            if (v < 0) throw new Error("raíz negativa");
            return Math.sqrt(v);
        }
        if (eat("∛")) return Math.cbrt(postfixOperand());
        if (eat("π")) return Math.PI;
        if (eat("Ans")) return ans;
        for (String f : FUNCS) {
            if (eat(f)) {
                double x = expr();
                eat(")");
                return func(f, x);
            }
        }
        if (eat("e")) return Math.E;
        return number();
    }

    /** Lo que va después de √: un número, una constante o un paréntesis, con sus potencias. */
    private double postfixOperand() throws Error {
        if (eat("−") || eat("-")) return -postfixOperand();
        return power();
    }

    private double number() throws Error {
        int start = i;
        boolean dot = false;
        while (!atEnd()) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) i++;
            else if (c == '.' && !dot) {
                dot = true;
                i++;
            } else break;
        }
        if (start == i) throw new Error("se esperaba un número");
        String n = s.substring(start, i);
        if (n.equals(".")) throw new Error("número inválido");
        return Double.parseDouble(n);
    }

    private double func(String f, double x) throws Error {
        double r = degrees ? Math.toRadians(x) : x;
        switch (f) {
            case "sin(":
                return Math.sin(r);
            case "cos(":
                return Math.cos(r);
            case "tan(":
                if (degrees && Math.abs(Math.IEEEremainder(x, 180)) == 90) throw new Error("tan indefinida");
                return Math.tan(r);
            case "asin(":
                if (x < -1 || x > 1) throw new Error("fuera de rango");
                return degrees ? Math.toDegrees(Math.asin(x)) : Math.asin(x);
            case "acos(":
                if (x < -1 || x > 1) throw new Error("fuera de rango");
                return degrees ? Math.toDegrees(Math.acos(x)) : Math.acos(x);
            case "atan(":
                return degrees ? Math.toDegrees(Math.atan(x)) : Math.atan(x);
            case "ln(":
                if (x <= 0) throw new Error("logaritmo de no positivo");
                return Math.log(x);
            default:
                if (x <= 0) throw new Error("logaritmo de no positivo");
                return Math.log10(x);
        }
    }

    private static double factorial(double v) throws Error {
        if (v < 0) throw new Error("factorial negativo");
        if (v > 170) return Double.POSITIVE_INFINITY;
        if (v == Math.floor(v)) {
            double r = 1;
            for (int k = 2; k <= (int) v; k++) r *= k;
            return r;
        }
        return gamma(v + 1);
    }

    /** Aproximación de Lanczos para factoriales no enteros. */
    private static double gamma(double x) {
        double[] g = {676.5203681218851, -1259.1392167224028, 771.32342877765313, -176.61502916214059,
                12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7};
        if (x < 0.5) return Math.PI / (Math.sin(Math.PI * x) * gamma(1 - x));
        x -= 1;
        double a = 0.99999999999980993;
        double t = x + 7.5;
        for (int k = 0; k < 8; k++) a += g[k] / (x + k + 1);
        return Math.sqrt(2 * Math.PI) * Math.pow(t, x + 0.5) * Math.exp(-t) * a;
    }

    // ---------- Formato ----------

    private static final String SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    /** Número bonito: separador de miles, sin ceros de más, notación científica si es enorme o diminuto. */
    static String format(double v) {
        if (Double.isNaN(v)) return "Error";
        if (Double.isInfinite(v)) return v > 0 ? "∞" : "−∞";
        if (v == 0) return "0";
        double a = Math.abs(v);
        if (a >= 1e15 || a < 1e-7) {
            int exp = (int) Math.floor(Math.log10(a));
            double mant = v / Math.pow(10, exp);
            if (Math.abs(mant) >= 9.9999999995) {
                mant /= 10;
                exp++;
            }
            DecimalFormat df = new DecimalFormat("0.#######", DecimalFormatSymbols.getInstance(Locale.US));
            return df.format(mant).replace("-", "−") + " × 10" + superscript(exp);
        }
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.US);
        int intDigits = a >= 1 ? (int) Math.floor(Math.log10(a)) + 1 : 1;
        int decimals = Math.max(0, Math.min(10, 12 - intDigits));
        StringBuilder pat = new StringBuilder("#,##0");
        if (decimals > 0) {
            pat.append('.');
            for (int k = 0; k < decimals; k++) pat.append('#');
        }
        DecimalFormat df = new DecimalFormat(pat.toString(), sym);
        return df.format(v).replace("-", "−");
    }

    private static String superscript(int n) {
        StringBuilder b = new StringBuilder();
        if (n < 0) b.append('⁻');
        for (char c : String.valueOf(Math.abs(n)).toCharArray()) b.append(SUP.charAt(c - '0'));
        return b.toString();
    }

    /** Valor de un resultado para seguir operando con él (sin comas ni notación especial). */
    static String plain(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "";
        String r = new BigDecimal(v).round(new MathContext(12)).stripTrailingZeros().toPlainString();
        return r.replace("-", "−");
    }
}
