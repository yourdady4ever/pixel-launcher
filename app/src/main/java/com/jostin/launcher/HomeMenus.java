package com.jostin.launcher;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menús de inicio: opciones de cada elemento, carpetas y el catálogo de widgets. */
final class HomeMenus {
    static int widgetsTab = 0;

    private HomeMenus() {}

    // ==================== Inicio ====================

    static void homeMenu(MainActivity a) {
        List<Sheet.Item> list = new ArrayList<>();
        if (!Prefs.homeLocked(a)) list.add(Sheet.item("Editar inicio", Icons.SHAPES, () -> a.home.setEditing(true)));
        list.add(Sheet.item("Agregar widget", Icons.PLUS, () -> widgets(a)));
        list.add(Sheet.item("Fondos", Icons.IMAGE, () -> Studio.show(a, a, 0)));
        list.add(Sheet.item("Personalizar", Icons.GEAR, () -> Studio.show(a, a, -1)));
        Sheet.show(a, a.backdrop(), "Inicio", list);
    }

    // ==================== Opciones de un elemento ====================

    static void item(MainActivity a, HomeModel.Item it) {
        if (it.type.equals("app")) {
            app(a, it);
            return;
        }
        if (it.type.equals("folder")) {
            folderMenu(a, it);
            return;
        }
        HomeView home = a.home;
        List<Sheet.Item> list = new ArrayList<>();
        View v = home.viewOf(it);
        String title = "Widget";
        if (it.type.equals("aw")) {
            AppWidgetProviderInfo info = AppWidgets.mgr(a).getAppWidgetInfo(it.num("id", -1));
            if (info != null) {
                title = info.loadLabel(a.getPackageManager());
                if (Build.VERSION.SDK_INT >= 31 && info.configure != null
                        && (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0) {
                    list.add(Sheet.item("Configurar", Icons.GEAR, () -> a.reconfigureWidget(it)));
                }
            }
        } else {
            Gadgets.Spec s = Gadgets.spec(it.type);
            if (s != null) title = s.name;
            if (v instanceof Gadgets.Gadget) ((Gadgets.Gadget) v).options(list);
        }
        if (!it.locked && !Prefs.homeLocked(a)) {
            list.add(Sheet.item("Mover y cambiar tamaño", Icons.SHAPES, () -> {
                home.setEditing(true);
                home.select(it);
            }));
        }
        Sheet.Item look = Sheet.item("Apariencia", Icons.IMAGE, () -> appearance(a, it));
        look.value = () -> Math.round(it.alpha * 100) + "%";
        list.add(look);
        if (home.model.free || Gadgets.decor(it.type)) {
            list.add(Sheet.item("Traer al frente", Icons.SWAP, () -> home.bringToFront(it, true)));
            list.add(Sheet.item("Enviar atrás", Icons.SWAP, () -> home.bringToFront(it, false)));
        }
        if (!it.type.equals("aw")) list.add(Sheet.item("Duplicar", Icons.PLUS, () -> home.duplicate(it)));
        list.add(Sheet.item("Quitar", Icons.TRASH, () -> home.removeItem(it)).danger());
        Sheet.show(a, a.backdrop(), title, list);
    }

    /** Transparencia, giro, vidrio y candado de un widget, con vista previa en vivo. */
    static void appearance(MainActivity a, HomeModel.Item it) {
        HomeView home = a.home;
        View v = home.viewOf(it);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 4), Ui.dp(a, 8));
        col.addView(Ctl.slider(a, "Opacidad", 20, 100, Math.round(it.alpha * 100), "%", (val, done) -> {
            home.setAlphaOf(it, val / 100f);
            if (done) home.saveModel();
        }));
        if (Gadgets.decor(it.type)) {
            col.addView(Ctl.slider(a, "Giro", -180, 180, Math.round(it.rot), "°", (val, done) -> {
                home.setRotationOf(it, val);
                if (done) home.saveModel();
            }));
        }
        if (v instanceof Gadgets.Gadget && !it.type.equals("buscar")) {
            Gadgets.Gadget g = (Gadgets.Gadget) v;
            col.addView(Ctl.toggle(a, "Fondo de vidrio", "Sin vidrio, el widget queda directo sobre tu fondo.", g.hasGlass(), on -> {
                it.put("bg", on);
                home.refreshItem(it);
                home.saveModel();
            }));
        }
        col.addView(Ctl.toggle(a, "Fijar en su lugar", "No se mueve ni cambia de tamaño hasta que lo quites.", it.locked, on -> {
            it.locked = on;
            home.saveModel();
            if (home.selected == it) home.updateFrame();
        }));
        TextView head = Ui.label(a, "Apariencia");
        head.setPadding(Ui.dp(a, 8), 0, 0, Ui.dp(a, 6));
        Sheet.panel(a, a.backdrop(), head, col, 0.5f, 0x14000000);
    }

    // ==================== Apps ====================

    static void app(MainActivity a, HomeModel.Item it) {
        String pkg = it.str("pkg", "");
        HomeView home = a.home;
        List<Sheet.Item> list = new ArrayList<>(shortcutItems(a, pkg));
        list.add(Sheet.item("Cambiar ícono o nombre", Icons.IMAGE, () -> iconMenu(a, pkg)));
        list.add(Sheet.item("Poner en el dock", Icons.HOME, () -> a.chooseSlot(pkg, IconEngine.label(a, pkg))));
        if (!it.locked && !Prefs.homeLocked(a) && !home.editing) {
            list.add(Sheet.item("Mover", Icons.SHAPES, () -> home.setEditing(true)));
        }
        Sheet.Item lock = Sheet.item("Fijar en su lugar", Widgets.LOCK, () -> {
            it.locked = !it.locked;
            home.saveModel();
        });
        lock.value(() -> it.locked ? "Sí" : "No");
        list.add(lock);
        list.add(Sheet.item("Información de la app", Icons.INFO, () -> MainActivity.appInfo(a, pkg)));
        list.add(Sheet.item("Quitar de inicio", Icons.CLOSE, () -> home.removeItem(it)));
        list.add(Sheet.item("Desinstalar", Icons.TRASH, () -> a.uninstall(pkg)).danger());
        Sheet.show(a, a.backdrop(), IconEngine.label(a, pkg), list);
    }

    /** Atajos de la app (los que salen al mantener presionado su ícono en cualquier launcher). */
    static List<Sheet.Item> shortcutItems(MainActivity a, String pkg) {
        List<Sheet.Item> out = new ArrayList<>();
        LauncherApps la = (LauncherApps) a.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        try {
            // Android solo los da al launcher que está puesto como predeterminado
            if (la == null || !la.hasShortcutHostPermission()) return out;
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
            q.setPackage(pkg);
            q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
            List<ShortcutInfo> list = la.getShortcuts(q, Process.myUserHandle());
            if (list == null) return out;
            List<ShortcutInfo> ok = new ArrayList<>();
            for (ShortcutInfo s : list) if (s.isEnabled()) ok.add(s);
            ok.sort((x, y) -> {
                int tx = x.isDeclaredInManifest() ? 0 : 1, ty = y.isDeclaredInManifest() ? 0 : 1;
                return tx != ty ? tx - ty : Integer.compare(x.getRank(), y.getRank());
            });
            int dpi = a.getResources().getDisplayMetrics().densityDpi;
            for (ShortcutInfo s : ok) {
                if (out.size() >= 4) break;
                CharSequence label = s.getShortLabel() != null ? s.getShortLabel() : s.getLongLabel();
                if (label == null) continue;
                Sheet.Item item = Sheet.item(label.toString(), Icons.SPARK, () -> {
                    try {
                        la.startShortcut(s, null, null);
                    } catch (Exception e) {
                        Sheet.pill(a, a.backdrop(), "Ese atajo no se pudo abrir");
                    }
                });
                try {
                    Drawable d = la.getShortcutIconDrawable(s, dpi);
                    if (d != null) item.drawable(d);
                } catch (Exception ignored) {
                    // se queda el ícono de línea
                }
                out.add(item);
            }
        } catch (Exception ignored) {
            // sin atajos
        }
        return out;
    }

    static void iconMenu(MainActivity a, String pkg) {
        List<Sheet.Item> list = new ArrayList<>();
        list.add(Sheet.item("Cambiar nombre", Gadgets.TEXT, () -> Sheet.prompt(a, a.backdrop(), "Nombre en inicio", "Deja vacío para el original",
                IconEngine.label(a, pkg), v -> {
                    IconEngine.setLabel(a, pkg, v);
                    a.refreshIcons(pkg);
                })));
        list.add(Sheet.item("Ícono desde tu galería", Icons.IMAGE, () -> a.pickIcon(pkg)));
        if (IconEngine.customFile(a, pkg).exists() || Prefs.sp(a).contains("label_" + pkg)) {
            list.add(Sheet.item("Volver al original", Icons.HISTORY, () -> {
                IconEngine.clearCustom(a, pkg);
                IconEngine.setLabel(a, pkg, null);
                a.refreshIcons(pkg);
            }));
        }
        Sheet.show(a, a.backdrop(), IconEngine.label(a, pkg), list);
    }

    // ==================== Carpetas ====================

    static void folder(MainActivity a, HomeModel.Item it, View from) {
        Backdrop b = a.backdrop();
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 18);
        panel.setPadding(p, p, p, p);
        Glass.apply(panel, b, 32, Glass.TINT_STRONG);
        panel.setClickable(true);
        TextView title = Ui.text(a, it.str("name", "Carpeta"), 20, Ui.TEXT, Ui.SEMIBOLD);
        title.setGravity(Gravity.CENTER);
        Ui.pressable(title, 0.96f);
        title.setOnClickListener(v -> renameFolder(a, it, title));
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        JSONArray apps = it.apps();
        LinearLayout row = null;
        int n = apps.length() + 1;
        for (int i = 0; i < n; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(a);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = Ui.dp(a, 14);
                panel.addView(row, rl);
            }
            View cell;
            if (i < apps.length()) {
                String pkg = apps.optString(i);
                cell = appCell(a, pkg, IconEngine.label(a, pkg), iv -> {
                    a.closeOverlay();
                    a.launchApp(pkg, iv);
                });
                cell.setOnLongClickListener(v -> {
                    Sound.play(v, Sound.HOLD);
                    List<Sheet.Item> list = new ArrayList<>(shortcutItems(a, pkg));
                    list.add(Sheet.item("Sacar de la carpeta", Icons.CLOSE, () -> {
                        a.closeOverlay();
                        takeOut(a, it, pkg);
                    }));
                    list.add(Sheet.item("Información de la app", Icons.INFO, () -> MainActivity.appInfo(a, pkg)));
                    Sheet.show(a, b, IconEngine.label(a, pkg), list);
                    return true;
                });
            } else {
                cell = appCell(a, null, "Agregar", iv -> {
                    a.closeOverlay();
                    a.pickAppForFolder(it);
                });
            }
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        // Que la última fila no se estire
        if (row != null) for (int k = row.getChildCount(); k < 4; k++) row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(a, 330), -2, Gravity.CENTER);
        a.showOverlay(panel, lp);
    }

    interface Tap {
        void tap(ImageView icon);
    }

    private static View appCell(MainActivity a, String pkg, String label, Tap tap) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView iv = new ImageView(a);
        if (pkg != null) {
            IconEngine.bind(iv, pkg);
        } else {
            Icons.Line plus = Icons.of(Icons.PLUS);
            plus.setColor(0xCCFFFFFF);
            iv.setImageDrawable(plus);
            int ip = Ui.dp(a, 14);
            iv.setPadding(ip, ip, ip, ip);
            iv.setBackground(Ui.outline(a, 0x0FFFFFFF, 0x40FFFFFF, 18));
        }
        c.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 52), Ui.dp(a, 52)));
        TextView t = Ui.text(a, label, 12, Ui.TEXT, Ui.MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = Ui.dp(a, 6);
        c.addView(t, tl);
        Ui.pressable(c, 0.9f);
        c.setOnClickListener(v -> tap.tap(iv));
        return c;
    }

    private static void renameFolder(MainActivity a, HomeModel.Item it, TextView title) {
        Sheet.prompt(a, a.backdrop(), "Nombre de la carpeta", "Ejemplo: Escuela", it.str("name", "Carpeta"), v -> {
            it.put("name", v.isEmpty() ? "Carpeta" : v);
            if (title != null) title.setText(it.str("name", "Carpeta"));
            a.home.replaceView(it);
            a.home.saveModel();
        });
    }

    /** Saca una app de la carpeta y la pone en inicio. Si queda una sola, la carpeta se vuelve esa app. */
    static void takeOut(MainActivity a, HomeModel.Item folder, String pkg) {
        HomeView home = a.home;
        JSONArray apps = folder.apps(), keep = new JSONArray();
        for (int i = 0; i < apps.length(); i++) if (!pkg.equals(apps.optString(i))) keep.put(apps.optString(i));
        home.snapshot();
        if (keep.length() == 1) {
            folder.type = "app";
            folder.data = new JSONObject();
            folder.put("pkg", keep.optString(0));
        } else {
            folder.put("apps", keep);
        }
        home.replaceView(folder);
        home.saveModel();
        a.addAppToHome(pkg);
    }

    static void folderMenu(MainActivity a, HomeModel.Item it) {
        HomeView home = a.home;
        List<Sheet.Item> list = new ArrayList<>();
        list.add(Sheet.item("Abrir", Icons.SHAPES, () -> folder(a, it, home.viewOf(it))));
        list.add(Sheet.item("Cambiar nombre", Gadgets.TEXT, () -> renameFolder(a, it, null)));
        list.add(Sheet.item("Agregar una app", Icons.PLUS, () -> a.pickAppForFolder(it)));
        if (!it.locked && !Prefs.homeLocked(a) && !home.editing) list.add(Sheet.item("Mover", Icons.SWAP, () -> home.setEditing(true)));
        list.add(Sheet.item("Deshacer carpeta", Icons.GRID, () -> {
            JSONArray apps = it.apps();
            if (apps.length() == 0) {
                home.removeItem(it);
                return;
            }
            home.snapshot();
            List<String> rest = new ArrayList<>();
            for (int i = 1; i < apps.length(); i++) rest.add(apps.optString(i));
            String first = apps.optString(0);
            it.type = "app";
            it.data = new JSONObject();
            it.put("pkg", first);
            home.replaceView(it);
            for (String p : rest) home.place("app", 1, 1, x -> x.put("pkg", p));
            home.saveModel();
        }));
        list.add(Sheet.item("Quitar carpeta", Icons.TRASH, () -> home.removeItem(it)).danger());
        Sheet.show(a, a.backdrop(), it.str("name", "Carpeta"), list);
    }

    // ==================== Catálogo de widgets ====================

    private static final String[][] GROUPS = {
            {"Hora y fechas", "reloj", "analogico", "hora_mundial", "calendario", "agenda", "cuenta"},
            {"Para ti", "saludo", "pendientes", "estudio", "jarvis", "nota"},
            {"Música y clima", "musica", "ondas", "clima"},
            {"Tu teléfono", "sistema", "bateria", "interruptores", "buscar", "atajo"},
            {"Decorativos (giran y se enciman)", "texto", "forma", "foto"}};

    static void widgets(MainActivity a) {
        Backdrop b = a.backdrop();
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(a, "WIDGETS", 15, Ui.TEXT, Ui.DISPLAY);
        title.setLetterSpacing(0.25f);
        title.setPadding(Ui.dp(a, 8), 0, 0, Ui.dp(a, 12));
        header.addView(title);
        FrameLayout body = new FrameLayout(a);
        header.addView(Ctl.chips(a, new String[]{"Jostin OS", "De tus apps"}, widgetsTab, t -> {
            widgetsTab = t;
            fillWidgets(a, body, t);
        }), new LinearLayout.LayoutParams(-1, -2));
        View line = new View(a);
        line.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, 1);
        ll.topMargin = Ui.dp(a, 12);
        header.addView(line, ll);
        fillWidgets(a, body, widgetsTab);
        Sheet.panel(a, b, header, body, 0.6f, 0x33000000);
    }

    private static void fillWidgets(MainActivity a, FrameLayout body, int tab) {
        body.removeAllViews();
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(a, 2), 0, Ui.dp(a, 2), Ui.dp(a, 10));
        body.addView(col, new FrameLayout.LayoutParams(-1, -2));
        if (tab == 0) ownWidgets(a, col);
        else appWidgets(a, col);
        col.setAlpha(0f);
        col.animate().alpha(1f).setDuration(220).start();
    }

    private static void ownWidgets(MainActivity a, LinearLayout col) {
        for (String[] g : GROUPS) {
            col.addView(Ctl.section(a, g[0]));
            LinearLayout row = null;
            int k = 0;
            for (int i = 1; i < g.length; i++) {
                Gadgets.Spec s = Gadgets.spec(g[i]);
                if (s == null) continue;
                if (k % 2 == 0) {
                    row = new LinearLayout(a);
                    LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                    if (k > 0) rl.topMargin = Ui.dp(a, 8);
                    col.addView(row, rl);
                }
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1f);
                if (k % 2 == 1) cl.leftMargin = Ui.dp(a, 8);
                row.addView(specCard(a, s), cl);
                k++;
            }
            if (row != null && row.getChildCount() == 1) {
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, 1, 1f);
                cl.leftMargin = Ui.dp(a, 8);
                row.addView(new View(a), cl);
            }
        }
    }

    private static View specCard(MainActivity a, Gadgets.Spec s) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 14);
        card.setPadding(p, p, p, p);
        card.setBackground(Ui.outline(a, 0x14FFFFFF, 0x1AFFFFFF, 22));
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(a);
        ic.setImageDrawable(new Icons.Line(s.icon, Theme.accent, 1.6f));
        int ip = Ui.dp(a, 9);
        ic.setPadding(ip, ip, ip, ip);
        ic.setBackground(Ui.fill(a, Ui.alpha(Theme.accent, 0x22), 14));
        top.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 38)));
        top.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView size = Ui.text(a, s.w + "×" + s.h, 11, Ui.MUTED, Ui.SEMIBOLD);
        int sp = Ui.dp(a, 8);
        size.setPadding(sp, Ui.dp(a, 4), sp, Ui.dp(a, 4));
        size.setBackground(Ui.fill(a, Ui.SOFT, 10));
        top.addView(size);
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView name = Ui.text(a, s.name, 15, Ui.TEXT, Ui.SEMIBOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(a, 12);
        card.addView(name, nl);
        TextView desc = Ui.text(a, s.desc, 12, Ui.MUTED, Ui.REGULAR);
        desc.setMaxLines(2);
        desc.setLineSpacing(0, 1.1f);
        desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = Ui.dp(a, 4);
        card.addView(desc, dl);
        Ui.pressable(card, 0.96f);
        card.setOnClickListener(v -> {
            Sound.play(v, Sound.CHECK);
            Sheet.dismiss(a, () -> a.addGadget(s));
        });
        return card;
    }

    /** Widgets que ofrecen tus apps (Google, Spotify, Gmail…), agrupados por app. */
    private static void appWidgets(MainActivity a, LinearLayout col) {
        TextView wait = Ui.text(a, "Buscando widgets…", 14, Ui.MUTED, Ui.REGULAR);
        wait.setPadding(Ui.dp(a, 6), Ui.dp(a, 18), 0, Ui.dp(a, 18));
        col.addView(wait);
        Context app = a.getApplicationContext();
        Gadgets.BG.execute(() -> {
            List<AppWidgetProviderInfo> all;
            try {
                all = AppWidgets.mgr(app).getInstalledProvidersForProfile(Process.myUserHandle());
            } catch (Exception e) {
                all = new ArrayList<>();
            }
            PackageManager pm = app.getPackageManager();
            Map<String, List<AppWidgetProviderInfo>> by = new HashMap<>();
            Map<String, String> names = new HashMap<>();
            for (AppWidgetProviderInfo info : all) {
                String pkg = info.provider.getPackageName();
                if (pkg.equals(app.getPackageName())) continue;
                List<AppWidgetProviderInfo> l = by.get(pkg);
                if (l == null) {
                    l = new ArrayList<>();
                    by.put(pkg, l);
                    try {
                        names.put(pkg, String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))));
                    } catch (Exception e) {
                        names.put(pkg, pkg);
                    }
                }
                l.add(info);
            }
            List<String> pkgs = new ArrayList<>(by.keySet());
            Collator c = Collator.getInstance(MainActivity.ES);
            pkgs.sort((x, y) -> c.compare(names.get(x), names.get(y)));
            Gadgets.MAIN.post(() -> {
                if (!col.isAttachedToWindow()) return;
                col.removeView(wait);
                if (pkgs.isEmpty()) {
                    TextView t = Ui.text(a, "Tus apps no ofrecen widgets, o Android no dejó verlos.", 14, Ui.MUTED, Ui.REGULAR);
                    t.setPadding(Ui.dp(a, 6), Ui.dp(a, 18), 0, 0);
                    col.addView(t);
                    return;
                }
                TextView tip = Ui.text(a, "Toca una app para ver sus widgets. La primera vez Android te pregunta si Jostin OS puede mostrarlos.", 12, Ui.MUTED, Ui.REGULAR);
                tip.setLineSpacing(0, 1.2f);
                tip.setPadding(Ui.dp(a, 6), Ui.dp(a, 12), 0, Ui.dp(a, 6));
                col.addView(tip);
                for (String pkg : pkgs) col.addView(appGroup(a, pkg, names.get(pkg), by.get(pkg)));
            });
        });
    }

    private static View appGroup(MainActivity a, String pkg, String name, List<AppWidgetProviderInfo> list) {
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(a, 8);
        head.setPadding(p, p, p, p);
        ImageView ic = new ImageView(a);
        try {
            ic.setImageDrawable(a.getPackageManager().getApplicationIcon(pkg));
        } catch (Exception ignored) {
            ic.setImageDrawable(Icons.of(Icons.SHAPES));
        }
        head.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34)));
        TextView t = Ui.text(a, name, 15, Ui.TEXT, Ui.MEDIUM);
        t.setMaxLines(1);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(a, 12);
        head.addView(t, tl);
        head.addView(Ui.text(a, list.size() == 1 ? "1 widget" : list.size() + " widgets", 13, Ui.MUTED, Ui.REGULAR));
        Ui.pressable(head, 0.98f);
        wrap.addView(head, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout items = new LinearLayout(a);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setVisibility(View.GONE);
        wrap.addView(items, new LinearLayout.LayoutParams(-1, -2));
        head.setOnClickListener(v -> {
            Sound.play(v, Sound.TOGGLE);
            boolean open = items.getVisibility() != View.VISIBLE;
            if (open && items.getChildCount() == 0) for (AppWidgetProviderInfo info : list) items.addView(widgetCard(a, info));
            items.setVisibility(open ? View.VISIBLE : View.GONE);
        });
        return wrap;
    }

    private static View widgetCard(MainActivity a, AppWidgetProviderInfo info) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 12);
        card.setPadding(p, p, p, p);
        card.setBackground(Ui.outline(a, 0x14FFFFFF, 0x1AFFFFFF, 20));
        FrameLayout preview = new FrameLayout(a);
        preview.setClipChildren(true);
        card.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(a, 120)));
        boolean shown = false;
        if (Build.VERSION.SDK_INT >= 31 && info.previewLayout != 0) {
            // Vista previa real del widget (así la muestran los launchers de Android 12 en adelante)
            try {
                AppWidgetHostView hv = new AppWidgetHostView(a);
                hv.setAppWidget(-1, info);
                hv.updateAppWidget(new RemoteViews(info.provider.getPackageName(), info.previewLayout));
                preview.addView(hv, new FrameLayout.LayoutParams(-1, -1));
                shown = true;
            } catch (Exception ignored) {
                preview.removeAllViews();
            }
        }
        if (!shown) {
            ImageView img = new ImageView(a);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            preview.addView(img, new FrameLayout.LayoutParams(-1, -1));
            int maxW = a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 80), maxH = Ui.dp(a, 120);
            Context app = a.getApplicationContext();
            Gadgets.BG.execute(() -> {
                Bitmap bm = null;
                try {
                    Drawable d = info.loadPreviewImage(app, 0);
                    if (d == null) d = info.loadIcon(app, 0);
                    bm = toBitmap(d, maxW, maxH);
                } catch (Exception ignored) {
                    // sin vista previa
                }
                Bitmap fb = bm;
                Gadgets.MAIN.post(() -> {
                    if (fb != null) img.setImageBitmap(fb);
                });
            });
        }
        int[] span = AppWidgets.span(info, a.home.cellW, a.home.cellH, a.home.cols, a.home.rows);
        LinearLayout bottom = new LinearLayout(a);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(a, info.loadLabel(a.getPackageManager()), 14, Ui.TEXT, Ui.MEDIUM);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bottom.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        bottom.addView(Ui.text(a, span[0] + "×" + span[1], 12, Ui.MUTED, Ui.SEMIBOLD));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = Ui.dp(a, 10);
        card.addView(bottom, bl);
        // Una capa encima para que el toque sea del catálogo y no del widget de muestra
        FrameLayout box = new FrameLayout(a);
        box.addView(card, new FrameLayout.LayoutParams(-1, -2));
        View shield = new View(a);
        box.addView(shield, new FrameLayout.LayoutParams(-1, -1));
        shield.setOnTouchListener((v, e) -> {
            int act = e.getActionMasked();
            if (act == android.view.MotionEvent.ACTION_DOWN) box.animate().scaleX(0.97f).scaleY(0.97f).setDuration(110).start();
            else if (act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL)
                box.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
            return false;
        });
        shield.setOnClickListener(v -> {
            Sound.play(v, Sound.CHECK);
            Sheet.dismiss(a, () -> a.addSystemWidget(info));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 8);
        box.setLayoutParams(lp);
        return box;
    }

    private static Bitmap toBitmap(Drawable d, int maxW, int maxH) {
        if (d == null) return null;
        int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            w = maxW;
            h = maxH;
        }
        float s = Math.min(1f, Math.min(maxW / (float) w, maxH / (float) h));
        int bw = Math.max(1, Math.round(w * s)), bh = Math.max(1, Math.round(h * s));
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, bw, bh);
        d.draw(c);
        return b;
    }
}
