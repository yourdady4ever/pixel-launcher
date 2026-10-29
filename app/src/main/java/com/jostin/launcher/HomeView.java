package com.jostin.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pantalla de inicio por páginas. Todo lo que hay (widgets, apps, carpetas, widgets de otras apps)
 * vive en una cuadrícula y se puede mover, cambiar de tamaño, girar (los decorativos), poner por
 * capas y deshacer. Mantén presionado para editar.
 */
class HomeView extends FrameLayout {
    static final String[] TRANSITIONS = {"Deslizar", "Desvanecer", "Zoom", "Cubo", "Voltear", "Pila", "Profundidad", "Paralaje"};

    final MainActivity act;
    final HomeModel model;
    private final Pager pager;
    private final FrameLayout layer;
    private final Dots dots;
    private LinearLayout editBar;
    private final View ghost;
    private final Frame frame;
    private final List<HomeGrid> grids = new ArrayList<>();
    private final Map<String, View> views = new HashMap<>();
    private final GestureDetector taps;
    private final int slop;
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    boolean editing = false, started = false;
    int page = 0;
    private int top = 0, reserve = 0, barBottom = 0;
    int cols = 4, rows = 6, side, gap, gridTop, gridH;
    float cellW = 1, cellH = 1;
    private boolean ready = false, enteredByLong = false, framed = false;

    private static final int NONE = 0, PAGE = 1, VERT = 2, DRAG = 3, RESIZE = 4, HOLD = 5, ROTATE = 6;
    private int mode = NONE, frameTap = 0;
    private float downX, downY, lastX, lastY, pageStart, rotStart, rotBase;
    private VelocityTracker vt;
    private View downView;
    private boolean longFired, pendingMenu, pass, moved;
    private long handledDown = -1;

    private HomeModel.Item dragItem;
    private View dragView;
    private int origPage, edgeDir;
    private float origX, origY, tx, ty, offX, offY;
    private boolean dropValid, dropMerge;
    private long edgeSince;

    HomeModel.Item selected;
    private ValueAnimator pageAnim;

