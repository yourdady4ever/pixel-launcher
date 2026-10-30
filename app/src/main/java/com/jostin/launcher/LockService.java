package com.jostin.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Servicio de accesibilidad de Jostin OS. Solo sirve para bloquear la pantalla con doble toque, y
 * solo si tú lo activas. No lee la pantalla ni lo que escribes.
 */
public class LockService extends AccessibilityService {
    static volatile LockService instance;

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
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
}
