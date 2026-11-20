package com.jostin.launcher;

import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Permiso de notificaciones. Android lo exige para que un launcher pueda ver la música que suena
 * y poner los puntos en los íconos. Aquí no se hace nada pesado: solo se avisa que algo cambió.
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
        notable(sbn);
        // Aviso de racha de Duolingo: el pez te lo recuerda. Solo se guarda la hora, no el texto
        if ("com.duolingo".equals(sbn.getPackageName())) {
            android.os.Bundle x = sbn.getNotification().extras;
            String t = (String.valueOf(x.getCharSequence(android.app.Notification.EXTRA_TITLE)) + " "
                    + x.getCharSequence(android.app.Notification.EXTRA_TEXT)).toLowerCase(java.util.Locale.ROOT);
            if (t.contains("racha") || t.contains("streak")) PezTalk.duoAlertAt = System.currentTimeMillis();
        }
    }

    /** Si es importante (mensaje, llamada, correo o de prioridad alta), el pez lo comenta sin leerlo. */
    private void notable(StatusBarNotification sbn) {
        try {
            android.app.Notification n = sbn.getNotification();
            if (sbn.isOngoing() || getPackageName().equals(sbn.getPackageName())
                    || (n.flags & android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return;
            String cat = n.category;
            boolean important = android.app.Notification.CATEGORY_MESSAGE.equals(cat) || android.app.Notification.CATEGORY_CALL.equals(cat)
                    || android.app.Notification.CATEGORY_EMAIL.equals(cat) || android.app.Notification.CATEGORY_SOCIAL.equals(cat);
            Ranking r = new Ranking();
            RankingMap map = getCurrentRanking();
            if (!important && map != null && map.getRanking(sbn.getKey(), r)) important = r.getImportance() >= NotificationManager.IMPORTANCE_HIGH;
            if (!important) return;
            android.content.pm.PackageManager pm = getPackageManager();
            PezTalk.notifApp = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(sbn.getPackageName(), 0)));
            PezTalk.notifAt = System.currentTimeMillis();
        } catch (Exception ignored) {
            // no se pudo saber de qué app es
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Media.notificationsChanged();
    }

    @Override
    public void onInterruptionFilterChanged(int filter) {
        Media.notificationsChanged();
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
