package com.jostin.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
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

import org.json.JSONArray;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements Studio.Host, Dashboard.Host, Drawer.Host, Backdrop.Listener, Media.Listener {
    static final Locale ES = new Locale("es", "MX");
    static final int REQ_PICK = 100, REQ_PHOTO = 200, REQ_ADD_APP = 300, REQ_BIND = 301, REQ_CONFIG = 302,
            REQ_GADGET_PHOTO = 303, REQ_ICON = 304, REQ_FOLDER_ADD = 305, REQ_CAL = 7;
    static final String CLAUDE_PKG = "com.anthropic.claude";

    final Handler handler = new Handler(Looper.getMainLooper());
    private Backdrop backdrop;
    private FrameLayout root, content, overlay;
    HomeView home;
    private LinearLayout bottomBox, dock;
    private ImageView drawerButton;
    private View dockSep;
    private Dashboard dashboard;
    private Drawer drawer;
    private final FrameLayout[] dockSlots = new FrameLayout[Prefs.SLOTS];
    private final ImageView[] dockIcons = new ImageView[Prefs.SLOTS];
    private final View[] dockBadges = new View[Prefs.SLOTS];
    private final String[] dockShown = new String[Prefs.SLOTS];
    private boolean wasStudying = false, drawerOpen = false, recovered = false;
    private int insetTop = 0, insetBottom = 0;
    private Set<String> badges = new HashSet<>();
    private int lookAccent = 0;
    private boolean lookLight = false;

    // Cosas en espera mientras otra pantalla responde
    private HomeModel.Item pendingItem;
    private String pendingPkg;
    private int pendingWidget = -1;

    // Arrastre vertical desde inicio (cajón o panel)
    private static final int V_NONE = 0, V_DRAWER = 1, V_DASH = 2, V_DONE = 3;
    private int vdrag = V_NONE;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            home.tick();
            refreshStudy();
            handler.postDelayed(this, 15_000);
        }
    };

    private final Runnable stable = () -> Safety.stable(this);

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Drawer.invalidateApps();
            Drawer.trim();
            String pkg = i.getData() != null ? i.getData().getSchemeSpecificPart() : null;
            boolean replacing = i.getBooleanExtra(Intent.EXTRA_REPLACING, false);
            if (pkg != null && pkg.equals(Prefs.iconPack(MainActivity.this))) IconEngine.Pack.reset();
            if (Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && !replacing && pkg != null) removeApp(pkg);
            else if (pkg != null) refreshIcons(pkg);
            refreshDock();
        }
    };

    /** El perfil de trabajo se pausó, se reanudó, se creó o se borró. */
    private final BroadcastReceiver profiles = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            reloadWork();
        }
    };

    /** Apps que instalan o quitan en el perfil de trabajo; las tuyas ya las avisa el receptor de paquetes. */
    private final LauncherApps.Callback workApps = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String pkg, UserHandle user) { if (!user.equals(Process.myUserHandle())) reloadWork(); }
        @Override public void onPackageAdded(String pkg, UserHandle user) { if (!user.equals(Process.myUserHandle())) reloadWork(); }
        @Override public void onPackageChanged(String pkg, UserHandle user) { if (!user.equals(Process.myUserHandle())) reloadWork(); }
        @Override public void onPackagesAvailable(String[] pkgs, UserHandle user, boolean replacing) { if (!user.equals(Process.myUserHandle())) reloadWork(); }
        @Override public void onPackagesUnavailable(String[] pkgs, UserHandle user, boolean replacing) { if (!user.equals(Process.myUserHandle())) reloadWork(); }
    };

    private void reloadWork() {
        Drawer.invalidateApps();
        if (drawerOpen) drawer.load(false);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        Sound.init(this);
        Theme.recompute(this);
        recovered = Safety.onCreate(this);
        current = new java.lang.ref.WeakReference<>(this);
        int savedPage = 0;
        if (saved != null) {
            pendingWidget = saved.getInt("pending_widget", -1);
            savedPage = saved.getInt("home_page", 0);
        }
        backdrop = new Backdrop(this);
        backdrop.setListener(this);
        Ui.edgeToEdge(this, false);
        if (!Prefs.slotsInitialized(this)) fillDefaultSlots();
        buildUi();
        // Android rehace la pantalla cuando cambian los colores del sistema (al cambiar el fondo): se vuelve a la misma página
        home.page = savedPage;
        backdrop.useCurrent();
        SystemWall.setVisible(this, true);
        SystemWall.sync(this);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        IntentFilter wf = new IntentFilter();
        wf.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
        wf.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
        wf.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
        wf.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        wf.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
        registerReceiver(profiles, wf);
        LauncherApps la = getSystemService(LauncherApps.class);
        if (la != null) la.registerCallback(workApps, handler);
        handleDemo(getIntent());
        if (recovered) {
            handler.postDelayed(() -> Sheet.pill(this, backdrop, "Algo falló al abrir. Regresé a tu último diseño que funcionaba."), 1200);
        } else if (!Prefs.onboarded(this)) {
            Prefs.setOnboarded(this);
            handler.postDelayed(() -> Sheet.pill(this, backdrop, "Mantén presionado cualquier cosa para moverla"), 1400);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("pending_widget", pendingWidget);
        out.putInt("home_page", home.page);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packages);
            unregisterReceiver(profiles);
        } catch (Exception ignored) {
            // nada
        }
        LauncherApps la = getSystemService(LauncherApps.class);
        if (la != null) la.unregisterCallback(workApps);
    }

    // ==================== Construcción ====================

    private void buildUi() {
        root = new FrameLayout(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        home = new HomeView(this, backdrop);
        content.addView(home, new FrameLayout.LayoutParams(-1, -1));

        bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER_HORIZONTAL);
        dock = buildDock();
        bottomBox.addView(dock, new LinearLayout.LayoutParams(-2, -2));
        content.addView(bottomBox, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        dashboard = new Dashboard(this, backdrop, this);
        root.addView(dashboard, new FrameLayout.LayoutParams(-1, -1));
        drawer = new Drawer(this, backdrop, this, false);
        drawer.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));

        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        root.setOnApplyWindowInsetsListener((v, in) -> {
            insetTop = in.getSystemWindowInsetTop();
            insetBottom = in.getSystemWindowInsetBottom();
            ((FrameLayout.LayoutParams) bottomBox.getLayoutParams()).bottomMargin = insetBottom + Ui.dp(this, 14);
            bottomBox.requestLayout();
            updateHomeFrame();
            return in;
        });
        setContentView(root);
        lookAccent = Theme.accent;
        lookLight = Theme.wallLight;
        layoutDock();
        refreshDock();
    }

    /** Dónde empieza y termina la cuadrícula de inicio según la barra de estado y el dock. */
    private void updateHomeFrame() {
        int dockH = Prefs.showDock(this) ? Ui.dp(this, Prefs.dockIconSize(this) + 16 + 6) : 0;
        int reserve = insetBottom + Ui.dp(this, 14) + dockH;
        home.setFrame(insetTop, reserve, insetBottom + Ui.dp(this, 14));
    }

    /** Dock: al deslizar hacia arriba sobre él también se abre el cajón. */
    private final class DockBar extends LinearLayout {
        private float y0, x0;
        private boolean vertical;
        private VelocityTracker vt;
        private final int slop;

        DockBar(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    y0 = e.getRawY();
                    x0 = e.getRawX();
                    vertical = false;
                    if (vt != null) vt.recycle();
                    vt = VelocityTracker.obtain();
                    vt.addMovement(e);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (vt != null) vt.addMovement(e);
                    float dy = e.getRawY() - y0, dx = e.getRawX() - x0;
                    if (!vertical && -dy > slop * 1.5f && Math.abs(dy) > Math.abs(dx)) {
                        vertical = true;
                        vStart(true);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!vertical) return super.onTouchEvent(e);
            if (vt != null) vt.addMovement(e);
            float dy = e.getRawY() - y0;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_MOVE) {
                vMove(dy);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                float vy = 0;
                if (vt != null) {
                    vt.computeCurrentVelocity(1000);
                    vy = vt.getYVelocity();
                }
                vEnd(dy, vy);
                vertical = false;
            }
            return true;
        }
    }

    private LinearLayout buildDock() {
        LinearLayout d = new DockBar(this);
        d.setOrientation(LinearLayout.HORIZONTAL);
        d.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < Prefs.SLOTS; i++) {
            final int slot = i;
            FrameLayout f = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            View badge = new View(this);
            badge.setVisibility(View.GONE);
            f.addView(badge, new FrameLayout.LayoutParams(Ui.dp(this, 10), Ui.dp(this, 10), Gravity.TOP | Gravity.END));
            Ui.pressable(f, 0.84f);
            f.setOnClickListener(v -> onDockTap(slot, v));
            f.setOnLongClickListener(v -> {
                Sound.play(v, Sound.HOLD);
                onDockLongPress(slot);
                return true;
            });
            dockSlots[i] = f;
            dockIcons[i] = iv;
            dockBadges[i] = badge;
            d.addView(f, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        }
        dockSep = new View(this);
        dockSep.setBackgroundColor(0x2EFFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Math.max(1, Ui.dp(this, 1)), Ui.dp(this, 28));
        slp.setMargins(Ui.dp(this, 7), 0, Ui.dp(this, 5), 0);
        d.addView(dockSep, slp);
        drawerButton = new ImageView(this);
        drawerButton.setImageDrawable(new Icons.Line(Icons.GRID, Ui.TEXT, 2.6f));
        int gp = Ui.dp(this, 9);
        drawerButton.setPadding(gp, gp, gp, gp);
        Ui.pressable(drawerButton, 0.84f);
        drawerButton.setOnClickListener(v -> {
            Sound.play(v, Sound.OPEN);
            openDrawer(false);
        });
        d.addView(drawerButton, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return d;
    }

    /** Tamaño, separación, cantidad y fondo del dock según las preferencias. */
    private void layoutDock() {
        int size = Ui.dp(this, Prefs.dockIconSize(this)), sp = Ui.dp(this, Prefs.dockSpacing(this));
        int n = Prefs.dockCount(this);
        for (int i = 0; i < Prefs.SLOTS; i++) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dockSlots[i].getLayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(sp, 0, sp, 0);
            dockSlots[i].setLayoutParams(lp);
            dockSlots[i].setVisibility(i < n ? View.VISIBLE : View.GONE);
        }
        LinearLayout.LayoutParams gl = (LinearLayout.LayoutParams) drawerButton.getLayoutParams();
        gl.width = size;
        gl.height = size;
        drawerButton.setLayoutParams(gl);
        int gp = Math.round(size * 0.2f);
        drawerButton.setPadding(gp, gp, gp, gp);
        boolean glass = Prefs.dockGlass(this);
        if (glass) Glass.apply(dock, backdrop, 30);
        else dock.setBackground(null);
        int pad = Ui.dp(this, 8);
        dock.setPadding(Ui.dp(this, glass ? 10 : 4), pad, Ui.dp(this, glass ? 10 : 4), pad);
        dockSep.setVisibility(glass ? View.VISIBLE : View.INVISIBLE);
        bottomBox.setVisibility(Prefs.showDock(this) && !home.editing ? View.VISIBLE : View.GONE);
    }

    /** Ícono del modo estudio: anillo con lo que falta de la sesión. */
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
        SystemWall.sync(this);
    }

    @Override
    public void applyLook() {
        Ui.setLightBars(this, Theme.wallLight && !drawerOpen && !dashboard.isOpen());
        // Solo se rehace inicio si de verdad cambió el color o lo claro del fondo
        if (Theme.accent != lookAccent || Theme.wallLight != lookLight) {
            lookAccent = Theme.accent;
            lookLight = Theme.wallLight;
            if (Prefs.iconStyle(this) == 2) Drawer.trim();
            refreshDock();
            home.rebuildAll();
            if (SystemColors.enabled(this)) SystemWall.sync(this);
        }
        dashboard.invalidate();
        backdrop.invalidateGlass();
    }

    @Override
    public void applyHome() {
        boolean free = Prefs.homeFree(this);
        Drawer.trim();
        layoutDock();
        refreshDock();
        updateHomeFrame();
        if (free != home.model.free) {
            home.model.free = free;
            home.model.normalize();
            home.saveModel();
            home.populate();
        } else {
            home.rebuildAll();
        }
        home.refreshTransitions();
        refreshStudy();
        onMediaChanged();
    }

    // ==================== Ciclo de vida ====================

    @Override
    protected void onStart() {
        super.onStart();
        SystemWall.setVisible(this, true);
        backdrop.setAnimated(Prefs.animated(this));
        backdrop.setParallax(Prefs.parallax(this) && Prefs.motion(this) < 2);
        backdrop.setSmooth(Prefs.smooth(this));
        backdrop.setRunning(true);
        Ui.setLightBars(this, Theme.wallLight && !drawerOpen && !dashboard.isOpen());
        AppWidgets.start(this);
        home.start();
        refreshDock();
        Media.addListener(this, this);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        handler.removeCallbacks(stable);
        handler.postDelayed(stable, 8000);
        Weather.refresh(this, false, n -> {
            home.tick();
            dashboard.refreshWeather();
        });
        if (!drawerOpen && !dashboard.isOpen() && !home.editing) playEntrance();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SystemWall.setVisible(this, false);
        backdrop.setRunning(false);
        AppWidgets.stop(this);
        home.stop();
        Media.removeListener(this);
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(stable);
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
        if (home.editing) {
            home.setEditing(false);
            return;
        }
        if (home.page != 0) {
            home.goTo(0, true);
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
        else if (home.editing) home.setEditing(false);
        else if (home.page != 0) home.goTo(0, true);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        // El fondo reacciona al dedo (la constelación se aparta) solo con inicio a la vista
        if (!drawerOpen && !dashboard.isOpen() && overlay.getVisibility() != View.VISIBLE && !home.editing) {
            int a = e.getActionMasked();
            backdrop.touch(e.getX(), e.getY(), a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE);
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_BIND) {
            if (res == RESULT_OK && pendingWidget >= 0) configureWidget(pendingWidget);
            else cancelPendingWidget();
            return;
        }
        if (req == REQ_CONFIG) {
            if (res == RESULT_OK && pendingWidget >= 0) placeWidget(pendingWidget);
            else cancelPendingWidget();
            return;
        }
        if (res != RESULT_OK || data == null) return;
        if (req >= REQ_PICK && req < REQ_PICK + Prefs.SLOTS) {
            Prefs.setSlot(this, req - REQ_PICK, data.getStringExtra(AppsActivity.EXTRA_PKG));
            refreshDock();
        } else if (req == REQ_PHOTO && data.getData() != null) {
            importPhoto(data.getData(), null);
        } else if (req == REQ_ADD_APP) {
            addAppToHome(data.getStringExtra(AppsActivity.EXTRA_PKG));
        } else if (req == REQ_FOLDER_ADD && pendingItem != null) {
            String pkg = data.getStringExtra(AppsActivity.EXTRA_PKG);
            HomeModel.Item f = pendingItem;
            pendingItem = null;
            if (pkg != null && home.model.items.contains(f)) {
                JSONArray arr = f.apps();
                boolean has = false;
                for (int i = 0; i < arr.length(); i++) if (pkg.equals(arr.optString(i))) has = true;
                if (!has) arr.put(pkg);
                home.replaceView(f);
                home.saveModel();
                Sheet.pill(this, backdrop, IconEngine.label(this, pkg) + " quedó en " + f.str("name", "la carpeta"));
            }
        } else if (req == REQ_GADGET_PHOTO && data.getData() != null && pendingItem != null) {
            HomeModel.Item it = pendingItem;
            pendingItem = null;
            Sheet.pill(this, backdrop, "Preparando tu foto…");
            Gadgets.PhotoG.importPhoto(this, data.getData(), Gadgets.PhotoG.file(this, it.id), () -> {
                if (home.model.items.contains(it)) home.replaceView(it);
            });
        } else if (req == REQ_ICON && data.getData() != null && pendingPkg != null) {
            String pkg = pendingPkg;
            pendingPkg = null;
            Uri uri = data.getData();
            Context app = getApplicationContext();
            Gadgets.BG.execute(() -> {
                boolean ok = IconEngine.importCustom(app, uri, pkg);
                Gadgets.MAIN.post(() -> {
                    if (ok) refreshIcons(pkg);
                    else Sheet.pill(this, backdrop, "No pude usar esa imagen");
                });
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_CAL) {
            home.forEachGadget(g -> {
                if (g instanceof Gadgets.AgendaG) g.rebuild();
            });
        }
    }

    private void playEntrance() {
        if (Prefs.motion(this) == 3) return;
        home.playEntrance();
        if (bottomBox.getVisibility() != View.VISIBLE) return;
        float d = getResources().getDisplayMetrics().density;
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

    // ==================== Inicio (lo que pide HomeView) ====================

    /** Crea la vista de un elemento de inicio. */
    View createItemView(HomeModel.Item it) {
        switch (it.type) {
            case "app":
                return it.str("pkg", "").isEmpty() ? null : new Gadgets.AppIcon(this, it);
            case "folder":
                return new Gadgets.FolderIcon(this, it);
            case "aw":
                return new AppWidgets.Frame(this, it);
            default:
                return Gadgets.create(this, it);
        }
    }

    void onEditMode(boolean on) {
        if (on) {
            bottomBox.animate().alpha(0f).translationY(Ui.dp(this, 30)).setDuration(200)
                    .withEndAction(() -> bottomBox.setVisibility(View.GONE)).start();
        } else if (Prefs.showDock(this)) {
            bottomBox.setVisibility(View.VISIBLE);
            bottomBox.animate().alpha(1f).translationY(0).setDuration(280).withEndAction(null).start();
        }
        Ui.setLightBars(this, !on && Theme.wallLight);
    }

    void onHomeDoubleTap() {
        doubleTap();
    }

    void onHomeLongPress() {
        Sound.play(home, Sound.HOLD);
        switch (Prefs.gLong(this)) {
            case 1:
                if (Prefs.homeLocked(this)) Sheet.pill(this, backdrop, "Tu diseño está bloqueado. Lo puedes desbloquear en Personalizar > Inicio");
                else home.setEditing(true);
                break;
            case 2:
                Studio.show(this, this, -1);
                break;
            case 3:
                break;
            default:
                HomeMenus.homeMenu(this);
                break;
        }
    }

    void itemMenu(HomeModel.Item it) {
        HomeMenus.item(this, it);
    }

    void openWidgetPicker() {
        HomeMenus.widgets(this);
    }

    void pickAppForHome() {
        Intent i = new Intent(this, AppsActivity.class);
        i.putExtra(AppsActivity.EXTRA_PICK, true);
        startActivityForResult(i, REQ_ADD_APP);
        overridePendingTransition(Screen.anim(this, "screen_enter"), Screen.anim(this, "stay_out"));
    }

    void pickAppForFolder(HomeModel.Item folder) {
        pendingItem = folder;
        Intent i = new Intent(this, AppsActivity.class);
        i.putExtra(AppsActivity.EXTRA_PICK, true);
        startActivityForResult(i, REQ_FOLDER_ADD);
        overridePendingTransition(Screen.anim(this, "screen_enter"), Screen.anim(this, "stay_out"));
    }

    void addAppToHome(String pkg) {
        if (pkg == null) return;
        HomeModel.Item it = home.place("app", 1, 1, x -> x.put("pkg", pkg));
        if (it == null) Sheet.pill(this, backdrop, "Ya no hay espacio en inicio");
        else Sheet.pill(this, backdrop, IconEngine.label(this, pkg) + " quedó en inicio");
    }

    void studioFromEdit() {
        Studio.show(this, this, Studio.TAB_HOME);
    }

    /** Lo mínimo que puede medir un elemento al cambiarle el tamaño. */
    float[] minSpan(HomeModel.Item it) {
        float[] m;
        if (it.type.equals("aw")) {
            m = AppWidgets.minSpan(this, it.num("id", -1), home.cellW, home.cellH);
        } else {
            Gadgets.Spec s = Gadgets.spec(it.type);
            m = s != null ? new float[]{s.minW, s.minH} : new float[]{1, 1};
        }
        if (!home.model.free) {
            m[0] = Math.max(1, (float) Math.ceil(m[0]));
            m[1] = Math.max(1, (float) Math.ceil(m[1]));
        }
        return m;
    }

    /** Libera los widgets de otras apps que ya no están en inicio (se hace al salir de editar, así se pueden deshacer). */
    void purgeWidgets(HomeModel m) {
        Set<Integer> used = new HashSet<>();
        for (HomeModel.Item it : m.items) if (it.type.equals("aw")) used.add(it.num("id", -1));
        if (pendingWidget >= 0) used.add(pendingWidget);
        try {
            for (int id : AppWidgets.host(this).getAppWidgetIds()) if (!used.contains(id)) AppWidgets.delete(this, id);
        } catch (Exception ignored) {
            // nada
        }
    }

    // ---------- Arrastre vertical ----------

    void vStart(boolean up) {
        if (up) {
            if (Prefs.gUp(this) == 2) {
                vdrag = V_DONE;
            } else {
                vdrag = V_DRAWER;
                drawer.load(false);
                drawer.setProgress(0f);
            }
        } else {
            int g = Prefs.gDown(this);
            if (g == 0) {
                vdrag = V_DASH;
                dashboard.setProgress(0f);
            } else {
                vdrag = V_DONE;
                if (g == 1) expandNotifications();
            }
        }
    }

    void vMove(float dy) {
        float h = Math.max(1, root.getHeight());
        if (vdrag == V_DRAWER) {
            float f = Math.max(0f, Math.min(1f, -dy / (h * 0.45f)));
            drawer.setProgress(f);
            content.setAlpha(1f - f * 0.8f);
        } else if (vdrag == V_DASH) {
            float f = Math.max(0f, Math.min(1f, dy / (h * 0.4f)));
            dashboard.setProgress(f);
            content.setAlpha(1f - f * 0.8f);
        }
    }

    void vEnd(float dy, float vy) {
        float h = Math.max(1, root.getHeight());
        if (vdrag == V_DRAWER) {
            float f = Math.max(0f, Math.min(1f, -dy / (h * 0.45f)));
            if (f > 0.28f || vy < -900) openDrawer(Prefs.gUp(this) == 1);
            else closeDrawer();
        } else if (vdrag == V_DASH) {
            float f = Math.max(0f, Math.min(1f, dy / (h * 0.4f)));
            if (f > 0.25f || vy > 900) openDashboard();
            else closeDashboard();
        }
        vdrag = V_NONE;
    }

    private void doubleTap() {
        switch (Prefs.gDouble(this)) {
            case 0:
                if (!LockService.lock()) requestLock();
                break;
            case 1:
                toggle(0);
                break;
            case 2:
                nextWallpaper();
                break;
            default:
                break;
        }
    }

    private void nextWallpaper() {
        cycleWallpaper(this);
        Sound.play(content, Sound.THEME);
        applyWallpaper();
    }

    /** Pasa al siguiente fondo (tus fotos y luego las escenas). También lo usa el botón de ajustes rápidos. */
    static void cycleWallpaper(Context c) {
        List<String> photos = Wallpaper.photos(c);
        int total = photos.size() + Scenes.COUNT;
        int cur;
        if (Prefs.wallKind(c) == 1 && photos.contains(Prefs.wallPhoto(c))) cur = photos.indexOf(Prefs.wallPhoto(c));
        else cur = photos.size() + Prefs.wallScene(c);
        int next = (cur + 1) % total;
        if (next < photos.size()) {
            Prefs.setWallKind(c, 1);
            Prefs.setWallPhoto(c, photos.get(next));
        } else {
            Prefs.setWallKind(c, 0);
            Prefs.setWallScene(c, next - photos.size());
        }
    }

    /** La pantalla de inicio que está abierta (para que los botones de Android la actualicen). */
    static java.lang.ref.WeakReference<MainActivity> current = new java.lang.ref.WeakReference<>(null);

    // ==================== Lo que piden los widgets ====================

    void launchApp(String pkg, View from) {
        if (home.editing) return;
        if (getPackageManager().getLaunchIntentForPackage(pkg) == null) {
            Sound.play(from, Sound.ERROR);
            Sheet.pill(this, backdrop, "Esa app ya no está instalada");
            return;
        }
        if (Prefs.studying(this) && Prefs.DISTRACTIONS.contains(pkg)) {
            Sound.play(from, Sound.ERROR);
            Sheet.confirm(this, backdrop, "Estás en modo estudio",
                    "Esta app está en pausa hasta que termine tu sesión. ¿La abres de todos modos?",
                    "Abrir", false, () -> {
                        Prefs.countLaunch(this, pkg);
                        launch(this, pkg, from);
                    });
            return;
        }
        Sound.play(from, Sound.OPEN);
        Prefs.countLaunch(this, pkg);
        launch(this, pkg, from);
    }

    void openFolder(HomeModel.Item it, View from) {
        if (home.editing) return;
        HomeMenus.folder(this, it, from);
    }

    void weatherTap() {
        if (Prefs.city(this).isEmpty()) {
            askCity();
            return;
        }
        Weather.Now w = Weather.cached(this);
        String title = w != null ? w.temp + "°  " + Weather.describe(w.code) + " en " + w.place : "Clima";
        Sheet.show(this, backdrop, title,
                Sheet.item("Actualizar ahora", Icons.HISTORY, () -> {
                    Sheet.pill(this, backdrop, "Actualizando el clima…");
                    Weather.refresh(this, true, n -> {
                        home.tick();
                        dashboard.refreshWeather();
                    });
                }),
                Sheet.item("Cambiar ciudad", Gadgets.GLOBE, this::askCity),
                Sheet.item("Ver el panel", Widgets.BELL, () -> {
                    dashboard.setProgress(0f);
                    openDashboard();
                }));
    }

    void openMusicApp() {
        if (Media.active()) {
            Media.open(this);
            return;
        }
        for (String p : new String[]{"com.spotify.music", "com.google.android.apps.youtube.music", "deezer.android.app", "com.apple.android.music", "com.amazon.mp3"}) {
            if (getPackageManager().getLaunchIntentForPackage(p) != null) {
                launch(this, p, null);
                return;
            }
        }
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Sheet.pill(this, backdrop, "No encontré una app de música");
        }
    }

    void requestCalendar() {
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL);
    }

    void askJarvis() {
        openJarvis();
    }

    /** Acciones de los atajos (también las usa el panel y la revisión automática). */
    void shortcut(String a) {
        switch (a) {
            case "calc":
                Screen.open(this, new Intent(this, CalcActivity.class));
                break;
            case "obra":
                Screen.open(this, new Intent(this, CalcActivity.class).putExtra("tab", 1));
                break;
            case "tasks":
                Screen.open(this, new Intent(this, PendientesActivity.class));
                break;
            case "panel":
                dashboard.setProgress(0f);
                openDashboard();
                break;
            case "buscar":
                openDrawer(true);
                break;
            case "linterna":
                toggle(0);
                break;
            case "fondo":
                nextWallpaper();
                break;
            case "personalizar":
                Studio.show(this, this, -1);
                break;
            case "editar":
                if (Prefs.homeLocked(this)) Sheet.pill(this, backdrop, "Tu diseño está bloqueado. Lo puedes desbloquear en Personalizar > Inicio");
                else home.setEditing(true);
                break;
            case "alarma":
                startSafe(new Intent(AlarmClock.ACTION_SHOW_ALARMS), "No encontré la app de alarmas");
                break;
            case "ajustes":
                startSafe(new Intent(Settings.ACTION_SETTINGS), null);
                break;
            default:
                break;
        }
    }

    private void startSafe(Intent i, String fail) {
        Controls.start(this, backdrop, i, fail);
    }

    /** 0 linterna, 1 internet, 2 volumen, 3 no molestar. Siempre dice lo que de verdad pasó. */
    void toggle(int k) {
        Controls.toggle(this, backdrop, k);
    }

    void voiceSearch() {
        try {
            startActivity(new Intent(RecognizerIntent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                Sheet.pill(this, backdrop, "No encontré búsqueda por voz en tu teléfono");
            }
        }
    }

    private void changed(HomeModel.Item it) {
        if (!home.model.items.contains(it)) return;
        home.refreshItem(it);
        home.saveModel();
    }

    void editCountdown(HomeModel.Item it) {
        Calendar cal = Calendar.getInstance();
        try {
            LocalDate d = LocalDate.parse(it.str("date", ""));
            cal.set(d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
        } catch (Exception ignored) {
            cal.add(Calendar.DAY_OF_MONTH, 7);
        }
        DatePickerDialog dlg = new DatePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert, (view, y, m, d) -> {
            String date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            Sheet.prompt(this, backdrop, "¿Qué pasa ese día?", "Ejemplo: Mi cumpleaños", it.str("title", ""), v -> {
                it.put("date", date);
                it.put("title", v.isEmpty() ? "Mi fecha" : v);
                changed(it);
            });
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    void editNote(HomeModel.Item it) {
        Sheet.prompt(this, backdrop, "Tu nota", "Escribe lo que quieras tener a la vista", it.str("text", ""), true, v -> {
            it.put("text", v);
            changed(it);
        });
    }

    void editText(HomeModel.Item it) {
        Sheet.prompt(this, backdrop, "Texto", "Ejemplo: Hoy sí", it.str("text", ""), true, v -> {
            it.put("text", v.isEmpty() ? "Jostin OS" : v);
            changed(it);
        });
    }

    void editName(HomeModel.Item it) {
        Sheet.prompt(this, backdrop, "¿Cómo quieres que te salude?", "Tu nombre", it.str("name", Prefs.userName(this)), v -> {
            if (v.isEmpty()) return;
            it.put("name", v);
            Prefs.setUserName(this, v);
            changed(it);
        });
    }

    void worldCity(HomeModel.Item it) {
        List<Sheet.Item> list = new ArrayList<>();
        String cur = it.str("tz", "America/New_York");
        for (String[] c : Gadgets.CITIES) {
            Sheet.Item x = Sheet.item(c[0], Gadgets.GLOBE, () -> {
                it.put("city", c[0]);
                it.put("tz", c[1]);
                changed(it);
            });
            x.value = () -> c[1].equals(cur) ? "Elegida" : Gadgets.WorldG.diff(c[1]);
            list.add(x);
        }
        Sheet.show(this, backdrop, "¿Qué ciudad?", list);
    }

    void pickGadgetPhoto(HomeModel.Item it) {
        pendingItem = it;
        pickImage(REQ_GADGET_PHOTO);
    }

    void pickIcon(String pkg) {
        pendingPkg = pkg;
        pickImage(REQ_ICON);
    }

    void chooseShortcut(HomeModel.Item it) {
        List<Sheet.Item> list = new ArrayList<>();
        String cur = it.str("action", "calc");
        for (String[] a : Gadgets.ACTIONS) {
            Sheet.Item x = Sheet.item(a[1], Gadgets.actionIcon(a[0]), () -> {
                it.put("action", a[0]);
                changed(it);
            });
            x.value = () -> a[0].equals(cur) ? "Elegido" : "";
            list.add(x);
        }
        Sheet.show(this, backdrop, "¿Qué hace este atajo?", list);
    }

    void chooseFrom(HomeModel.Item it, String key, String[] opts) {
        List<Sheet.Item> list = new ArrayList<>();
        int cur = it.num(key, 0);
        for (int i = 0; i < opts.length; i++) {
            final int k = i;
            Sheet.Item x = Sheet.item(opts[i], null, () -> {
                it.put(key, k);
                changed(it);
            });
            x.value = () -> k == cur ? "Elegido" : "";
            list.add(x);
        }
        Sheet.show(this, backdrop, null, list);
    }

    // ==================== Widgets de otras apps ====================

    /** Paso 1: pedir permiso para usar el widget (Android lo exige la primera vez). */
    void addSystemWidget(AppWidgetProviderInfo info) {
        int id;
        try {
            id = AppWidgets.host(this).allocateAppWidgetId();
        } catch (Exception e) {
            Sheet.pill(this, backdrop, "No se pudo agregar ese widget");
            return;
        }
        pendingWidget = id;
        boolean ok;
        try {
            ok = AppWidgets.mgr(this).bindAppWidgetIdIfAllowed(id, info.getProfile(), info.provider, null);
        } catch (Exception e) {
            ok = false;
        }
        if (ok) {
            configureWidget(id);
            return;
        }
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
        try {
            startActivityForResult(i, REQ_BIND);
        } catch (Exception e) {
            cancelPendingWidget();
            Sheet.pill(this, backdrop, "Android no dejó agregar ese widget");
        }
    }

    /** Paso 2: si el widget tiene pantalla de configuración, se abre. */
    private void configureWidget(int id) {
        AppWidgetProviderInfo info = AppWidgets.mgr(this).getAppWidgetInfo(id);
        if (info == null) {
            cancelPendingWidget();
            return;
        }
        boolean optional = Build.VERSION.SDK_INT >= 31
                && (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0;
        if (info.configure != null && !optional) {
            try {
                AppWidgets.host(this).startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null);
                return;
            } catch (Exception e) {
                // si su configuración no se puede abrir, se pone tal cual
            }
        }
        placeWidget(id);
    }

    /** Paso 3: ponerlo en inicio. */
    private void placeWidget(int id) {
        pendingWidget = -1;
        AppWidgetProviderInfo info = AppWidgets.mgr(this).getAppWidgetInfo(id);
        if (info == null) {
            AppWidgets.delete(this, id);
            Sheet.pill(this, backdrop, "No se pudo agregar ese widget");
            return;
        }
        int[] span = AppWidgets.span(info, home.cellW, home.cellH, home.cols, home.rows);
        if (!home.editing) home.setEditing(true);
        HomeModel.Item it = home.place("aw", span[0], span[1], x -> x.put("id", id));
        if (it == null) {
            AppWidgets.delete(this, id);
            Sheet.pill(this, backdrop, "No hay espacio para ese widget. Quita algo o agrega una página.");
        }
    }

    private void cancelPendingWidget() {
        if (pendingWidget >= 0) AppWidgets.delete(this, pendingWidget);
        pendingWidget = -1;
    }

    void reconfigureWidget(HomeModel.Item it) {
        int id = it.num("id", -1);
        if (Build.VERSION.SDK_INT < 31 || id < 0) return;
        try {
            AppWidgets.host(this).startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG + 1000, null);
        } catch (Exception e) {
            Sheet.pill(this, backdrop, "Este widget no se puede configurar de nuevo");
        }
    }

    /** Agrega un widget de Jostin OS y, si hace falta, pregunta lo necesario para que no quede vacío. */
    void addGadget(Gadgets.Spec s) {
        if (!home.editing) home.setEditing(true);
        HomeModel.Item it = home.place(s.type, s.w, s.h, null);
        if (it == null) {
            Sheet.pill(this, backdrop, "Ya no hay espacio. Quita algo o agrega una página.");
            return;
        }
        Runnable next = null;
        switch (s.type) {
            case "atajo": next = () -> chooseShortcut(it); break;
            case "cuenta": next = () -> editCountdown(it); break;
            case "nota": next = () -> editNote(it); break;
            case "texto": next = () -> editText(it); break;
            case "foto": next = () -> pickGadgetPhoto(it); break;
            case "hora_mundial": next = () -> worldCity(it); break;
            case "agenda":
                if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) next = this::requestCalendar;
                break;
            default: break;
        }
        if (next != null) handler.postDelayed(next, 450);
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
        if (app.work()) {
            // Inicio, dock y ocultas guardan solo el paquete: con una de trabajo se confundirían con la tuya
            Sheet.show(this, backdrop, app.label + " · trabajo",
                    Sheet.item("Información de la app", Icons.INFO, () -> WorkApps.info(this, app)),
                    Sheet.item("Pausar apps de trabajo", Icons.WORK, () -> {
                        if (WorkApps.setPaused(this, true)) Sheet.pill(this, backdrop, "Apps de trabajo en pausa");
                    }),
                    Sheet.item("Desinstalar", Icons.TRASH, () -> uninstall(app.pkg, app.user)).danger());
            return;
        }
        Sheet.show(this, backdrop, app.label,
                Sheet.item("Agregar a inicio", Icons.PLUS, () -> {
                    closeDrawer();
                    handler.postDelayed(() -> addAppToHome(app.pkg), 300);
                }),
                Sheet.item("Poner en el dock", Icons.HOME, () -> chooseSlot(app.pkg, app.label)),
                Sheet.item("Ocultar del cajón", Icons.CLOSE, () -> {
                    Set<String> h = Prefs.hidden(this);
                    h.add(app.pkg);
                    Prefs.setHidden(this, h);
                    drawer.load(false);
                    Sheet.pill(this, backdrop, app.label + " quedó oculta");
                }),
                Sheet.item("Información de la app", Icons.INFO, () -> appInfo(this, app.pkg)),
                Sheet.item("Desinstalar", Icons.TRASH, () -> uninstall(app.pkg)).danger());
    }

    void uninstall(String pkg) {
        uninstall(pkg, null);
    }

    /** La ventana de Android para desinstalar; con perfil, la quita solo del perfil de trabajo. */
    void uninstall(String pkg, UserHandle user) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
        if (user != null) i.putExtra(Intent.EXTRA_USER, user);
        try {
            startActivity(i);
        } catch (Exception e) {
            appInfo(this, pkg);
        }
    }

    void chooseSlot(String pkg, String label) {
        List<Sheet.Item> items = new ArrayList<>();
        for (int k = 0; k < Prefs.dockCount(this); k++) {
            final int slot = k;
            String cur = Prefs.slot(this, k);
            String name = cur == null ? "Vacío" : IconEngine.label(this, cur);
            Sheet.Item it = Sheet.item("Lugar " + (k + 1), Icons.SWAP, () -> {
                Prefs.setSlot(this, slot, pkg);
                refreshDock();
                Sheet.pill(this, backdrop, label + " quedó en el dock");
            });
            it.value = () -> name;
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
        for (String pkg : hidden) {
            final String n = IconEngine.label(this, pkg);
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
            home.tick();
            dashboard.refreshWeather();
            if (!v.isEmpty()) {
                Sheet.pill(this, backdrop, "Buscando el clima de " + v);
                Weather.refresh(this, true, n -> {
                    home.tick();
                    dashboard.refreshWeather();
                });
            }
        });
    }

    @Override
    public void askGreeting() {
        Sheet.prompt(this, backdrop, "Frase bajo el reloj", "Ejemplo: Hoy se construye algo grande", Prefs.greeting(this), v -> {
            Prefs.setGreeting(this, v);
            home.tick();
        });
    }

    @Override
    public void quick(int action) {
        switch (action) {
            case Dashboard.Q_TORCH:
                toggle(0);
                break;
            case Dashboard.Q_STUDY:
                closeDashboard();
                handler.postDelayed(this::toggleStudy, 260);
                break;
            case Dashboard.Q_WALL:
                nextWallpaper();
                break;
            case Dashboard.Q_NET:
                toggle(1);
                break;
            case Dashboard.Q_BT:
                startSafe(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), null);
                break;
            case Dashboard.Q_DND:
                toggle(3);
                break;
            case Dashboard.Q_VOL:
                toggle(2);
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
                "Abrir ajustes", false, () -> startSafe(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), null));
    }

    // ==================== Estudio (Studio.Host) ====================

    @Override
    public void editHome() {
        if (Prefs.homeLocked(this)) {
            Sheet.pill(this, backdrop, "Primero quita «Bloquear diseño» en Personalizar > Inicio");
            return;
        }
        home.setEditing(true);
    }

    @Override
    public void openWidgets() {
        HomeMenus.widgets(this);
    }

    @Override
    public void restoreHome() {
        List<String> tags = Safety.tags(this);
        if (tags.isEmpty()) {
            Sheet.pill(this, backdrop, "Todavía no hay copias. Se hace una cada día que cambias algo.");
            return;
        }
        List<Sheet.Item> list = new ArrayList<>();
        for (String t : tags) {
            list.add(Sheet.item(Safety.label(t), Icons.HISTORY, () -> Sheet.confirm(this, backdrop, "¿Volver a esta copia?",
                    "Tu diseño de ahora se guarda como copia por si te arrepientes.", "Restaurar", false, () -> {
                        String json = Safety.backup(this, t);
                        if (json == null) return;
                        Safety.backupNow(this, "antes-" + new SimpleDateFormat("HHmmss", Locale.US).format(new Date()));
                        home.setEditing(false);
                        home.restore(json);
                        purgeWidgets(home.model);
                        Sheet.pill(this, backdrop, "Listo, regresó tu diseño");
                    })));
        }
        Sheet.show(this, backdrop, "Copias de tu inicio", list);
    }

    @Override
    public void resetHome() {
        Sheet.confirm(this, backdrop, "¿Empezar de cero?", "Inicio vuelve al diseño de fábrica. Tu diseño de ahora se guarda como copia.", "Empezar de cero", true, () -> {
            Safety.backupNow(this, "antes-" + new SimpleDateFormat("HHmmss", Locale.US).format(new Date()));
            home.setEditing(false);
            home.model.buildDefault(this);
            home.model.free = Prefs.homeFree(this);
            home.model.normalize();
            home.saveModel();
            home.populate();
            purgeWidgets(home.model);
        });
    }

    @Override
    public void previewTransition() {
        if (home.model.pages < 2) {
            Sheet.pill(this, backdrop, "Agrega otra página para ver la transición");
            return;
        }
        int back = home.page;
        home.goTo(back == 0 ? 1 : back - 1, true);
        handler.postDelayed(() -> home.goTo(back, true), 900);
    }

    @Override
    public void chooseIconPack() {
        List<String[]> packs = IconEngine.Pack.installed(this);
        List<Sheet.Item> list = new ArrayList<>();
        String cur = Prefs.iconPack(this);
        Sheet.Item none = Sheet.item("Ninguno", Icons.CLOSE, () -> applyIconPack(null));
        none.value = () -> cur == null ? "Elegido" : "";
        list.add(none);
        PackageManager pm = getPackageManager();
        for (String[] p : packs) {
            Sheet.Item x = Sheet.item(p[1], null, () -> applyIconPack(p[0]));
            try {
                x.drawable(pm.getApplicationIcon(p[0]));
            } catch (Exception ignored) {
                // sin ícono
            }
            x.value = () -> p[0].equals(cur) ? "Elegido" : "";
            list.add(x);
        }
        if (packs.isEmpty()) {
            list.add(Sheet.item("Buscar paquetes en Play Store", Icons.SEARCH, () -> startSafe(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://search?q=icon%20pack%20nova&c=apps")), "No encontré Play Store")));
        }
        Sheet.show(this, backdrop, "Paquete de íconos", list);
    }

    private void applyIconPack(String pkg) {
        Prefs.setIconPack(this, pkg);
        IconEngine.Pack.reset();
        if (pkg == null) {
            applyHome();
            return;
        }
        Sheet.pill(this, backdrop, "Cargando íconos…");
        Context app = getApplicationContext();
        // El paquete se lee fuera del hilo principal para que no se trabe
        Gadgets.BG.execute(() -> {
            IconEngine.Pack.get(app, "android");
            Gadgets.MAIN.post(this::applyHome);
        });
    }

    // ==================== Fotos ====================

    @Override
    public void pickPhoto() {
        pickImage(REQ_PHOTO);
    }

    private void pickImage(int req) {
        Intent i;
        if (Build.VERSION.SDK_INT >= 33) {
            i = new Intent(MediaStore.ACTION_PICK_IMAGES);
            i.setType("image/*");
        } else {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
        }
        try {
            startActivityForResult(i, req);
        } catch (ActivityNotFoundException e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.setType("image/*");
            try {
                startActivityForResult(g, req);
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
            handler.postDelayed(() -> Studio.show(this, this, Studio.TAB_WALL), 250);
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
        int n = Prefs.dockCount(this);
        Set<String> inDock = new HashSet<>();
        for (int i = 0; i < n; i++) {
            String p = Prefs.slot(this, i);
            if (p != null) inDock.add(p);
        }
        List<String> sugg = Prefs.dockSuggest(this) ? IconEngine.suggestions(this, n, inDock) : Collections.emptyList();
        int s = 0;
        for (int i = 0; i < Prefs.SLOTS; i++) {
            ImageView iv = dockIcons[i];
            String pkg = Prefs.slot(this, i);
            boolean real = pkg != null && getPackageManager().getLaunchIntentForPackage(pkg) != null;
            boolean suggested = false;
            if (!real && i < n && s < sugg.size()) {
                pkg = sugg.get(s++);
                real = true;
                suggested = true;
            }
            dockShown[i] = real ? pkg : null;
            if (real) {
                IconEngine.bind(iv, pkg);
                iv.setPadding(0, 0, 0, 0);
                iv.setBackground(null);
                iv.setAlpha(studying && Prefs.DISTRACTIONS.contains(pkg) ? 0.25f : suggested ? 0.8f : 1f);
                dockSlots[i].setForeground(suggested ? Ui.outline(this, 0, 0x55FFFFFF, 18) : null);
            } else {
                Icons.Line plus = Icons.of(Icons.PLUS);
                plus.setColor(0xB3FFFFFF);
                iv.setImageDrawable(plus);
                iv.setClipToOutline(false);
                int p = Ui.dp(this, 11);
                iv.setPadding(p, p, p, p);
                iv.setBackground(Ui.outline(this, 0x0FFFFFFF, 0x33FFFFFF, 22));
                iv.setAlpha(1f);
                dockSlots[i].setForeground(null);
            }
        }
        refreshDockBadges();
    }

    private void refreshDockBadges() {
        for (int i = 0; i < Prefs.SLOTS; i++) {
            String pkg = dockShown[i];
            dockBadges[i].setBackground(Ui.outline(this, Theme.accent, 0xFF0E0F12, 6));
            dockBadges[i].setVisibility(pkg != null && badges.contains(pkg) ? View.VISIBLE : View.GONE);
        }
    }

    private void onDockTap(int slot, View v) {
        String pkg = dockShown[slot];
        if (pkg == null) {
            Sound.play(v, Sound.TAP);
            pickApp(slot);
            return;
        }
        launchApp(pkg, v);
    }

    private void onDockLongPress(int slot) {
        String pkg = Prefs.slot(this, slot);
        String shown = dockShown[slot];
        if (pkg == null && shown != null) {
            Sheet.show(this, backdrop, "Sugerencia: " + IconEngine.label(this, shown),
                    Sheet.item("Dejarla fija aquí", Icons.HOME, () -> {
                        Prefs.setSlot(this, slot, shown);
                        refreshDock();
                    }),
                    Sheet.item("Elegir otra app", Icons.SWAP, () -> pickApp(slot)),
                    Sheet.item("Apagar sugerencias", Icons.CLOSE, () -> {
                        Prefs.setDockSuggest(this, false);
                        refreshDock();
                    }));
            return;
        }
        if (pkg == null) {
            pickApp(slot);
            return;
        }
        List<Sheet.Item> list = new ArrayList<>(HomeMenus.shortcutItems(this, pkg));
        list.add(Sheet.item("Cambiar app", Icons.SWAP, () -> pickApp(slot)));
        list.add(Sheet.item("Cambiar ícono o nombre", Icons.IMAGE, () -> HomeMenus.iconMenu(this, pkg)));
        list.add(Sheet.item("Información de la app", Icons.INFO, () -> appInfo(this, pkg)));
        list.add(Sheet.item("Quitar del dock", Icons.CLOSE, () -> {
            Prefs.setSlot(this, slot, null);
            refreshDock();
        }).danger());
        Sheet.show(this, backdrop, IconEngine.label(this, pkg), list);
    }

    private void pickApp(int slot) {
        Intent i = new Intent(this, AppsActivity.class);
        i.putExtra(AppsActivity.EXTRA_PICK, true);
        startActivityForResult(i, REQ_PICK + slot);
        overridePendingTransition(Screen.anim(this, "screen_enter"), Screen.anim(this, "stay_out"));
    }

    /** Vuelve a dibujar el ícono y el nombre de una app en inicio, carpetas, dock y cajón. */
    void refreshIcons(String pkg) {
        Drawer.trim();
        for (HomeModel.Item it : new ArrayList<>(home.model.items)) {
            boolean match = it.type.equals("app") && pkg.equals(it.str("pkg", ""));
            if (it.type.equals("folder")) {
                JSONArray a = it.apps();
                for (int i = 0; i < a.length(); i++) if (pkg.equals(a.optString(i))) match = true;
            }
            if (match) home.replaceView(it);
        }
        refreshDock();
    }

    /** Una app se desinstaló: se quita de inicio, de las carpetas y del dock. */
    private void removeApp(String pkg) {
        boolean changed = false;
        for (HomeModel.Item it : new ArrayList<>(home.model.items)) {
            if (it.type.equals("app") && pkg.equals(it.str("pkg", ""))) {
                home.model.items.remove(it);
                changed = true;
            } else if (it.type.equals("folder")) {
                JSONArray a = it.apps(), keep = new JSONArray();
                for (int i = 0; i < a.length(); i++) if (!pkg.equals(a.optString(i))) keep.put(a.optString(i));
                if (keep.length() != a.length()) {
                    changed = true;
                    if (keep.length() == 0) {
                        home.model.items.remove(it);
                    } else if (keep.length() == 1) {
                        it.type = "app";
                        String only = keep.optString(0);
                        it.data = new org.json.JSONObject();
                        it.put("pkg", only);
                    } else {
                        it.put("apps", keep);
                    }
                }
            }
        }
        for (int i = 0; i < Prefs.SLOTS; i++) if (pkg.equals(Prefs.slot(this, i))) Prefs.setSlot(this, i, null);
        if (changed) {
            home.saveModel();
            home.populate();
        }
    }

    // ==================== Estudio y Jarvis ====================

    void toggleStudy() {
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
        String chip = null;
        if (on) {
            long left = Prefs.studyEnd(this) - System.currentTimeMillis();
            int min = (int) Math.ceil(left / 60_000.0);
            chip = "MODO ESTUDIO  ·  " + min + " MIN";
        } else if (wasStudying) {
            Sheet.pill(this, backdrop, "Terminó tu sesión de estudio. Buen trabajo.");
        }
        final String c = chip;
        home.forEachGadget(g -> {
            if (g instanceof Gadgets.ClockG && ((Gadgets.ClockG) g).cv != null) ((Gadgets.ClockG) g).cv.setStudy(c);
            else if (g instanceof Gadgets.StudyG) g.tick();
        });
        if (on != wasStudying) refreshDock();
        wasStudying = on;
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
            startSafe(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + CLAUDE_PKG)), null);
        }
    }

    // ==================== Notificaciones (Media.Listener) ====================

    @Override
    public void onMediaChanged() {
        badges = Prefs.badges(this) ? Media.badgePackages(this) : new HashSet<>();
        home.onMedia(badges);
        refreshDockBadges();
    }

    // ==================== Ventana flotante (calendario, carpetas) ====================

    void showOverlay(View panel, FrameLayout.LayoutParams lp) {
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

    void closeOverlay() {
        overlay.animate().alpha(0f).setStartDelay(0).setDuration(200).withEndAction(() -> {
            overlay.setVisibility(View.GONE);
            overlay.removeAllViews();
        }).start();
    }

    int insetTop() {
        return insetTop;
    }

    void showCalendar() {
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
                home.tick();
                dashboard.refreshWeather();
            });
            any = true;
        }
        if (i.hasExtra("demo_free")) {
            Prefs.setHomeFree(this, i.getBooleanExtra("demo_free", false));
            applyHome();
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
        if (i.hasExtra("demo_android_colors")) {
            Prefs.setAndroidColors(this, i.getBooleanExtra("demo_android_colors", false));
            Prefs.setAndroidStyle(this, 1);
            SystemWall.sync(this);
            any = true;
        }
        if (i.hasExtra("demo_edge")) {
            Prefs.setEdgeShade(this, i.getBooleanExtra("demo_edge", false));
            LockService.updateEdge();
            any = true;
        }
        if (i.getBooleanExtra("jostin_studio", false)) {
            handler.postDelayed(() -> Studio.show(this, this, -1), 500);
            any = true;
        }
        String shot = i.getStringExtra("shot");
        if (shot != null) {
            any = true;
            int tab = i.getIntExtra("tab", 0);
            String arg = i.getStringExtra("arg");
            handler.postDelayed(() -> demoShot(shot, tab, arg), 900);
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
        for (String k : new String[]{"demo_reset", "theme", "demo_effect", "demo_overlay", "demo_clock", "demo_city", "demo_photo",
                "demo_free", "shot", "open", "arg", "jostin_studio", "demo_android_colors", "demo_edge"}) i.removeExtra(k);
        return any;
    }

    private void demoShot(String shot, int tab, String arg) {
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
            case "page": home.goTo(tab, true); break;
            case "edit": home.setEditing(true); break;
            case "done": home.setEditing(false); break;
            case "picker": HomeMenus.widgets(this); break;
            case "picker_apps": HomeMenus.widgetsTab = 1; HomeMenus.widgets(this); break;
            case "select": {
                home.setEditing(true);
                for (HomeModel.Item it : home.model.on(home.page)) {
                    if (home.resizable(it)) {
                        home.select(it);
                        break;
                    }
                }
                break;
            }
            case "menu": {
                for (HomeModel.Item it : home.model.on(home.page)) {
                    if (arg == null || arg.equals(it.type)) {
                        itemMenu(it);
                        break;
                    }
                }
                break;
            }
            case "add": {
                Gadgets.Spec s = Gadgets.spec(arg == null ? "nota" : arg);
                if (s != null) addGadget(s);
                break;
            }
            case "aw": {
                // Agrega el primer widget de una app (para probar los widgets de Android)
                AppWidgetProviderInfo pick = null;
                for (AppWidgetProviderInfo info : AppWidgets.mgr(this).getInstalledProviders()) {
                    boolean match = arg == null ? info.configure == null : info.provider.getPackageName().equals(arg);
                    if (match) {
                        pick = info;
                        break;
                    }
                }
                if (pick != null) addSystemWidget(pick);
                else Sheet.pill(this, backdrop, "No hay widgets de otras apps para probar");
                break;
            }
            case "folder": {
                List<String> apps = new ArrayList<>();
                Drawer.apps(this, list -> {
                    for (Drawer.App x : list) {
                        if (apps.size() < 4) apps.add(x.pkg);
                    }
                    if (apps.size() < 2) return;
                    HomeModel.Item f = home.place("folder", 1, 1, x -> {
                        x.put("name", "Favoritas");
                        JSONArray arr = new JSONArray();
                        for (String p : apps) arr.put(p);
                        x.put("apps", arr);
                    });
                    if (f != null) handler.postDelayed(() -> HomeMenus.folder(this, f, home.viewOf(f)), 600);
                });
                break;
            }
            case "undo": home.undo(); break;
            case "tema": {
                List<org.json.JSONObject> p = Themes.presets();
                ThemeStudio.tryOn(this, p.get(Math.max(0, Math.min(p.size() - 1, tab))), false);
                break;
            }
            case "tema_undo": Themes.undo(this); break;
            default: break;
        }
    }

    // ==================== Utilidades ====================

    static void launch(Activity a, Drawer.App x, View from) {
        if (x.work()) WorkApps.launch(a, x, from, launchOptions(a, from));
        else launch(a, x.pkg, from);
    }

    static void launch(Activity a, String pkg, View from) {
        Intent i = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(i, launchOptions(a, from));
        } catch (Exception ignored) {
            // nada
        }
    }

    /** La animación con que se abre una app, según lo que elegiste en el estudio. */
    private static Bundle launchOptions(Activity a, View from) {
        Bundle opts = null;
        int style = Prefs.motion(a) == 3 ? -1 : Prefs.appAnim(a);
        boolean hasView = from != null && from.getWidth() > 0;
        if (style == -1) {
            opts = ActivityOptions.makeCustomAnimation(a, 0, 0).toBundle();
        } else if (style == 0 && hasView) {
            opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
        } else if (style == 1 && hasView) {
            opts = ActivityOptions.makeClipRevealAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
        } else if (style == 2) {
            opts = ActivityOptions.makeCustomAnimation(a, android.R.anim.fade_in, android.R.anim.fade_out).toBundle();
        }
        return opts;
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
