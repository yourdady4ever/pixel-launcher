package com.jostin.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
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

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            refreshEdge();
        }
    };

    @Override
    protected void onServiceConnected() {
        instance = this;
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_SCREEN_ON);
        try {
            registerReceiver(screen, f);
        } catch (Exception ignored) {
            // nada
        }
        refreshEdge();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        removeStrip();
        try {
            unregisterReceiver(screen);
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
        if (Prefs.edgeShade(this) && !locked) addStrip();
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
