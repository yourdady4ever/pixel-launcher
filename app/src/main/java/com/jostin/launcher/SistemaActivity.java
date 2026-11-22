package com.jostin.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pantallas de sistema con Shizuku: Limpiar, Poco usadas, Congeladas y RAM. */
public class SistemaActivity extends Screen {
    static final String MODE = "mode";
    static final String LIMPIAR = "limpiar", POCO = "poco", CONGELADAS = "congeladas", RAM = "ram";
    private static final int[] DAYS = {7, 15, 30, 60, 90};

    private static final ExecutorService BG = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout col;
    private String mode;
    private boolean resumed;
    private final Set<String> picked = new HashSet<>();
    private final Runnable shizuChanged = this::reload;
    private final Runnable ramTick = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            reload();
            main.postDelayed(this, 15_000);
        }
    };

    static void open(Activity from, String mode) {
        Screen.open(from, new Intent(from, SistemaActivity.class).putExtra(MODE, mode));
    }

    static Intent intent(Context c, String mode) {
        return new Intent(c, SistemaActivity.class).putExtra(MODE, mode);
    }

    private String mode() {
        if (mode == null) {
            mode = getIntent().getStringExtra(MODE);
            if (mode == null) mode = LIMPIAR;
        }
        return mode;
    }

    @Override
    String screenTitle() {
        switch (mode()) {
            case POCO:
                return "Apps poco usadas";
            case CONGELADAS:
                return "Congeladas";
            case RAM:
                return "Memoria RAM";
            default:
                return "Limpiar";
        }
    }

    @Override
    String screenSubtitle() {
        return "Con Shizuku";
    }

    @Override
    View createBody() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        headerAction(Icons.HISTORY, v -> reload());
        return sv;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Shizu.watch(shizuChanged);
        // La RAM se lee solo mientras esta pantalla está abierta
        if (RAM.equals(mode())) main.post(ramTick);
        else reload();
    }

    @Override
    protected void onPause() {
        resumed = false;
        Shizu.unwatch(shizuChanged);
        main.removeCallbacks(ramTick);
        super.onPause();
    }

    private TextView note(String s) {
        TextView t = Ui.text(this, s, 13, Ui.MUTED, Ui.REGULAR);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 6));
        return t;
    }

    private void pill(String s) {
        Sheet.pill(this, backdrop, s);
    }

    /** Vuelve a leer y pinta la pantalla. */
    private void reload() {
        if (isFinishing() || isDestroyed()) return;
        if (!Shizu.listo(this)) {
            col.removeAllViews();
            col.addView(note("Esto necesita Shizuku listo. Ahorita: " + Shizu.texto(this)));
            col.addView(Ctl.action(this, Icons.CHECK, "Conectar con Shizuku", null, v -> {
                String m = Shizu.pedir(this);
                if (m != null) pill(m);
            }));
            return;
        }
        String m = mode();
        if (POCO.equals(m) && !AppUsage.granted(this)) {
            col.removeAllViews();
            col.addView(note("Para saber qué apps no abres, Android pide «Acceso al uso» para Jostin OS."));
            col.addView(Ctl.action(this, Icons.GEAR, "Dar acceso al uso", null, v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Exception ignored) {
                    pill("Búscalo en Ajustes › Apps › Acceso especial");
                }
            }));
            return;
        }
        if (col.getChildCount() == 0) col.addView(note("Leyendo…"));
        int days = Prefs.sp(this).getInt("unused_days", 30);
        BG.execute(() -> {
            List<SysApps.App> list;
            switch (m) {
                case POCO:
                    list = SysApps.unused(this, days);
                    break;
                case CONGELADAS:
                    list = SysApps.frozen(this);
                    break;
                case RAM:
                    list = SysApps.ram(this);
                    break;
                default:
                    list = SysApps.running(this);
            }
            final List<SysApps.App> l = list;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (l == null) {
                    col.removeAllViews();
                    col.addView(note("Shizuku no respondió. Revisa que siga corriendo."));
                    return;
                }
                switch (m) {
                    case POCO:
                        paintUnused(l, days);
                        break;
                    case CONGELADAS:
                        paintFrozen(l);
                        break;
                    case RAM:
                        paintRam(l);
                        break;
                    default:
                        paintRunning(l);
                }
            });
        });
    }

    // ---------- Limpiar ----------

    private void paintRunning(List<SysApps.App> l) {
        col.removeAllViews();
        if (l.isEmpty()) {
            col.addView(note("No hay apps tuyas corriendo en segundo plano. Todo limpio ✓"));
            return;
        }
        picked.clear();
        for (SysApps.App a : l) picked.add(a.pkg);
        col.addView(note("Se cierran a la fuerza (como «Forzar detención»). Las protegidas, tu teclado, tu fondo, Spin, Shizuku y Google no salen aquí."));
        addPicker(l, true);
        col.addView(Ctl.action(this, Icons.SPARK, "Cerrar las marcadas", null, v -> {
            List<SysApps.App> sel = selected(l);
            if (sel.isEmpty()) {
                pill("No marcaste ninguna");
                return;
            }
            confirm("¿Cerrar estas " + sel.size() + " apps?", sel, "Cerrar", () -> act(sel, SysApps::forceStop, "Cerré"));
        }));
    }

    // ---------- Poco usadas ----------

    private void paintUnused(List<SysApps.App> l, int days) {
        col.removeAllViews();
        col.addView(Ctl.section(this, "Sin abrir desde hace"));
        int sel = 2;
        for (int i = 0; i < DAYS.length; i++) if (DAYS[i] == days) sel = i;
        String[] names = new String[DAYS.length];
        for (int i = 0; i < DAYS.length; i++) names[i] = DAYS[i] + " días";
        col.addView(Ctl.chips(this, names, sel, i -> {
            Prefs.sp(this).edit().putInt("unused_days", DAYS[i]).apply();
            col.removeAllViews();
            reload();
        }));
        if (l.isEmpty()) {
            col.addView(note("Todas tus apps se han usado en ese tiempo ✓"));
            return;
        }
        picked.clear();
        long now = System.currentTimeMillis();
        col.addView(note("Congelar la deja instalada pero apagada (sin ícono ni avisos); la regresas en «Congeladas». Las del sistema solo se quitan para tu usuario."));
        for (SysApps.App a : l) {
            String sub = (a.last <= 0 ? "Sin uso registrado" : "Hace " + ((now - a.last) / 86_400_000L) + " días") + (a.system ? " · del sistema" : "");
            col.addView(Ctl.toggle(this, a.label, sub, false, on -> {
                if (on) picked.add(a.pkg);
                else picked.remove(a.pkg);
            }));
        }
        col.addView(Ctl.action(this, Icons.BOX, "Congelar las marcadas", null, v -> {
            List<SysApps.App> s = selected(l);
            if (s.isEmpty()) {
                pill("No marcaste ninguna");
                return;
            }
            confirm("¿Congelar estas " + s.size() + " apps?", s, "Congelar", () -> act(s, SysApps::freeze, "Congelé"));
        }));
        col.addView(Ctl.action(this, Icons.TRASH, "Desinstalar las marcadas", null, v -> {
            List<SysApps.App> s = selected(l);
            if (s.isEmpty()) {
                pill("No marcaste ninguna");
                return;
            }
            boolean user = false;
            for (SysApps.App a : s) user |= !a.system;
            confirm("¿Desinstalar estas " + s.size() + " apps?" + (user ? " Las tuyas se borran con sus datos." : ""), s, "Desinstalar",
                    () -> act(s, SysApps::uninstall, "Desinstalé"));
        }));
    }

    // ---------- Congeladas ----------

    private void paintFrozen(List<SysApps.App> l) {
        col.removeAllViews();
        if (l.isEmpty()) {
            col.addView(note("No tienes apps congeladas ni apps del sistema quitadas."));
            return;
        }
        boolean anyFrozen = false, anyRemoved = false;
        for (SysApps.App a : l) {
            if (a.removed) anyRemoved = true;
            else anyFrozen = true;
        }
        if (anyFrozen) col.addView(Ctl.section(this, "Congeladas"));
        for (SysApps.App a : l) {
            if (a.removed) continue;
            col.addView(Ctl.action(this, Icons.BOX, a.label, "Descongelar", v -> act(one(a), SysApps::unfreeze, "Descongelé")));
        }
        if (anyRemoved) col.addView(Ctl.section(this, "Del sistema, quitadas"));
        for (SysApps.App a : l) {
            if (!a.removed) continue;
            col.addView(Ctl.action(this, Icons.PLUS, a.label, "Reinstalar", v -> act(one(a), SysApps::reinstall, "Reinstalé")));
        }
    }

    // ---------- RAM ----------

    private void paintRam(List<SysApps.App> l) {
        col.removeAllViews();
        long total = 0;
        for (SysApps.App a : l) total += a.kb;
        col.addView(note("Las 15 que más memoria usan ahorita (" + mb(total) + " entre todas). Se actualiza cada 15 s mientras estás aquí. Toca una para cerrarla."));
        for (SysApps.App a : l) {
            boolean prot = Protegidas.es(this, a.pkg);
            col.addView(Ctl.action(this, prot ? Icons.LOCK : Icons.GRID, a.label, mb(a.kb), v -> {
                if (prot) {
                    pill("Esa está protegida; no la cierro");
                    return;
                }
                confirm("¿Cerrar esta app?", one(a), "Cerrar", () -> act(one(a), SysApps::forceStop, "Cerré"));
            }));
        }
    }

    private static String mb(long kb) {
        return kb >= 1024 * 1024 ? String.format(Locale.US, "%.1f GB", kb / 1048576f) : (kb / 1024) + " MB";
    }

    // ---------- Comunes ----------

    private void addPicker(List<SysApps.App> l, boolean on) {
        for (SysApps.App a : l) {
            col.addView(Ctl.toggle(this, a.label, a.pkg, on, v -> {
                if (v) picked.add(a.pkg);
                else picked.remove(a.pkg);
            }));
        }
    }

    private List<SysApps.App> selected(List<SysApps.App> l) {
        List<SysApps.App> out = new ArrayList<>();
        for (SysApps.App a : l) if (picked.contains(a.pkg)) out.add(a);
        return out;
    }

    private static List<SysApps.App> one(SysApps.App a) {
        List<SysApps.App> l = new ArrayList<>();
        l.add(a);
        return l;
    }

    /** Antes de algo que cierra, congela o borra: el diálogo con la lista exacta de apps. */
    private void confirm(String title, List<SysApps.App> apps, String ok, Runnable go) {
        StringBuilder b = new StringBuilder();
        for (SysApps.App a : apps) b.append("• ").append(a.label).append('\n');
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setMessage(b.toString().trim())
                .setPositiveButton(ok, (d, w) -> go.run())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private interface Action {
        int run(Context c, List<SysApps.App> apps);
    }

    private void act(List<SysApps.App> apps, Action action, String verb) {
        pill("Trabajando…");
        BG.execute(() -> {
            int n = action.run(this, apps);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (n < 0) pill("Shizuku no respondió");
                else pill(verb + " " + n + " de " + apps.size());
                reload();
            });
        });
    }
}
