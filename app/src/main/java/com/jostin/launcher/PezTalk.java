package com.jostin.launcher;

import android.content.Context;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Lo que dice el pez: las mismas frases y reglas que el fondo de la PC y la página del celular
 * (tools/personality.js). Las frases vienen en assets/pez/frases.json, sacadas tal cual del JS.
 * Aquí siempre se habla como en el celular: chatter({pocket:true}), sin Godzilla ni música.
 */
final class PezTalk {
    private static JSONObject L;
    private static final Random R = new Random();
    private static final List<String> recent = new ArrayList<>();
    private static final List<Long> clickTimes = new ArrayList<>();
    private static long appCool = 0;

    /** Lo último de /apps.json (pc lo escribe la compu, cel lo escribe este teléfono). */
    static JSONObject appsPc, appsCel;
    /** Cuándo llegó un aviso de racha de Duolingo (solo se guarda la hora, nada del texto). */
    static volatile long duoAlertAt = 0;
    /** Última notificación importante: solo la hora y el nombre de la app, nunca lo que dice. */
    static volatile long notifAt = 0;
    static volatile String notifApp = "";
    private static long notifCool, battCool, plugAt;
    private static String limitApp;
    private static int limitMin, limitMax;

    /** Te pasaste del límite que le pusiste a una app: lo dice en cuanto se vea. */
    static void limitOver(String app, int min, int limit) {
        limitApp = app;
        limitMin = min;
        limitMax = limit;
    }
    private static int battPct = -1, plugged = -1;

    private PezTalk() {}

    private static Context appCtx;
    private static boolean recentLoaded;

    /** Las frases recientes y la hora del último saludo se guardan aparte, para no olvidarlas al recrear el gadget. */
    private static android.content.SharedPreferences sp() {
        return appCtx.getSharedPreferences("pez_talk", Context.MODE_PRIVATE);
    }

