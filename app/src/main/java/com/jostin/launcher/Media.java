package com.jostin.launcher;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * La música que suena en cualquier app (Spotify, YouTube Music...) y tus notificaciones recientes.
 * Solo escucha mientras el launcher está a la vista; al salir se desconecta para no gastar.
 */
final class Media {
    interface Listener {
        void onMediaChanged();
    }

    static final class Note {
        String pkg, title, text;
        long time;
        PendingIntent intent;
    }

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final List<Listener> listeners = new ArrayList<>();
    private static MediaSessionManager msm;
    private static MediaController current;
    private static Context app;
    private static Bitmap art;
    private static String artKey;

    private static final MediaSessionManager.OnActiveSessionsChangedListener sessions = list -> pick(list);

    private static final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            notifyAll0();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            art = null;
            artKey = null;
            notifyAll0();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    private Media() {}

    static ComponentName component(Context c) {
        return new ComponentName(c, NotifService.class);
    }

    static boolean hasAccess(Context c) {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.isNotificationListenerAccessGranted(component(c));
        }
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName());
    }

    static void requestAccess(Activity a) {
        Intent i;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(a).flattenToString());
        } else {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        try {
            a.startActivity(i);
        } catch (Exception e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception ignored) {
                // nada
            }
        }
    }

    static void addListener(Context c, Listener l) {
        app = c.getApplicationContext();
        if (!listeners.contains(l)) listeners.add(l);
        if (listeners.size() == 1) connect();
        l.onMediaChanged();
    }

    static void removeListener(Listener l) {
        listeners.remove(l);
        if (listeners.isEmpty()) disconnect();
    }

    static void listenerReady() {
        main.post(() -> {
            if (!listeners.isEmpty()) connect();
        });
    }

    static void notificationsChanged() {
        main.post(Media::notifyAll0);
    }

    private static void connect() {
        if (app == null || !hasAccess(app)) {
            notifyAll0();
            return;
        }
        try {
            if (msm == null) msm = (MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE);
            msm.removeOnActiveSessionsChangedListener(sessions);
            msm.addOnActiveSessionsChangedListener(sessions, component(app));
            pick(msm.getActiveSessions(component(app)));
        } catch (Exception e) {
            current = null;
            notifyAll0();
        }
    }

    private static void disconnect() {
        try {
            if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions);
        } catch (Exception ignored) {
            // nada
        }
        if (current != null) current.unregisterCallback(callback);
        current = null;
        art = null;
        artKey = null;
    }

    private static void refresh() {
        if (msm == null || app == null) return;
        try {
            pick(msm.getActiveSessions(component(app)));
        } catch (Exception e) {
            pick(null);
        }
    }

    /** Se queda con la sesión que está sonando; si ninguna suena, con la primera. */
    private static void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController m : list) {
                PlaybackState s = m.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    best = m;
                    break;
                }
            }
            if (best == null && !list.isEmpty()) best = list.get(0);
        }
        if (current != null && (best == null || !best.getSessionToken().equals(current.getSessionToken()))) {
            current.unregisterCallback(callback);
        }
        if (best != null && (current == null || !best.getSessionToken().equals(current.getSessionToken()))) {
            best.registerCallback(callback, main);
            art = null;
            artKey = null;
        }
        current = best;
        notifyAll0();
    }

    private static void notifyAll0() {
        for (Listener l : new ArrayList<>(listeners)) l.onMediaChanged();
    }

    // ---------- Lo que suena ----------

    static boolean active() {
        return current != null && current.getMetadata() != null && title() != null;
    }

    static boolean playing() {
        PlaybackState s = current == null ? null : current.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    static String title() {
        MediaMetadata m = current == null ? null : current.getMetadata();
        if (m == null) return null;
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null) t = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        return t;
    }

    static String artist() {
        MediaMetadata m = current == null ? null : current.getMetadata();
        if (m == null) return "";
        String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        return a == null ? "" : a;
    }

    /** Portada reducida a 256 px para no guardar imágenes enormes en memoria. */
    static Bitmap art() {
        MediaMetadata m = current == null ? null : current.getMetadata();
        if (m == null) return null;
        String key = title() + "|" + artist();
        if (key.equals(artKey) && art != null) return art;
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (b != null && (b.getWidth() > 256 || b.getHeight() > 256)) {
            float s = 256f / Math.max(b.getWidth(), b.getHeight());
            b = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true);
        }
        art = b;
        artKey = key;
        return b;
    }

    static long duration() {
        MediaMetadata m = current == null ? null : current.getMetadata();
        return m == null ? 0 : Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION));
    }

    /** Posición actual, calculada a partir del último dato y el tiempo que ha pasado. */
    static long position() {
        PlaybackState s = current == null ? null : current.getPlaybackState();
        if (s == null) return 0;
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            long dt = android.os.SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime();
            pos += (long) (dt * s.getPlaybackSpeed());
        }
        long d = duration();
        return d > 0 ? Math.max(0, Math.min(d, pos)) : Math.max(0, pos);
    }

    static String appPackage() {
        return current == null ? null : current.getPackageName();
    }

    static void playPause() {
        if (current == null) return;
        if (playing()) current.getTransportControls().pause();
        else current.getTransportControls().play();
    }

    static void next() {
        if (current != null) current.getTransportControls().skipToNext();
    }

    static void previous() {
        if (current != null) current.getTransportControls().skipToPrevious();
    }

    static void open(Activity a) {
        if (current == null) return;
        PendingIntent pi = current.getSessionActivity();
        try {
            if (pi != null) {
                pi.send();
                return;
            }
        } catch (Exception ignored) {
            // probamos abrir la app
        }
        Intent i = a.getPackageManager().getLaunchIntentForPackage(current.getPackageName());
        if (i != null) a.startActivity(i);
    }

    // ---------- Notificaciones ----------

    static List<Note> recent(Context c, int max) {
        List<Note> out = new ArrayList<>();
        NotifService s = NotifService.instance;
        if (s == null) return out;
        StatusBarNotification[] all;
        try {
            all = s.getActiveNotifications();
        } catch (Exception e) {
            return out;
        }
        if (all == null) return out;
        Arrays.sort(all, (x, y) -> Long.compare(y.getPostTime(), x.getPostTime()));
        for (StatusBarNotification n : all) {
            if (out.size() >= max) break;
            if (n.isOngoing() || n.getPackageName().equals(c.getPackageName())) continue;
            Notification no = n.getNotification();
            if ((no.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            Bundle ex = no.extras;
            CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);
            if (title == null) continue;
            Note note = new Note();
            note.pkg = n.getPackageName();
            note.title = title.toString();
            note.text = text == null ? "" : text.toString();
            note.time = n.getPostTime();
            note.intent = no.contentIntent;
            out.add(note);
        }
        return out;
    }
}
