package com.jostin.launcher;

import android.app.Notification;
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
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap ranking) {
        Media.notificationsChanged();
        peekCheck(sbn, ranking);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Media.notificationsChanged();
        LockService.peekGone(sbn.getKey());
    }

    /**
     * Android enseña arriba, como aviso emergente, los de importancia alta que No molestar no calla.
     * Para esos se quita un momento la franja del panel, que si no tapa su parte de arriba.
     */
    private void peekCheck(StatusBarNotification sbn, RankingMap ranking) {
        if (sbn.getPackageName().equals(getPackageName())) return;
        Notification n = sbn.getNotification();
        boolean incomingCall = Notification.CATEGORY_CALL.equals(n.category) && n.fullScreenIntent != null;
        // La llamada se contestó o se colgó: el aviso cambia a "llamada en curso" con la misma clave
        if (!incomingCall) LockService.peekGone(sbn.getKey());
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        // Las fijas (mapas, descargas, cronómetros) se actualizan seguido sin salir arriba
        if (sbn.isOngoing() && !incomingCall) return;
        Ranking r = new Ranking();
        if (ranking == null || !ranking.getRanking(sbn.getKey(), r)) return;
        if (r.getImportance() < NotificationManager.IMPORTANCE_HIGH) return;
        if ((r.getSuppressedVisualEffects() & NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK) != 0) return;
        LockService.peek(sbn.getKey(), incomingCall);
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