    private static synchronized JSONObject phrases(Context c) {
        if (appCtx == null) appCtx = c.getApplicationContext();
        if (L != null) return L;
        try (InputStream in = c.getAssets().open("pez/frases.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            L = new JSONObject(out.toString("UTF-8"));
        } catch (Exception e) {
            L = new JSONObject();
        }
        return L;
    }

    /** Carga las frases fuera del hilo principal (para que el primer saludo no trabe). */
    static void preload(Context c) {
        Context app = c.getApplicationContext();
        Gadgets.BG.execute(() -> phrases(app));
    }

    /** Segundos desde que prendió el teléfono (el «T» del JS). */
    private static double T() {
        return SystemClock.elapsedRealtime() / 1000.0;
    }

    private static List<String> list(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    private static List<String> get(Context c, String key) {
        return list(phrases(c).optJSONArray(key));
    }

    private static List<String> sub(Context c, String group, String key) {
        JSONObject o = phrases(c).optJSONObject(group);
        return o == null ? new ArrayList<>() : list(o.optJSONArray(key));
    }

    private static <E> E pick(List<E> a) {
        return a.get(R.nextInt(a.size()));
    }

    /** Elige entre las que no ha dicho hace poco (las últimas 60); si ya dijo todas, cualquiera. */
    static String pickFresh(List<String> a) {
        if (a == null || a.isEmpty()) return "";
        if (!recentLoaded && appCtx != null) {
            recentLoaded = true;
            try {
                JSONArray r = new JSONArray(sp().getString("recent", "[]"));
                for (int i = 0; i < r.length(); i++) recent.add(r.optString(i));
            } catch (Exception ignored) {
                // sin historial
            }
        }
        List<String> fresh = new ArrayList<>();
        for (String x : a) if (!recent.contains(x)) fresh.add(x);
        String s = pick(fresh.isEmpty() ? a : fresh);
        recent.remove(s);
        recent.add(s);
        while (recent.size() > 60) recent.remove(0);
        if (appCtx != null) sp().edit().putString("recent", new JSONArray(recent).toString()).apply();
        return s;
    }

    /** Hasta cuándo está despierto aunque sea de madrugada (un toque o volver a inicio: 2 min). */
    static long wakeUntil = 0;

    static void wake() {
        wakeUntil = SystemClock.elapsedRealtime() + 120_000;
    }

    /** Duerme de 2 a 6 de la mañana, salvo que lo acaben de despertar. */
    static boolean sleeping() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 2 && h < 6 && SystemClock.elapsedRealtime() > wakeUntil;
    }

    static List<String> seasonal(Context c) {
        Calendar d = Calendar.getInstance();
        int m = d.get(Calendar.MONTH), day = d.get(Calendar.DAY_OF_MONTH), wd = d.get(Calendar.DAY_OF_WEEK) - 1;
        List<String> out = new ArrayList<>();
        if (m == 11 && day < 24) {
            int n = 24 - day;
            String s = n > 1 ? "s" : "";
            out.add("Faltan " + n + " día" + s + " para Nochebuena 🎄 ¿Ya tienes mi regalo?");
            out.add("¡" + n + " día" + s + " para Nochebuena! Ya quiero mi croqueta navideña");
        }
        if (m == 11 && (day == 24 || day == 25)) {
            out.add("¡FELIZ NAVIDAD! 🎄 Mi regalo para ti: existir. De nada.");
            out.add("¡Navidad! Quiero tamales… de croqueta");
        }
        if (m == 11 && day == 31) out.add("¡Último día del año! Mi propósito: comer más 🍤");
        if (m == 0 && day == 1) out.add("¡Feliz año nuevo! 🎆 Este año sí voy a hacer ejercicio. Mentira.");
        if (wd == 1) out.addAll(get(c, "monday"));
        if (wd == 5) out.addAll(get(c, "friday"));
        if (wd == 0 || wd == 6) out.addAll(get(c, "weekend"));
        return out;
    }

    /** Saluda solo si pasaron más de 15 min desde el último saludo; si no, platica normal. */
    static String greet(Context c, float food, float happy) {
        phrases(c);
        long now = System.currentTimeMillis();
        if (now - sp().getLong("greet_at", 0) < 15 * 60_000L) return chatter(c, food, happy);
        sp().edit().putLong("greet_at", now).apply();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (food < 30) return "¡Volviste! Tengo un hambre… 🍤";
        // En la mañana, a veces con el clima que ya tiene el widget del clima
        Weather.Now w = Weather.cached(c);
        if (h >= 5 && h < 12 && w != null && R.nextDouble() < .6) {
            List<String> out = new ArrayList<>();
            String sky = Weather.describe(w.code).toLowerCase(new Locale("es", "MX"));
            for (String x : get(c, "weatherMorning")) out.add(x.replace("{t}", String.valueOf(w.temp)).replace("{cielo}", sky));
            return pickFresh(out);
        }
        return pickFresh(get(c, h < 5 ? "lateNight" : h < 12 ? "morning" : h < 19 ? "afternoon" : "evening"));
    }

    /** Lee la batería (sin quedarse escuchando); si acabas de conectar el cargador lo anota. */
    static void pollBattery(Context c) {
        try {
            android.content.Intent b = c.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return;
            int lv = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
            int pl = b.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0 ? 1 : 0;
            if (plugged == 0 && pl == 1) plugAt = System.currentTimeMillis();
            plugged = pl;
            battPct = lv < 0 || sc <= 0 ? -1 : lv * 100 / sc;
        } catch (Exception ignored) {
            // sin datos de batería
        }
    }

    /** Último porcentaje de batería leído (-1 si no se sabe). */
    static int battery() {
        return battPct;
    }

    /** Lo que vale la pena decir ya: cargador recién conectado o una notificación importante. */
    static String urgent(Context c) {
        long now = System.currentTimeMillis();
        if (plugAt > 0) {
            boolean fresh = now - plugAt < 60_000;
            plugAt = 0;
            if (fresh) return pickFresh(get(c, "charging"));
        }
        if (limitApp != null) {
            List<String> out = new ArrayList<>();
            for (String x : get(c, "limitOver")) out.add(x.replace("{app}", limitApp).replace("{t}", fmtMin(limitMin)).replace("{l}", fmtMin(limitMax)));
            limitApp = null;
            return pickFresh(out);
        }
        if (notifAt > 0 && now - notifAt < 60_000 && now > notifCool && !notifApp.isEmpty()) {
            notifAt = 0;
            notifCool = now + 3 * 60_000L;
            List<String> out = new ArrayList<>();
            for (String x : get(c, "notif")) out.add(x.replace("{app}", notifApp));
            return pickFresh(out);
        }
        return null;
    }

    /** Batería baja (menos de 15 % y sin cargar), cuando mucho cada 10 min. */
    private static String battLine(Context c) {
        long now = System.currentTimeMillis();
        if (battPct >= 0 && battPct < 15 && plugged == 0 && now > battCool) {
            battCool = now + 10 * 60_000L;
            return pickFresh(get(c, "battLow"));
        }
        return null;
    }

    /** Frase del estado de ánimo (enojado, mareado, enamorado…), si tiene. */
    static String moodLine(Context c, String mood) {
        List<String> a = sub(c, "moods", mood);
        return a.isEmpty() ? null : pickFresh(a);
    }

    static String chatter(Context c, float food, float happy) {
        return chatter(c, food, happy, null);
    }

    static String chatter(Context c, float food, float happy, String mood) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (sleeping()) return pickFresh(get(c, "night"));
        if (food < 10) return pickFresh(get(c, "starving"));
        String batt = battLine(c);
        if (batt != null) return batt;
        if (mood != null && R.nextDouble() < .3) {
            String m = moodLine(c, mood);
            if (m != null) return m;
        }
        if (food < 30 && R.nextDouble() < .45) return pickFresh(get(c, "hungry"));
        if (happy < 30 && R.nextDouble() < .4) return pickFresh(get(c, "bored"));
        if (T() > appCool) {
            String a = appLine(c);
            if (a != null && R.nextDouble() < .6) {
                appCool = (long) (T() + 150);
                return a;
            }
        }
        if ((h >= 23 || h < 2) && R.nextDouble() < .35) return pickFresh(get(c, "lateNight"));
        List<List<String>> pools = new ArrayList<>();
        List<String> talk = get(c, "talk"), pocket = get(c, "pocket");
        pools.add(talk);
        pools.add(talk);
        pools.add(get(c, "jokes"));
        pools.add(get(c, "facts"));
        pools.add(get(c, "brag"));
        pools.add(pocket);
        pools.add(pocket);
        if (happy > 85) pools.add(get(c, "happy"));
        List<String> s = seasonal(c);
        if (!s.isEmpty()) pools.add(s);
        return pickFresh(pick(pools));
    }

