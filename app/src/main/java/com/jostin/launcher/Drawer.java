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
    private static final Map<String, Drawable> iconCache = new HashMap<>();
    private static int iconStyleCached = -1;
    private static final Handler main = new Handler(Looper.getMainLooper());

    static void invalidateApps() {
        cache = null;
        workCache = new ArrayList<>();
        iconCache.clear();
    }

    static void trim() {
        iconCache.clear();
    }

    static void apps(Context c, Loaded l) {
        if (cache != null) {
            l.loaded(cache);
            return;
        }
        Context app = c.getApplicationContext();
        new Thread(() -> {
            PackageManager pm = app.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            List<App> out = new ArrayList<>();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(app.getPackageName()) || !seen.add(pkg)) continue;
                out.add(new App(String.valueOf(ri.loadLabel(pm)), pkg));
            }
            Collator col = Collator.getInstance(MainActivity.ES);
            Collections.sort(out, (x, y) -> col.compare(x.label, y.label));
            // Los íconos se preparan aquí, fuera del hilo principal, para que el cajón no se trabe
            int style = Prefs.iconStyle(app);
            Map<String, Drawable> pre = new HashMap<>();
            for (App x : out) {
                Drawable d = IconEngine.icon(app, x.pkg);
                if (d != null) pre.put(x.pkg, d);
            }
            List<App> work = WorkApps.load(app);
            main.post(() -> {
                if (iconStyleCached == style || iconStyleCached == -1) {
                    iconStyleCached = style;
                    for (Map.Entry<String, Drawable> en : pre.entrySet()) {
                        if (!iconCache.containsKey(en.getKey())) iconCache.put(en.getKey(), en.getValue());
                    }
                }
                cache = out;
                workCache = work;
                l.loaded(out);
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
    private final List<App> work = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private final Segmented tabs;
    private final View workRow;
    private final TextView workText;
    private boolean workPaused = false;
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

        empty = Ui.text(a, "", 15, Ui.MUTED, Ui.REGULAR);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(a, 30), 0, Ui.dp(a, 10));
        empty.setVisibility(GONE);
        column.addView(empty, new LinearLayout.LayoutParams(-1, -2));

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
            filter(animate);
        });
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
        if (q.isEmpty()) {
            shown.addAll(workTab ? work : all);
        } else {
            // Al buscar salen las dos; la de trabajo se distingue por su maletín
            for (App x : all) if (x.key.contains(q)) shown.add(x);
            for (App x : work) if (x.key.contains(q)) shown.add(x);
        }
        boolean none = shown.isEmpty() && !q.isEmpty() && (!all.isEmpty() || !work.isEmpty());
        boolean noWork = workTab && q.isEmpty() && work.isEmpty();
        empty.setVisibility(none || noWork ? VISIBLE : GONE);
        empty.setText(noWork ? (workPaused ? "Tus apps de trabajo están en pausa" : "No hay apps de trabajo") : "No encontré esa app");
        webRow.setVisibility(none && !pick ? VISIBLE : GONE);
        webText.setText("Buscar «" + search.getText().toString().trim() + "» en internet");
        workRow.setVisibility(workTab && q.isEmpty() ? VISIBLE : GONE);
        workText.setText(workPaused ? "Reanudar apps de trabajo" : "Pausar apps de trabajo");
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
            final App app = x;
            LinearLayout cell = new LinearLayout(act);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView icon = new ImageView(act);
            icon.setImageDrawable(icon(act, pkg));
            IconEngine.clip(icon, pkg);
            cell.addView(icon, new LinearLayout.LayoutParams(Ui.dp(act, 56), Ui.dp(act, 56)));
            TextView label = Ui.text(act, app.label, 12, Theme.accent, Ui.MEDIUM);
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
            suggestRow.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        for (int k = suggestRow.getChildCount(); k < 4; k++) suggestRow.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1f));
        suggestRow.setVisibility(VISIBLE);
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
        if (x.work() && workPaused) {
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
                icon.setImageDrawable(icon(act, x.pkg));
                IconEngine.clip(icon, x.pkg);
            }
            // En pausa se ven apagadas
            cell.setAlpha(x.work() && workPaused ? 0.45f : 1f);
            return cell;
        }
    }
}
