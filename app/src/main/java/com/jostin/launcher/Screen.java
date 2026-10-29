package com.jostin.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Base para las pantallas secundarias: mismo fondo animado (más oscuro), encabezado con
 * botón de regreso, transiciones de entrada y salida, y hojas de vidrio.
 */
abstract class Screen extends Activity {
    protected Backdrop backdrop;
    protected FrameLayout root;
    protected LinearLayout page;
    protected LinearLayout header;
    private LinearLayout actions;

    abstract String screenTitle();

    String screenSubtitle() {
        return null;
    }

    /** El contenido debajo del encabezado. Ocupa el resto de la pantalla. */
    abstract View createBody();

    float dim() {
        return 0.5f;
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        Sound.init(this);
        Ui.edgeToEdge(this, false);
        backdrop = new Backdrop(this, Prefs.theme(this));
        backdrop.setDim(dim());

        root = new FrameLayout(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        header = buildHeader();
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));
        View body = createBody();
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        root.setOnApplyWindowInsetsListener((v, in) -> {
            int side = Ui.dp(this, 16);
            page.setPadding(side, in.getSystemWindowInsetTop() + Ui.dp(this, 8), side, in.getSystemWindowInsetBottom() + Ui.dp(this, 10));
            onInsets(in.getSystemWindowInsetBottom());
            return in.consumeSystemWindowInsets();
        });
        setContentView(root);
        Ui.rise(header, 0, 12);
    }

    void onInsets(int bottom) {}

    private LinearLayout buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 16));

        ImageView back = new ImageView(this);
        back.setImageDrawable(Icons.of(Icons.BACK));
        int p = Ui.dp(this, 11);
        back.setPadding(p, p, p, p);
        Glass.apply(back, backdrop, 23);
        Ui.pressable(back, 0.88f);
        back.setOnClickListener(v -> {
            Sound.play(v, Sound.BACK);
            finish();
        });
        h.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, screenTitle().toUpperCase(MainActivity.ES), 17, Ui.TEXT, Ui.DISPLAY);
        title.setLetterSpacing(0.22f);
        texts.addView(title);
        String sub = screenSubtitle();
        if (sub != null) {
            TextView s = Ui.text(this, sub, 13, Ui.MUTED, Ui.REGULAR);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.dp(this, 5);
            texts.addView(s, slp);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(this, 14);
        h.addView(texts, tlp);

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        h.addView(actions);
        return h;
    }

    /** Botón redondo a la derecha del encabezado. */
    ImageView headerAction(Icons.Shape icon, View.OnClickListener l) {
        ImageView b = new ImageView(this);
        b.setImageDrawable(Icons.of(icon));
        int p = Ui.dp(this, 11);
        b.setPadding(p, p, p, p);
        Glass.apply(b, backdrop, 23);
        Ui.pressable(b, 0.88f);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46));
        lp.leftMargin = Ui.dp(this, 8);
        actions.addView(b, lp);
        return b;
    }

    @Override
    protected void onStart() {
        super.onStart();
        backdrop.setAnimated(Prefs.animated(this));
        backdrop.setParallax(Prefs.parallax(this));
        backdrop.setRunning(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        backdrop.setRunning(false);
    }

    @Override
    public void onBackPressed() {
        if (Sheet.dismissTop(this)) return;
        Sound.play(root, Sound.BACK);
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(anim(this, "stay_in"), anim(this, "screen_exit"));
    }

    static void open(Activity from, Intent i) {
        from.startActivity(i);
        from.overridePendingTransition(anim(from, "screen_enter"), anim(from, "stay_out"));
    }

    static int anim(Activity a, String name) {
        return a.getResources().getIdentifier(name, "anim", a.getPackageName());
    }
}