    /** Al tocarlo; con 3 toques en 4 s se marea. */
    static String clickLine(Context c) {
        double now = T();
        clickTimes.removeIf(t -> now - t / 1000.0 >= 4);
        clickTimes.add((long) (now * 1000));
        return pickFresh(get(c, clickTimes.size() >= 3 ? "poke" : "click"));
    }

    static String lookLine(Context c, String hat) {
        List<String> a = new ArrayList<>(sub(c, "hat", hat));
        a.addAll(get(c, "look"));
        return pickFresh(a);
    }

    /** Lo que dice cuando lo cuidaron desde la compu (feed, play, look o talk). */
    static String fromPc(Context c, String ev) {
        return pickFresh(sub(c, "fromPc", ev));
    }

    static String fmtMin(int m) {
        if (m < 60) return m + " minutos";
        double h = Math.round(m / 6.0) / 10.0;
        String n = h == Math.rint(h) ? String.valueOf((long) h) : String.format(Locale.US, "%.1f", h);
        return n + " hora" + (h == 1 ? "" : "s");
    }

    private static List<String> fill(List<String> a, String app, int m) {
        List<String> out = new ArrayList<>();
        for (String x : a) out.add(x.replace("{app}", app).replace("{t}", fmtMin(m)));
        return out;
    }

    /** Frases sobre tus apps (TikTok, racha de Duolingo y chismes de la compu), como appLine({pocket:true}). */
    static String appLine(Context c) {
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        JSONObject pc = appsPc, cel = appsCel;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (pc != null && !pc.optString("app", "").isEmpty() && now - pc.optLong("t", 0) < 5 * 60_000L) {
            int m = pc.optInt("min", 0);
            if (m >= 45) out.addAll(fill(get(c, "gossipPc"), pc.optString("app"), m));
        }
        if (cel != null && now - cel.optLong("t", 0) < 30 * 60_000L && cel.optInt("tiktok", 0) >= 45) {
            out.addAll(fill(get(c, "celTikTok"), "TikTok", cel.optInt("tiktok", 0)));
        }
        boolean duoDone = (cel != null && cel.optBoolean("duo", false)) || (pc != null && pc.optBoolean("duo", false));
        if (cel != null && now - cel.optLong("t", 0) < 6 * 3600_000L && !duoDone && h >= 18) out.addAll(get(c, "celDuo"));
        return out.isEmpty() ? null : pickFresh(out);
    }

    /** Si Duolingo mandó aviso de racha hace poco, una frase de celDuo (una sola vez por aviso). */
    static String duoAlert(Context c, long seenAlert) {
        long at = duoAlertAt;
        if (at == 0 || at == seenAlert || System.currentTimeMillis() - at > 30 * 60_000L) return null;
        return pickFresh(get(c, "celDuo"));
    }
}
