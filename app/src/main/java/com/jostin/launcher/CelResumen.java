package com.jostin.launcher;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.provider.CalendarContract;
import android.service.notification.StatusBarNotification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resumen corto (≤600 caracteres) de cómo va tu cel, para que el pez lo sepa: pantalla, apps, batería,
 * música, cuántas notificaciones por app (nunca su texto), próximo evento, alarma, estado y clima.
 * Bancos, Spin y apps de dinero quedan fuera por completo. Nunca se escribe en el registro.
 * Se arma solo en el tick de AppUsage (cada 5 min con la pantalla prendida).
 */
final class CelResumen {
    private CelResumen() {}

    static final int MAX = 600;
    /** El último resumen armado (para la IA del gadget); null si no se comparte. */
    static volatile String last;

    private static final Pattern MONEY = Pattern.compile(
            "spin|oxxo|bank|banco|banca|bbva|santander|banorte|banamex|citi|hsbc|scotia|azteca|inbursa|banregio|afirme|"
                    + "nu\\.production|mercadopago|paypal|pago|wallet|pay|cash|money|finan|credit|klar|stori|coppel|ualabee|uala|"
                    + "didi.*pay|bitso|binance|crypto|broker|invest|gbm|cetes|fintech|walletnfcrel");

    private static final Locale ES = new Locale("es", "MX");

    /** Apps de dinero (y Spin): ni se cuentan ni se nombran. */
    static boolean money(String pkg) {
        return pkg == null || MONEY.matcher(pkg.toLowerCase(Locale.ROOT)).find();
    }

    private static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String dur(long ms) {
        long m = ms / 60_000L;
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min";
    }

