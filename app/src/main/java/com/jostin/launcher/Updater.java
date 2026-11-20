package com.jostin.launcher;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Busca si en GitHub hay una versión más nueva de Jostin OS y la instala con el instalador de
 * Android (siempre te pide confirmar). Al abrir revisa en silencio una vez al día.
 */
final class Updater {
    static final String LATEST = "https://api.github.com/repos/yourdady4ever/pixel-launcher/releases/latest";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean busy;

    private Updater() {}

    interface Found {
        /** tag null = no se pudo revisar; url null = ya tienes la más nueva. */
        void found(String tag, String url);
    }

    /** "1.0.21" contra "v1.0.22": ¿la segunda es más nueva? */
    static boolean newer(String have, String tag) {
        String[] a = have.replaceAll("[^0-9.]", "").split("\\."), b = tag.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
            if (x != y) return y > x;
        }
        return false;
    }

    static void check(Context c, Found done) {
        Context app = c.getApplicationContext();
        new Thread(() -> {
            String tag = null, url = null;
            try {
                JSONObject o = new JSONObject(Weather.get(LATEST));
                tag = o.optString("tag_name", null);
                JSONArray as = o.optJSONArray("assets");
                if (tag != null && as != null && newer(Bitacora.version(app), tag)) {
                    for (int i = 0; i < as.length(); i++) {
                        JSONObject x = as.getJSONObject(i);
                        if (x.optString("name").endsWith(".apk")) url = x.optString("browser_download_url");
                    }
                }
                Prefs.sp(app).edit().putLong("update_checked", System.currentTimeMillis())
                        .putString("update_tag", url != null ? tag : "").apply();
            } catch (Throwable e) {
                Bitacora.error("buscar actualización", e);
            }
            String t = tag, u = url;
            MAIN.post(() -> done.found(t, u));
        }).start();
    }

    /** Al abrir: como mucho una vez al día y sin molestar; si hay nueva, avisa con una pastilla. */
    static void daily(MainActivity a) {
        long last = Prefs.sp(a).getLong("update_checked", 0);
        if (System.currentTimeMillis() - last < 24 * 3600_000L) return;
        check(a, (tag, url) -> {
            if (url != null && !a.isFinishing()) Sheet.pill(a, a.backdrop(), "Hay una versión nueva de Jostin OS (" + tag + "). Ve a Personalizar › Inicio › Buscar actualización");
        });
    }

    /** Lo que sale en ajustes junto al botón: "Hay nueva" o tu versión. */
    static String status(Context c) {
        String tag = Prefs.sp(c).getString("update_tag", "");
        return !tag.isEmpty() && newer(Bitacora.version(c), tag) ? "● " + tag + " lista" : "v" + Bitacora.version(c);
    }

    /** El botón de ajustes: revisa, pregunta y descarga. */
    static void ask(Activity a, Backdrop b) {
        if (busy) {
            Sheet.pill(a, b, "Ya se está descargando…");
            return;
        }
        Sheet.pill(a, b, "Buscando actualización…");
        check(a, (tag, url) -> {
            if (tag == null) {
                Sheet.pill(a, b, "No pude revisar. ¿Tienes internet?");
            } else if (url == null) {
                Sheet.pill(a, b, "Ya tienes la más nueva (v" + Bitacora.version(a) + ")");
            } else {
                Sheet.show(a, b, "Versión nueva: " + tag,
                        Sheet.item("Descargar e instalar", Icons.CHECK, () -> install(a, b, url)),
                        Sheet.item("Ahora no", Icons.CLOSE, () -> {}));
            }
        });
    }

    private static void install(Activity a, Backdrop b, String url) {
        // Android pide una vez que permitas instalar apps desde Jostin OS
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Sheet.pill(a, b, "Activa «Permitir de esta fuente» y vuelve a tocar Buscar actualización");
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) {
                // sin pantalla de permiso
            }
            return;
        }
        busy = true;
        Sheet.pill(a, b, "Descargando la versión nueva…");
        Context app = a.getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            try {
                PackageInstaller pi = app.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int id = pi.createSession(sp);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                    con.setConnectTimeout(10_000);
                    con.setReadTimeout(20_000);
                    con.setInstanceFollowRedirects(true);
                    try (InputStream in = con.getInputStream(); OutputStream out = s.openWrite("jostin.apk", 0, -1)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        s.fsync(out);
                    } finally {
                        con.disconnect();
                    }
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent done = PendingIntent.getBroadcast(app, 7, new Intent(app, Done.class), flags);
                    s.commit(done.getIntentSender());
                    ok = true;
                }
            } catch (Throwable e) {
                Bitacora.error("descargar actualización", e);
            }
            boolean fine = ok;
            MAIN.post(() -> {
                busy = false;
                if (!fine) Sheet.pill(a, b, "No se pudo descargar. Intenta otra vez");
            });
        }).start();
    }

    /** Android contesta aquí: casi siempre pide que confirmes la instalación. */
    public static final class Done extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        c.startActivity(confirm);
                    } catch (Exception e) {
                        Bitacora.error("abrir instalador", e);
                    }
                }
            } else if (st != PackageInstaller.STATUS_SUCCESS) {
                Bitacora.log("Actualización no instalada: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }
    }
}
