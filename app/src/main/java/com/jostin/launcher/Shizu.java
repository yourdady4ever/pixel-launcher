package com.jostin.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Puente con Shizuku: dice si está listo y corre comandos de shell con su UserService.
 * Sin Shizuku todo sigue funcionando; lo extra se esconde y se activa solo cuando Shizuku arranca.
 * El servicio de shell se abre solo cuando hay un comando y se cierra al minuto sin uso.
 */
final class Shizu {
    private Shizu() {}

    static final String PKG = "moe.shizuku.privileged.api";
    private static final int REQ = 7301;
    private static final long IDLE_MS = 60_000;

    enum Estado { NO_INSTALADO, NO_CORRE, SIN_PERMISO, LISTO }

    /** Resultado de un comando: código de salida (0 = bien) y lo que imprimió. */
    static final class Res {
        final int code;
        final String out;

        Res(int code, String out) {
            this.code = code;
            this.out = out == null ? "" : out;
        }

        boolean ok() {
            return code == 0;
        }
    }

    interface Done {
        void on(Res r);
    }

    private static Context app;
    private static volatile IShell shell;
    private static volatile boolean binding;
    private static Shizuku.UserServiceArgs args;
    private static final Object LOCK = new Object();
    private static final List<Runnable> WATCH = new CopyOnWriteArrayList<>();
    private static final ExecutorService RUN = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jostin-shizuku");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Runnable CLOSE = () -> {
        if (shell == null || args == null) return;
        try {
            Shizuku.unbindUserService(args, Shizu.CONN, true);
        } catch (Throwable ignored) {
            // Shizuku ya se fue
        }
        shell = null;
    };

    private static final ServiceConnection CONN = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            shell = b != null && b.pingBinder() ? IShell.Stub.asInterface(b) : null;
            binding = false;
            synchronized (LOCK) {
                LOCK.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            shell = null;
            binding = false;
            synchronized (LOCK) {
                LOCK.notifyAll();
            }
        }
    };

    /** Se llama al abrir el launcher (y en cualquier punto de entrada); escucha cuando Shizuku llega o se va. */
    static synchronized void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        try {
            Shizuku.addBinderReceivedListenerSticky(() -> {
                fire();
                // Al conectar (p. ej. después de reiniciar) se revisa si hay que volver a poner los colores exactos
                if (ColorExacto.on(app)) RUN.execute(() -> {
                    if (listo(app)) ColorExacto.reapplyIfNeeded(app);
                });
            }, MAIN);
            Shizuku.addBinderDeadListener(() -> {
                shell = null;
                binding = false;
                fire();
            }, MAIN);
            Shizuku.addRequestPermissionResultListener((code, result) -> {
                if (code == REQ) fire();
            }, MAIN);
        } catch (Throwable t) {
            Bitacora.error("Shizuku init", t);
        }
    }

    /** Avisa (en el hilo principal) cada vez que cambie el estado; quítalo con unwatch. */
    static void watch(Runnable r) {
        WATCH.add(r);
    }

    static void unwatch(Runnable r) {
        WATCH.remove(r);
    }

    private static void fire() {
        for (Runnable r : WATCH) {
            try {
                r.run();
            } catch (Throwable ignored) {
                // una pantalla que ya se cerró
            }
        }
    }

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static Estado estado(Context c) {
        init(c);
        if (!installed(c)) return Estado.NO_INSTALADO;
        try {
            if (!Shizuku.pingBinder()) return Estado.NO_CORRE;
            if (Shizuku.isPreV11()) return Estado.SIN_PERMISO;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ? Estado.LISTO : Estado.SIN_PERMISO;
        } catch (Throwable t) {
            return Estado.NO_CORRE;
        }
    }

    static boolean listo(Context c) {
        return estado(c) == Estado.LISTO;
    }

    static String texto(Context c) {
        switch (estado(c)) {
            case NO_INSTALADO:
                return "No está instalado. Las funciones de sistema quedan escondidas.";
            case NO_CORRE:
                return "Instalado, pero no está corriendo. Ábrelo y toca Iniciar.";
            case SIN_PERMISO:
                return "Corriendo, pero Jostin todavía no tiene permiso.";
            default:
                int v = 0;
                try {
                    v = Shizuku.getVersion();
                } catch (Throwable ignored) {
                    // nada
                }
                return "Listo ✓ (Shizuku " + v + ")";
        }
    }

    /** Hace lo que falte para quedar listo: abrir Shizuku o pedir el permiso. Regresa un aviso corto o null. */
    static String pedir(Activity a) {
        switch (estado(a)) {
            case NO_INSTALADO:
                return "Primero instala Shizuku";
            case NO_CORRE:
                return abrir(a) ? "Toca Iniciar en Shizuku y regresa" : "No pude abrir Shizuku";
            case SIN_PERMISO:
                try {
                    // Si ya lo negaste con «no volver a preguntar», solo se puede dar desde Shizuku
                    if (Shizuku.shouldShowRequestPermissionRationale()) {
                        return abrir(a) ? "Dale permiso a Jostin OS en la lista de Shizuku" : "Dale permiso desde Shizuku";
                    }
                    Shizuku.requestPermission(REQ);
                } catch (Throwable t) {
                    return "Shizuku no respondió";
                }
                return null;
            default:
                return "Shizuku ya está listo";
        }
    }

    private static boolean abrir(Activity a) {
        Intent i = a.getPackageManager().getLaunchIntentForPackage(PKG);
        if (i == null) return false;
        try {
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int version(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) p.getLongVersionCode() : legacyCode(p);
        } catch (Exception e) {
            return 1;
        }
    }

    @SuppressWarnings("deprecation")
    private static int legacyCode(PackageInfo p) {
        return p.versionCode;
    }

    /** Abre el servicio de shell si hace falta y espera hasta 6 s. Nunca en el hilo principal. */
    private static IShell connect() {
        IShell s = shell;
        if (s != null && s.asBinder().pingBinder()) return s;
        shell = null;
        if (!listo(app)) return null;
        synchronized (LOCK) {
            if (!binding) {
                binding = true;
                MAIN.post(() -> {
                    try {
                        if (args == null) {
                            args = new Shizuku.UserServiceArgs(new ComponentName(app.getPackageName(), ShellService.class.getName()))
                                    .daemon(false).processNameSuffix("shell").debuggable(false).version(version(app));
                        }
                        Shizuku.bindUserService(args, CONN);
                    } catch (Throwable t) {
                        binding = false;
                        Bitacora.error("Shizuku bind", t);
                        synchronized (LOCK) {
                            LOCK.notifyAll();
                        }
                    }
                });
            }
            long end = System.currentTimeMillis() + 6000;
            while (shell == null && binding) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) break;
                try {
                    LOCK.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            // Si no contestó, la próxima vez se vuelve a intentar desde cero
            if (shell == null) binding = false;
        }
        return shell;
    }

    /** Corre un comando y espera el resultado. Solo desde un hilo de fondo. Sin Shizuku regresa null. */
    static Res runSync(Context c, String cmd) {
        init(c);
        IShell s = connect();
        if (s == null) return null;
        MAIN.removeCallbacks(CLOSE);
        try {
            String r = s.run(cmd);
            int nl = r == null ? -1 : r.indexOf('\n');
            if (nl < 0) return new Res(-1, r);
            int code;
            try {
                code = Integer.parseInt(r.substring(0, nl).trim());
            } catch (NumberFormatException e) {
                code = -1;
            }
            return new Res(code, r.substring(nl + 1));
        } catch (Throwable t) {
            shell = null;
            return new Res(-1, String.valueOf(t));
        } finally {
            MAIN.postDelayed(CLOSE, IDLE_MS);
        }
    }

    interface Call {
        String on(IShell s) throws Exception;
    }

    /** Llama otra función del servicio de shell (no un comando). Solo en hilo de fondo; null sin Shizuku. */
    static String call(Context c, Call f) {
        init(c);
        IShell s = connect();
        if (s == null) return null;
        MAIN.removeCallbacks(CLOSE);
        try {
            return f.on(s);
        } catch (Throwable t) {
            shell = null;
            return String.valueOf(t);
        } finally {
            MAIN.postDelayed(CLOSE, IDLE_MS);
        }
    }

    /** Lo mismo pero en fondo; done recibe el resultado (o null sin Shizuku) en el hilo principal. */
    static void run(Context c, String cmd, Done done) {
        init(c);
        RUN.execute(() -> {
            Res r = runSync(c, cmd);
            if (done != null) MAIN.post(() -> done.on(r));
        });
    }

    /** Pone comillas seguras para sh (por si un nombre trae espacios o símbolos). */
    static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
