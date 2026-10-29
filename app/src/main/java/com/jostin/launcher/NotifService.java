package com.jostin.launcher;

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
}
