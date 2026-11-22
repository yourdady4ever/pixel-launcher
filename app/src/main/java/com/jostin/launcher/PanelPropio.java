package com.jostin.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Panel de notificaciones propio (apagado de fábrica).
 * - La franja de arriba (12 dp) vive en el servicio de accesibilidad: es la única ventana que queda
 *   encima de la barra de Android y no necesita servicio en primer plano.
 * - Con Shizuku se bloquea el panel de Android. El bloqueo está amarrado a TOKEN, que vive en este proceso:
 *   si Jostin truena o se cierra, Android lo quita solo. Al apagar la pantalla se quita; al desbloquear se vuelve a poner.
 * - Nunca se bloquea si la franja no está puesta o falta el acceso a notificaciones.
 */
final class PanelPropio {
    private PanelPropio() {}

    private static final String PREF = "own_panel";
    /** StatusBarManager.DISABLE_EXPAND */
    private static final int DISABLE_EXPAND = 0x00010000;
    private static final IBinder TOKEN = new Binder();
    private static final ExecutorService BG = Executors.newSingleThreadExecutor();

    private static volatile boolean blocked;
    private static AccessibilityService host;
    private static View strip;
    private static BroadcastReceiver screen;

    static boolean enabled(Context c) {
        return Prefs.sp(c).getBoolean(PREF, false);
    }

    static void setEnabled(Context c, boolean on) {
        Prefs.sp(c).edit().putBoolean(PREF, on).apply();
        refresh(c);
    }

    static boolean hostReady() {
        return host != null;
    }

    static boolean blocked() {
        return blocked;
    }

    // ---------- El servicio de accesibilidad ----------

    static void attach(AccessibilityService s) {
        host = s;
        screen = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                    removeStrip();
                    sync(c);
                } else {
                    refresh(c);
                }
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= 33) s.registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
            else s.registerReceiver(screen, f);
        } catch (Exception e) {
            screen = null;
        }
        refresh(s);
    }

    static void detach(Context c) {
        removeStrip();
        if (host != null && screen != null) {
            try {
                host.unregisterReceiver(screen);
            } catch (Exception ignored) {
                // ya no estaba
            }
        }
        screen = null;
        host = null;
        // Sin franja no hay forma de abrir el panel propio: el de Android tiene que regresar sí o sí
        releaseOrDie(c.getApplicationContext());
    }

    private static boolean unlocked(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        return (pm == null || pm.isInteractive()) && (km == null || !km.isKeyguardLocked());
    }

    /** Pone o quita la franja según el ajuste y la pantalla, y luego el bloqueo. */
    static void refresh(Context c) {
        AccessibilityService s = host;
        if (s != null && enabled(c) && unlocked(c)) addStrip(s);
        else removeStrip();
        sync(c);
    }

    private static void addStrip(AccessibilityService s) {
        if (strip != null) return;
        try {
            WindowManager wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
            View v = new View(s);
            final float[] y0 = {0};
            final boolean[] fired = {false};
            final int need = Ui.dp(s, 14);
            v.setOnTouchListener((view, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        y0[0] = e.getRawY();
                        fired[0] = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!fired[0] && e.getRawY() - y0[0] > need) {
                            fired[0] = true;
                            open(s);
                        }
                        return true;
                    default:
                        return true;
                }
            });
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, Ui.dp(s, 12),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP;
            if (Build.VERSION.SDK_INT >= 30) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            else if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            lp.setTitle("Jostin panel");
            wm.addView(v, lp);
            strip = v;
        } catch (Throwable t) {
            strip = null;
            Bitacora.error("Franja del panel", t);
        }
    }

    private static void removeStrip() {
        View v = strip;
        strip = null;
        if (v == null || host == null) return;
        try {
            ((WindowManager) host.getSystemService(Context.WINDOW_SERVICE)).removeViewImmediate(v);
        } catch (Throwable ignored) {
            // ya no estaba
        }
    }

    /** Abre el panel propio; si no se puede, regresa el de Android en ese instante. */
    static void open(Context c) {
        try {
            Intent i = new Intent(c, PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            c.startActivity(i);
        } catch (Throwable t) {
            Bitacora.error("Abrir panel propio", t);
            emergency(c);
        }
    }

    // ---------- Bloqueo del panel de Android ----------

    private static boolean shouldBlock(Context c) {
        return enabled(c) && strip != null && host != null && Media.hasAccess(c) && unlocked(c);
    }

    /** Pone o quita el bloqueo según haga falta (fondo). Se llama al arrancar, al conectar Shizuku y al desbloquear. */
    static void sync(Context c) {
        Context app = c.getApplicationContext();
        BG.execute(() -> {
            if (shouldBlock(app)) {
                if (!Shizu.listo(app)) return;
                String r = Shizu.call(app, s -> s.statusBar(TOKEN, DISABLE_EXPAND));
                blocked = "ok".equals(r);
                if (!blocked) Bitacora.error("Bloquear panel de Android", new RuntimeException(String.valueOf(r)));
            } else if (blocked) {
                releaseNow(app);
            }
        });
    }

    /** Quita el bloqueo (fondo de preferencia). Regresa si quedó libre. */
    static boolean releaseNow(Context c) {
        String r = Shizu.call(c, s -> s.statusBar(TOKEN, 0));
        // Por si alguien lo bloqueó con el comando de shell
        Shizu.Res x = Shizu.runSync(c, "cmd statusbar send-disable-flag none");
        boolean ok = "ok".equals(r) || (x != null && x.ok());
        if (ok) blocked = false;
        return ok;
    }

    /** Si no se puede quitar con Shizuku, Jostin se reinicia: al morir el TOKEN, Android quita el bloqueo solo. */
    private static void releaseOrDie(Context c) {
        BG.execute(() -> {
            if (!blocked) return;
            if (!releaseNow(c)) android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    /** Salida de emergencia: el panel de Android regresa ya. */
    static void emergency(Context c) {
        Context app = c.getApplicationContext();
        BG.execute(() -> {
            if (!releaseNow(app) && blocked) android.os.Process.killProcess(android.os.Process.myPid());
        });
    }
}
