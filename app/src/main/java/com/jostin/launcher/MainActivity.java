package com.jostin.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements Studio.Host, Dashboard.Host, Drawer.Host, Backdrop.Listener {
    static final Locale ES = new Locale("es", "MX");
    private static final int REQ_PICK = 100, REQ_PHOTO = 200;
    private static final String CLAUDE_PKG = "com.anthropic.claude";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Backdrop backdrop;
    private FrameLayout root, content, overlay;
    private ClockView clock;
    private LinearLayout bottomBox, dock, panelsView;
    private Widgets.MusicCard musicCard;
    private TextView studySub, tasksSub;
    private StudyIcon studyIcon;
    private Dashboard dashboard;
    private Drawer drawer;
    private final List<View> panels = new ArrayList<>();
    private final FrameLayout[] dockSlots = new FrameLayout[Prefs.SLOTS];
    private final ImageView[] dockIcons = new ImageView[Prefs.SLOTS];
    private GestureDetector gestures;
    private boolean wasStudying = false, drawerOpen = false;
    private int insetTop = 0;

    // Arrastre desde inicio
    private static final int DRAG_NONE = 0, DRAG_DRAWER = 1, DRAG_DASH = 2, DRAG_DONE = 3;
    private int drag = DRAG_NONE;
    private float downX, downY;
    private VelocityTracker vt;
    private int slop;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            clock.refresh();
            refreshStudy();
            handler.postDelayed(this, 15_000);
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Drawer.invalidateApps();
            refreshDock();
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        Sound.init(this);
        Theme.recompute(this);
        slop = ViewConfiguration.get(this).getScaledTouchSlop();
        backdrop = new Backdrop(this);
        backdrop.setListener(this);
        Ui.edgeToEdge(this, false);
        if (!Prefs.slotsInitialized(this)) fillDefaultSlots();
        buildUi();
        backdrop.useCurrent();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        handleDemo(getIntent());
        if (!Prefs.onboarded(this)) {
            Prefs.setOnboarded(this);
            handler.postDelayed(() -> Sheet.pill(this, backdrop, "Mantén presionado el fondo para personalizar"), 1400);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packages);
        } catch (Exception ignored) {
            // nada
        }
    }

    // ==================== Construcción ====================

    private void buildUi() {
        root = new FrameLayout(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        clock = new ClockView(this, backdrop);
        content.addView(clock, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        clock.setOnClickListener(v -> {
            Sound.play(v, Sound.TAP);
            showCalendar();
        });
        clock.setOnLongClickListener(v -> {
            Sound.play(v, Sound.HOLD);
            Studio.show(this, this, -1);
            return true;
        });

        bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = Ui.dp(this, 12);
        bottomBox.setPadding(side, 0, side, 0);
        musicCard = new Widgets.MusicCard(this, backdrop);
        musicCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.setMargins(Ui.dp(this, 5), 0, Ui.dp(this, 5), Ui.dp(this, 6));
        bottomBox.addView(musicCard, mlp);
        panelsView = buildPanels();
        bottomBox.addView(panelsView, new LinearLayout.LayoutParams(-1, -2));
        dock = buildDock();
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(this, 16);
        bottomBox.addView(dock, dlp);
        content.addView(bottomBox, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        dashboard = new Dashboard(this, backdrop, this);
        root.addView(dashboard, new FrameLayout.LayoutParams(-1, -1));
        drawer = new Drawer(this, backdrop, this, false);
        drawer.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));

        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                doubleTap();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (drag != DRAG_NONE) return;
                int g = Prefs.gLong(MainActivity.this);
                if (g == 2) return;
                Sound.play(backdrop, Sound.HOLD);
                Studio.show(MainActivity.this, MainActivity.this, g == 1 ? 0 : -1);
            }
        });
        content.setOnTouchListener(this::onHomeTouch);

        root.setOnApplyWindowInsetsListener((v, in) -> {
            insetTop = in.getSystemWindowInsetTop();
            positionClock();
            ((FrameLayout.LayoutParams) bottomBox.getLayoutParams()).bottomMargin = in.getSystemWindowInsetBottom() + Ui.dp(this, 14);
            bottomBox.requestLayout();
            return in;
        });
        setContentView(root);
        applyHome();
    }

    private void positionClock() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) clock.getLayoutParams();
        int screenH = getResources().getDisplayMetrics().heightPixels;
        boolean left = Prefs.clockAlign(this) == 1;
        lp.gravity = Gravity.TOP | (left ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        lp.leftMargin = left ? Ui.dp(this, 26) : 0;
        lp.topMargin = insetTop + (int) (screenH * (Prefs.clockStyle(this) == 0 ? 0.10f : 0.08f));
        clock.setLayoutParams(lp);
    }

    private LinearLayout buildPanels() {
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

    private View panel(Icons.Shape icon, String title, String sub, View customIcon, View.OnClickListener action) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        Glass.apply(p, backdrop, 24);
        p.setPadding(Ui.dp(this, 13), 0, Ui.dp(this, 10), 0);
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
            openDrawer(false);
        });
        d.addView(grid, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return d;
    }

    /** Ícono del panel de estudio: anillo con lo que falta de la sesión. */
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
                ring.setColor(Theme.accent);
                track.setStrokeWidth(sw);
                oval.set(sw, sw, w - sw, h - sw);
                c.drawArc(oval, 0, 360, false, track);
                c.drawArc(oval, -90, 360 * progress, false, ring);
            }
        }
    }

    // ==================== Aplicar cambios (también desde el estudio) ====================

    @Override
    public Backdrop backdrop() {
        return backdrop;
    }

    @Override
    public void onWallpaperChanged() {
        applyLook();
    }

    @Override
    public void applyWallpaper() {
        backdrop.setAnimated(Prefs.animated(this));
        backdrop.useCurrent();
    }

    @Override
    public void applyLook() {
        Ui.setLightBars(this, Theme.wallLight && !drawerOpen && !dashboard.isOpen());
        clock.refresh();
        if (Prefs.iconStyle(this) == 2) {
            Drawer.trim();
            refreshDock();
        }
        studyIcon.invalidate();
        dashboard.invalidate();
        backdrop.invalidateGlass();
    }

    @Override
    public void applyHome() {
        clock.rebuild();
        positionClock();
        panelsView.setVisibility(Prefs.showPanels(this) ? View.VISIBLE : View.GONE);
        dock.setVisibility(Prefs.showDock(this) ? View.VISIBLE : View.GONE);
        int n = Prefs.dockCount(this);
        for (int i = 0; i < Prefs.SLOTS; i++) dockSlots[i].setVisibility(i < n ? View.VISIBLE : View.GONE);
        if (Prefs.showMusic(this)) Media.addListener(this, musicCard);
        else Media.removeListener(musicCard);
        musicCard.onMediaChanged();
        refreshDock();
        refreshStudy();
    }

    // ==================== Ciclo de vida ====================

    @Override
    protected void onStart() {
        super.onStart();
        backdrop.setAnimated(Prefs.animated(this));
        backdrop.setParallax(Prefs.parallax(this));
        backdrop.setSmooth(Prefs.smooth(this));
        backdrop.setRunning(true);
        Ui.setLightBars(this, Theme.wallLight && !drawerOpen && !dashboard.isOpen());
        refreshDock();
        refreshTasks();
        if (Prefs.showMusic(this)) Media.addListener(this, musicCard);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        Weather.refresh(this, false, n -> {
            clock.refresh();
            dashboard.refreshWeather();
        });
        if (!drawerOpen && !dashboard.isOpen()) playEntrance();
    }

    @Override
    protected void onStop() {
        super.onStop();
        backdrop.setRunning(false);
        Media.removeListener(musicCard);
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            Wallpaper.trim();
            Drawer.trim();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (handleDemo(intent)) return;
        // Botón de inicio estando en inicio: cerrar lo que esté abierto
        if (Sheet.dismissTop(this)) return;
        if (overlay.getVisibility() == View.VISIBLE) {
            closeOverlay();
            return;
        }
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (dashboard.isOpen()) {
            closeDashboard();
            return;
        }
        playEntrance();
    }

    @Override
    public void onBackPressed() {
        if (Sheet.dismissTop(this)) return;
        if (overlay.getVisibility() == View.VISIBLE) closeOverlay();
        else if (drawerOpen) closeDrawer();
        else if (dashboard.isOpen()) closeDashboard();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req >= REQ_PICK && req < REQ_PICK + Prefs.SLOTS) {
            Prefs.setSlot(this, req - REQ_PICK, data.getStringExtra(AppsActivity.EXTRA_PKG));
            refreshDock();
        } else if (req == REQ_PHOTO && data.getData() != null) {
            importPhoto(data.getData(), null);
        }
    }

    private void playEntrance() {
        float d = getResources().getDisplayMetrics().density;
        clock.playEntrance();
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

    // ==================== Gestos en inicio ====================

    private boolean onHomeTouch(View v, MotionEvent e) {
        int a = e.getActionMasked();
        backdrop.touch(e.getX(), e.getY(), a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE);
        if (a == MotionEvent.ACTION_DOWN) {
            drag = DRAG_NONE;
            downX = e.getRawX();
            downY = e.getRawY();
            if (vt != null) vt.recycle();
            vt = VelocityTracker.obtain();
        }
        if (vt != null) vt.addMovement(e);
        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
        float h = root.getHeight();
        if (drag == DRAG_NONE && a == MotionEvent.ACTION_MOVE && Math.abs(dy) > slop * 1.5f && Math.abs(dy) > Math.abs(dx)) {
            MotionEvent cancel = MotionEvent.obtain(e);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            gestures.onTouchEvent(cancel);
            cancel.recycle();
            if (dy < 0) {
                if (Prefs.gUp(this) == 2) {
                    drag = DRAG_DONE;
                } else {
                    drag = DRAG_DRAWER;
                    drawer.load(false);
                    drawer.setProgress(0f);
                }
            } else {
                int g = Prefs.gDown(this);
                if (g == 0) {
                    drag = DRAG_DASH;
                    dashboard.setProgress(0f);
                } else {
                    drag = DRAG_DONE;
                    if (g == 1) expandNotifications();
                }
            }
        }
        if (drag == DRAG_NONE) {
            gestures.onTouchEvent(e);
            return true;
        }
        if (drag == DRAG_DRAWER) {
            float f = Math.max(0f, Math.min(1f, -dy / (h * 0.45f)));
            if (a == MotionEvent.ACTION_MOVE) {
                drawer.setProgress(f);
                content.setAlpha(1f - f * 0.8f);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                vt.computeCurrentVelocity(1000);
                if (f > 0.28f || vt.getYVelocity() < -900) openDrawer(Prefs.gUp(this) == 1);
                else closeDrawer();
                drag = DRAG_NONE;
            }
        } else if (drag == DRAG_DASH) {
            float f = Math.max(0f, Math.min(1f, dy / (h * 0.4f)));
            if (a == MotionEvent.ACTION_MOVE) {
                dashboard.setProgress(f);
                content.setAlpha(1f - f * 0.8f);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                vt.computeCurrentVelocity(1000);
                if (f > 0.25f || vt.getYVelocity() > 900) openDashboard();
                else closeDashboard();
                drag = DRAG_NONE;
            }
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            drag = DRAG_NONE;
        }
        return true;
    }

    private void doubleTap() {
        switch (Prefs.gDouble(this)) {
            case 0:
                if (!LockService.lock()) requestLock();
                break;
            case 1:
                boolean on = Device.toggleTorch(this);
                Sound.play(content, Sound.TOGGLE);
                Sheet.pill(this, backdrop, on ? "Linterna encendida" : "Linterna apagada");
                break;
            case 2:
                nextWallpaper();
                break;
            default:
                break;
        }
    }

    private void nextWallpaper() {
        List<String> photos = Wallpaper.photos(this);
        int total = photos.size() + Scenes.COUNT;
        int cur;
        if (Prefs.wallKind(this) == 1 && photos.contains(Prefs.wallPhoto(this))) cur = photos.indexOf(Prefs.wallPhoto(this));
        else cur = photos.size() + Prefs.wallScene(this);
        int next = (cur + 1) % total;
        if (next < photos.size()) {
            Prefs.setWallKind(this, 1);
            Prefs.setWallPhoto(this, photos.get(next));
        } else {
            Prefs.setWallKind(this, 0);
            Prefs.setWallScene(this, next - photos.size());
        }
        Sound.play(content, Sound.THEME);
        applyWallpaper();
    }

    // ==================== Cajón y panel ====================

    private void openDrawer(boolean search) {
        drawerOpen = true;
        drawer.load(drawer.getVisibility() != View.VISIBLE);
        if (drawer.getVisibility() != View.VISIBLE) drawer.setProgress(0f);
        drawer.settle(true, search ? drawer::focusSearch : null);
        content.animate().alpha(0f).setStartDelay(0).setDuration(240).start();
        Ui.setLightBars(this, false);
    }

    @Override
    public void closeDrawer() {
        drawerOpen = false;
        drawer.settle(false, null);
        content.animate().alpha(1f).setStartDelay(0).setDuration(300).start();
        Ui.setLightBars(this, Theme.wallLight);
    }

    private void openDashboard() {
        dashboard.settle(true);
        content.animate().alpha(0f).setStartDelay(0).setDuration(240).start();
        Ui.setLightBars(this, false);
    }

    @Override
    public void closeDashboard() {
        dashboard.settle(false);
        content.animate().alpha(1f).setStartDelay(0).setDuration(300).start();
        Ui.setLightBars(this, Theme.wallLight);
    }

    @Override
    public void appActions(Drawer.App app, View from) {
        Sheet.show(this, backdrop, app.label,
                Sheet.item("Poner en el dock", Icons.HOME, () -> chooseSlot(app)),
                Sheet.item("Ocultar del cajón", Icons.CLOSE, () -> {
                    Set<String> h = Prefs.hidden(this);
                    h.add(app.pkg);
                    Prefs.setHidden(this, h);
                    drawer.load(false);
                    Sheet.pill(this, backdrop, app.label + " quedó oculta");
                }),
                Sheet.item("Información de la app", Icons.INFO, () -> appInfo(this, app.pkg)),
                Sheet.item("Desinstalar", Icons.TRASH, () -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)));
                    } catch (Exception e) {
                        appInfo(this, app.pkg);
                    }
                }).danger());
    }

    private void chooseSlot(Drawer.App app) {
        List<Sheet.Item> items = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (int k = 0; k < Prefs.dockCount(this); k++) {
            final int slot = k;
            String cur = Prefs.slot(this, k);
            String name = "Vacío";
            if (cur != null) {
                try {
                    name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(cur, 0)));
                } catch (PackageManager.NameNotFoundException e) {
                    name = "Vacío";
                }
            }
            final String n = name;
            Sheet.Item it = Sheet.item("Lugar " + (k + 1), Icons.SWAP, () -> {
                Prefs.setSlot(this, slot, app.pkg);
                refreshDock();
                Sheet.pill(this, backdrop, app.label + " quedó en el dock");
            });
            it.value = () -> n;
            items.add(it);
        }
        Sheet.show(this, backdrop, "¿En qué lugar?", items);
    }

    @Override
    public void showHiddenApps() {
        Set<String> hidden = Prefs.hidden(this);
        if (hidden.isEmpty()) {
            Sheet.pill(this, backdrop, "No tienes apps ocultas");
            return;
        }
        List<Sheet.Item> items = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (String pkg : hidden) {
            String name = pkg;
            try {
                name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (PackageManager.NameNotFoundException ignored) {
                // se queda el paquete
            }
            final String n = name;
            Sheet.Item it = Sheet.item(n, Icons.SHAPES, () -> {
                Set<String> h = Prefs.hidden(this);
                h.remove(pkg);
                Prefs.setHidden(this, h);
                drawer.load(false);
                Sheet.pill(this, backdrop, n + " volvió al cajón");
            });
            it.value = () -> "Mostrar";
            it.keepOpen = false;
            items.add(it);
        }
        Sheet.show(this, backdrop, "Apps ocultas", items);
    }

    // ==================== Panel (Dashboard.Host) ====================

    @Override
    public void personalize() {
        closeDashboard();
        handler.postDelayed(() -> Studio.show(this, this, -1), 260);
    }

    @Override
    public void askCity() {
        Sheet.prompt(this, backdrop, "¿De qué ciudad quieres el clima?", "Ejemplo: Cuautla", Prefs.city(this), v -> {
            Prefs.setCity(this, v);
            clock.refresh();
            dashboard.refreshWeather();
            if (!v.isEmpty()) {
                Sheet.pill(this, backdrop, "Buscando el clima de " + v);
                Weather.refresh(this, true, n -> {
                    clock.refresh();
                    dashboard.refreshWeather();
                });
            }
        });
    }

    @Override
    public void askGreeting() {
        Sheet.prompt(this, backdrop, "Frase bajo el reloj", "Ejemplo: Hoy se construye algo grande", Prefs.greeting(this), v -> {
            Prefs.setGreeting(this, v);
            clock.refresh();
        });
    }

    @Override
    public void quick(int action) {
        switch (action) {
            case Dashboard.Q_TORCH:
                boolean on = Device.toggleTorch(this);
                Sheet.pill(this, backdrop, on ? "Linterna encendida" : "Linterna apagada");
                break;
            case Dashboard.Q_STUDY:
                closeDashboard();
                handler.postDelayed(this::toggleStudy, 260);
                break;
            case Dashboard.Q_WALL:
                nextWallpaper();
                break;
            default:
                if (!LockService.lock()) requestLock();
                break;
        }
    }

    @Override
    public void requestLock() {
        Sheet.confirm(this, backdrop, "Bloquear con doble toque",
                "Android solo deja bloquear la pantalla a un servicio de accesibilidad. Activa «Jostin OS» en la lista que se va a abrir. No lee nada de tu pantalla.",
                "Abrir ajustes", false, () -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception ignored) {
                        // nada
                    }
                });
    }

    // ==================== Fotos (Studio.Host) ====================

    @Override
    public void pickPhoto() {
        Intent i;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            i = new Intent(MediaStore.ACTION_PICK_IMAGES);
            i.setType("image/*");
        } else {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
        }
        try {
            startActivityForResult(i, REQ_PHOTO);
        } catch (ActivityNotFoundException e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.setType("image/*");
            try {
                startActivityForResult(g, REQ_PHOTO);
            } catch (ActivityNotFoundException ignored) {
                Sheet.pill(this, backdrop, "No encontré una galería");
            }
        }
    }

    private void importPhoto(Uri uri, File file) {
        Sheet.pill(this, backdrop, "Preparando tu foto…");
        Wallpaper.Done<String> done = id -> {
            if (id == null) {
                Sheet.pill(this, backdrop, "No pude abrir esa foto");
                return;
            }
            Prefs.setWallKind(this, 1);
            Prefs.setWallPhoto(this, id);
            applyWallpaper();
            if (Studio.strip != null && Studio.strip.isAttachedToWindow()) {
                Studio.strip.rebuild();
                Studio.strip.scrollToSelected();
            }
        };
        if (uri != null) Wallpaper.importUri(this, uri, done);
        else Wallpaper.importFile(this, file, done);
    }

    @Override
    public void deletePhoto(String id) {
        Sheet.confirm(this, backdrop, "¿Borrar esta foto?", "Se quita de tus fondos. La foto original de tu galería no se toca.", "Borrar", true, () -> {
            boolean current = Prefs.wallKind(this) == 1 && id.equals(Prefs.wallPhoto(this));
            Wallpaper.delete(this, id);
            if (current) {
                Prefs.setWallKind(this, 0);
                applyWallpaper();
            }
            handler.postDelayed(() -> Studio.show(this, this, 0), 250);
        });
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
        boolean studying = Prefs.studying(this);
        for (int i = 0; i < Prefs.SLOTS; i++) {
            ImageView iv = dockIcons[i];
            String pkg = Prefs.slot(this, i);
            Drawable icon = null;
            if (pkg != null && getPackageManager().getLaunchIntentForPackage(pkg) != null) icon = Icons.app(this, pkg, Prefs.iconStyle(this));
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
            clock.setStudy("MODO ESTUDIO  ·  " + min + " MIN");
            studySub.setText("Faltan " + min + " min");
            long total = 90 * 60_000L;
            for (int m : new int[]{25, 50, 90}) {
                if (left <= m * 60_000L) {
                    total = m * 60_000L;
                    break;
                }
            }
            studyIcon.setProgress(left / (float) total);
        } else {
            clock.setStudy(null);
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
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + CLAUDE_PKG)));
            } catch (Exception ignored) {
                // nada
            }
        }
    }

    // ==================== Calendario flotante ====================

    private void showOverlay(View panel, FrameLayout.LayoutParams lp) {
        overlay.removeAllViews();
        View dim = new View(this);
        dim.setBackgroundColor(0x66000000);
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
        showOverlay(cal, lp);
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

    // ==================== Atajos para la revisión automática ====================

    private boolean handleDemo(Intent i) {
        if (i == null || i.getExtras() == null) return false;
        boolean any = false;
        if (i.hasExtra("demo_reset")) {
            Prefs.setClockStyle(this, 0);
            Prefs.setClockAlign(this, 0);
            Prefs.setClockFont(this, 0);
            Prefs.setWallOverlay(this, 0);
            Prefs.setWallEffect(this, 0);
            Prefs.setAccentMode(this, 0);
            applyHome();
            any = true;
        }
        if (i.hasExtra("theme")) {
            Prefs.setWallKind(this, 0);
            Prefs.setWallScene(this, Math.max(0, Math.min(Scenes.COUNT - 1, i.getIntExtra("theme", 0))));
            any = true;
        }
        if (i.hasExtra("demo_effect")) {
            Prefs.setWallEffect(this, i.getIntExtra("demo_effect", 0));
            any = true;
        }
        if (i.hasExtra("demo_overlay")) {
            Prefs.setWallOverlay(this, i.getIntExtra("demo_overlay", 0));
            any = true;
        }
        if (i.hasExtra("demo_clock")) {
            Prefs.setClockStyle(this, i.getIntExtra("demo_clock", 0));
            Prefs.setClockAlign(this, i.getIntExtra("demo_align", 0));
            Prefs.setClockFont(this, i.getIntExtra("demo_font", 0));
            applyHome();
            any = true;
        }
        if (i.hasExtra("demo_city")) {
            Prefs.setCity(this, i.getStringExtra("demo_city"));
            Weather.refresh(this, true, n -> {
                clock.refresh();
                dashboard.refreshWeather();
            });
            any = true;
        }
        String photo = i.getStringExtra("demo_photo");
        if (photo != null) {
            File f = new File(getExternalFilesDir(null), photo);
            if (f.exists()) importPhoto(null, f);
            any = true;
        } else if (any) {
            applyWallpaper();
        }
        String shot = i.getStringExtra("shot");
        if (shot != null) {
            any = true;
            int tab = i.getIntExtra("tab", 0);
            handler.postDelayed(() -> {
                switch (shot) {
                    case "studio": Studio.show(this, this, tab); break;
                    case "dashboard": dashboard.setProgress(0f); openDashboard(); break;
                    case "drawer": openDrawer(false); break;
                    case "calendar": showCalendar(); break;
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
            }, 900);
        }
        String open = i.getStringExtra("open");
        if (open != null) {
            any = true;
            Class<?> cls = open.equals("calc") ? CalcActivity.class : open.equals("tasks") ? PendientesActivity.class : AppsActivity.class;
            Intent x = new Intent(this, cls);
            x.putExtras(i.getExtras());
            x.removeExtra("open");
            handler.postDelayed(() -> Screen.open(this, x), 300);
        }
        // Que no se repitan si la actividad se recrea
        for (String k : new String[]{"demo_reset", "theme", "demo_effect", "demo_overlay", "demo_clock", "demo_city", "demo_photo", "shot", "open"}) i.removeExtra(k);
        return any;
    }

    // ==================== Utilidades ====================

    static void launch(Activity a, String pkg, View from) {
        Intent i = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle opts = null;
        if (from != null) opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
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
