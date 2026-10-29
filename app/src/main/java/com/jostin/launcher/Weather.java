package com.jostin.launcher;

import android.content.Context;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Clima de tu ciudad con Open-Meteo (gratis, sin cuenta). Se pide como mucho cada 30 minutos y
 * solo cuando el launcher está a la vista; mientras tanto se usa lo guardado.
 */
final class Weather {
    static final class Now {
        int temp, max, min, code;
        boolean day;
        String place;
    }

    interface Done {
        void done(Now now);
    }

    private static final long FRESH_MS = 30 * 60_000L;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile boolean busy = false;

    private Weather() {}

    static Now cached(Context c) {
        String s = Prefs.weatherCache(c);
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            Now n = new Now();
            n.temp = o.getInt("t");
            n.max = o.getInt("mx");
            n.min = o.getInt("mn");
            n.code = o.getInt("c");
            n.day = o.optBoolean("d", true);
            n.place = Prefs.place(c);
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    /** Actualiza si hace falta. Llama a done en el hilo principal solo si hubo datos nuevos. */
    static void refresh(Context c, boolean force, Done done) {
        String city = Prefs.city(c);
        if (city.isEmpty() || busy) return;
        if (!force && System.currentTimeMillis() - Prefs.weatherTime(c) < FRESH_MS && cached(c) != null) return;
        Context app = c.getApplicationContext();
        busy = true;
        new Thread(() -> {
            Now result = null;
            try {
                float la = Prefs.lat(app), lo = Prefs.lon(app);
                if (Float.isNaN(la) || Float.isNaN(lo)) {
                    JSONObject g = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=es&name="
                            + URLEncoder.encode(city, "UTF-8")));
                    JSONArray rs = g.optJSONArray("results");
                    if (rs == null || rs.length() == 0) throw new Exception("ciudad no encontrada");
                    JSONObject r = rs.getJSONObject(0);
                    la = (float) r.getDouble("latitude");
                    lo = (float) r.getDouble("longitude");
                    Prefs.setCoords(app, la, lo, r.optString("name", city));
                }
                JSONObject f = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + la + "&longitude=" + lo
                        + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"));
                JSONObject cur = f.getJSONObject("current");
                JSONObject daily = f.getJSONObject("daily");
                JSONObject save = new JSONObject();
                save.put("t", (int) Math.round(cur.getDouble("temperature_2m")));
                save.put("c", cur.getInt("weather_code"));
                save.put("d", cur.optInt("is_day", 1) == 1);
                save.put("mx", (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)));
                save.put("mn", (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)));
                Prefs.setWeather(app, save.toString());
                result = cached(app);
            } catch (Exception e) {
                result = null;
            }
            busy = false;
            final Now r = result;
            if (r != null) main.post(() -> done.done(r));
        }).start();
    }

    private static String get(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestProperty("User-Agent", "JostinOS/1.0");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    static String describe(int code) {
        if (code == 0) return "Despejado";
        if (code <= 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Niebla";
        if (code >= 51 && code <= 57) return "Llovizna";
        if (code >= 61 && code <= 67) return "Lluvia";
        if (code >= 71 && code <= 77) return "Nieve";
        if (code >= 80 && code <= 82) return "Chubascos";
        if (code >= 95) return "Tormenta";
        return "Variable";
    }

    static Icons.Shape icon(int code, boolean day) {
        if (code == 0) return day ? SUN : MOON;
        if (code <= 2) return day ? SUN_CLOUD : CLOUD;
        if (code == 3) return CLOUD;
        if (code == 45 || code == 48) return FOG;
        if (code >= 71 && code <= 77) return SNOW;
        if (code >= 95) return STORM;
        if (code >= 51) return RAIN;
        return CLOUD;
    }

    // ---------- Íconos del clima ----------

    static final Icons.Shape SUN = p -> {
        p.addCircle(12, 12, 4.2f, Path.Direction.CW);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            p.moveTo(12 + (float) Math.cos(a) * 7, 12 + (float) Math.sin(a) * 7);
            p.lineTo(12 + (float) Math.cos(a) * 9.5f, 12 + (float) Math.sin(a) * 9.5f);
        }
    };

    static final Icons.Shape MOON = p -> {
        p.moveTo(15.5f, 4.2f);
        p.cubicTo(9.5f, 4.5f, 6.5f, 11f, 9.8f, 16f);
        p.cubicTo(12.5f, 20f, 18f, 20.2f, 20.5f, 16.5f);
        p.cubicTo(15f, 17f, 11.8f, 10.5f, 15.5f, 4.2f);
        p.close();
    };

    private static void cloud(Path p, float dx, float dy) {
        p.moveTo(7f + dx, 18.5f + dy);
        p.cubicTo(3.8f + dx, 18.5f + dy, 3.5f + dx, 13.8f + dy, 6.6f + dx, 13.4f + dy);
        p.cubicTo(7f + dx, 9.5f + dy, 12.6f + dx, 8.4f + dy, 14.2f + dx, 12f + dy);
        p.cubicTo(17.6f + dx, 11f + dy, 20.2f + dx, 13.8f + dy, 19f + dx, 16.5f + dy);
        p.cubicTo(18.6f + dx, 17.8f + dy, 17.6f + dx, 18.5f + dy, 16.5f + dx, 18.5f + dy);
        p.close();
    }

    static final Icons.Shape CLOUD = p -> cloud(p, 0, -1.5f);

    static final Icons.Shape SUN_CLOUD = p -> {
        p.addArc(new RectF(9.5f, 3.5f, 17.5f, 11.5f), 150, 250);
        p.moveTo(13.5f, 1.2f);
        p.lineTo(13.5f, 2.2f);
        p.moveTo(19.5f, 7.5f);
        p.lineTo(20.5f, 7.5f);
        p.moveTo(18f, 3f);
        p.lineTo(18.7f, 2.3f);
        cloud(p, -1f, 2f);
    };

    static final Icons.Shape RAIN = p -> {
        cloud(p, 0, -4f);
        for (int i = 0; i < 3; i++) {
            p.moveTo(8.5f + i * 3.5f, 17.2f);
            p.lineTo(7.5f + i * 3.5f, 20.5f);
        }
    };

    static final Icons.Shape STORM = p -> {
        cloud(p, 0, -4f);
        p.moveTo(12.5f, 15.5f);
        p.lineTo(10.5f, 19f);
        p.lineTo(13f, 19f);
        p.lineTo(11f, 22.5f);
    };

    static final Icons.Shape SNOW = p -> {
        cloud(p, 0, -4f);
        for (int i = 0; i < 3; i++) p.addCircle(8.5f + i * 3.5f, 19f, 0.4f, Path.Direction.CW);
    };

    static final Icons.Shape FOG = p -> {
        for (int i = 0; i < 4; i++) {
            p.moveTo(4f + (i % 2) * 2, 7f + i * 3.5f);
            p.lineTo(20f - ((i + 1) % 2) * 2, 7f + i * 3.5f);
        }
    };
}
