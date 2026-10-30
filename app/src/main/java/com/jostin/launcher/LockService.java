package com.jostin.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Servicio de accesibilidad de Jostin OS. Hace dos cosas y solo si tú lo activas:
 * bloquear la pantalla con doble toque y (opcional) abrir el panel de Jostin deslizando desde la
 * orilla de arriba. No lee la pantalla ni lo que escribes: solo pone una franja invisible arriba.
 */
public class LockService extends AccessibilityService {
    static volatile LockService instance;
    private View strip;
    private float downY, downX;
    private boolean fired;

    // La franja queda encima de los avisos emergentes de Android y se comía el toque en su parte de
    // arriba; mientras se ven, se quita. Una llamada entrante la quita hasta que contestas o cuelgas.
    private static final long PEEK_MS = 6000;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static long hiddenUntil = 0;
    private static String callKey = null;
    private static final Runnable back = () -> {
        LockService s = instance;
        if (s != null) s.refreshEdge();
    };

    /** NotifService avisa que va a salir un aviso emergente arriba. */
    static void peek(String key, boolean incomingCall) {
        if (incomingCall) callKey = key;
        hiddenUntil = SystemClock.uptimeMillis() + PEEK_MS;
        // Se quita ya y se revisa de nuevo cuando el aviso normal ya se fue
        main.removeCallbacks(back);
        main.post(back);
        main.postDelayed(back, PEEK_MS + 100);
    }

    /** El aviso se quitó, o la llamada ya se contestó o colgó. */
    static void peekGone(String key) {
        if (key == null || !key.equals(callKey)) return;
        callKey = null;
        main.post(back);
    }

    private static boolean peeking() {
        return callKey != null || SystemClock.uptimeMillis() < hiddenUntil;
    }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            refreshEdge();
        }
    };

    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Controls.saverCharging(c, Intent.ACTION_POWER_CONNECTED.equals(i.getAction()));
        }
    };

    @Override
    protected void onServiceConnected() {
        instance = this;
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_SCREEN_ON);
        IntentFilter p = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        p.addAction(Intent.ACTION_POWER_DISCONNECTED);
        try {
            registerReceiver(screen, f);
            registerReceiver(power, p);
        } catch (Exception ignored) {
            // nada
        }
        refreshEdge();
        // Pudo conectarse o desconectarse el cargador mientras el servicio no corría
        Controls.saverCharging(this, Controls.plugged(this));
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        removeStrip();
        try {
            unregisterReceiver(screen);
            unregisterReceiver(power);
        } catch (Exception ignored) {
            // nada
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    static boolean lock() {
        LockService s = instance;
        if (s == null || android.os.Build.VERSION.SDK_INT < 28) return false;
        return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** Se llama al cambiar la opción en Personalizar. */
    static void updateEdge() {
        LockService s = instance;
        if (s != null) s.refreshEdge();
    }

    static boolean running() {
        return instance != null;
    }

    private void refreshEdge() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = km != null && km.isKeyguardLocked();
        // En la pantalla de bloqueo nunca: ahí la barra de Android se queda como está
        if (Prefs.edgeShade(this) && !locked && !peeking()) addStrip();
        else removeStrip();
    }

    private void addStrip() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        int zone = Prefs.edgeZone(this);
        int width = zone == 0 ? WindowManager.LayoutParams.MATCH_PARENT : getResources().getDisplayMetrics().widthPixels / 2;
        int gravity = Gravity.TOP | (zone == 2 ? Gravity.START : zone == 1 ? Gravity.END : Gravity.CENTER_HORIZONTAL);
        // Tapa también la barra de estado: si no, desde la orilla misma salía el panel de Android
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, statusBarHeight(wm) + Ui.dp(this, 22),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        // Desde Android 11 la ventana se acomoda debajo de las barras si no se le dice lo contrario
        if (android.os.Build.VERSION.SDK_INT >= 30) lp.setFitInsetsTypes(0);
        try {
            if (strip != null) {
                wm.updateViewLayout(strip, lp);
                return;
            }
            strip = new View(this);
            strip.setOnTouchListener((v, e) -> onStripTouch(e));
            wm.addView(strip, lp);
        } catch (Exception e) {
            strip = null;
        }
    }

    /** Alto de la barra de estado, contando el hueco de la cámara. */
    private int statusBarHeight(WindowManager wm) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                int top = wm.getCurrentWindowMetrics().getWindowInsets()
                        .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
                if (top > 0) return top;
            } catch (Exception ignored) {
                // se usa el valor de Android
            }
        }
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(this, 24);
    }

    private void removeStrip() {
        if (strip == null) return;
        try {
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(strip);
        } catch (Exception ignored) {
            // ya no estaba
        }
        strip = null;
    }

    private boolean onStripTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getRawY();
                downX = e.getRawX();
                fired = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!fired && e.getRawY() - downY > Ui.dp(this, 18) && Math.abs(e.getRawX() - downX) < Ui.dp(this, 60)) {
                    fired = true;
                    Intent i = new Intent(this, ShadeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    try {
                        startActivity(i);
                    } catch (Exception ignored) {
                        // nada
                    }
                }
                return true;
            default:
                return true;
        }
    }
}
