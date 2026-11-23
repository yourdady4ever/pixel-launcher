package com.jostin.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * La IA del pez, solo para momentos especiales (saludo del día, al despertar, cuidados desde la PC,
 * subir de nivel, TikTok de más, Duolingo pendiente y a veces al tocarlo). Lo demás son sus frases de siempre.
 * Límites: 1 llamada cada 10 min, 30 al día, nunca de noche y sin reintentos. Si falla, se usa la frase normal.
 * La clave del pez va solo en el cuerpo de la petición; nunca en el registro.
 */
final class PezIA {
    private PezIA() {}

    private static final String URL_IA = "https://pez-ai.ponteenelmapa-servicios.workers.dev";
    private static final long GAP = 10 * 60_000L;
    private static final int PER_DAY = 30;
    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Done {
        void on(String text);
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("pez_ia", Context.MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static boolean night() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 22 || h < 6;
    }

    /** ¿Se puede gastar una llamada ahorita? */
    static boolean allowed(Context c) {
        if (PezKey.get(c) == null || night()) return false;
        SharedPreferences p = sp(c);
        if (System.currentTimeMillis() - p.getLong("at", 0) < GAP) return false;
        return !today().equals(p.getString("day", "")) || p.getInt("n", 0) < PER_DAY;
    }

    /** Una sola vez al día por motivo (TikTok, Duolingo…): true si hoy todavía no se usó. */
    static boolean once(Context c, String what) {
        String d = today();
        if (d.equals(sp(c).getString("once_" + what, ""))) return false;
        sp(c).edit().putString("once_" + what, d).apply();
        return true;
    }

    /** Saludo especial: primera vista del día, al despertar (después de las 6) o tras más de 3 h sin verlo. null si no toca. */
    static String greetMoment(Context c) {
        long last = sp(c).getLong("seen_at", 0), now = System.currentTimeMillis();
        Calendar six = Calendar.getInstance();
        six.set(Calendar.HOUR_OF_DAY, 6);
        six.set(Calendar.MINUTE, 0);
        six.set(Calendar.SECOND, 0);
        six.set(Calendar.MILLISECOND, 0);
        if (last > 0 && last < six.getTimeInMillis() && now >= six.getTimeInMillis()) return "acaba de despertar; es la primera vez que ve al jefe después de dormir";
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(last);
        if (last == 0 || d.get(Calendar.DAY_OF_YEAR) != Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) return "es la primera vez que ve al jefe hoy";
        if (now - last > 3 * 3600_000L) return "el jefe volvió después de más de " + ((now - last) / 3600_000L) + " horas sin verlo";
        return null;
    }

    /** Se anota cuándo se vio por última vez (como mucho una escritura por minuto). */
    static void markSeen(Context c) {
        long now = System.currentTimeMillis();
        if (now - sp(c).getLong("seen_at", 0) > 60_000L) sp(c).edit().putLong("seen_at", now).apply();
    }

    /** ¿Subió de nivel desde la última vez? (la primera lectura solo se anota) */
    static boolean levelUp(Context c, int lv) {
        int old = sp(c).getInt("lvl", -1);
        if (old == lv) return false;
        sp(c).edit().putInt("lvl", lv).apply();
        return old >= 0 && lv > old;
    }

    /** TikTok de más de una hora o Duolingo pendiente después de las 9 pm (una vez al día cada uno). */
    static String appMoment(Context c) {
        JSONObject cel = PezTalk.appsCel, pc = PezTalk.appsPc;
        long now = System.currentTimeMillis();
        if (cel == null || now - cel.optLong("t", 0) > 30 * 60_000L) return null;
        int tt = cel.optInt("tiktok", 0);
        if (tt > 60 && once(c, "tt")) return "hoy lleva " + tt + " min en TikTok";
        boolean duo = cel.optBoolean("duo", false) || (pc != null && pc.optBoolean("duo", false));
        if (!duo && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 21 && once(c, "duo")) return "no ha hecho Duolingo y ya son más de las 9 pm";
        return null;
    }

    /**
     * Pide un comentario. done recibe el texto en el hilo principal, o null si falló o tardó más de waitMs
     * (entonces se dice la frase normal). La llamada se cuenta antes de salir: nunca se reintenta.
     */
    static void ask(Context c, String mood, float food, float happy, String extra, long waitMs, Done done) {
        Context app = c.getApplicationContext();
        String key = PezKey.get(app);
        if (key == null) {
            done.on(null);
            return;
        }
        SharedPreferences p = sp(app);
        String d = today();
        int n = d.equals(p.getString("day", "")) ? p.getInt("n", 0) : 0;
        p.edit().putLong("at", System.currentTimeMillis()).putString("day", d).putInt("n", n + 1).apply();

        final boolean[] answered = {false};
        Runnable timeout = () -> {
            if (answered[0]) return;
            answered[0] = true;
            done.on(null);
        };
        MAIN.postDelayed(timeout, waitMs);
        NET.execute(() -> {
            String text = null;
            HttpURLConnection h = null;
            try {
                JSONObject ctx = new JSONObject()
                        .put("mood", mood)
                        .put("food", Math.round(food))
                        .put("happy", Math.round(happy))
                        .put("hora", new SimpleDateFormat("HH:mm", Locale.US).format(new Date()))
                        .put("donde", "cel")
                        .put("extra", extra == null ? "" : extra.length() > 900 ? extra.substring(0, 900) : extra);
                JSONObject body = new JSONObject().put("k", key).put("msg", "").put("ctx", ctx).put("hist", new JSONArray());
                h = (HttpURLConnection) new URL(URL_IA).openConnection();
                h.setConnectTimeout(4000);
                h.setReadTimeout(5000);
                h.setRequestMethod("POST");
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = h.getOutputStream()) {
                    o.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                if (h.getResponseCode() == 200) {
                    try (InputStream in = h.getInputStream()) {
                        ByteArrayOutputStream b = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int r;
                        while ((r = in.read(buf)) > 0 && b.size() < 16_384) b.write(buf, 0, r);
                        String t = new JSONObject(b.toString("UTF-8")).optString("text", "").replaceAll("\\s+", " ").trim();
                        if (!t.isEmpty()) text = t.length() > 240 ? t.substring(0, 237) + "…" : t;
                    }
                }
            } catch (Exception ignored) {
                // sin internet o la IA no contestó: frase normal (sin escribir la clave en el registro)
            } finally {
                if (h != null) h.disconnect();
            }
            final String out = text;
            MAIN.post(() -> {
                if (answered[0]) return;
                answered[0] = true;
                MAIN.removeCallbacks(timeout);
                done.on(out);
            });
        });
    }
}
