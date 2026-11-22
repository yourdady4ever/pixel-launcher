package com.jostin.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Registro pequeño de lo que falla y de cuándo se traba la pantalla, para poder mandarlo.
 * Guarda en dos archivos de 48 KB que se van turnando; nada sale del teléfono si tú no lo compartes.
 */
final class Bitacora {
    private static final long MAX = 48 * 1024;
    private static File file, old;
    /** Qué estaba a la vista (inicio, cajón, ajustes…), para saber dónde fue el trabón. */
    static volatile String screen = "inicio";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean watching;
    private static volatile long postedAt, ranAt;
    private static Thread dog;

    private Bitacora() {}

    static synchronized void init(Context c) {
        if (file != null) return;
        File dir = c.getApplicationContext().getFilesDir();
        file = new File(dir, "registro.txt");
        PezKey.get(c);
        old = new File(dir, "registro.1.txt");
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            error("Cierre inesperado en " + t.getName(), e);
            // Si el panel de Android está bloqueado, se suelta ya (si no, queda bloqueado mientras sale el aviso de error)
            PanelPropio.onCrash();
            if (prev != null) prev.uncaughtException(t, e);
        });
        log("Jostin OS " + version(c) + " abrió · Android " + Build.VERSION.RELEASE + " · " + Build.MODEL);
    }

    static String version(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    static synchronized void log(String msg) {
        if (file == null) return;
        try {
            if (file.length() > MAX) {
                old.delete();
                file.renameTo(old);
            }
            try (FileWriter w = new FileWriter(file, true)) {
                w.write(new SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new Date()) + "  " + PezKey.hide(msg) + "\n");
            }
        } catch (Exception ignored) {
            // si no se puede escribir, no pasa nada
        }
    }

    static void error(String where, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        if (s.length() > 3000) s = s.substring(0, 3000) + "…";
        log("ERROR " + where + " [" + screen + "]: " + s);
    }

    // ---------- Detector de trabones ----------

    private static final Runnable pong = () -> {
        ranAt = SystemClock.uptimeMillis();
        long late = ranAt - postedAt;
        if (late > 200) log("Trabón de " + late + " ms en " + screen);
    };

    /**
     * Un hilo aparte le manda un «¿sigues ahí?» al hilo principal cada medio segundo. Si tarda más de
     * 200 ms en contestar, se anota; si pasa de 2 s, se anota también qué estaba haciendo.
     * Solo corre mientras inicio está a la vista.
     */
    static synchronized void watch(boolean on) {
        watching = on;
        if (!on || dog != null) return;
        dog = new Thread(() -> {
            while (watching) {
                postedAt = SystemClock.uptimeMillis();
                ranAt = 0;
                MAIN.post(pong);
                boolean dumped = false;
                try {
                    Thread.sleep(500);
                    while (watching && ranAt == 0) {
                        Thread.sleep(250);
                        if (!dumped && SystemClock.uptimeMillis() - postedAt > 2000) {
                            dumped = true;
                            StringBuilder sb = new StringBuilder("Trabado más de 2 s en " + screen + ". Iba en:\n");
                            StackTraceElement[] st = Looper.getMainLooper().getThread().getStackTrace();
                            for (int i = 0; i < Math.min(14, st.length); i++) sb.append("    at ").append(st[i]).append('\n');
                            log(sb.toString());
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
            synchronized (Bitacora.class) {
                dog = null;
            }
        }, "trabones");
        dog.setDaemon(true);
        dog.start();
    }

    // ---------- Ver y compartir ----------

    static String read() {
        StringBuilder sb = new StringBuilder();
        try {
            if (old != null && old.exists()) sb.append(new String(Files.readAllBytes(old.toPath()), StandardCharsets.UTF_8));
            if (file != null && file.exists()) sb.append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // vacío
        }
        return sb.length() == 0 ? "El registro está vacío." : sb.toString();
    }

    static void share(Activity a) {
        String txt = read();
        if (txt.length() > 90_000) txt = txt.substring(txt.length() - 90_000);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Registro de Jostin OS " + version(a));
        i.putExtra(Intent.EXTRA_TEXT, txt);
        try {
            a.startActivity(Intent.createChooser(i, "Compartir registro"));
        } catch (Exception ignored) {
            // nada con qué compartir
        }
    }

    static synchronized void clear() {
        if (file != null) file.delete();
        if (old != null) old.delete();
    }
}
