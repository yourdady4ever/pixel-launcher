package com.jostin.launcher;

import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Permiso de notificaciones. Android lo exige para que un launcher pueda ver la música que suena
 * y mostrar tus avisos en el panel. Aquí no se hace nada pesado: solo se avisa que algo cambió.
 */
public class NotifService extends NotificationListenerService {
    static volatile NotifService instance;

    @Override
    public void onListenerConnected() {
        instance = this;
        Media.listenerReady();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Media.notificationsChanged();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Media.notificationsChanged();
    }

    @Override
    public void onInterruptionFilterChanged(int filter) {
        Media.notificationsChanged();
    }

    /** Descarta una notificación (como deslizarla en la barra de Android). */
    static boolean cancel(String key) {
        NotifService s = instance;
        if (s == null || key == null) return false;
        try {
            s.cancelNotification(key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Descarta todas las que se pueden descartar. Las fijas (música, descargas) se quedan. */
    static boolean cancelAll() {
        NotifService s = instance;
        if (s == null) return false;
        try {
            s.cancelAllNotifications();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Si "No molestar" está activo. Leerlo no pide ningún permiso. */
    static boolean dndOn(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;
        int f = nm.getCurrentInterruptionFilter();
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }

    /**
     * Prende o apaga "No molestar". Android solo lo deja si la app tiene acceso a "No molestar"
     * (las apps con permiso de notificaciones lo tienen). Regresa false si no se pudo.
     */
    static boolean setDnd(Context c, boolean on) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || !nm.isNotificationPolicyAccessGranted()) return false;
        try {
            nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_PRIORITY : NotificationManager.INTERRUPTION_FILTER_ALL);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
