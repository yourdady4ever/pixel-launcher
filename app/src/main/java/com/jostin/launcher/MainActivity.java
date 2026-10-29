package com.jostin.launcher;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

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
    private StudyIcon studyIcon;
    private final List<View> panels = new ArrayList<>();
    private final FrameLayout[] dockSlots = new FrameLayout[Prefs.SLOTS];
    private final ImageView[] dockIcons = new ImageView[Prefs.SLOTS];
    private GestureDetector gestures;
    private boolean wasStudying = false;
    private int insetTop = 0;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshDate();
            refreshStudy();
            handler.postDelayed(this, 15_000);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        Sound.init(this);
        backdrop = new Backdrop(this, Prefs.theme(this));
        Ui.edgeToEdge(this, backdrop.scene().light());
        if (!Prefs.slotsInitialized(this)) fillDefaultSlots();
        buildUi();
        handleDemo(getIntent());
    }

    // ==================== Construcción ====================

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        clockBox = buildClock();
        content.addView(clockBox, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = Ui.dp(this, 12);
        bottomBox.setPadding(side, 0, side, 0);
        bottomBox.addView(buildPanels(), new LinearLayout.LayoutParams(-1, -2));
        dock = buildDock();
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(this, 16);
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
                if (vy < -1100) {
                    openDrawer();
                    return true;
                }
                if (vy > 1100) {
                    expandNotifications();
                    return true;
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Sound.play(backdrop, Sound.HOLD);
                showThemePicker();
            }
        });
        content.setOnTouchListener((v, e) -> gestures.onTouchEvent(e));

        root.setOnApplyWindowInsetsListener((v, in) -> {
            insetTop = in.getSystemWindowInsetTop();
            int screenH = getResources().getDisplayMetrics().heightPixels;
            ((FrameLayout.LayoutParams) clockBox.getLayoutParams()).topMargin = insetTop + (int) (screenH * 0.10f);
            clockBox.requestLayout();
            ((FrameLayout.LayoutParams) bottomBox.getLayoutParams()).bottomMargin = in.getSystemWindowInsetBottom() + Ui.dp(this, 14);
            bottomBox.requestLayout();
            return in.consumeSystemWindowInsets();
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

        dayView = Ui.text(this, "", 34, Ui.TEXT, Ui.DISPLAY);
        dayView.setLetterSpacing(0.3f);
        dayView.setMaxLines(1);
        dayView.setGravity(Gravity.CENTER);
        dayView.setAutoSizeTextTypeUniformWithConfiguration(18, 36, 1, TypedValue.COMPLEX_UNIT_SP);
        int dayWidth = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 28);
        box.addView(dayView, new LinearLayout.LayoutParams(dayWidth, Ui.dp(this, 52)));

        dateView = Ui.text(this, "", 11, Ui.TEXT, Ui.SEMIBOLD);
        dateView.setLetterSpacing(0.42f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(this, 10);
        box.addView(dateView, dlp);

        timeView = new TextClock(this);
        timeView.setFormat24Hour("'–   'HH:mm'   –'");
        timeView.setFormat12Hour("'–   'h:mm'   –'");
        timeView.setTextSize(13);
        timeView.setLetterSpacing(0.32f);
        timeView.setTypeface(Ui.LIGHT);
        timeView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(this, 8);
        box.addView(timeView, tlp);

        studyLine = Ui.text(this, "", 11, Ui.TEXT, Ui.SEMIBOLD);
        studyLine.setLetterSpacing(0.25f);
        int h = Ui.dp(this, 12), vv = Ui.dp(this, 7);
        studyLine.setPadding(h, vv, h, vv);
        Glass.apply(studyLine, backdrop, 14);
        studyLine.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(this, 16);
        box.addView(studyLine, slp);

        box.setOnClickListener(v -> {
            Sound.play(v, Sound.TAP);
            showCalendar();
        });
        box.setOnLongClickListener(v -> {
            Sound.play(v, Sound.HOLD);
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
        row1.addView(panel(Icons.CALC, "Calculadora", "Científica y obra", null, v ->
                Screen.open(this, new Intent(this, CalcActivity.class))), weight());
        studyIcon = new StudyIcon(this);
        View study = panel(Icons.FOCUS, "Estudio", "Modo enfoque", studyIcon, v -> toggleStudy());
        studySub = (TextView) study.getTag();
        row1.addView(study, weight());

        LinearLayout row2 = row();
        View tasks = panel(Icons.TASKS, "Pendientes", "Ponte en el Mapa", null, v ->
                Screen.open(this, new Intent(this, PendientesActivity.class)));
        tasksSub = (TextView) tasks.getTag();
        row2.addView(tasks, weight());
        row2.addView(panel(Icons.SPARK, "Jarvis", "Abrir Claude", null, v -> openJarvis()), weight());

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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 70), 1f);
        int m = Ui.dp(this, 5);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    /** Panel de vidrio con ícono, título y subtítulo. El subtítulo queda en el tag. */
    private View panel(Icons.Shape icon, String title, String sub, View customIcon, View.OnClickListener action) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        Glass.apply(p, backdrop, 24);
        int pad = Ui.dp(this, 13);
        p.setPadding(pad, 0, Ui.dp(this, 10), 0);

        View ic;
        if (customIcon != null) {
            ic = customIcon;
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(Icons.of(icon));
            int ip = Ui.dp(this, 10);
            iv.setPadding(ip, ip, ip, ip);
            iv.setBackground(Ui.fill(this, 0x1AFFFFFF, 15));
            ic = iv;
        }
        p.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 12), 0, 0, 0);
        TextView t = Ui.text(this, title, 15, Ui.TEXT, Ui.SEMIBOLD);
        t.setMaxLines(1);
        texts.addView(t);
        TextView s = Ui.text(this, sub, 12, Ui.MUTED, Ui.REGULAR);
        s.setMaxLines(1);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(this, 4);
        texts.addView(s, slp);
        p.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        p.setTag(s);
        Ui.pressable(p, 0.95f);
        p.setOnClickListener(v -> {
            Sound.play(v, Sound.OPEN);
            action.onClick(v);
        });
        panels.add(p);
        return p;
    }

    private LinearLayout buildDock() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.HORIZONTAL);
        d.setGravity(Gravity.CENTER_VERTICAL);
        Glass.apply(d, backdrop, 30);
        int pad = Ui.dp(this, 8);
        d.setPadding(Ui.dp(this, 10), pad, Ui.dp(this, 10), pad);
        for (int i = 0; i < Prefs.SLOTS; i++) {
            final int slot = i;
            FrameLayout f = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            Ui.pressable(f, 0.84f);
            f.setOnClickListener(v -> onDockTap(slot, v));
            f.setOnLongClickListener(v -> {
                Sound.play(v, Sound.HOLD);
                onDockLongPress(slot);
                return true;
            });
            dockSlots[i] = f;
            dockIcons[i] = iv;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
            lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            d.addView(f, lp);
        }
        View sep = new View(this);
        sep.setBackgroundColor(0x2EFFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Math.max(1, Ui.dp(this, 1)), Ui.dp(this, 28));
        slp.setMargins(Ui.dp(this, 7), 0, Ui.dp(this, 5), 0);
        d.addView(sep, slp);

        ImageView grid = new ImageView(this);
        grid.setImageDrawable(new Icons.Line(Icons.GRID, Ui.TEXT, 2.6f));
        int gp = Ui.dp(this, 9);
        grid.setPadding(gp, gp, gp, gp);
        Ui.pressable(grid, 0.84f);
        grid.setOnClickListener(v -> {
            Sound.play(v, Sound.OPEN);
            openDrawer();
        });
        d.addView(grid, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return d;
    }

    /** Ícono del panel de estudio: muestra un anillo con lo que falta de la sesión. */
    static final class StudyIcon extends View {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Icons.Line icon = Icons.of(Icons.FOCUS);
        private final RectF oval = new RectF();
        private float progress = 0;

        StudyIcon(Context c) {
            super(c);
            bg.setColor(0x1AFFFFFF);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(0xFFF5F6F8);
            ring.setStrokeCap(Paint.Cap.ROUND);
            track.setStyle(Paint.Style.STROKE);
            track.setColor(0x26FFFFFF);
        }

        void setProgress(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float r = Ui.dp(getContext(), 15);
            c.drawRoundRect(0, 0, w, h, r, r, bg);
            int ip = Ui.dp(getContext(), 10);
            icon.setBounds(ip, ip, (int) w - ip, (int) h - ip);
            icon.draw(c);
            if (progress > 0) {
                float sw = Ui.dp(getContext(), 2);
                ring.setStrokeWidth(sw);
                track.setStrokeWidth(sw);
                oval.set(sw, sw, w - sw, h - sw);
                c.drawArc(oval, 0, 360, false, track);
                c.drawArc(oval, -90, 360 * progress, false, ring);
            }
        }
    }

    // ==================== Ciclo de vida ====================

    @Override
    protected void onStart() {
        super.onStart();
        backdrop.setAnimated(Prefs.animated(this));
        backdrop.setParallax(Prefs.parallax(this));
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
        setIntent(intent);
        if (handleDemo(intent)) return;
        // Botón de inicio estando en inicio: cerrar lo abierto y repetir la animación
        if (Sheet.dismissTop(this)) return;
        if (overlay.getVisibility() == View.VISIBLE) closeOverlay();
        else playEntrance();
    }

    @Override
    public void onBackPressed() {
        if (Sheet.dismissTop(this)) return;
        if (overlay.getVisibility() == View.VISIBLE) closeOverlay();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == RESULT_OK && data != null && req >= REQ_PICK && req < REQ_PICK + Prefs.SLOTS) {
            Prefs.setSlot(this, req - REQ_PICK, data.getStringExtra(AppsActivity.EXTRA_PKG));
            refreshDock();
        }
    }

    /** Atajos para abrir pantallas desde fuera (los usa la revisión automática de capturas). */
    private boolean handleDemo(Intent i) {
        if (i == null) return false;
        boolean any = false;
        if (i.hasExtra("theme")) {
            int t = Math.max(0, Math.min(Scenes.COUNT - 1, i.getIntExtra("theme", 0)));
            Prefs.setTheme(this, t);
            backdrop.setTheme(t);
            applyThemeColors();
            i.removeExtra("theme");
            any = true;
        }
        String shot = i.getStringExtra("shot");
        if (shot != null) {
            i.removeExtra("shot");
            any = true;
            handler.postDelayed(() -> {
                switch (shot) {
                    case "picker": showThemePicker(); break;
                    case "calendar": showCalendar(); break;
                    case "settings": showSettings(); break;
                    case "study":
                        Prefs.setStudyEnd(this, System.currentTimeMillis() + 25 * 60_000L);
                        refreshStudy();
                        break;
                    case "nostudy":
                        Prefs.setStudyEnd(this, 0);
                        refreshStudy();
                        break;
                    default: break;
                }
            }, 700);
        }
        String open = i.getStringExtra("open");
        if (open != null) {
            i.removeExtra("open");
            any = true;
            Class<?> cls = open.equals("calc") ? CalcActivity.class : open.equals("tasks") ? PendientesActivity.class : AppsActivity.class;
            Intent x = new Intent(this, cls);
            Bundle ex = i.getExtras();
            if (ex != null) x.putExtras(ex);
            handler.postDelayed(() -> Screen.open(this, x), 300);
        }
        return any;
    }

    // ==================== Animaciones ====================

    private void playEntrance() {
        if (dayView == null) return;
        float d = getResources().getDisplayMetrics().density;
        dayView.setAlpha(0f);
        dayView.setTranslationY(-10 * d);
        dayView.animate().alpha(1f).translationY(0).setStartDelay(0).setDuration(600)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        ObjectAnimator spread = ObjectAnimator.ofFloat(dayView, "letterSpacing", 0.75f, 0.3f);
        spread.setDuration(900);
        spread.setInterpolator(new DecelerateInterpolator(2.6f));
        spread.start();

        View[] small = {dateView, timeView};
        for (int k = 0; k < small.length; k++) {
            small[k].setAlpha(0f);
            small[k].animate().alpha(1f).setStartDelay(260 + k * 110L).setDuration(500).start();
        }
        for (int k = 0; k < panels.size(); k++) {
            View p = panels.get(k);
            p.setAlpha(0f);
            p.setTranslationY(36 * d);
            p.setScaleX(0.96f);
            p.setScaleY(0.96f);
            p.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setStartDelay(140 + k * 60L).setDuration(520)
                    .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        }
        dock.setAlpha(0f);
        dock.setTranslationY(90 * d);
        dock.animate().alpha(1f).translationY(0).setStartDelay(200).setDuration(640)
                .setInterpolator(new OvershootInterpolator(1.05f)).start();
        for (int k = 0; k < dockSlots.length; k++) {
            View s = dockSlots[k];
            s.setScaleX(0.5f);
            s.setScaleY(0.5f);
            s.animate().scaleX(1f).scaleY(1f).setStartDelay(320 + k * 40L).setDuration(420)
                    .setInterpolator(new OvershootInterpolator(2.2f)).start();
        }
    }

    // ==================== Reloj y tema ====================

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
        dateView.setTextColor(Ui.alpha(c, 0xCC));
        timeView.setTextColor(Ui.alpha(c, 0xCC));
        float r = s.light() ? 0 : Ui.dp(this, 14);
        int shadow = s.light() ? 0x00000000 : 0x66000000;
        dayView.setShadowLayer(r, 0, 0, shadow);
        Ui.setLightBars(this, s.light());
    }

    // ==================== Dock ====================

    private void fillDefaultSlots() {
        int slot = 0;
        for (String pkg : Prefs.DEFAULT_APPS) {
            if (slot >= Prefs.SLOTS) break;
            if (getPackageManager().getLaunchIntentForPackage(pkg) != null) Prefs.setSlot(this, slot++, pkg);
        }
        Prefs.markSlotsInitialized(this);
    }

    private void refreshDock() {
        boolean mono = Prefs.monoIcons(this);
        boolean studying = Prefs.studying(this);
        for (int i = 0; i < Prefs.SLOTS; i++) {
            ImageView iv = dockIcons[i];
            String pkg = Prefs.slot(this, i);
            Drawable icon = null;
            if (pkg != null && getPackageManager().getLaunchIntentForPackage(pkg) != null) icon = Icons.app(this, pkg, mono);
            if (icon != null) {
                iv.setImageDrawable(icon);
                iv.setPadding(0, 0, 0, 0);
                iv.setBackground(null);
                iv.setAlpha(studying && Prefs.DISTRACTIONS.contains(pkg) ? 0.25f : 1f);
            } else {
                Icons.Line plus = Icons.of(Icons.PLUS);
                plus.setColor(0xB3FFFFFF);
                iv.setImageDrawable(plus);
                int p = Ui.dp(this, 11);
                iv.setPadding(p, p, p, p);
                iv.setBackground(Ui.outline(this, 0x0FFFFFFF, 0x33FFFFFF, 22));
                iv.setAlpha(1f);
            }
        }
    }

    private void onDockTap(int slot, View v) {
        String pkg = Prefs.slot(this, slot);
        if (pkg == null || getPackageManager().getLaunchIntentForPackage(pkg) == null) {
            Sound.play(v, Sound.TAP);
            pickApp(slot);
            return;
        }
        if (Prefs.studying(this) && Prefs.DISTRACTIONS.contains(pkg)) {
            Sound.play(v, Sound.ERROR);
            Sheet.confirm(this, backdrop, "Estás en modo estudio",
                    "Esta app está en pausa hasta que termine tu sesión. ¿La abres de todos modos?",
                    "Abrir", false, () -> launch(this, pkg, v));
            return;
        }
        Sound.play(v, Sound.OPEN);
        launch(this, pkg, v);
    }

    private void onDockLongPress(int slot) {
        String pkg = Prefs.slot(this, slot);
        if (pkg == null) {
            pickApp(slot);
            return;
        }
        Sheet.show(this, backdrop, "Dock",
                Sheet.item("Cambiar app", Icons.SWAP, () -> pickApp(slot)),
                Sheet.item("Información de la app", Icons.INFO, () -> appInfo(this, pkg)),
                Sheet.item("Quitar del dock", Icons.CLOSE, () -> {
                    Prefs.setSlot(this, slot, null);
                    refreshDock();
                }).danger());
    }

    private void pickApp(int slot) {
        Intent i = new Intent(this, AppsActivity.class);
        i.putExtra(AppsActivity.EXTRA_PICK, true);
        startActivityForResult(i, REQ_PICK + slot);
        overridePendingTransition(Screen.anim(this, "screen_enter"), Screen.anim(this, "stay_out"));
    }

    private void openDrawer() {
        Screen.open(this, new Intent(this, AppsActivity.class));
    }

    // ==================== Paneles ====================

    private void toggleStudy() {
        if (Prefs.studying(this)) {
            Sheet.confirm(this, backdrop, "¿Terminar el modo estudio?", "Las apps que distraen vuelven a estar disponibles.",
                    "Terminar", false, () -> {
                        Prefs.setStudyEnd(this, 0);
                        refreshStudy();
                    });
            return;
        }
        int[] mins = {25, 50, 90};
        String[] names = {"25 minutos", "50 minutos", "1 hora y media"};
        List<Sheet.Item> items = new ArrayList<>();
        for (int k = 0; k < mins.length; k++) {
            final int m = mins[k];
            items.add(Sheet.item(names[k], Icons.FOCUS, () -> {
                Prefs.setStudyEnd(this, System.currentTimeMillis() + m * 60_000L);
                refreshStudy();
                Sheet.pill(this, backdrop, "Modo estudio activado");
            }));
        }
        Sheet.show(this, backdrop, "Modo estudio", items);
    }

    private void refreshStudy() {
        boolean on = Prefs.studying(this);
        if (on) {
            long left = Prefs.studyEnd(this) - System.currentTimeMillis();
            int min = (int) Math.ceil(left / 60_000.0);
            studyLine.setText("MODO ESTUDIO  ·  " + min + " MIN");
            studyLine.setVisibility(View.VISIBLE);
            studySub.setText("Faltan " + min + " min");
            long total = Math.max(left, 25 * 60_000L);
            for (int m : new int[]{25, 50, 90}) if (left <= m * 60_000L) {
                total = m * 60_000L;
                break;
            }
            studyIcon.setProgress(left / (float) total);
        } else {
            studyLine.setVisibility(View.GONE);
            studySub.setText("Modo enfoque");
            studyIcon.setProgress(0);
            if (wasStudying) Sheet.pill(this, backdrop, "Terminó tu sesión de estudio. Buen trabajo.");
        }
        if (on != wasStudying) refreshDock();
        wasStudying = on;
    }

    private void refreshTasks() {
        int pending = 0;
        try {
            JSONArray arr = new JSONArray(Prefs.tasks(this));
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
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
            safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + CLAUDE_PKG)));
        }
    }

    // ==================== Ventanas flotantes ====================

    private void showOverlay(View panel, FrameLayout.LayoutParams lp, int dimColor) {
        overlay.removeAllViews();
        View dim = new View(this);
        dim.setBackgroundColor(dimColor);
        dim.setOnClickListener(v -> {
            Sound.play(v, Sound.BACK);
            closeOverlay();
        });
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        overlay.addView(panel, lp);
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setStartDelay(0).setDuration(220).withEndAction(null).start();
        panel.setScaleX(0.9f);
        panel.setScaleY(0.9f);
        panel.setTranslationY(-Ui.dp(this, 12));
        panel.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(420)
                .setInterpolator(new OvershootInterpolator(1.3f)).start();
    }

    private void closeOverlay() {
        bottomBox.animate().alpha(1f).translationY(0).setStartDelay(0).setDuration(360)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        overlay.animate().alpha(0f).setStartDelay(0).setDuration(200).withEndAction(() -> {
            overlay.setVisibility(View.GONE);
            overlay.removeAllViews();
        }).start();
    }

    private void showCalendar() {
        CalendarPanel cal = new CalendarPanel(this, backdrop, "Ver pendientes", () -> {
            closeOverlay();
            Screen.open(this, new Intent(this, PendientesActivity.class));
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(this, 336), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = insetTop + (int) (getResources().getDisplayMetrics().heightPixels * 0.07f);
        showOverlay(cal, lp, 0x66000000);
    }

    private void showThemePicker() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);

        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        Glass.apply(dots, backdrop, 14, Glass.TINT_STRONG);
        int dp6 = Ui.dp(this, 6);
        dots.setPadding(dp6 * 2, dp6 + 2, dp6 * 2, dp6 + 2);
        View[] dotViews = new View[Scenes.COUNT];
        for (int k = 0; k < Scenes.COUNT; k++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            dots.addView(dot, lp);
            dotViews[k] = dot;
        }
        box.addView(dots, new LinearLayout.LayoutParams(-2, -2));

        int cardW = Ui.dp(this, 150), cardH = Ui.dp(this, 270);
        CoverFlow flow = new CoverFlow(this, cardW, Ui.dp(this, 2));
        ThemeCard[] cards = new ThemeCard[Scenes.COUNT];
        for (int k = 0; k < Scenes.COUNT; k++) {
            ThemeCard card = new ThemeCard(this, k);
            final int idx = k;
            card.setOnClickListener(v -> flow.smoothTo(idx));
            flow.add(card, cardH);
            cards[k] = card;
        }
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, cardH + Ui.dp(this, 40));
        flp.topMargin = Ui.dp(this, 18);
        box.addView(flow, flp);

        TextView name = Ui.text(this, "", 15, Ui.TEXT, Ui.DISPLAY);
        name.setLetterSpacing(0.35f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = Ui.dp(this, 10);
        box.addView(name, nlp);
        TextView hint = Ui.text(this, "Desliza para cambiar el fondo", 13, 0xB3FFFFFF, Ui.REGULAR);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.topMargin = Ui.dp(this, 8);
        box.addView(hint, hlp);

        int current = Prefs.theme(this);
        final long openedAt = System.currentTimeMillis();
        flow.setListener(new CoverFlow.Listener() {
            @Override
            public void onCentered(int index) {
                for (int k = 0; k < cards.length; k++) {
                    cards[k].setSelectedCard(k == index);
                    dotViews[k].setBackground(Ui.fill(MainActivity.this, k == index ? 0xFFFFFFFF : 0x4DFFFFFF, 4));
                }
                name.setText(Scenes.NAMES[index].toUpperCase(ES));
                if (System.currentTimeMillis() - openedAt > 400) Sound.play(flow, Sound.TAP);
            }

            @Override
            public void onSettled(int index) {
                if (index == Prefs.theme(MainActivity.this)) return;
                Sound.play(flow, Sound.THEME);
                Prefs.setTheme(MainActivity.this, index);
                backdrop.setTheme(index);
                applyThemeColors();
            }
        });
        flow.jumpTo(current);
        name.setText(Scenes.NAMES[current].toUpperCase(ES));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.topMargin = Ui.dp(this, 60);
        showOverlay(box, lp, 0x99000000);
        // Mientras se elige fondo, los paneles y el dock se apartan
        bottomBox.animate().alpha(0f).translationY(Ui.dp(this, 40)).setStartDelay(0).setDuration(260).start();
    }

    private void showSettings() {
        List<Sheet.Item> items = new ArrayList<>();
        items.add(Sheet.item("Sonidos", Icons.SOUND, () -> Prefs.setSounds(this, !Prefs.sounds(this)))
                .value(() -> Prefs.sounds(this) ? "Sí" : "No"));
        items.add(Sheet.item("Fondo en movimiento", Icons.WAVES, () -> {
            Prefs.setAnimated(this, !Prefs.animated(this));
            backdrop.setAnimated(Prefs.animated(this));
        }).value(() -> Prefs.animated(this) ? "Sí" : "No"));
        items.add(Sheet.item("Efecto al inclinar", Icons.SHAPES, () -> {
            Prefs.setParallax(this, !Prefs.parallax(this));
            backdrop.setParallax(Prefs.parallax(this));
        }).value(() -> Prefs.parallax(this) ? "Sí" : "No"));
        items.add(Sheet.item("Íconos monocromos", Icons.HOME, () -> {
            Prefs.setMonoIcons(this, !Prefs.monoIcons(this));
            refreshDock();
        }).value(() -> Prefs.monoIcons(this) ? "Sí" : "No"));
        items.add(Sheet.item("Cambiar fondo", Icons.IMAGE, this::showThemePicker));
        items.add(Sheet.item("Pantalla de inicio", Icons.GEAR, () -> safeStart(new Intent(Settings.ACTION_HOME_SETTINGS))));
        Sheet.show(this, backdrop, "Jostin OS", items);
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Sheet.pill(this, backdrop, "No se pudo abrir");
        }
    }

    @SuppressWarnings({"WrongConstant", "JavaReflectionMemberAccess"})
    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class<?> cls = Class.forName("android.app.StatusBarManager");
            cls.getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception ignored) {
            // Algunos teléfonos no lo permiten
        }
    }

    // ==================== Utilidades ====================

    static void launch(Activity a, String pkg, View from) {
        Intent i = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle opts = null;
        if (from != null) {
            opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
        }
        try {
            a.startActivity(i, opts);
        } catch (Exception ignored) {
            // nada
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
}
