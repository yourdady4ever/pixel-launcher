package com.jostin.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Servicio de accesibilidad de Jostin OS: bloquear la pantalla con doble toque y la franja que abre el
 * panel propio. Solo si tú lo activas. No lee la pantalla ni lo que escribes.
 */
public class LockService extends AccessibilityService {
    static volatile LockService instance;

    @Override
    protected void onServiceConnected() {
        instance = this;
        // La franja del panel propio vive aquí (si lo activaste)
        PanelPropio.attach(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        PanelPropio.detach(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
            PanelPropio.detach(this);
        }
        super.onDestroy();
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
}
