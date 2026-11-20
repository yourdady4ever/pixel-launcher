package com.jostin.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.GridLayoutAnimationController;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** El cajón de apps: sube siguiendo tu dedo y se cierra arrastrándolo hacia abajo. */
class Drawer extends FrameLayout {
    interface Host {
        void closeDrawer();
        void appActions(App app, View from);

    }

    static final class App {
        final String label, pkg, key;
        /** Solo en las de trabajo: su perfil, la pantalla que abre y su ícono con maletín. */
        final UserHandle user;
        final ComponentName comp;
        Drawable badged;
        /** Del espacio privado (también tiene perfil, pero no se pausa: se bloquea). */
        boolean priv;
        /** La categoría que la app dice tener (ApplicationInfo.category). */
        int cat = -1;

        App(String label, String pkg) {
            this(label, pkg, null, null);
        }

        App(String label, String pkg, UserHandle user, ComponentName comp) {
            this.label = label;
            this.pkg = pkg;
            this.key = strip(label);
            this.user = user;
            this.comp = comp;
        }

        boolean work() {
            return user != null;
        }
    }

    // ---------- Lista compartida de apps (se carga una vez y se reutiliza) ----------

    interface Loaded {
        void loaded(List<App> apps);
    }

    private static List<App> cache;
    private static List<App> workCache = new ArrayList<>();
    private static List<App> privateCache = new ArrayList<>();
    private static final Map<String, Drawable> iconCache = new HashMap<>();
    private static int iconStyleCached = -1;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static boolean loadingApps = false, warming = false;
    private static final List<Loaded> waiting = new ArrayList<>();
    /** Cajones abiertos, para redibujarlos cuando lleguen íconos preparados en segundo plano. */
    private static final List<java.lang.ref.WeakReference<Drawer>> drawers = new ArrayList<>();

    static void invalidateApps() {
        cache = null;
        workCache = new ArrayList<>();
        privateCache = new ArrayList<>();
    }

    static void trim() {
        iconCache.clear();
    }

    /** Olvida el ícono de una sola app (se instaló, actualizó o cambió). */
    static void forget(String pkg) {
        iconCache.remove(pkg);
    }