    /** fg = minutos en primer plano de hoy por paquete (de AppUsage.scan). */
    static String build(Context c, Map<String, Long> fg, int unlocks, boolean duo) {
        List<String> parts = new ArrayList<>();
        battery(c, parts);
        usage(c, fg, unlocks, parts);
        notifs(c, parts);
        music(parts);
        calendar(c, parts);
        alarm(c, parts);
        state(c, parts);
        Weather.Now w = Weather.cached(c);
        if (w != null) parts.add("clima " + w.temp + "° " + Weather.describe(w.code).toLowerCase(ES));
        parts.add(duo ? "Duolingo hecho" : "Duolingo pendiente");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (b.length() + p.length() + 2 > MAX) break;
            if (b.length() > 0) b.append("; ");
            b.append(p);
        }
        return b.toString();
    }

    private static void battery(Context c, List<String> out) {
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return;
            int lv = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (lv < 0 || sc <= 0) return;
            boolean plug = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            int temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10;
            out.add("batería " + (lv * 100 / sc) + "% " + (plug ? "cargando" : "sin cargar") + (temp >= 40 ? ", caliente " + temp + "°C" : ""));
        } catch (Exception ignored) {
            // sin datos
        }
    }

    private static void usage(Context c, Map<String, Long> fg, int unlocks, List<String> out) {
        if (fg == null || fg.isEmpty()) return;
        long total = 0;
        List<Map.Entry<String, Long>> list = new ArrayList<>();
        for (Map.Entry<String, Long> e : fg.entrySet()) {
            String p = e.getKey();
            total += e.getValue();
            if (money(p) || p.equals(c.getPackageName()) || p.contains("launcher") || p.equals("com.android.systemui")) continue;
            if (e.getValue() >= 60_000L) list.add(e);
        }
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder top = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Long> e : list) {
            if (n >= 5) break;
            String l = label(c, e.getKey());
            if (l == null) continue;
            if (n > 0) top.append(", ");
            top.append(l).append(' ').append(e.getValue() / 60_000L).append(" min");
            n++;
        }
        out.add("hoy " + dur(total) + " de pantalla" + (n > 0 ? " (" + top + ")" : ""));
        if (unlocks > 0) out.add(unlocks + " desbloqueos");
    }

    /** Solo cuántas hay y de qué app; nunca el texto ni de quién. */
    private static void notifs(Context c, List<String> out) {
        NotifService s = NotifService.instance;
        if (s == null) return;
        StatusBarNotification[] all;
        try {
            all = s.getActiveNotifications();
        } catch (Exception e) {
            return;
        }
        if (all == null) return;
        Map<String, Integer> per = new HashMap<>();
        int total = 0;
        for (StatusBarNotification n : all) {
            String p = n.getPackageName();
            if (n.isOngoing() || (n.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            if (money(p) || p.equals(c.getPackageName()) || p.equals("android") || p.equals("com.android.systemui")) continue;
            per.put(p, per.getOrDefault(p, 0) + 1);
            total++;
        }
        if (total == 0) {
            out.add("sin notificaciones pendientes");
            return;
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(per.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder b = new StringBuilder();
        int k = 0;
        for (Map.Entry<String, Integer> e : list) {
            if (k >= 4) break;
            String l = label(c, e.getKey());
            if (l == null) continue;
            if (k > 0) b.append(", ");
            b.append(l).append(' ').append(e.getValue());
            k++;
        }
        out.add(total + " notificaciones sin ver" + (k > 0 ? " (" + b + ")" : ""));
    }

    private static void music(List<String> out) {
        try {
            if (!Media.active() || !Media.playing()) return;
            String t = Media.title(), a = Media.artist();
            if (t == null || t.isEmpty() || money(Media.appPackage())) return;
            out.add("sonando \"" + cut(t, 50) + "\"" + (a != null && !a.isEmpty() ? " de " + cut(a, 40) : ""));
        } catch (Exception ignored) {
            // nada
        }
    }

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n - 1) + "…" : s;
    }

    /** Próximo evento de hoy o mañana (solo si ya diste permiso de calendario para el widget de agenda). */
    private static void calendar(Context c, List<String> out) {
        if (c.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return;
        long now = System.currentTimeMillis();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.DAY_OF_YEAR, 1);
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        android.net.Uri.Builder u = CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(u, now);
        android.content.ContentUris.appendId(u, end.getTimeInMillis());
        try (Cursor cur = c.getContentResolver().query(u.build(),
                new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY},
                CalendarContract.Instances.BEGIN + ">=?", new String[]{String.valueOf(now)}, CalendarContract.Instances.BEGIN + " ASC")) {
            if (cur == null || !cur.moveToFirst()) return;
            String title = cur.getString(0);
            long begin = cur.getLong(1);
            boolean allDay = cur.getInt(2) != 0;
            if (title == null || title.isEmpty()) return;
            Calendar b = Calendar.getInstance();
            b.setTimeInMillis(begin);
            boolean today = b.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            out.add("próximo evento: " + cut(title, 50) + " " + (today ? "hoy" : "mañana")
                    + (allDay ? "" : " " + new SimpleDateFormat("H:mm", ES).format(new Date(begin))));
        } catch (Exception ignored) {
            // sin calendario
        }
    }

    private static void alarm(Context c, List<String> out) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo a = am == null ? null : am.getNextAlarmClock();
            if (a == null || a.getTriggerTime() - System.currentTimeMillis() > 36 * 3600_000L) return;
            out.add("alarma " + new SimpleDateFormat("EEE H:mm", ES).format(new Date(a.getTriggerTime())));
        } catch (Exception ignored) {
            // nada
        }
    }

    private static void state(Context c, List<String> out) {
        List<String> s = new ArrayList<>();
        try {
            if (NotifService.dndOn(c)) s.add("no molestar");
        } catch (Exception ignored) {
            // nada
        }
        if (Switches.on(c, Switches.PLANE)) s.add("modo avión");
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) s.add("sin internet");
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s.add("en Wi-Fi");
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) s.add("con datos");
        } catch (Exception ignored) {
            // nada
        }
        try {
            StatFs f = new StatFs(Environment.getDataDirectory().getPath());
            long free = f.getAvailableBytes(), all = f.getTotalBytes();
            if (all > 0 && free * 100 / all < 10) s.add("almacenamiento casi lleno (" + (free >> 30) + " GB libres)");
        } catch (Exception ignored) {
            // nada
        }
        if (!s.isEmpty()) out.add(String.join(", ", s));
    }
}
