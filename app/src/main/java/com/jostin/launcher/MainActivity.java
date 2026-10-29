package com.jostin.launcher;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final Locale ES = new Locale("es", "MX");
    private static final int REQ_PICK = 100;
    private static final String CLAUDE_PKG = "com.anthropic.claude";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Backdrop backdrop;
    private FrameLayout overlay;
    private LinearLayout clockBox, bottomBox, dock;
    private TextView dayView, dateView, studyLine, studySub, tasksSub;
    private TextClock timeView;
    private final List<View> panels = new ArrayList<>();
    private final ImageView[] dockIcons = new ImageView[Prefs.SLOTS];
    private GestureDetector gestures;
    private boolean wasStudying = false;
    private int insetTop = 0, insetBottom = 0;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshDate();
            refreshStudy();
            handler.postDelayed(this, 20_000);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        backdrop = new Backdrop(this, Prefs.theme(this));
        Ui.edgeToEdge(this, backdrop.scene().light());
        Ui.initSounds(this);
        if (!Prefs.slotsInitialized(this)) fillDefaultSlots();
        buildUi();
    }

    // ---------- Construcción de la pantalla ----------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        clockBox = buildClock();
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        content.addView(clockBox, clp);

        bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = Ui.dp(this, 14);
        bottomBox.setPadding(side, 0, side, 0);
        bottomBox.addView(buildPanels(), new LinearLayout.LayoutParams(-1, -2));
        dock = buildDock();
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(this, 14);
        bottomBox.addView(dock, dlp);
        content.addView(bottomBox, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (Math.abs(vy) < Math.abs(vx)) return false;
                if (vy < -1200) {
                    openDrawer(false);
                    return true;
                }
                if (vy > 1200) {
                    expandNotifications();
                    return true;
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Ui.open(backdrop);
                showThemePicker();
            }
        });
        content.setOnTouchListener((v, e) -> gestures.onTouchEvent(e));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            int screenH = getResources().getDisplayMetrics().heightPixels;
            ((FrameLayout.LayoutParams) clockBox.getLayoutParams()).topMargin = insetTop + (int) (screenH * 0.11f);
            clockBox.requestLayout();
            ((FrameLayout.LayoutParams) bottomBox.getLayoutParams()).bottomMargin = insetBottom + Ui.dp(this, 12);
            bottomBox.requestLayout();
            return insets.consumeSystemWindowInsets();
        });

        setContentView(root);
        applyThemeColors();
        refreshDock();
        refreshDate();
    }

    private LinearLayout buildClock() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        dayView = Ui.text(this, "", 40, Ui.TEXT, Ui.THIN);
        dayView.setLetterSpacing(0.42f);
        dayView.setMaxLines(1);
        dayView.setGravity(Gravity.CENTER);
        dayView.setAutoSizeTextTypeUniformWithConfiguration(20, 44, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        int pad = Ui.dp(this, 16);
        dayView.setPadding(pad, 0, pad, 0);
        int dayWidth = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24);
        box.addView(dayView, new LinearLayout.LayoutParams(dayWidth, Ui.dp(this, 60)));

        dateView = Ui.text(this, "", 12, Ui.TEXT, Ui.LIGHT);
        dateView.setLetterSpacing(0.32f);
        dateView.setGravity(Gravity.CENTER);
        box.addView(dateView);

        timeView = new TextClock(this);
        timeView.setFormat24Hour("'-  'HH:mm'  -'");
        timeView.setFormat12Hour("'-  'h:mm'  -'");
        timeView.setTextSize(12);
        timeView.setLetterSpacing(0.3f);
        timeView.setTypeface(Ui.LIGHT);
        timeView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(this, 4);
        box.addView(timeView, tlp);

        studyLine = Ui.text(this, "", 11, Ui.TEXT, Ui.MEDIUM);
        studyLine.setLetterSpacing(0.25f);
        studyLine.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(this, 10);
        box.addView(studyLine, slp);

        box.setOnClickListener(v -> {
            Ui.click(v);
            showCalendar();
        });
        box.setOnLongClickListener(v -> {
            Ui.open(v);
            showSettings();
            return true;
        });
        return box;
    }

    private View buildPanels() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        panels.clear();

        LinearLayout row1 = row();
        row1.addView(panel("▦", "Obra", "Calculadora", v ->
                startActivity(new Intent(this, ObraActivity.class))), weight());
        View study = panel("◐", "Estudio", "Modo enfoque", v -> toggleStudy());
        studySub = (TextView) study.getTag();
        row1.addView(study, weight());

        LinearLayout row2 = row();
        View tasks = panel("☰", "Pendientes", "Ponte en el Mapa", v ->
                startActivity(new Intent(this, PendientesActivity.class)));
        tasksSub = (TextView) tasks.getTag();
        row2.addView(tasks, weight());
        row2.addView(panel("◆", "Jarvis", "Abrir Claude", v -> openJarvis()), weight());

        grid.addView(row1);
        grid.addView(row2);
        return grid;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 66), 1f);
        int m = Ui.dp(this, 5);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    /** Panel de vidrio: símbolo, título y subtítulo. El subtítulo queda en el tag para actualizarlo. */
    private View panel(String glyph, String title, String sub, View.OnClickListener action) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setBackground(Ui.glass(this, 22));
        int pad = Ui.dp(this, 12);
        p.setPadding(pad, 0, pad, 0);

        TextView g = Ui.text(this, glyph, 18, Ui.TEXT, null);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.fill(this, Ui.GLASS_LIGHT, 14));
        p.addView(g, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 10), 0, 0, 0);
        texts.addView(Ui.text(this, title, 15, Ui.TEXT, Ui.MEDIUM));
        TextView s = Ui.text(this, sub, 12, Ui.MUTED, null);
        s.setMaxLines(1);
        texts.addView(s);
        p.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        p.setTag(s);
        Ui.pressable(p);
        p.setOnClickListener(v -> {
            Ui.click(v);
            action.onClick(v);
        });
        panels.add(p);
        return p;
    }

    private LinearLayout buildDock() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.HORIZONTAL);
        d.setGravity(Gravity.CENTER_VERTICAL);
        d.setBackground(Ui.glass(this, 26));
        int pad = Ui.dp(this, 9);
        d.setPadding(pad, pad, pad, pad);
        for (int i = 0; i < Prefs.SLOTS; i++) {
            final int slot = i;
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = Ui.dp(this, 3);
            iv.setPadding(p, p, p, p);
            Ui.pressable(iv);
            iv.setOnClickListener(v -> onDockTap(slot, v));
            iv.setOnLongClickListener(v -> {
                Ui.open(v);
                onDockLongPress(slot);
                return true;
            });
            dockIcons[i] = iv;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46));
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            d.addView(iv, lp);
        }
        View sep = new View(this);
        sep.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(this, 1), Ui.dp(this, 30));
        slp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        d.addView(sep, slp);

        View grid = new View(this) {
            private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                dot.setColor(0xFFE6E6E6);
                float s = getWidth() / 4f, r = getWidth() / 16f;
                for (int y = 1; y <= 3; y++)
                    for (int x = 1; x <= 3; x++) c.drawCircle(x * s, y * s, r, dot);
            }
        };
        Ui.pressable(grid);
        grid.setOnClickListener(v -> {
            Ui.click(v);
            openDrawer(false);
        });
        d.addView(grid, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        return d;
    }

    // ---------- Ciclo de vida ----------

    @Override
    protected void onStart() {
        super.onStart();
        backdrop.setAnimated(Prefs.animated(this));
        backdrop.setRunning(true);
        refreshDock();
        refreshTasks();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        playEntrance();
    }

    @Override
    protected void onStop() {
        super.onStop();
        backdrop.setRunning(false);
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Botón de inicio estando ya en inicio: cerrar ventanas y repetir la animación
        if (overlay.getVisibility() == View.VISIBLE) closeOverlay();
        else playEntrance();
    }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) closeOverlay();
        // En la pantalla de inicio, atrás no hace nada
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == RESULT_OK && data != null && req >= REQ_PICK && req < REQ_PICK + Prefs.SLOTS) {
            Prefs.setSlot(this, req - REQ_PICK, data.getStringExtra(AppsActivity.EXTRA_PKG));
            refreshDock();
        }
    }

    // ---------- Animaciones ----------

    private void playEntrance() {
        if (dayView == null) return;
        float d = getResources().getDisplayMetrics().density;
        dayView.setAlpha(0f);
        dayView.setTranslationY(-14 * d);
        dayView.animate().alpha(1f).translationY(0).setDuration(520)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        ObjectAnimator spread = ObjectAnimator.ofFloat(dayView, "letterSpacing", 0.9f, 0.42f);
        spread.setDuration(700);
        spread.setInterpolator(new DecelerateInterpolator(2.5f));
        spread.start();

        View[] small = {dateView, timeView};
        for (int i = 0; i < small.length; i++) {
            small[i].setAlpha(0f);
            small[i].animate().alpha(1f).setStartDelay(180 + i * 90L).setDuration(400).start();
        }
        for (int i = 0; i < panels.size(); i++) {
            View p = panels.get(i);
            p.setAlpha(0f);
            p.setTranslationY(40 * d);
            p.animate().alpha(1f).translationY(0).setStartDelay(120 + i * 55L).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        dock.setTranslationY(110 * d);
        dock.animate().translationY(0).setStartDelay(160).setDuration(560)
                .setInterpolator(new OvershootInterpolator(1.1f)).start();
    }

    // ---------- Reloj, fecha y tema ----------

    private void refreshDate() {
        Date now = new Date();
        String day = new SimpleDateFormat("EEEE", ES).format(now).toUpperCase(ES);
        if (!day.contentEquals(dayView.getText())) dayView.setText(day);
        dateView.setText(new SimpleDateFormat("d MMM yyyy", ES).format(now).replace(".", "").toUpperCase(ES));
    }

    private void applyThemeColors() {
        Scenes.Scene s = backdrop.scene();
        int c = s.clockColor();
        dayView.setTextColor(c);
        dateView.setTextColor(Ui.alpha(c, 0xBB));
        timeView.setTextColor(Ui.alpha(c, 0xBB));
        studyLine.setTextColor(c);
        Ui.setLightBars(this, s.light());
    }

    // ---------- Dock ----------

    private void fillDefaultSlots() {
        int slot = 0;
        for (String pkg : Prefs.DEFAULT_APPS) {
            if (slot >= Prefs.SLOTS) break;
            if (getPackageManager().getLaunchIntentForPackage(pkg) != null) {
                Prefs.setSlot(this, slot++, pkg);
            }
        }
        Prefs.markSlotsInitialized(this);
    }

    private void refreshDock() {
        PackageManager pm = getPackageManager();
        boolean gray = Prefs.grayIcons(this);
        boolean studying = Prefs.studying(this);
        for (int i = 0; i < Prefs.SLOTS; i++) {
            ImageView iv = dockIcons[i];
            String pkg = Prefs.slot(this, i);
            Drawable icon = null;
            if (pkg != null && pm.getLaunchIntentForPackage(pkg) != null) {
                try {
                    icon = pm.getApplicationIcon(pkg);
                } catch (PackageManager.NameNotFoundException ignored) {
                    icon = null;
                }
            }
            if (icon != null) {
                iv.setImageDrawable(icon);
                iv.setBackground(null);
                Ui.grayscale(iv, gray);
                iv.setAlpha(studying && Prefs.DISTRACTIONS.contains(pkg) ? 0.28f : 1f);
            } else {
                iv.setImageDrawable(new PlusDrawable());
                iv.setBackground(Ui.fill(this, Ui.GLASS_LIGHT, 14));
                iv.clearColorFilter();
                iv.setAlpha(1f);
            }
        }
    }

    private void onDockTap(int slot, View v) {
        String pkg = Prefs.slot(this, slot);
        if (pkg == null || getPackageManager().getLaunchIntentForPackage(pkg) == null) {
            Ui.click(v);
            pickApp(slot);
            return;
        }
        if (Prefs.studying(this) && Prefs.DISTRACTIONS.contains(pkg)) {
            Ui.click(v);
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Estás en modo estudio")
                    .setMessage("Esta app está en pausa hasta que termine tu sesión. ¿La abres de todos modos?")
                    .setPositiveButton("Abrir", (d, w) -> launch(this, pkg, v))
                    .setNegativeButton("Seguir estudiando", null)
                    .show();
            return;
        }
        Ui.open(v);
        launch(this, pkg, v);
    }

    private void onDockLongPress(int slot) {
        String pkg = Prefs.slot(this, slot);
        String[] items = pkg == null
                ? new String[]{"Elegir app"}
                : new String[]{"Cambiar app", "Quitar del dock", "Información de la app"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setItems(items, (d, which) -> {
                    if (which == 0) pickApp(slot);
                    else if (which == 1) {
                        Prefs.setSlot(this, slot, null);
                        refreshDock();
                    } else appInfo(this, pkg);
                })
                .show();
    }

    private void pickApp(int slot) {
        Intent i = new Intent(this, AppsActivity.class);
        i.putExtra(AppsActivity.EXTRA_PICK, true);
        startActivityForResult(i, REQ_PICK + slot);
    }

    private void openDrawer(boolean keyboard) {
        Intent i = new Intent(this, AppsActivity.class);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ---------- Paneles ----------

    private void toggleStudy() {
        if (Prefs.studying(this)) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("¿Terminar el modo estudio?")
                    .setPositiveButton("Terminar", (d, w) -> {
                        Prefs.setStudyEnd(this, 0);
                        refreshStudy();
                    })
                    .setNegativeButton("Seguir", null)
                    .show();
            return;
        }
        String[] opts = {"25 minutos", "50 minutos", "1 hora 30 minutos"};
        int[] mins = {25, 50, 90};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Modo estudio")
                .setItems(opts, (d, which) -> {
                    Prefs.setStudyEnd(this, System.currentTimeMillis() + mins[which] * 60_000L);
                    refreshStudy();
                    Toast.makeText(this, "Listo. Las apps que distraen quedan en pausa.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void refreshStudy() {
        boolean on = Prefs.studying(this);
        if (on) {
            long left = Prefs.studyEnd(this) - System.currentTimeMillis();
            int min = (int) Math.ceil(left / 60_000.0);
            studyLine.setText("MODO ESTUDIO  ·  " + min + " MIN");
            studyLine.setVisibility(View.VISIBLE);
            studySub.setText("Faltan " + min + " min");
        } else {
            studyLine.setVisibility(View.GONE);
            studySub.setText("Modo enfoque");
            if (wasStudying) {
                Toast.makeText(this, "Terminó tu sesión de estudio. Buen trabajo.", Toast.LENGTH_LONG).show();
            }
        }
        if (on != wasStudying) refreshDock();
        wasStudying = on;
    }

    private void refreshTasks() {
        int pending = 0;
        try {
            JSONArray arr = new JSONArray(Prefs.tasks(this));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!o.optBoolean("d")) pending++;
            }
        } catch (Exception ignored) {
            pending = 0;
        }
        tasksSub.setText(pending == 0 ? "Ponte en el Mapa" : pending + (pending == 1 ? " pendiente" : " pendientes"));
    }

    private void openJarvis() {
        Intent i = getPackageManager().getLaunchIntentForPackage(CLAUDE_PKG);
        if (i != null) {
            startActivity(i);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + CLAUDE_PKG)));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + CLAUDE_PKG)));
        }
    }

    // ---------- Ventanas flotantes ----------

    private void showOverlay(View panel, int gravity) {
        overlay.removeAllViews();
        View dim = new View(this);
        dim.setBackgroundColor(0x99000000);
        dim.setOnClickListener(v -> closeOverlay());
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, gravity);
        lp.topMargin = insetTop + Ui.dp(this, 16);
        overlay.addView(panel, lp);
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(200).start();
        panel.setScaleX(0.92f);
        panel.setScaleY(0.92f);
        panel.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new OvershootInterpolator(1.4f)).start();
    }

    private void closeOverlay() {
        overlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            overlay.setVisibility(View.GONE);
            overlay.removeAllViews();
        }).start();
    }

    private void showCalendar() {
        CalendarPanel cal = new CalendarPanel(this, () -> {
            closeOverlay();
            startActivity(new Intent(this, PendientesActivity.class));
        });
        cal.setLayoutParams(new ViewGroup.LayoutParams(Ui.dp(this, 330), -2));
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(cal, new FrameLayout.LayoutParams(Ui.dp(this, 330), -2));
        showOverlay(wrap, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ((FrameLayout.LayoutParams) wrap.getLayoutParams()).topMargin =
                insetTop + (int) (getResources().getDisplayMetrics().heightPixels * 0.08f);
    }

    private void showThemePicker() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        dots.setBackground(Ui.glass(this, 14));
        int dp6 = Ui.dp(this, 6);
        dots.setPadding(dp6 * 2, dp6, dp6 * 2, dp6);
        View[] dotViews = new View[Scenes.COUNT];
        for (int i = 0; i < Scenes.COUNT; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 9), Ui.dp(this, 9));
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            dots.addView(dot, lp);
            dotViews[i] = dot;
        }
        box.addView(dots);

        TextView name = Ui.text(this, "", 13, Ui.TEXT, Ui.MEDIUM);
        name.setLetterSpacing(0.35f);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        int pad = Ui.dp(this, 24);
        cards.setPadding(pad, pad, pad, pad);
        ThemeCard[] all = new ThemeCard[Scenes.COUNT];
        int current = Prefs.theme(this);
        for (int i = 0; i < Scenes.COUNT; i++) {
            ThemeCard card = new ThemeCard(this, i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 128), Ui.dp(this, 230));
            lp.setMargins(-Ui.dp(this, 10), 0, -Ui.dp(this, 10), 0);
            cards.addView(card, lp);
            all[i] = card;
            card.setOnClickListener(v -> {
                Ui.click(v);
                int t = ((ThemeCard) v).theme();
                Prefs.setTheme(this, t);
                backdrop.setTheme(t);
                applyThemeColors();
                markTheme(all, dotViews, name, t);
            });
        }
        hs.addView(cards);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = Ui.dp(this, 8);
        box.addView(hs, hlp);
        box.addView(name);
        markTheme(all, dotViews, name, current);

        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(box, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        showOverlay(wrap, Gravity.CENTER);
        wrap.getLayoutParams().width = -1;
        final int sel = current;
        hs.post(() -> hs.scrollTo(Math.max(0, all[sel].getLeft() - hs.getWidth() / 2 + all[sel].getWidth() / 2), 0));
    }

    private void markTheme(ThemeCard[] cards, View[] dots, TextView name, int t) {
        for (int i = 0; i < cards.length; i++) {
            cards[i].setSelectedCard(i == t);
            dots[i].setBackground(Ui.fill(this, i == t ? 0xFFFFFFFF : 0x55FFFFFF, 5));
        }
        name.setText(Scenes.NAMES[t].toUpperCase(ES));
    }

    private void showSettings() {
        String[] items = {
                "Sonidos: " + (Prefs.sounds(this) ? "sí" : "no"),
                "Fondo en movimiento: " + (Prefs.animated(this) ? "sí" : "no"),
                "Íconos en gris: " + (Prefs.grayIcons(this) ? "sí" : "no"),
                "Cambiar fondo",
                "Elegir pantalla de inicio",
                "Ajustes del teléfono"
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Jostin OS")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            Prefs.setSounds(this, !Prefs.sounds(this));
                            break;
                        case 1:
                            Prefs.setAnimated(this, !Prefs.animated(this));
                            backdrop.setAnimated(Prefs.animated(this));
                            break;
                        case 2:
                            Prefs.setGrayIcons(this, !Prefs.grayIcons(this));
                            refreshDock();
                            break;
                        case 3:
                            showThemePicker();
                            break;
                        case 4:
                            safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));
                            break;
                        default:
                            safeStart(new Intent(Settings.ACTION_SETTINGS));
                            break;
                    }
                })
                .show();
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings({"WrongConstant", "JavaReflectionMemberAccess"})
    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class<?> cls = Class.forName("android.app.StatusBarManager");
            cls.getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception ignored) {
            // Algunos teléfonos no lo permiten; no pasa nada
        }
    }

    // ---------- Utilidades compartidas ----------

    static void launch(Activity a, String pkg, View from) {
        Intent i = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(a, "Esa app ya no está instalada", Toast.LENGTH_SHORT).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle opts = null;
        if (from != null) {
            opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
        }
        try {
            a.startActivity(i, opts);
        } catch (Exception e) {
            Toast.makeText(a, "No se pudo abrir", Toast.LENGTH_SHORT).show();
        }
    }

    static void appInfo(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (ActivityNotFoundException ignored) {
            // nada
        }
    }

    /** Signo "+" para un lugar vacío del dock. */
    static final class PlusDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        PlusDrawable() {
            p.setColor(0xCCFFFFFF);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            float cx = getBounds().left + w / 2, cy = getBounds().top + h / 2, s = w * 0.18f;
            p.setStrokeWidth(w * 0.07f);
            c.drawLine(cx - s, cy, cx + s, cy, p);
            c.drawLine(cx, cy - s, cx, cy + s, p);
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
            p.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