    HomeView(MainActivity a, Backdrop b) {
        super(a);
        act = a;
        model = HomeModel.load(a);
        model.free = Prefs.homeFree(a);
        slop = ViewConfiguration.get(a).getScaledTouchSlop();
        setClipChildren(false);
        pager = new Pager(a);
        addView(pager, new LayoutParams(-1, 0));
        dots = new Dots(a);
        addView(dots, new LayoutParams(-1, Ui.dp(a, 26)));
        layer = new FrameLayout(a);
        layer.setClipChildren(false);
        addView(layer, new LayoutParams(-1, -1));
        ghost = new View(a);
        ghost.setVisibility(GONE);
        layer.addView(ghost, new FrameLayout.LayoutParams(0, 0));
        frame = new Frame(a);
        frame.setVisibility(GONE);
        layer.addView(frame, new FrameLayout.LayoutParams(-1, -1));
        taps = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!editing) act.onHomeDoubleTap();
                return true;
            }
        });
        taps.setIsLongpressEnabled(false);
    }

    // ==================== Geometría ====================

    void setFrame(int top, int reserve, int barBottom) {
        this.top = top;
        this.reserve = reserve;
        this.barBottom = barBottom;
        framed = true;
        if (editBar != null) {
            LayoutParams lp = (LayoutParams) editBar.getLayoutParams();
            lp.bottomMargin = barBottom;
            editBar.setLayoutParams(lp);
        }
        layoutGeometry();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        post(this::layoutGeometry);
    }

    private void layoutGeometry() {
        int W = getWidth(), H = getHeight();
        if (W == 0 || H == 0 || !framed) return;
        side = Ui.dp(act, 10);
        gap = Ui.dp(act, 5);
        gridTop = top + Ui.dp(act, 6);
        gridH = Math.max(Ui.dp(act, 240), H - gridTop - reserve - Ui.dp(act, 26));
        cellW = (W - 2f * side) / cols;
        int r = Math.max(4, Math.min(8, Math.round(gridH / (cellW * 1.08f))));
        cellH = gridH / (float) r;
        rows = r;
        LayoutParams lp = (LayoutParams) pager.getLayoutParams();
        lp.height = gridH;
        lp.topMargin = gridTop;
        pager.setLayoutParams(lp);
        LayoutParams dl = (LayoutParams) dots.getLayoutParams();
        dl.topMargin = gridTop + gridH;
        dots.setLayoutParams(dl);
        boolean first = !ready;
        ready = true;
        if (model.fresh || r != model.rows) {
            model.rows = r;
            model.cols = cols;
            if (model.fresh) model.buildDefault(act);
            else model.normalize();
            model.save(act);
            populate();
        } else if (first) {
            model.cols = cols;
            populate();
        } else {
            for (HomeGrid g : grids) g.requestLayout();
        }
    }

    Rect cellRect(float x, float y, float w, float h) {
        return new Rect(Math.round(side + x * cellW + gap), Math.round(y * cellH + gap),
                Math.round(side + (x + w) * cellW - gap), Math.round((y + h) * cellH - gap));
    }

    private float gridLeft(int p) {
        return p * getWidth() - pager.getScrollX();
    }

    private float snap(float v) {
        return model.free ? Math.round(v * 4f) / 4f : Math.round(v);
    }

    // ==================== Vistas ====================

    void populate() {
        for (View v : views.values()) {
            ViewGroup p = (ViewGroup) v.getParent();
            if (p != null) p.removeView(v);
            if (v instanceof Gadgets.Gadget) ((Gadgets.Gadget) v).stop();
        }
        views.clear();
        pager.removeAllViews();
        grids.clear();
        ensureGrids();
        for (HomeModel.Item it : new ArrayList<>(model.items)) addItemView(it);
        page = Math.min(page, model.pages - 1);
        goTo(page, false);
        dots.invalidate();
    }

    void ensureGrids() {
        while (grids.size() < model.pages) {
            HomeGrid g = new HomeGrid(act, this);
            grids.add(g);
            pager.addView(g);
        }
        while (grids.size() > model.pages) {
            HomeGrid g = grids.remove(grids.size() - 1);
            pager.removeView(g);
        }
    }

    View addItemView(HomeModel.Item it) {
        View v = act.createItemView(it);
        if (v == null) {
            model.items.remove(it);
            return null;
        }
        views.put(it.id, v);
        ensureGrids();
        while (grids.size() <= it.page) {
            model.pages = Math.max(model.pages, it.page + 1);
            ensureGrids();
        }
        applyLook(it, v);
        grids.get(it.page).addView(v, new HomeGrid.LP(it));
        if (started && v instanceof Gadgets.Gadget) ((Gadgets.Gadget) v).start();
        if (v instanceof Gadgets.AppIcon) ((Gadgets.AppIcon) v).setBadge(badges.contains(((Gadgets.AppIcon) v).pkg));
        else if (v instanceof Gadgets.FolderIcon) ((Gadgets.FolderIcon) v).setBadges(badges);
        return v;
    }

    private void applyLook(HomeModel.Item it, View v) {
        v.setAlpha(it.alpha);
        v.setRotation(Gadgets.decor(it.type) ? it.rot : 0);
    }

    View viewOf(HomeModel.Item it) {
        return views.get(it.id);
    }

    /** Vuelve a construir la vista de un elemento (después de cambiar sus datos). */
    void refreshItem(HomeModel.Item it) {
        View v = views.get(it.id);
        if (v instanceof Gadgets.Gadget) {
            ((Gadgets.Gadget) v).applyBg();
            ((Gadgets.Gadget) v).rebuild();
            applyLook(it, v);
            v.invalidate();
        } else {
            replaceView(it);
        }
    }

    void replaceView(HomeModel.Item it) {
        View old = views.remove(it.id);
        if (old != null) {
            ViewGroup p = (ViewGroup) old.getParent();
            if (p != null) p.removeView(old);
            if (old instanceof Gadgets.Gadget) ((Gadgets.Gadget) old).stop();
        }
        addItemView(it);
    }

    void rebuildAll() {
        for (HomeModel.Item it : new ArrayList<>(model.items)) {
            View v = views.get(it.id);
            if (v instanceof Gadgets.Gadget) {
                ((Gadgets.Gadget) v).applyBg();
                ((Gadgets.Gadget) v).rebuild();
            } else if (v instanceof Gadgets.AppIcon) {
                replaceView(it);
            } else if (v instanceof Gadgets.FolderIcon) {
                replaceView(it);
            }
        }
        dots.invalidate();
        if (editBar != null && editing) buildEditBar(true);
    }

    void forEachGadget(Consumer<Gadgets.Gadget> f) {
        for (View v : views.values()) if (v instanceof Gadgets.Gadget) f.accept((Gadgets.Gadget) v);
    }

    void tick() {
        forEachGadget(Gadgets.Gadget::tick);
    }

    private Set<String> badges = new java.util.HashSet<>();

    void onMedia(Set<String> badges) {
        this.badges = badges;
        forEachGadget(Gadgets.Gadget::onMedia);
        for (View v : views.values()) {
            if (v instanceof Gadgets.AppIcon) ((Gadgets.AppIcon) v).setBadge(badges.contains(((Gadgets.AppIcon) v).pkg));
            else if (v instanceof Gadgets.FolderIcon) ((Gadgets.FolderIcon) v).setBadges(badges);
        }
    }

    void start() {
        started = true;
        forEachGadget(Gadgets.Gadget::start);
    }

    void stop() {
        started = false;
        forEachGadget(Gadgets.Gadget::stop);
    }

    void saveModel() {
        model.save(act);
    }

    void playEntrance() {
        if (page >= grids.size()) return;
        HomeGrid g = grids.get(page);
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof Gadgets.ClockG) {
                ClockView cv = ((Gadgets.ClockG) v).cv;
                if (cv != null) cv.playEntrance();
                continue;
            }
            HomeModel.Item it = ((HomeGrid.LP) v.getLayoutParams()).item;
            v.setAlpha(0f);
            v.setTranslationY(30 * d);
            v.animate().alpha(it.alpha).translationY(0).setStartDelay(100 + Math.round(it.y * 50)).setDuration(500)
                    .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        }
    }

    // ==================== Agregar y quitar ====================

    /** Pone un elemento nuevo donde quepa (empieza por la página actual) y lo devuelve. */
    HomeModel.Item place(String type, float w, float h, Consumer<HomeModel.Item> init) {
        snapshot();
        w = Math.min(w, cols);
        h = Math.min(h, rows);
        int p = page;
        int[] s = model.spot(p, w, h);
        for (int i = 0; s == null && i < model.pages; i++) {
            s = model.spot(i, w, h);
            if (s != null) p = i;
        }
        if (s == null && model.pages < HomeModel.MAX_PAGES) {
            p = model.pages;
            model.pages++;
            ensureGrids();
            s = new int[]{0, 0};
        }
        if (s == null && model.free) s = new int[]{0, 0};
        if (s == null) {
            undo.poll();
            return null;
        }
        HomeModel.Item it = model.add(type, p, s[0], s[1], w, h);
        if (init != null) init.accept(it);
        View v = addItemView(it);
        model.save(act);
        dots.invalidate();
        goTo(p, true);
        if (v != null && Prefs.motion(act) < 3) {
            v.setScaleX(0.6f);
            v.setScaleY(0.6f);
            v.setAlpha(0f);
            v.animate().scaleX(1f).scaleY(1f).alpha(it.alpha).setDuration(380).setInterpolator(new OvershootInterpolator(1.6f)).start();
        }
        if (editing && resizable(it)) post(() -> select(it));
        return it;
    }

    void removeItem(HomeModel.Item it) {
        snapshot();
        View v = views.remove(it.id);
        if (it == selected) select(null);
        model.items.remove(it);
        if (v != null) {
            v.animate().scaleX(0.6f).scaleY(0.6f).alpha(0f).setDuration(180).withEndAction(() -> {
                ViewGroup p = (ViewGroup) v.getParent();
                if (p != null) p.removeView(v);
                if (v instanceof Gadgets.Gadget) ((Gadgets.Gadget) v).stop();
            }).start();
        }
        model.save(act);
        Sound.play(this, Sound.BACK);
        // Fuera de edición no hay deshacer, así que el widget de otra app se libera de una vez
        if (!editing && it.type.equals("aw")) act.purgeWidgets(model);
    }

    void duplicate(HomeModel.Item src) {
        if (src.type.equals("aw")) {
            Sheet.pill(act, act.backdrop(), "Los widgets de otras apps se agregan desde Widgets");
            return;
        }
        snapshot();
        HomeModel.Item it = model.copy(src);
        if (model.free) {
            it.x = Math.min(cols - it.w, it.x + 0.5f);
            it.y = Math.min(rows - it.h, it.y + 0.5f);
        } else {
            int[] s = model.spot(src.page, it.w, it.h);
            if (s == null) {
                model.items.remove(it);
                undo.poll();
                Sheet.pill(act, act.backdrop(), "No hay espacio en esta página");
                return;
            }
            it.x = s[0];
            it.y = s[1];
        }
        if (src.type.equals("foto")) {
            java.io.File f = Gadgets.PhotoG.file(act, src.id);
            if (f.exists()) {
                try {
                    java.nio.file.Files.copy(f.toPath(), Gadgets.PhotoG.file(act, it.id).toPath());
                } catch (Exception ignored) {
                    // se queda sin foto
                }
            }
        }
        addItemView(it);
        model.save(act);
        Sound.play(this, Sound.CHECK);
        if (editing && resizable(it)) select(it);
    }

    void bringToFront(HomeModel.Item it, boolean front) {
        snapshot();
        it.z = front ? model.maxZ(it.page) + 1 : model.minZ(it.page) - 1;
        if (it.page < grids.size()) grids.get(it.page).orderChanged();
        model.save(act);
    }

    void setAlphaOf(HomeModel.Item it, float a) {
        it.alpha = a;
        View v = views.get(it.id);
        if (v != null) v.setAlpha(a);
    }

    void setRotationOf(HomeModel.Item it, float r) {
        it.rot = r;
        View v = views.get(it.id);
        if (v != null) v.setRotation(r);
        if (it == selected) updateFrame();
    }

    // ==================== Deshacer ====================

    /** Guarda cómo estaba todo antes de un cambio. */
    void snapshot() {
        undo.push(model.toJson());
        while (undo.size() > 40) undo.removeLast();
        redo.clear();
        updateUndoButtons();
    }

    void undo() {
        if (undo.isEmpty()) return;
        redo.push(model.toJson());
        restore(undo.pop());
        Sound.play(this, Sound.BACK);
    }

    void redo() {
        if (redo.isEmpty()) return;
        undo.push(model.toJson());
        restore(redo.pop());
        Sound.play(this, Sound.TAP);
    }

    void restore(String json) {
        select(null);
        if (model.fromJson(json)) {
            model.free = Prefs.homeFree(act);
            model.normalize();
            model.save(act);
            populate();
        }
        updateUndoButtons();
    }

    // ==================== Modo edición ====================

    void setEditing(boolean on) {
        if (editing == on) return;
        editing = on;
        select(null);
        act.onEditMode(on);
        for (HomeGrid g : grids) g.invalidate();
        if (on) {
            buildEditBar(false);
            editBar.setAlpha(0f);
            editBar.setTranslationY(Ui.dp(act, 40));
            editBar.animate().alpha(1f).translationY(0).setDuration(300).setInterpolator(new DecelerateInterpolator(2f)).start();
            Sound.play(this, Sound.OPEN);
        } else {
            enteredByLong = false;
            if (editBar != null) {
                LinearLayout bar = editBar;
                bar.animate().alpha(0f).translationY(Ui.dp(act, 40)).setDuration(220).withEndAction(() -> bar.setVisibility(GONE)).start();
            }
            int before = model.pages;
            model.trimPages();
            if (model.pages != before) populate();
            act.purgeWidgets(model);
            model.save(act);
            undo.clear();
            redo.clear();
        }
        act.backdrop().setScreenDim(on ? 0.18f : 0f);
        dots.invalidate();
    }

    private ImageView undoBtn, redoBtn;

    private void buildEditBar(boolean keepVisible) {
        if (editBar != null) removeView(editBar);
        LinearLayout bar = new LinearLayout(act);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(act, 6);
        bar.setPadding(p, p, p, p);
        Glass.apply(bar, act.backdrop(), 30, Glass.TINT_STRONG);
        bar.addView(barButton(Icons.SHAPES, "Widgets", v -> act.openWidgetPicker()));
        bar.addView(barButton(Icons.PLUS, "Apps", v -> act.pickAppForHome()));
        undoBtn = barIcon(Icons.HISTORY, false, v -> undo());
        bar.addView(undoBtn);
        redoBtn = barIcon(Icons.HISTORY, true, v -> redo());
        bar.addView(redoBtn);
        bar.addView(barButton(Icons.GEAR, "Estilo", v -> act.studioFromEdit()));
        TextView done = Sheet.button(act, "Listo", Theme.accent, Theme.onAccent);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(act, 72), Ui.dp(act, 46));
        dl.leftMargin = Ui.dp(act, 4);
        bar.addView(done, dl);
        done.setOnClickListener(v -> setEditing(false));
        LayoutParams lp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = barBottom;
        addView(bar, lp);
        editBar = bar;
        bar.setVisibility(VISIBLE);
        if (keepVisible) bar.setAlpha(1f);
        updateUndoButtons();
    }

    private View barButton(Icons.Shape icon, String label, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(act);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(act);
        iv.setImageDrawable(Icons.of(icon));
        b.addView(iv, new LinearLayout.LayoutParams(Ui.dp(act, 20), Ui.dp(act, 20)));
        TextView t = Ui.text(act, label, 10, Ui.TEXT, Ui.MEDIUM);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = Ui.dp(act, 4);
        b.addView(t, tl);
        Ui.pressable(b, 0.9f);
        b.setOnClickListener(v -> {
            Sound.play(v, Sound.TAP);
            l.onClick(v);
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(act, 58), Ui.dp(act, 46)));
        return b;
    }

    private ImageView barIcon(Icons.Shape icon, boolean mirror, View.OnClickListener l) {
        ImageView iv = new ImageView(act);
        iv.setImageDrawable(Icons.of(icon));
        int p = Ui.dp(act, 12);
        iv.setPadding(p, p, p, p);
        if (mirror) iv.setScaleX(-1f);
        iv.setOnClickListener(l);
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(act, 44), Ui.dp(act, 46)));
        return iv;
    }

    private void updateUndoButtons() {
        if (undoBtn != null) undoBtn.setAlpha(undo.isEmpty() ? 0.3f : 1f);
        if (redoBtn != null) redoBtn.setAlpha(redo.isEmpty() ? 0.3f : 1f);
    }

    boolean resizable(HomeModel.Item it) {
        if (it.type.equals("app") || it.type.equals("folder")) return false;
        if (it.type.equals("aw")) return AppWidgets.resizable(act, it.num("id", -1));
        return true;
    }

    void select(HomeModel.Item it) {
        selected = it;
        if (it == null) {
            frame.setVisibility(GONE);
            return;
        }
        if (it.page != page) goTo(it.page, true);
        updateFrame();
        frame.setVisibility(VISIBLE);
        frame.setAlpha(0f);
        frame.animate().alpha(1f).setDuration(160).start();
    }

    void updateFrame() {
        HomeModel.Item it = selected;
        if (it == null) return;
        Rect r = cellRect(it.x, it.y, it.w, it.h);
        float gl = gridLeft(it.page);
        frame.place(new RectF(gl + r.left, gridTop + r.top, gl + r.right, gridTop + r.bottom),
                !it.locked && resizable(it), !it.locked && Gadgets.decor(it.type), it.locked);
    }

    // ==================== Toques ====================

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return process(e, true);
    }

    /** Si algo de adentro (una lista dentro de un widget) se queda con el dedo, no hay pulsación larga. */
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallow) {
        if (disallow && mode == NONE) removeCallbacks(longPress);
        super.requestDisallowInterceptTouchEvent(disallow);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && e.getEventTime() == handledDown) return true;
        process(e, false);
        return true;
    }

    private final Runnable longPress = () -> {
        if (mode != NONE) return;
        longFired = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        HomeModel.Item it = itemOf(downView);
        if (it != null) {
            if (Prefs.homeLocked(getContext()) || it.locked) {
                mode = HOLD;
                Sound.play(this, Sound.HOLD);
                HomeView.this.act.itemMenu(it);
                return;
            }
            // Se levanta el elemento; si se mueve entra a editar, si se suelta sin moverlo salen sus opciones
            enteredByLong = !editing;
            startDrag(it, downView);
            pendingMenu = true;
            mode = DRAG;
        } else if (!editing) {
            mode = HOLD;
            HomeView.this.act.onHomeLongPress();
        }
    };

    private static HomeModel.Item itemOf(View v) {
        if (v == null || !(v.getLayoutParams() instanceof HomeGrid.LP)) return null;
        return ((HomeGrid.LP) v.getLayoutParams()).item;
    }

    private boolean hit(View v, float x, float y) {
        return v != null && v.getVisibility() == VISIBLE && x >= v.getX() && x < v.getX() + v.getWidth()
                && y >= v.getY() && y < v.getY() + v.getHeight();
    }

    private View viewAt(float x, float y) {
        if (page >= grids.size()) return null;
        HomeGrid g = grids.get(page);
        float gx = x - gridLeft(page), gy = y - gridTop;
        List<View> order = g.ordered();
        for (int i = order.size() - 1; i >= 0; i--) {
            View c = order.get(i);
            if (gx >= c.getLeft() && gx < c.getRight() && gy >= c.getTop() && gy < c.getBottom()) return c;
        }
        return null;
    }

    private boolean process(MotionEvent e, boolean intercept) {
        int a = e.getActionMasked();
        float x = e.getX(), y = e.getY();
        if (a == MotionEvent.ACTION_DOWN) {
            handledDown = e.getEventTime();
            removeCallbacks(longPress);
            if (dragView != null) drop(true);
            mode = NONE;
            frameTap = 0;
            longFired = false;
            pendingMenu = false;
            moved = false;
            downX = lastX = x;
            downY = lastY = y;
            if (vt != null) vt.recycle();
            vt = VelocityTracker.obtain();
            vt.addMovement(e);
            pass = hit(editBar, x, y) || (hit(dots, x, y) && (model.pages > 1 || editing));
            if (pass) return false;
            if (editing && selected != null) {
                int f = frame.hitTest(x, y);
                if (f == Frame.HANDLE) {
                    snapshot();
                    mode = RESIZE;
                    return true;
                }
                if (f == Frame.ROTATE) {
                    snapshot();
                    mode = ROTATE;
                    rotBase = selected.rot;
                    rotStart = frame.angleTo(x, y);
                    return true;
                }
                if (f != 0) {
                    frameTap = f;
                    mode = HOLD;
                    return true;
                }
            }
            downView = viewAt(x, y);
            if (!editing && downView == null) taps.onTouchEvent(e);
            if (downView != null || !editing) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
            return editing;
        }
        if (pass) return false;
        if (vt != null) vt.addMovement(e);
        if (!editing && downView == null && !intercept) taps.onTouchEvent(e);
        float dx = x - downX, dy = y - downY;
        if (a == MotionEvent.ACTION_MOVE) {
            lastX = x;
            lastY = y;
            switch (mode) {
                case DRAG:
                    if (Math.hypot(dx, dy) > slop && !moved) {
                        pendingMenu = false;
                        moved = true;
                        if (!editing) setEditing(true);
                    }
                    moveDrag(x, y);
                    return true;
                case RESIZE:
                    moveResize(x, y);
                    return true;
                case ROTATE:
                    moveRotate(x, y);
                    return true;
                case PAGE:
                    dragPage(dx);
                    return true;
                case VERT:
                    act.vMove(dy);
                    return true;
                case HOLD:
                    return true;
                default:
                    break;
            }
            float vslop = downView instanceof AppWidgets.Frame ? slop * 2.5f : slop;
            if (Math.abs(dx) > slop || Math.abs(dy) > vslop) {
                removeCallbacks(longPress);
                HomeModel.Item it = itemOf(downView);
                if (editing && it != null && !it.locked) {
                    startDrag(it, downView);
                    pendingMenu = false;
                    moved = true;
                    mode = DRAG;
                    moveDrag(x, y);
                    return true;
                }
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (model.pages > 1 || editing) {
                        mode = PAGE;
                        stopPageAnim();
                        pageStart = pager.getScrollX();
                        return true;
                    }
                    return false;
                }
                if (!editing && Math.abs(dy) > vslop) {
                    mode = VERT;
                    act.vStart(dy < 0);
                    act.vMove(dy);
                    return true;
                }
            }
            return false;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPress);
            boolean consumed = mode != NONE || longFired || editing;
            float vx = 0, vy = 0;
            if (vt != null) {
                vt.computeCurrentVelocity(1000);
                vx = vt.getXVelocity();
                vy = vt.getYVelocity();
            }
            switch (mode) {
                case DRAG:
                    drop(a == MotionEvent.ACTION_CANCEL);
                    break;
                case RESIZE:
                case ROTATE:
                    model.save(act);
                    break;
                case PAGE:
                    settlePage(vx);
                    break;
                case VERT:
                    act.vEnd(dy, vy);
                    break;
                case HOLD:
                    if (frameTap != 0 && a == MotionEvent.ACTION_UP) frameAction(frameTap);
                    break;
                default:
                    if (a == MotionEvent.ACTION_UP && editing && !longFired) {
                        HomeModel.Item it = itemOf(downView);
                        if (it != null) tapInEdit(it);
                        else if (selected != null) select(null);
                        else setEditing(false);
                    }
                    break;
            }
            mode = NONE;
            return consumed;
        }
        return mode != NONE;
    }

    private void tapInEdit(HomeModel.Item it) {
        Sound.play(this, Sound.TAP);
        if (resizable(it) || Gadgets.decor(it.type) || it.locked) {
            if (selected == it) act.itemMenu(it);
            else select(it);
        } else {
            act.itemMenu(it);
        }
    }

    private void frameAction(int f) {
        HomeModel.Item it = selected;
        if (it == null) return;
        if (f == Frame.CLOSE) {
            if (it.locked) Sheet.pill(act, act.backdrop(), "Está fijo. Quítale el candado en sus opciones.");
            else removeItem(it);
        } else if (f == Frame.MENU) {
            act.itemMenu(it);
        }
    }

    // ==================== Arrastrar ====================

    private void startDrag(HomeModel.Item it, View v) {
        select(null);
        snapshot();
        HomeGrid g = grids.get(it.page);
        float vx = gridLeft(it.page) + v.getLeft(), vy = gridTop + v.getTop();
        int w = v.getWidth(), h = v.getHeight();
        g.removeView(v);
        layer.addView(v, new FrameLayout.LayoutParams(w, h));
        v.setTranslationX(vx);
        v.setTranslationY(vy);
        offX = lastX - vx;
        offY = lastY - vy;
        v.animate().scaleX(1.05f).scaleY(1.05f).alpha(Math.min(it.alpha, 0.92f)).setDuration(150).start();
        dragItem = it;
        dragView = v;
        origPage = it.page;
        origX = it.x;
        origY = it.y;
        tx = it.x;
        ty = it.y;
        edgeDir = 0;
        Sound.play(this, Sound.HOLD);
        postDelayed(edgeTick, 120);
    }

    private void moveDrag(float x, float y) {
        if (dragView == null) return;
        float left = x - offX, topY = y - offY;
        dragView.setTranslationX(left);
        dragView.setTranslationY(topY);
        HomeModel.Item it = dragItem;
        float cx = snap((left - side - gap) / cellW), cy = snap((topY - gridTop - gap) / cellH);
        cx = Math.max(0, Math.min(cols - it.w, cx));
        cy = Math.max(0, Math.min(rows - it.h, cy));
        tx = cx;
        ty = cy;
        HomeModel.Item occ = model.free ? null : model.cellAt(page, cx, cy, it);
        dropMerge = it.type.equals("app") && occ != null && (occ.type.equals("app") || occ.type.equals("folder"));
        dropValid = dropMerge || model.fits(page, cx, cy, it.w, it.h, it);
        Rect r = cellRect(cx, cy, it.w, it.h);
        float gl = gridLeft(page);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) ghost.getLayoutParams();
        if (lp.width != r.width() || lp.height != r.height()) {
            lp.width = r.width();
            lp.height = r.height();
            ghost.setLayoutParams(lp);
        }
        ghost.setTranslationX(gl + r.left);
        ghost.setTranslationY(gridTop + r.top);
        int c = dropValid ? Theme.accent : 0xFFFF6B6B;
        ghost.setBackground(Ui.outline(act, Ui.alpha(c, dropMerge ? 0x55 : 0x26), Ui.alpha(c, 0xCC), dropMerge ? 30 : 22));
        ghost.setVisibility(VISIBLE);
    }

    private final Runnable edgeTick = new Runnable() {
        @Override
        public void run() {
            if (mode != DRAG || dragView == null) return;
            int edge = Ui.dp(act, 24);
            int dir = lastX < edge ? -1 : lastX > getWidth() - edge ? 1 : 0;
            long now = SystemClock.uptimeMillis();
            if (dir == 0 || dir != edgeDir) {
                edgeDir = dir;
                edgeSince = now;
            } else if (now - edgeSince > 520) {
                int target = page + dir;
                if (target >= 0) {
                    if (target >= model.pages && model.pages < HomeModel.MAX_PAGES) {
                        model.pages++;
                        ensureGrids();
                        dots.invalidate();
                    }
                    if (target < model.pages) {
                        goTo(target, true);
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        edgeSince = now + 500;
                        postDelayed(() -> moveDrag(lastX, lastY), 320);
                    }
                }
            }
            postDelayed(this, 120);
        }
    };

    private void drop(boolean cancelled) {
        removeCallbacks(edgeTick);
        HomeModel.Item it = dragItem;
        View v = dragView;
        dragItem = null;
        dragView = null;
        ghost.setVisibility(GONE);
        if (it == null || v == null) return;
        boolean showMenu = pendingMenu && !moved && !cancelled;
        if (!cancelled && !showMenu && dropMerge) {
            HomeModel.Item occ = model.cellAt(page, tx, ty, it);
            if (occ != null) {
                merge(it, v, occ);
                return;
            }
        }
        boolean ok = !cancelled && !showMenu && dropValid;
        if (ok) {
            it.page = page;
            it.x = tx;
            it.y = ty;
            if (model.free) it.z = model.maxZ(page) + 1;
        } else {
            it.page = origPage;
            it.x = origX;
            it.y = origY;
            if (!showMenu) undo.poll();
        }
        if (showMenu) undo.poll();
        Rect r = cellRect(it.x, it.y, it.w, it.h);
        float toX = gridLeft(it.page) + r.left, toY = gridTop + r.top;
        v.animate().translationX(toX).translationY(toY).scaleX(1f).scaleY(1f).alpha(it.alpha).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(2f)).withEndAction(() -> {
                    layer.removeView(v);
                    v.setTranslationX(0);
                    v.setTranslationY(0);
                    ensureGrids();
                    grids.get(it.page).addView(v, new HomeGrid.LP(it));
                    if (it == selected) updateFrame();
                }).start();
        model.save(act);
        updateUndoButtons();
        if (ok) Sound.play(this, Sound.TAP);
        if (showMenu) act.itemMenu(it);
        enteredByLong = false;
    }

    private void merge(HomeModel.Item it, View v, HomeModel.Item occ) {
        layer.removeView(v);
        views.remove(it.id);
        model.items.remove(it);
        String pkg = it.str("pkg", "");
        if (occ.type.equals("app")) {
            String first = occ.str("pkg", "");
            occ.type = "folder";
            occ.data = new org.json.JSONObject();
            occ.put("name", "Carpeta");
            JSONArray arr = new JSONArray();
            arr.put(first);
            arr.put(pkg);
            occ.put("apps", arr);
        } else {
            JSONArray arr = occ.apps();
            boolean has = false;
            for (int i = 0; i < arr.length(); i++) if (pkg.equals(arr.optString(i))) has = true;
            if (!has) arr.put(pkg);
        }
        replaceView(occ);
        model.save(act);
        updateUndoButtons();
        Sound.play(this, Sound.CHECK);
        Sheet.pill(act, act.backdrop(), occ.str("name", "Carpeta") + ": " + occ.apps().length() + " apps");
    }

    // ==================== Tamaño y giro ====================

    private void moveResize(float x, float y) {
        HomeModel.Item it = selected;
        if (it == null) return;
        float gl = gridLeft(it.page);
        float[] min = act.minSpan(it);
        float nw = snap((x - gl - side) / cellW - it.x), nh = snap((y - gridTop) / cellH - it.y);
        nw = Math.max(min[0], Math.min(cols - it.x, nw));
        nh = Math.max(min[1], Math.min(rows - it.y, nh));
        if ((Math.abs(nw - it.w) > 0.01f || Math.abs(nh - it.h) > 0.01f) && model.fits(it.page, it.x, it.y, nw, nh, it)) {
            it.w = nw;
            it.h = nh;
            if (it.page < grids.size()) grids.get(it.page).requestLayout();
            View v = views.get(it.id);
            if (v instanceof Gadgets.Gadget) post(((Gadgets.Gadget) v)::rebuild);
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            updateFrame();
        }
    }

    private void moveRotate(float x, float y) {
        HomeModel.Item it = selected;
        if (it == null) return;
        float r = rotBase + frame.angleTo(x, y) - rotStart;
        r = ((r % 360) + 540) % 360 - 180;
        float snapTo = Math.round(r / 45f) * 45f;
        if (Math.abs(r - snapTo) < 4f) r = snapTo;
        setRotationOf(it, r);
    }

    // ==================== Páginas ====================

    private void dragPage(float dx) {
        float s = pageStart - dx;
        float max = (model.pages - 1) * getWidth();
        if (s < 0) s *= 0.35f;
        else if (s > max) s = max + (s - max) * 0.35f;
        pager.setScrollX(Math.round(s));
        onScrolled();
    }

    private void settlePage(float vx) {
        int W = getWidth();
        float s = pager.getScrollX();
        int target = Math.round(s / W);
        if (vx < -900) target = (int) Math.floor(s / W) + 1;
        else if (vx > 900) target = (int) Math.ceil(s / W) - 1;
        goTo(Math.max(0, Math.min(model.pages - 1, target)), true);
    }

    private void stopPageAnim() {
        if (pageAnim != null) pageAnim.cancel();
        pageAnim = null;
    }

    void goTo(int p, boolean anim) {
        int np = Math.max(0, Math.min(model.pages - 1, p));
        boolean changed = np != page;
        page = np;
        stopPageAnim();
        if (selected != null && selected.page != page) select(null);
        int to = page * getWidth();
        if (!anim || getWidth() == 0) {
            pager.setScrollX(to);
            onScrolled();
            return;
        }
        if (changed) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        pageAnim = ValueAnimator.ofInt(pager.getScrollX(), to);
        pageAnim.setDuration(Math.round(320 * Ui.motion(act)));
        pageAnim.setInterpolator(new DecelerateInterpolator(2f));
        pageAnim.addUpdateListener(v -> {
            pager.setScrollX((int) v.getAnimatedValue());
            onScrolled();
        });
        pageAnim.start();
    }

    void addPage() {
        if (model.pages >= HomeModel.MAX_PAGES) return;
        model.pages++;
        ensureGrids();
        goTo(model.pages - 1, true);
        dots.invalidate();
    }

    private void onScrolled() {
        int W = getWidth();
        if (W == 0) return;
        float sx = pager.getScrollX();
        float f = model.pages > 1 ? sx / ((model.pages - 1f) * W) : 0.5f;
        int anim = Prefs.motion(act) >= 2 ? 0 : Prefs.pageAnim(act);
        act.backdrop().setPageScroll(Math.max(0, Math.min(1, f)), anim == 7 ? 1f : Prefs.motion(act) >= 2 ? 0f : 0.45f);
        for (int i = 0; i < grids.size(); i++) {
            HomeGrid g = grids.get(i);
            float off = (i * W - sx) / W;
            transform(g, off, anim, W);
        }
        dots.invalidate();
        if (selected != null) updateFrame();
    }

    /** Transición entre páginas. off: 0 = a la vista, -1 = a la izquierda, 1 = a la derecha. */
    private void transform(View g, float off, int anim, int W) {
        float a = Math.abs(off);
        g.setTranslationX(0);
        g.setAlpha(1f);
        g.setScaleX(1f);
        g.setScaleY(1f);
        g.setRotationY(0);
        g.setCameraDistance(W * 8f);
        if (a >= 1f || anim == 0 || anim == 7) {
            g.setVisibility(a >= 1.5f ? INVISIBLE : VISIBLE);
            return;
        }
        g.setVisibility(VISIBLE);
        switch (anim) {
            case 1:
                g.setTranslationX(-off * W);
                g.setAlpha(1f - a);
                break;
            case 2:
                g.setTranslationX(-off * W * 0.5f);
                g.setScaleX(1f - 0.3f * a);
                g.setScaleY(1f - 0.3f * a);
                g.setAlpha(1f - a * 0.8f);
                break;
            case 3:
                g.setPivotX(off < 0 ? W : 0);
                g.setPivotY(g.getHeight() / 2f);
                g.setRotationY(-off * 90f);
                break;
            case 4:
                g.setTranslationX(-off * W);
                g.setRotationY(-off * 180f);
                g.setAlpha(a < 0.5f ? 1f : 0f);
                break;
            case 5:
                if (off > 0) {
                    g.setTranslationX(-off * W * 0.85f);
                    g.setScaleX(1f - 0.12f * off);
                    g.setScaleY(1f - 0.12f * off);
                    g.setAlpha(1f - off * 0.6f);
                }
                break;
            case 6:
                if (off > 0) {
                    g.setTranslationX(-off * W);
                    float s = 0.75f + 0.25f * (1f - off);
                    g.setScaleX(s);
                    g.setScaleY(s);
                    g.setAlpha(1f - off);
                }
                break;
            default:
                break;
        }
    }

    void refreshTransitions() {
        onScrolled();
    }

    // ==================== Piezas ====================

    /** Contenedor horizontal de páginas. */
    static final class Pager extends ViewGroup {
        Pager(Context c) {
            super(c);
            setClipChildren(false);
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l, h = b - t;
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(i * w, 0, (i + 1) * w, h);
        }
    }

    /** Una página: acomoda a cada elemento en sus celdas y lo dibuja según su capa. */
    static final class HomeGrid extends ViewGroup {
        static final class LP extends ViewGroup.LayoutParams {
            final HomeModel.Item item;

            LP(HomeModel.Item it) {
                super(0, 0);
                item = it;
            }
        }

        private final HomeView home;
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[] order = new int[0];
        private boolean orderDirty = true;

        HomeGrid(Context c, HomeView h) {
            super(c);
            home = h;
            setWillNotDraw(false);
            setClipChildren(false);
            setChildrenDrawingOrderEnabled(true);
            dot.setColor(0x66FFFFFF);
        }

        void orderChanged() {
            orderDirty = true;
            invalidate();
        }

        @Override
        public void onViewAdded(View child) {
            super.onViewAdded(child);
            orderDirty = true;
        }

        @Override
        public void onViewRemoved(View child) {
            super.onViewRemoved(child);
            orderDirty = true;
        }

        List<View> ordered() {
            List<View> list = new ArrayList<>();
            for (int i = 0; i < getChildCount(); i++) list.add(getChildAt(i));
            Collections.sort(list, (a, b) -> Integer.compare(((LP) a.getLayoutParams()).item.z, ((LP) b.getLayoutParams()).item.z));
            return list;
        }

        @Override
        protected int getChildDrawingOrder(int count, int i) {
            if (orderDirty || order.length != count) {
                List<View> list = ordered();
                order = new int[count];
                for (int k = 0; k < count && k < list.size(); k++) order[k] = indexOfChild(list.get(k));
                orderDirty = false;
            }
            return i < order.length ? order[i] : i;
        }

        @Override
        protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
            return p instanceof LP;
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
            setMeasuredDimension(w, h);
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                HomeModel.Item it = ((LP) c.getLayoutParams()).item;
                Rect r = home.cellRect(it.x, it.y, it.w, it.h);
                c.measure(MeasureSpec.makeMeasureSpec(Math.max(1, r.width()), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.max(1, r.height()), MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                HomeModel.Item it = ((LP) c.getLayoutParams()).item;
                Rect rr = home.cellRect(it.x, it.y, it.w, it.h);
                c.layout(rr.left, rr.top, rr.right, rr.bottom);
            }
            orderDirty = true;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!home.editing) return;
            float rad = Ui.dp(getContext(), 1.6f);
            float step = home.model.free ? 0.5f : 1f;
            for (float y = 0; y <= home.rows + 0.01f; y += step) {
                for (float x = 0; x <= home.cols + 0.01f; x += step) {
                    c.drawCircle(home.side + x * home.cellW, y * home.cellH, rad, dot);
                }
            }
        }
    }

    /** Puntos de página (y un + para agregar una página en modo edición). */
    final class Dots extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Dots(Context c) {
            super(c);
            setClickable(true);
        }

        private int count() {
            return model.pages + (editing && model.pages < HomeModel.MAX_PAGES ? 1 : 0);
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = count();
            if (n <= 1 && !editing) return;
            float sp = Ui.dp(getContext(), 16), r = Ui.dp(getContext(), 3.2f);
            float start = getWidth() / 2f - (n - 1) * sp / 2f, cy = getHeight() / 2f;
            float cur = getWidth() == 0 ? 0 : pager.getScrollX() / (float) Math.max(1, HomeView.this.getWidth());
            int light = Theme.wallLight && !editing ? 0xFF16171A : 0xFFFFFFFF;
            for (int i = 0; i < model.pages; i++) {
                float k = Math.max(0, 1f - Math.abs(cur - i));
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.alpha(light, (int) (90 + 165 * k)));
                c.drawCircle(start + i * sp, cy, r * (1f + 0.35f * k), p);
            }
            if (n > model.pages) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.4f));
                p.setColor(0xCCFFFFFF);
                float x = start + model.pages * sp, s = r * 1.3f;
                c.drawLine(x - s, cy, x + s, cy, p);
                c.drawLine(x, cy - s, x, cy + s, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return model.pages > 1 || editing;
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                int n = count();
                float sp = Ui.dp(getContext(), 16);
                float start = getWidth() / 2f - (n - 1) * sp / 2f;
                int i = Math.round((e.getX() - start) / sp);
                if (i >= 0 && i < model.pages) {
                    Sound.play(this, Sound.TAP);
                    goTo(i, true);
                } else if (i == model.pages && editing) {
                    Sound.play(this, Sound.TAP);
                    addPage();
                }
            }
            return true;
        }
    }

    /** Marco del elemento seleccionado: tamaño, giro, quitar y opciones. */
    static final class Frame extends View {
        static final int HANDLE = 1, CLOSE = 2, MENU = 3, ROTATE = 4;
        private final RectF box = new RectF();
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean resizable, rotatable, locked;
        private final Icons.Line gear = Icons.of(Icons.GEAR), close = Icons.of(Icons.CLOSE), lock = Icons.of(Widgets.LOCK), rot = Icons.of(Icons.HISTORY);

        Frame(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeCap(Paint.Cap.ROUND);
        }

        void place(RectF r, boolean resizable, boolean rotatable, boolean locked) {
            float o = Ui.dp(getContext(), 3);
            box.set(r.left - o, r.top - o, r.right + o, r.bottom + o);
            this.resizable = resizable;
            this.rotatable = rotatable;
            this.locked = locked;
            invalidate();
        }

        float angleTo(float x, float y) {
            return (float) Math.toDegrees(Math.atan2(y - box.centerY(), x - box.centerX()));
        }

        private boolean near(float x, float y, float cx, float cy) {
            return Math.hypot(x - cx, y - cy) < Ui.dp(getContext(), 26);
        }

        int hitTest(float x, float y) {
            if (getVisibility() != VISIBLE) return 0;
            if (resizable && near(x, y, box.right, box.bottom)) return HANDLE;
            if (rotatable && near(x, y, box.centerX(), box.top - Ui.dp(getContext(), 22))) return ROTATE;
            if (near(x, y, box.right, box.top)) return CLOSE;
            if (near(x, y, box.left, box.top)) return MENU;
            return 0;
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            stroke.setColor(Theme.accent);
            stroke.setStrokeWidth(2 * d);
            stroke.setPathEffect(new android.graphics.DashPathEffect(new float[]{8 * d, 6 * d}, 0));
            c.drawRoundRect(box, 22 * d, 22 * d, stroke);
            float r = 13 * d;
            if (resizable) {
                fill.setColor(Theme.accent);
                c.drawCircle(box.right, box.bottom, r, fill);
                ink.setColor(Theme.onAccent);
                ink.setStrokeWidth(2 * d);
                c.drawLine(box.right - 5 * d, box.bottom + 2 * d, box.right + 2 * d, box.bottom - 5 * d, ink);
            }
            if (rotatable) {
                float cy = box.top - 22 * d;
                stroke.setPathEffect(null);
                stroke.setStrokeWidth(1.5f * d);
                c.drawLine(box.centerX(), box.top, box.centerX(), cy + r, stroke);
                circle(c, box.centerX(), cy, r, rot, 0xF21B1D21);
            }
            circle(c, box.right, box.top, r, close, 0xF21B1D21);
            circle(c, box.left, box.top, r, locked ? lock : gear, 0xF21B1D21);
        }

        private void circle(Canvas c, float cx, float cy, float r, Icons.Line icon, int bg) {
            fill.setColor(bg);
            c.drawCircle(cx, cy, r, fill);
            int s = Math.round(r * 0.62f);
            icon.setBounds(Math.round(cx - s), Math.round(cy - s), Math.round(cx + s), Math.round(cy + s));
            icon.draw(c);
        }
    }
}
