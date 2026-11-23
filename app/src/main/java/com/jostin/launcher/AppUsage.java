package com.jostin.launcher;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Para que el pez sepa cuánto llevas en TikTok y si ya hiciste tu Duolingo. Con el permiso de
 * «Acceso al uso», cada ~5 min y solo con la pantalla prendida, sube a /u/<tu clave>/apps/cel tres datos:
 * minutos de TikTok hoy, si abriste Duolingo hoy y la hora. Nada de historial ni otras apps.
 */
final class AppUsage {
    private static final String[] TIKTOK = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"};
    private static final String DUO = "com.duolingo";
    private static final long EVERY = 5 * 60_000L;
    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context app;
    private static long pezAt, putAt;
    private static String putLast = "";
    private static boolean attached;

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            MAIN.removeCallbacks(this);
            if (app == null || !screenOn(app)) return;
            // Se lee si hay permiso y algo que hacer con eso: compartir con el pez o vigilar un límite
            if (granted(app) && (wanted(app) || limits(app).length() > 0)) report(app);
            // En la misma vuelta: ¿el pez necesita algo? (red fuera del hilo principal)
            if (PezAviso.on(app) && System.currentTimeMillis() - pezAt > 20 * 60_000L) {
                pezAt = System.currentTimeMillis();
                NET.execute(() -> PezAviso.check(app));
            }
            MAIN.postDelayed(this, EVERY);
        }
    };

    private AppUsage() {}

    static boolean wanted(Context c) {
        return Prefs.sp(c).getBoolean("apps_share", false);
    }

    static void setWanted(Context c, boolean on) {
        Prefs.sp(c).edit().putBoolean("apps_share", on).apply();
        if (on) kick(c);
    }

    static boolean granted(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode = Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static boolean enabled(Context c) {
        return wanted(c) && granted(c);
    }

    private static boolean screenOn(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }

    /** Una sola vez por proceso: escucha cuando se prende o apaga la pantalla. */
    static void attach(Context c) {
        app = c.getApplicationContext();
        if (attached) return;
        attached = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        app.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) MAIN.removeCallbacks(tick);
                else MAIN.postDelayed(tick, 10_000);
            }
        }, f);
        kick(c);
    }

    /** Revisa ya (al volver a inicio o al dar el permiso) y sigue cada 5 min. */
    static void kick(Context c) {
        if (app == null) app = c.getApplicationContext();
        MAIN.removeCallbacks(tick);
        MAIN.postDelayed(tick, 1500);
    }

    private static long startOfDay() {
        Calendar d = Calendar.getInstance();
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
        return d.getTimeInMillis();
    }

    @SuppressWarnings("deprecation")
    private static void report(Context c) {
        NET.execute(() -> {
            try {
                UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm == null) return;
                long from = startOfDay(), now = System.currentTimeMillis();
                java.util.Map<String, Long> fg = new java.util.HashMap<>();
                int[] unlocks = {0};
                long duoLast = scan(usm, from, now, fg, unlocks);
                long tiktokMs = 0;
                for (String t : TIKTOK) {
                    Long v = fg.get(t);
                    if (v != null) tiktokMs += v;
                }
                checkLimits(c, fg);
                if (!wanted(c)) return;
                // Si Duolingo avisó de la racha después de abrirlo, todavía no está hecha
                boolean duo = duoLast >= from && duoLast > PezTalk.duoAlertAt;
                int tk = (int) (tiktokMs / 60_000L);
                // Resumen de cómo va el cel (sin textos de notificaciones ni apps de dinero); viaja solo a la ruta con clave
                String resumen = CelResumen.build(c, fg, unlocks[0], duo);
                CelResumen.last = resumen;
                String same = tk + "/" + duo + "/" + resumen;
                // Sin cambios, se sube cuando mucho cada 15 min (para que el pez sepa que sigue vigente)
                if (same.equals(putLast) && now - putAt < 15 * 60_000L) return;
                putLast = same;
                putAt = now;
                JSONObject o = new JSONObject();
                o.put("tiktok", tk);
                o.put("duo", duo);
                o.put("resumen", resumen);
                o.put("t", now);
                String dest = PezKey.appsCel(c);
                if (dest == null) return;
                put(dest, o.toString());
                MAIN.post(() -> PezTalk.appsCel = o);
            } catch (Throwable ignored) {
                // sin internet o sin permiso: se intenta en la siguiente vuelta
            }
        });
    }

    /** Suma el tiempo de hoy en primer plano por app; devuelve cuándo se abrió Duolingo por última vez. */
    @SuppressWarnings("deprecation")
    static long scan(UsageStatsManager usm, long from, long now, java.util.Map<String, Long> fg) {
        return scan(usm, from, now, fg, null);
    }

    /** Lo mismo y, si unlocks no es null, cuenta cuántas veces se desbloqueó hoy. */
    @SuppressWarnings("deprecation")
    static long scan(UsageStatsManager usm, long from, long now, java.util.Map<String, Long> fg, int[] unlocks) {
        UsageEvents evs = usm.queryEvents(from, now);
        UsageEvents.Event e = new UsageEvents.Event();
        java.util.Map<String, Long> open = new java.util.HashMap<>();
        long duoLast = 0;
        while (evs.hasNextEvent()) {
            evs.getNextEvent(e);
            String p = e.getPackageName();
            int type = e.getEventType();
            long ts = e.getTimeStamp();
            if (unlocks != null && android.os.Build.VERSION.SDK_INT >= 28 && type == UsageEvents.Event.KEYGUARD_HIDDEN) unlocks[0]++;
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (DUO.equals(p)) duoLast = ts;
                if (!open.containsKey(p)) open.put(p, ts);
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                Long st = open.remove(p);
                if (st != null) fg.put(p, fg.getOrDefault(p, 0L) + ts - st);
            }
        }
        for (java.util.Map.Entry<String, Long> en : open.entrySet()) fg.put(en.getKey(), fg.getOrDefault(en.getKey(), 0L) + now - en.getValue());
        return duoLast;
    }

    // ---------- Límites (solo avisan, nunca bloquean) ----------

    /** Aviso suave para cuando regreses a inicio. */
    static volatile String notice;

    static JSONObject limits(Context c) {
        try {
            return new JSONObject(Prefs.sp(c).getString("limits", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static void setLimit(Context c, String pkg, int min) {
        JSONObject o = limits(c);
        try {
            if (min <= 0) o.remove(pkg);
            else o.put(pkg, min);
        } catch (Exception ignored) {
            // nada
        }
        Prefs.sp(c).edit().putString("limits", o.toString()).apply();
        if (min > 0) kick(c);
    }

    private static void checkLimits(Context c, java.util.Map<String, Long> fg) {
        JSONObject lim = limits(c);
        if (lim.length() == 0) return;
        String day = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date());
        java.util.Set<String> warned = new java.util.HashSet<>(Prefs.sp(c).getStringSet("limit_warned", new java.util.HashSet<>()));
        boolean changed = false;
        for (java.util.Iterator<String> it = lim.keys(); it.hasNext(); ) {
            String pkg = it.next();
            int l = lim.optInt(pkg);
            Long ms = fg.get(pkg);
            int m = ms == null ? 0 : (int) (ms / 60_000L);
            if (l > 0 && m >= l && warned.add(day + ":" + pkg)) {
                changed = true;
                String label = IconEngine.label(c, pkg);
                MAIN.post(() -> {
                    PezTalk.limitOver(label, m, l);
                    notice = "Llevas " + PezTalk.fmtMin(m) + " en " + label + " (tu límite: " + PezTalk.fmtMin(l) + "). ¿Un descanso?";
                });
            }
        }
        if (changed) {
            warned.removeIf(s -> !s.startsWith(day));
            Prefs.sp(c).edit().putStringSet("limit_warned", warned).apply();
        }
    }

    private static final int[] LIMITS = {0, 15, 30, 45, 60, 90, 120};

    /** Tiempo de hoy por app y en total; tocar una app le pone o quita un límite. */
    static void panel(MainActivity a) {
        if (!granted(a)) {
            TextView t = Ui.text(a, "Para ver cuánto tiempo llevas en cada app, Android pide el permiso «Acceso al uso». Todo se queda en tu teléfono.", 14, Ui.MUTED, Ui.REGULAR);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), Ui.dp(a, 10));
            List<Sheet.Item> items = new ArrayList<>();
            items.add(Sheet.item("Dar permiso", Icons.CHECK, () -> {
                try {
                    a.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Exception ignored) {
                    // sin pantalla de permisos
                }
                Sheet.pill(a, a.backdrop(), "Busca Jostin OS y activa «Permitir acceso al uso»");
            }));
            Sheet.show(a, a.backdrop(), "Tiempo en pantalla", t, items);
            return;
        }
        Context app = a.getApplicationContext();
        NET.execute(() -> {
            java.util.Map<String, Long> fg = new java.util.HashMap<>();
            try {
                UsageStatsManager usm = (UsageStatsManager) app.getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm != null) scan(usm, startOfDay(), System.currentTimeMillis(), fg);
            } catch (Throwable e) {
                Bitacora.error("tiempo en pantalla", e);
            }
            fg.remove(app.getPackageName());
            List<java.util.Map.Entry<String, Long>> list = new ArrayList<>(fg.entrySet());
            list.removeIf(en -> en.getValue() < 60_000L || app.getPackageManager().getLaunchIntentForPackage(en.getKey()) == null);
            list.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
            long total = 0;
            for (java.util.Map.Entry<String, Long> en : list) total += en.getValue();
            long tot = total;
            MAIN.post(() -> showPanel(a, list, tot));
        });
    }

    private static void showPanel(MainActivity a, List<java.util.Map.Entry<String, Long>> list, long total) {
        if (a.isFinishing()) return;
        JSONObject lim = limits(a);
        TextView head = Ui.text(a, list.isEmpty() ? "Hoy casi no has usado el celular" : "Hoy llevas " + PezTalk.fmtMin((int) (total / 60_000L)) + " en total. Toca una app para ponerle un límite: el pez te avisa, no se bloquea nada.", 14, Ui.MUTED, Ui.REGULAR);
        head.setLineSpacing(0, 1.2f);
        head.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), Ui.dp(a, 10));
        List<Sheet.Item> items = new ArrayList<>();
        for (int i = 0; i < Math.min(15, list.size()); i++) {
            String pkg = list.get(i).getKey();
            int m = (int) (list.get(i).getValue() / 60_000L), l = lim.optInt(pkg, 0);
            String label = IconEngine.label(a, pkg);
            Sheet.Item it = Sheet.item(label, Icons.HISTORY, () -> limitPicker(a, pkg, label));
            try {
                it.drawable(a.getPackageManager().getApplicationIcon(pkg));
            } catch (Exception ignored) {
                // ícono de línea
            }
            String v = PezTalk.fmtMin(m).replace(" minutos", " min") + (l > 0 ? " · límite " + PezTalk.fmtMin(l).replace(" minutos", " min") : "");
            it.value = () -> v;
            it.keepOpen = false;
            items.add(it);
        }
        Sheet.show(a, a.backdrop(), "Tiempo en pantalla", head, items);
    }

    private static void limitPicker(MainActivity a, String pkg, String label) {
        int cur = limits(a).optInt(pkg, 0);
        List<Sheet.Item> items = new ArrayList<>();
        for (int l : LIMITS) {
            String name = l == 0 ? "Sin límite" : PezTalk.fmtMin(l);
            Sheet.Item it = Sheet.item(name, l == cur ? Icons.CHECK : Icons.HISTORY, () -> {
                setLimit(a, pkg, l);
                Sheet.pill(a, a.backdrop(), l == 0 ? label + " ya no tiene límite" : "Te aviso cuando pases de " + name + " en " + label);
            });
            items.add(it);
        }
        Sheet.show(a, a.backdrop(), "Límite para " + label, items);
    }

    private static boolean isTikTok(String p) {
        for (String t : TIKTOK) if (t.equals(p)) return true;
        return false;
    }

    private static void put(String u, String body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestMethod("PUT");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        try {
            try (OutputStream out = con.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            con.getResponseCode();
        } finally {
            con.disconnect();
        }
    }

    /** La pantallita que explica el permiso antes de mandarte a los ajustes de Android. */
    static void explain(Activity a, Backdrop b) {
        TextView t = Ui.text(a, "Con «Acceso al uso», el pez sabe cómo va tu cel hoy y te lo comenta: pantalla, tus apps más usadas, desbloqueos, batería, "
                + "música, cuántas notificaciones tienes por app (nunca su texto ni de quién), tu próximo evento, la alarma, el clima y si ya hiciste Duolingo.\n\n"
                + "Se sube un resumen corto cada 5 minutos con la pantalla prendida. Bancos, Spin y apps de dinero nunca salen. "
                + "Ojo: esos datos los puede leer quien tenga la dirección de tu pez.", 14, Ui.MUTED, Ui.REGULAR);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), Ui.dp(a, 10));
        List<Sheet.Item> items = new ArrayList<>();
        if (enabled(a)) {
            items.add(Sheet.item("Dejar de compartir", Icons.CLOSE, () -> {
                setWanted(a, false);
                Sheet.pill(a, b, "El pez ya no ve tus apps");
            }));
        } else {
            items.add(Sheet.item(granted(a) ? "Activar" : "Dar permiso", Icons.CHECK, () -> {
                setWanted(a, true);
                if (granted(a)) {
                    Sheet.pill(a, b, "Listo, el pez ya ve tus apps");
                    return;
                }
                Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                if (Build.VERSION.SDK_INT >= 29) i.setData(Uri.parse("package:" + a.getPackageName()));
                try {
                    a.startActivity(i);
                } catch (Exception e) {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    } catch (Exception ignored) {
                        // no hay pantalla de permisos
                    }
                }
                Sheet.pill(a, b, "Busca Jostin OS y activa «Permitir acceso al uso»");
            }));
            items.add(Sheet.item("Ahora no", Icons.CLOSE, () -> {}));
        }
        Sheet.show(a, b, "El pez y tus apps", t, items);
    }
}