    static void apps(Context c, Loaded l) {
        if (cache != null) {
            l.loaded(cache);
            // Si se soltaron íconos por memoria, se rehacen fuera del hilo principal
            warmIcons(c);
            return;
        }
        // Si ya se está cargando, se espera a esa misma carga en vez de lanzar otra
        waiting.add(l);
        if (loadingApps) return;
        loadingApps = true;
        Context app = c.getApplicationContext();
        java.util.Set<String> have = Prefs.iconStyle(app) == iconStyleCached
                ? new java.util.HashSet<>(iconCache.keySet()) : new java.util.HashSet<>();
        new Thread(() -> {
            PackageManager pm = app.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            List<App> out = new ArrayList<>();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(app.getPackageName()) || !seen.add(pkg)) continue;
                App ax = new App(String.valueOf(ri.loadLabel(pm)), pkg);
                ax.cat = ri.activityInfo.applicationInfo.category;
                out.add(ax);
            }
            Collator col = Collator.getInstance(MainActivity.ES);
            Collections.sort(out, (x, y) -> col.compare(x.label, y.label));
            // Los íconos se preparan aquí, fuera del hilo principal, para que el cajón no se trabe
            int style = Prefs.iconStyle(app);
            Map<String, Drawable> pre = new HashMap<>();
            for (App x : out) {
                if (have.contains(x.pkg)) continue;
                Drawable d = IconEngine.icon(app, x.pkg);
                if (d != null) pre.put(x.pkg, d);
            }
            List<App> work = WorkApps.load(app);
            List<App> privList = WorkApps.loadPrivate(app);
            main.post(() -> {
                if (iconStyleCached == style || iconStyleCached == -1) {
                    iconStyleCached = style;
                    for (Map.Entry<String, Drawable> en : pre.entrySet()) {
                        if (!iconCache.containsKey(en.getKey())) iconCache.put(en.getKey(), en.getValue());
                    }
                }
                cache = out;
                workCache = work;
                privateCache = privList;
                loadingApps = false;
                List<Loaded> ls = new ArrayList<>(waiting);
                waiting.clear();
                for (Loaded w : ls) w.loaded(out);
            });
        }).start();
    }

    /** Prepara en segundo plano los íconos que falten en la caché y luego redibuja los cajones. */
    private static void warmIcons(Context c) {
        if (warming || cache == null) return;
        int style = Prefs.iconStyle(c);
        if (style != iconStyleCached) {
            iconCache.clear();
            iconStyleCached = style;
        }
        List<String> missing = new ArrayList<>();
        for (App x : cache) if (!iconCache.containsKey(x.pkg)) missing.add(x.pkg);
        if (missing.isEmpty()) return;
        warming = true;
        Context app = c.getApplicationContext();
        new Thread(() -> {
            Map<String, Drawable> pre = new HashMap<>();
            for (String pkg : missing) {
                Drawable d = IconEngine.icon(app, pkg);
                if (d != null) pre.put(pkg, d);
            }
            main.post(() -> {
                warming = false;
                if (iconStyleCached != style) return;
                for (Map.Entry<String, Drawable> en : pre.entrySet()) {
                    if (!iconCache.containsKey(en.getKey())) iconCache.put(en.getKey(), en.getValue());
                }
                java.util.Iterator<java.lang.ref.WeakReference<Drawer>> it = drawers.iterator();
                while (it.hasNext()) {
                    Drawer d = it.next().get();
                    if (d == null) it.remove();
                    else d.adapter.notifyDataSetChanged();
                }
            });
        }).start();
    }

    static Drawable icon(Context c, String pkg) {
        int style = Prefs.iconStyle(c);
        if (style != iconStyleCached) {
            iconCache.clear();
            iconStyleCached = style;
        }
        Drawable d = iconCache.get(pkg);
        if (d == null) {
            d = IconEngine.icon(c, pkg);
            if (d != null) iconCache.put(pkg, d);
        }
        return d;
    }

    static String strip(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase(MainActivity.ES);
    }

    // ---------- Vista ----------

    private final Activity act;
    private final Backdrop backdrop;
    private final Host host;
    private final boolean pick;
    private final List<App> all = new ArrayList<>();
    private final View catChips;
    private int cat = 0;
    /** Resultados extra al buscar: cuentas, ajustes del sistema y contactos. */
    private final LinearLayout extras;
    private String lastQuery = "";
    private final List<App> work = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private final Segmented tabs;
    private final View workRow;
    private final TextView workText;
    private boolean workPaused = false;
    private final List<App> priv = new ArrayList<>();
    private final View privBox, privScroll;
    private final LinearLayout privApps;
    private final TextView privText;
    private boolean hasPrivate = false, privLocked = true;
    private final Adapter adapter = new Adapter();
    private final GridView grid;
    private final EditText search;
    private final TextView empty;
    private final View webRow;
    private final TextView webText;
    private final LinearLayout suggestRow;
    private final LinearLayout column;
    private float downX, downY;
    private boolean dragging = false, swiping = false;
    private VelocityTracker vt;
    private boolean showHidden = false;

    Drawer(Activity a, Backdrop b, Host h, boolean pickMode) {
        super(a);
        act = a;
        backdrop = b;
        host = h;
        pick = pickMode;
        setClickable(true);
        if (!pickMode) {
            Glass g = new Glass(b, 0f, Glass.TINT_STRONG);
            setBackground(g);
            b.register(this);
        }

        column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(a, 16);
        column.setPadding(pickMode ? 0 : side, pickMode ? 0 : Ui.dp(a, 40), pickMode ? 0 : side, 0);
        addView(column, new LayoutParams(-1, -1));

        View handle = new View(a);
        handle.setBackground(Ui.fill(a, 0x59FFFFFF, 3));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = Ui.dp(a, 14);
        if (!pickMode) column.addView(handle, hl);

        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), 0);
        Glass.apply(bar, b, 26);
        ImageView si = new ImageView(a);
        Icons.Line sd = Icons.of(Icons.SEARCH);
        sd.setColor(Ui.MUTED);
        si.setImageDrawable(sd);
        bar.addView(si, new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)));
        search = new EditText(a);
        search.setHint(pick ? "Elige una app" : "Buscar apps");
        search.setHintTextColor(0x80FFFFFF);
        search.setTextColor(Ui.TEXT);
        search.setTextSize(16);
        search.setTypeface(Ui.REGULAR);
        search.setBackground(null);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(0, -2, 1f);
        ql.leftMargin = Ui.dp(a, 10);
        bar.addView(search, ql);
        column.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(a, 52)));

        // Personal y Trabajo: solo sale si tienes perfil de trabajo
        tabs = new Segmented(a, b, "Personal", "Trabajo");
        tabs.setListener(k -> filter(true));
        tabs.setVisibility(GONE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, Ui.dp(a, 44));
        tl.topMargin = Ui.dp(a, 12);
        column.addView(tabs, tl);

        // Filtros por tipo de app (automáticos, y se pueden cambiar a mano en cada app)
        catChips = Ctl.chips(a, CATS, 0, k -> {
            cat = k;
            filter(true);
        });
        LinearLayout.LayoutParams ccl = new LinearLayout.LayoutParams(-1, -2);
        ccl.topMargin = Ui.dp(a, 10);
        column.addView(catChips, ccl);
        catChips.setVisibility(pickMode ? GONE : VISIBLE);

        empty = Ui.text(a, "", 15, Ui.MUTED, Ui.REGULAR);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(a, 30), 0, Ui.dp(a, 10));
        empty.setVisibility(GONE);
        column.addView(empty, new LinearLayout.LayoutParams(-1, -2));

        extras = new LinearLayout(a);
        extras.setOrientation(LinearLayout.VERTICAL);
        extras.setVisibility(GONE);
        LinearLayout.LayoutParams exl = new LinearLayout.LayoutParams(-1, -2);
        exl.topMargin = Ui.dp(a, 8);
        column.addView(extras, exl);

        LinearLayout web = new LinearLayout(a);
        web.setGravity(Gravity.CENTER_VERTICAL);
        int wp = Ui.dp(a, 14);
        web.setPadding(wp, wp, wp, wp);
        Glass.apply(web, b, 20);
        ImageView wi = new ImageView(a);
        wi.setImageDrawable(Icons.of(Icons.SEARCH));
        web.addView(wi, new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)));
        webText = Ui.text(a, "", 15, Ui.TEXT, Ui.MEDIUM);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.leftMargin = Ui.dp(a, 12);
        web.addView(webText, wl);
        Ui.pressable(web, 0.97f);
        web.setOnClickListener(v -> webSearch());
        web.setVisibility(GONE);
        webRow = web;
        column.addView(web, new LinearLayout.LayoutParams(-1, -2));

        // Pausar o reanudar el perfil de trabajo, como en el launcher de Pixel
        LinearLayout wr = new LinearLayout(a);
        wr.setGravity(Gravity.CENTER_VERTICAL);
        wr.setPadding(wp, wp, wp, wp);
        Glass.apply(wr, b, 20);
        ImageView wri = new ImageView(a);
        wri.setImageDrawable(Icons.of(Icons.WORK));
        wr.addView(wri, new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)));
        workText = Ui.text(a, "", 15, Ui.TEXT, Ui.MEDIUM);
        LinearLayout.LayoutParams wtl = new LinearLayout.LayoutParams(0, -2, 1f);
        wtl.leftMargin = Ui.dp(a, 12);
        wr.addView(workText, wtl);
        Ui.pressable(wr, 0.97f);
        wr.setOnClickListener(v -> {
            Sound.play(v, Sound.TOGGLE);
            setWorkPaused(!workPaused);
        });
        wr.setVisibility(GONE);
        workRow = wr;
        LinearLayout.LayoutParams wrl = new LinearLayout.LayoutParams(-1, -2);
        wrl.topMargin = Ui.dp(a, 12);
        column.addView(wr, wrl);

        // Las apps que más usas (se cuenta solo en tu teléfono)
        suggestRow = new LinearLayout(a);
        suggestRow.setVisibility(GONE);
        suggestRow.setPadding(0, Ui.dp(a, 16), 0, Ui.dp(a, 4));
        column.addView(suggestRow, new LinearLayout.LayoutParams(-1, -2));


        // Espacio privado: bloqueado solo se ve el candado; desbloqueado, sus apps en una fila
        LinearLayout pb = new LinearLayout(a);
        pb.setOrientation(LinearLayout.VERTICAL);
        pb.setPadding(wp, wp, wp, wp);
        Glass.apply(pb, b, 20);
        LinearLayout ph = new LinearLayout(a);
        ph.setGravity(Gravity.CENTER_VERTICAL);
        ImageView pi = new ImageView(a);
        pi.setImageDrawable(Icons.of(Icons.LOCK));
        ph.addView(pi, new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)));
        TextView pt = Ui.text(a, "Espacio privado", 15, Ui.TEXT, Ui.MEDIUM);
        LinearLayout.LayoutParams ptl = new LinearLayout.LayoutParams(0, -2, 1f);
        ptl.leftMargin = Ui.dp(a, 12);
        ph.addView(pt, ptl);
        privText = Ui.text(a, "", 14, Ui.MUTED, Ui.REGULAR);
        ph.addView(privText);
        Ui.pressable(ph, 0.97f);
        ph.setOnClickListener(v -> {
            Sound.play(v, Sound.TOGGLE);
            setPrivateLocked(!privLocked);
        });
        pb.addView(ph, new LinearLayout.LayoutParams(-1, -2));
        android.widget.HorizontalScrollView ps = new android.widget.HorizontalScrollView(a);
        ps.setHorizontalScrollBarEnabled(false);
        privApps = new LinearLayout(a);
        ps.addView(privApps);
        LinearLayout.LayoutParams psl = new LinearLayout.LayoutParams(-1, -2);
        psl.topMargin = Ui.dp(a, 14);
        pb.addView(ps, psl);
        privScroll = ps;
        pb.setVisibility(GONE);
        privBox = pb;
        LinearLayout.LayoutParams pbl = new LinearLayout.LayoutParams(-1, -2);
        pbl.topMargin = Ui.dp(a, 12);
        column.addView(pb, pbl);

        grid = new GridView(a);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(Ui.dp(a, 12));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setPadding(0, Ui.dp(a, 18), 0, Ui.dp(a, 30));
        grid.setAdapter(adapter);
        column.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        AnimationSet an = new AnimationSet(true);
        an.addAnimation(new AlphaAnimation(0f, 1f));
        an.addAnimation(new ScaleAnimation(0.75f, 1f, 0.75f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f));
        an.setDuration(320);
        an.setInterpolator(new DecelerateInterpolator(2f));
        GridLayoutAnimationController ctl = new GridLayoutAnimationController(an, 0.1f, 0.14f);
        grid.setLayoutAnimation(ctl);

        grid.setOnItemClickListener((p, v, pos, id) -> choose(shown.get(pos), v));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pick) return false;
            Sound.play(v, Sound.HOLD);
            host.appActions(shown.get(pos), v);
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable s) {
                filter(false);
            }
        });
        search.setOnEditorActionListener((v, act0, e) -> {
            if (!shown.isEmpty()) choose(shown.get(0), null);
            else webSearch();
            return true;
        });
        drawers.add(new java.lang.ref.WeakReference<>(this));
        if (!pickMode) setOnApplyWindowInsetsListener((v, in) -> {
            column.setPadding(side, in.getSystemWindowInsetTop() + Ui.dp(a, 12), side, 0);
            grid.setPadding(0, Ui.dp(a, 18), 0, in.getSystemWindowInsetBottom() + Ui.dp(a, 24));
            return in;
        });
    }

    void setShowHidden(boolean on) {
        showHidden = on;
    }

    /** Carga la lista (si hace falta) y la muestra. */
    void load(boolean animate) {
        apps(act, list -> {
            all.clear();
            Set<String> hidden = Prefs.hidden(act);
            for (App x : list) if (showHidden || !hidden.contains(x.pkg)) all.add(x);
            // Al elegir una app para el dock no salen las de trabajo: el dock guarda solo el paquete
            work.clear();
            boolean hasWork = !pick && !WorkApps.profiles(act).isEmpty();
            if (hasWork) work.addAll(workCache);
            workPaused = hasWork && WorkApps.paused(act);
            tabs.setVisibility(hasWork ? VISIBLE : GONE);
            if (!hasWork && tabs.selected() == 1) tabs.select(0, false);
            // Se revisa cada vez: si Android lo bloqueó (al apagar la pantalla), no se enseña aunque haya caché
            priv.clear();
            UserHandle space = pick ? null : WorkApps.privateSpace(act);
            hasPrivate = space != null;
            privLocked = !hasPrivate || WorkApps.locked(act, space);
            if (!privLocked) priv.addAll(privateCache);
            fillPrivate();
            filter(animate);
        });
    }

    private void fillPrivate() {
        privText.setText(privLocked ? "Desbloquear" : "Bloquear");
        privApps.removeAllViews();
        for (App x : priv) privApps.addView(appCell(x, 0xE6FFFFFF), new LinearLayout.LayoutParams(Ui.dp(act, 76), -2));
        privScroll.setVisibility(priv.isEmpty() ? GONE : VISIBLE);
    }

    /** Para desbloquear, Android pide tu huella o PIN; MainActivity recarga cuando cambia. */
    private void setPrivateLocked(boolean on) {
        if (!WorkApps.setPrivateLocked(act, on)) {
            Sheet.pill(act, backdrop, "Android no dejó cambiarlo; usa el espacio privado desde Ajustes");
            return;
        }
        if (on) Sheet.pill(act, backdrop, "Espacio privado bloqueado");
    }

    /** Pausar no pide nada; para reanudar Android puede pedir tu PIN. MainActivity recarga al cambiar. */
    private void setWorkPaused(boolean on) {
        if (!WorkApps.setPaused(act, on)) {
            Sheet.pill(act, backdrop, "Android no dejó cambiarlo; usa el botón de trabajo de los ajustes rápidos");
            return;
        }
        Sheet.pill(act, backdrop, on ? "Apps de trabajo en pausa" : "Reanudando apps de trabajo");
    }

    void focusSearch() {
        search.requestFocus();
        InputMethodManager im = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.showSoftInput(search, 0);
    }

    void reset() {
        tabs.select(0, false);
        search.setText("");
        Sheet.hideKeyboard(act, search);
        grid.setSelection(0);
    }

    private void filter(boolean animate) {
        String q = strip(search.getText().toString().trim());
        boolean workTab = tabs.getVisibility() == VISIBLE && tabs.selected() == 1;
        shown.clear();
        catChips.setVisibility(!pick && q.isEmpty() && !workTab ? VISIBLE : GONE);
        if (q.isEmpty()) {
            if (workTab) shown.addAll(work);
            else if (cat == 0 || pick) shown.addAll(all);
            else for (App x : all) if (catOf(act, x) == cat) shown.add(x);
        } else {
            // Al buscar salen todas; la de trabajo se distingue por su maletín. Las privadas solo si está desbloqueado
            for (App x : all) if (x.key.contains(q)) shown.add(x);
            for (App x : work) if (x.key.contains(q)) shown.add(x);
            for (App x : priv) if (x.key.contains(q)) shown.add(x);
        }
        fillExtras(q, search.getText().toString().trim());
        // Espacio privado: bloqueado no se ve; para abrirlo se busca "privado", como en Pixel
        boolean askPrivate = q.contains("privad") || q.contains("private");
        boolean showPrivate = hasPrivate && !workTab && (privLocked ? askPrivate : q.isEmpty() || askPrivate);
        boolean none = shown.isEmpty() && !q.isEmpty() && !showPrivate && (!all.isEmpty() || !work.isEmpty());
        boolean noWork = workTab && q.isEmpty() && work.isEmpty();
        empty.setVisibility(none || noWork ? VISIBLE : GONE);
        empty.setText(noWork ? (workPaused ? "Tus apps de trabajo están en pausa" : "No hay apps de trabajo") : "No encontré esa app");
        webRow.setVisibility(!q.isEmpty() && !pick ? VISIBLE : GONE);
        webText.setText("Buscar «" + search.getText().toString().trim() + "» en internet");
        workRow.setVisibility(workTab && q.isEmpty() ? VISIBLE : GONE);
        workText.setText(workPaused ? "Reanudar apps de trabajo" : "Pausar apps de trabajo");
        privBox.setVisibility(showPrivate ? VISIBLE : GONE);
        fillSuggestions(q.isEmpty() && !pick && !workTab && Prefs.drawerSuggest(act));
        adapter.notifyDataSetChanged();
        if (animate) grid.scheduleLayoutAnimation();
    }

    private void fillSuggestions(boolean on) {
        suggestRow.removeAllViews();
        List<String> pkgs = on ? IconEngine.suggestions(act, 4, Prefs.hidden(act)) : new ArrayList<>();
        if (pkgs.size() < 2) {
            suggestRow.setVisibility(GONE);
            return;
        }
        for (String pkg : pkgs) {
            App x = null;
            for (App y : all) if (y.pkg.equals(pkg)) x = y;
            if (x == null) continue;
            suggestRow.addView(appCell(x, Theme.accent), new LinearLayout.LayoutParams(0, -2, 1f));
        }
        for (int k = suggestRow.getChildCount(); k < 4; k++) suggestRow.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1f));
        suggestRow.setVisibility(VISIBLE);
    }

    /** Una app suelta, fuera de la cuadrícula (sugerencias y espacio privado). */
    private LinearLayout appCell(App app, int labelColor) {
        LinearLayout cell = new LinearLayout(act);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(act);
        if (app.work()) {
            icon.setImageDrawable(app.badged);
        } else {
            icon.setImageDrawable(icon(act, app.pkg));
            IconEngine.clip(icon, app.pkg);
        }
        cell.addView(icon, new LinearLayout.LayoutParams(Ui.dp(act, 56), Ui.dp(act, 56)));
        TextView label = Ui.text(act, app.label, 12, labelColor, Ui.MEDIUM);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(act, 8);
        cell.addView(label, lp);
        Ui.pressable(cell, 0.9f);
        cell.setOnClickListener(v -> choose(app, icon));
        cell.setOnLongClickListener(v -> {
            Sound.play(v, Sound.HOLD);
            host.appActions(app, v);
            return true;
        });
        return cell;
    }

    static final int REQ_CONTACTS = 410;

    static final String[] CATS = {"Todas", "Juegos", "Redes", "Fotos y video", "Música", "Estudio", "Herramientas"};
    private static final String[] STUDY = {"duolingo", "classroom", "notion", "khan", "quizlet", "photomath", "brainly", "canvas",
            "chegg", "docs", "office", "teams", "keep", "anki", "symbolab", "geogebra", "wolfram", "busuu", "babbel", "moodle"};
    private static org.json.JSONObject manual;

    private static org.json.JSONObject manual(Context c) {
        if (manual == null) {
            try {
                manual = new org.json.JSONObject(Prefs.sp(c).getString("app_cats", "{}"));
            } catch (Exception e) {
                manual = new org.json.JSONObject();
            }
        }
        return manual;
    }

    /** 0 = automática. */
    static int manualCat(Context c, String pkg) {
        return manual(c).optInt(pkg, 0);
    }

    static void setManualCat(Context c, String pkg, int k) {
        org.json.JSONObject m = manual(c);
        try {
            if (k <= 0) m.remove(pkg);
            else m.put(pkg, k);
        } catch (Exception ignored) {
            // nada
        }
        Prefs.sp(c).edit().putString("app_cats", m.toString()).apply();
    }

    static int catOf(Context c, App x) {
        int m = manualCat(c, x.pkg);
        if (m > 0) return m;
        String p = x.pkg.toLowerCase(MainActivity.ES);
        for (String s : STUDY) if (p.contains(s)) return 5;
        switch (x.cat) {
            case android.content.pm.ApplicationInfo.CATEGORY_GAME: return 1;
            case android.content.pm.ApplicationInfo.CATEGORY_SOCIAL:
            case android.content.pm.ApplicationInfo.CATEGORY_NEWS: return 2;
            case android.content.pm.ApplicationInfo.CATEGORY_IMAGE:
            case android.content.pm.ApplicationInfo.CATEGORY_VIDEO: return 3;
            case android.content.pm.ApplicationInfo.CATEGORY_AUDIO: return 4;
            case android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY: return 5;
            default: return 6;
        }
    }

    /** Atajos a ajustes de Android: nombre, palabras con que se encuentra y a dónde lleva. */
    private static final String[][] SETTINGS = {
            {"Wi-Fi", "wifi internet red inalambrica", android.provider.Settings.ACTION_WIFI_SETTINGS},
            {"Bluetooth", "bluetooth audifonos bocina", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS},
            {"Batería", "bateria pila carga ahorro", Intent.ACTION_POWER_USAGE_SUMMARY},
            {"Pantalla", "pantalla brillo tema oscuro", android.provider.Settings.ACTION_DISPLAY_SETTINGS},
            {"Sonido", "sonido volumen tono vibracion", android.provider.Settings.ACTION_SOUND_SETTINGS},
            {"Apps", "apps aplicaciones permisos", android.provider.Settings.ACTION_APPLICATION_SETTINGS},
            {"Notificaciones", "notificaciones avisos", "android.settings.NOTIFICATION_SETTINGS"},
            {"Ubicación", "ubicacion gps localizacion", android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS},
            {"Datos móviles", "datos red movil sim", android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS},
            {"Modo avión", "avion vuelo", android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS},
            {"Almacenamiento", "almacenamiento memoria espacio", android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS},
            {"No molestar", "no molestar silencio", "android.settings.ZEN_MODE_SETTINGS"},
            {"Seguridad", "seguridad bloqueo huella contrasena", android.provider.Settings.ACTION_SECURITY_SETTINGS},
            {"Fecha y hora", "fecha hora reloj", android.provider.Settings.ACTION_DATE_SETTINGS},
            {"Idioma y teclado", "idioma teclado", android.provider.Settings.ACTION_LOCALE_SETTINGS},
            {"Ajustes", "ajustes configuracion settings", android.provider.Settings.ACTION_SETTINGS}};

    private static String[] settingsKeys;

    /** Cuenta rápida arriba, atajos a ajustes y contactos (el permiso se pide solo si lo usas). */
    private void fillExtras(String q, String raw) {
        extras.removeAllViews();
        lastQuery = q;
        if (pick || q.isEmpty()) {
            extras.setVisibility(GONE);
            return;
        }
        // Cuenta: solo si parece operación (números y algún signo)
        if (raw.matches(".*\\d.*") && raw.matches(".*[-+*/×÷^%(].*")) {
            try {
                double v = Expr.eval(raw.replace('×', '*').replace('÷', '/').replace(',', '.'), false, 0);
                String res = Expr.format(v);
                extras.addView(extraRow(Icons.CALC, "= " + res, "Toca para copiar", () -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("resultado", Expr.plain(v)));
                    Sheet.pill(act, backdrop, "Copié " + res);
                }));
            } catch (Throwable ignored) {
                // no era cuenta
            }
        }
        if (q.length() >= 2) {
            if (settingsKeys == null) {
                settingsKeys = new String[SETTINGS.length];
                for (int i = 0; i < SETTINGS.length; i++) settingsKeys[i] = strip(SETTINGS[i][0] + " " + SETTINGS[i][1]);
            }
            int found = 0;
            for (int i = 0; i < SETTINGS.length && found < 2; i++) {
                if (!settingsKeys[i].contains(q)) continue;
                found++;
                String action = SETTINGS[i][2];
                extras.addView(extraRow(Icons.GEAR, SETTINGS[i][0], "Ajustes de Android", () -> openIntent(new Intent(action))));
            }
            contacts(q, raw);
        }
        extras.setVisibility(extras.getChildCount() > 0 ? VISIBLE : GONE);
    }

    private void contacts(String q, String raw) {
        boolean granted = act.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            if (Prefs.sp(act).getBoolean("contacts_no", false)) return;
            extras.addView(extraRow(Icons.SEARCH, "Buscar «" + raw + "» en contactos", "Te pide permiso una vez", () -> {
                Prefs.sp(act).edit().putBoolean("contacts_no", true).apply();
                act.requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
            }));
            return;
        }
        Context app = act.getApplicationContext();
        Gadgets.BG.execute(() -> {
            List<String[]> found = new ArrayList<>();
            String[] proj = {android.provider.ContactsContract.Contacts._ID, android.provider.ContactsContract.Contacts.LOOKUP_KEY,
                    android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY};
            try (android.database.Cursor c = app.getContentResolver().query(android.provider.ContactsContract.Contacts.CONTENT_URI, proj,
                    android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?", new String[]{"%" + raw + "%"},
                    android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC")) {
                while (c != null && c.moveToNext() && found.size() < 3) found.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
            } catch (Exception e) {
                Bitacora.error("buscar contactos", e);
            }
            main.post(() -> {
                if (!q.equals(lastQuery) || found.isEmpty()) return;
                for (String[] x : found) {
                    android.net.Uri uri = android.provider.ContactsContract.Contacts.getLookupUri(Long.parseLong(x[0]), x[1]);
                    extras.addView(extraRow(Icons.HOME, x[2] == null ? "(Sin nombre)" : x[2], "Contacto", () -> openIntent(new Intent(Intent.ACTION_VIEW, uri))));
                }
                extras.setVisibility(VISIBLE);
            });
        });
    }

    /** El permiso de contactos llegó (o no): se vuelve a buscar lo mismo. */
    void refresh() {
        filter(false);
    }

    private void openIntent(Intent i) {
        Sound.play(this, Sound.OPEN);
        try {
            act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            postDelayed(host::closeDrawer, 300);
        } catch (Exception e) {
            Sheet.pill(act, backdrop, "Tu teléfono no tiene esa pantalla");
        }
    }

    private View extraRow(Icons.Shape icon, String title, String sub, Runnable tap) {
        LinearLayout r = new LinearLayout(act);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(act, 12);
        r.setPadding(Ui.dp(act, 14), p, Ui.dp(act, 14), p);
        r.setBackground(Ui.fill(act, 0x14FFFFFF, 18));
        ImageView iv = new ImageView(act);
        iv.setImageDrawable(Icons.of(icon));
        r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(act, 20), Ui.dp(act, 20)));
        LinearLayout t = new LinearLayout(act);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView a = Ui.text(act, title, 15, Ui.TEXT, Ui.MEDIUM);
        a.setMaxLines(1);
        a.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(a);
        t.addView(Ui.text(act, sub, 12, Ui.MUTED, Ui.REGULAR));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(act, 12);
        r.addView(t, tl);
        Ui.pressable(r, 0.97f);
        r.setOnClickListener(v -> tap.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(act, 6);
        r.setLayoutParams(lp);
        return r;
    }

    private void webSearch() {
        String q = search.getText().toString().trim();
        if (q.isEmpty()) return;
        Sound.play(this, Sound.OPEN);
        Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
        i.putExtra(android.app.SearchManager.QUERY, q);
        try {
            act.startActivity(i);
        } catch (Exception e) {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(q))));
            } catch (Exception ignored) {
                return;
            }
        }
        host.closeDrawer();
    }

    private void choose(App x, View from) {
        if (pick) {
            Sound.play(from != null ? from : this, Sound.TAP);
            act.setResult(Activity.RESULT_OK, new Intent().putExtra(AppsActivity.EXTRA_PKG, x.pkg));
            act.finish();
            return;
        }
        if (x.work() && !x.priv && workPaused) {
            Sound.play(from != null ? from : this, Sound.TAP);
            setWorkPaused(false);
            return;
        }
        Sound.play(from != null ? from : this, Sound.OPEN);
        // Las sugerencias cuentan por paquete; las de trabajo no se mezclan con tus apps
        if (!x.work()) Prefs.countLaunch(act, x.pkg);
        MainActivity.launch(act, x, from);
        postDelayed(host::closeDrawer, 350);
    }

    // ---------- Arrastrar ----------

    void setProgress(float f) {
        if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
        float h = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        setTranslationY((1f - f) * h * 0.45f);
        setAlpha(Math.max(0f, Math.min(1f, f * 1.5f)));
    }

    void settle(boolean open, Runnable after) {
        float h = getHeight();
        if (open) {
            animate().translationY(0).alpha(1f).setDuration(360).setInterpolator(new DecelerateInterpolator(2.4f)).withEndAction(after).start();
        } else {
            animate().translationY(h * 0.45f).alpha(0f).setDuration(240).setInterpolator(new DecelerateInterpolator(1.4f))
                    .withEndAction(() -> {
                        setVisibility(GONE);
                        reset();
                        if (after != null) after.run();
                    }).start();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (pick) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                dragging = false;
                swiping = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (e.getRawY() - downY > Ui.dp(act, 16) && !grid.canScrollVertically(-1)) {
                    dragging = true;
                    vt = VelocityTracker.obtain();
                    Sheet.hideKeyboard(act, search);
                    return true;
                }
                // De lado: cambia entre Personal y Trabajo
                float dx = e.getRawX() - downX;
                if (tabs.getVisibility() == VISIBLE && search.length() == 0
                        && Math.abs(dx) > Ui.dp(act, 24) && Math.abs(dx) > Math.abs(e.getRawY() - downY) * 1.5f) {
                    swiping = true;
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    /**
     * Con dos pestañas, deslizar hacia cualquier lado lleva a la otra. La lista se va hacia donde
     * mueves el dedo y la otra entra por el lado contrario.
     */
    private void switchTab(float dx) {
        Sound.play(this, Sound.TOGGLE);
        tabs.select(tabs.selected() == 0 ? 1 : 0, true);
        float out = Math.signum(dx) * grid.getWidth() * 0.5f;
        grid.animate().translationX(out).alpha(0f).setDuration(140).withEndAction(() -> {
            filter(false);
            grid.setTranslationX(-out);
            grid.animate().translationX(0).alpha(1f).setDuration(240).setInterpolator(new DecelerateInterpolator(2f)).start();
        }).start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (swiping) {
            float sx = e.getRawX() - downX;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    grid.setTranslationX(sx * 0.35f);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    swiping = false;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(sx) > Ui.dp(act, 56)) switchTab(sx);
                    else grid.animate().translationX(0).setDuration(200).start();
                    return true;
                default:
                    return true;
            }
        }
        if (!dragging) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && !pick) {
                downY = e.getRawY();
                dragging = true;
                vt = VelocityTracker.obtain();
            } else {
                return super.onTouchEvent(e);
            }
        }
        if (vt != null) vt.addMovement(e);
        float dy = e.getRawY() - downY;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                setProgress(Math.max(0f, 1f - Math.max(0f, dy) / Math.max(1f, getHeight() * 0.7f)));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                float vy = 0;
                if (vt != null) {
                    vt.computeCurrentVelocity(1000);
                    vy = vt.getYVelocity();
                    vt.recycle();
                    vt = null;
                }
                if (dy > getHeight() * 0.16f || vy > 1200) {
                    Sound.play(this, Sound.BACK);
                    host.closeDrawer();
                } else {
                    settle(true, null);
                }
                return true;
            default:
                return true;
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convert == null) {
                cell = new LinearLayout(act);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                int p = Ui.dp(act, 4);
                cell.setPadding(p, p, p, p);
                icon = new ImageView(act);
                cell.addView(icon, new LinearLayout.LayoutParams(Ui.dp(act, 56), Ui.dp(act, 56)));
                label = Ui.text(act, "", 12, 0xE6FFFFFF, Ui.MEDIUM);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = Ui.dp(act, 8);
                cell.addView(label, lp);
                cell.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            } else {
                cell = (LinearLayout) convert;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            }
            App x = shown.get(i);
            label.setText(x.label);
            if (x.work()) {
                // Sin recortar, para que no se corte el maletín de la esquina
                icon.setImageDrawable(x.badged);
                icon.setClipToOutline(false);
                icon.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            } else {
                // Si el ícono no está listo no se genera aquí (trabaría el scroll): se pide en segundo plano
                Drawable d = iconCache.get(x.pkg);
                if (d == null && cache != null && Prefs.iconStyle(act) == iconStyleCached) {
                    warmIcons(act);
                    icon.setImageDrawable(null);
                } else {
                    icon.setImageDrawable(d != null ? d : icon(act, x.pkg));
                }
                IconEngine.clip(icon, x.pkg);
            }
            // En pausa se ven apagadas
            cell.setAlpha(x.work() && !x.priv && workPaused ? 0.45f : 1f);
            return cell;
        }
    }
}
