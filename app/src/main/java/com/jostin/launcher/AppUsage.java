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
 * «Acceso al uso», cada ~5 min y solo con la pantalla prendida, sube a /apps/cel tres datos:
 * minutos de TikTok hoy, si abriste Duolingo hoy y la hora. Nada de historial ni otras apps.
 */
final class AppUsage {
    static final String URL_CEL = "https://pez-estudio-default-rtdb.firebaseio.com/apps/cel.json";
    private static final String[] TIKTOK = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"};
    private static final String DUO = "com.duolingo";
    private static final long EVERY = 5 * 60_000L;
    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context app;
    private static boolean attached;

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            MAIN.removeCallbacks(this);
            if (app == null || !screenOn(app)) return;
            if (enabled(app)) report(app);
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
                UsageEvents evs = usm.queryEvents(from, now);
                UsageEvents.Event e = new UsageEvents.Event();
                long tiktokMs = 0, openAt = -1, duoLast = 0;
                while (evs.hasNextEvent()) {
                    evs.getNextEvent(e);
                    String p = e.getPackageName();
                    int type = e.getEventType();
                    if (DUO.equals(p) && type == UsageEvents.Event.MOVE_TO_FOREGROUND) duoLast = e.getTimeStamp();
                    if (!isTikTok(p)) continue;
                    if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        if (openAt < 0) openAt = e.getTimeStamp();
                    } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND && openAt >= 0) {
                        tiktokMs += e.getTimeStamp() - openAt;
                        openAt = -1;
                    }
                }
                if (openAt >= 0) tiktokMs += now - openAt;
                // Si Duolingo avisó de la racha después de abrirlo, todavía no está hecha
                boolean duo = duoLast >= from && duoLast > PezTalk.duoAlertAt;
                JSONObject o = new JSONObject();
                o.put("tiktok", (int) (tiktokMs / 60_000L));
                o.put("duo", duo);
                o.put("t", now);
                put(URL_CEL, o.toString());
                MAIN.post(() -> PezTalk.appsCel = o);
            } catch (Throwable ignored) {
                // sin internet o sin permiso: se intenta en la siguiente vuelta
            }
        });
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
        TextView t = Ui.text(a, "Con «Acceso al uso», el pez sabe cuántos minutos llevas hoy en TikTok y si ya abriste Duolingo, y te lo comenta.\n\n"
                + "Solo se suben esos dos datos y la hora, cada 5 minutos con la pantalla prendida. Nada de historial ni otras apps. "
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
