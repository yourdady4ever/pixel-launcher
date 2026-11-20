package com.jostin.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.util.Calendar;

/**
 * Notificación cuando el pez tiene mucha hambre (panza < 15) o está muy triste (ánimo < 15).
 * Como mucho una cada 3 horas, nunca de 10 pm a 8 am, y solo si tienes su widget en inicio.
 * Se revisa en la misma vuelta de cada 5 min con la pantalla prendida (fuera del hilo principal).
 */
final class PezAviso {
    private static final String CH = "pez";

    private PezAviso() {}

    static boolean on(Context c) {
        return Prefs.sp(c).getBoolean("pez_notify", false) && !Prefs.sp(c).getBoolean("pez_notify_off", false);
    }

    /** Lo llama el widget al crearse: desde ahí el pez puede avisar. */
    static void mark(Context c) {
        if (!Prefs.sp(c).getBoolean("pez_notify", false)) Prefs.sp(c).edit().putBoolean("pez_notify", true).apply();
    }

    /** En segundo plano. */
    static void check(Context c) {
        if (!on(c)) return;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 22 || h < 8) return;
        long now = System.currentTimeMillis();
        if (now - Prefs.sp(c).getLong("pez_notif_at", 0) < 3 * 3600_000L) return;
        Gadgets.Pez p;
        try {
            p = new Gadgets.Pez(new JSONObject(Weather.get(Gadgets.PezG.DATA)));
        } catch (Throwable e) {
            return;
        }
        float food = p.food(now), happy = p.happy(now);
        String title, text;
        if (food < 15) {
            title = "Tu pez tiene mucha hambre 🍤";
            text = "Panza al " + Math.round(food) + "%. Dale de comer un ratito.";
        } else if (happy < 15) {
            title = "Tu pez está muy triste 😢";
            text = "Ánimo al " + Math.round(happy) + "%. Juega con él tantito.";
        } else {
            return;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        nm.createNotificationChannel(new NotificationChannel(CH, "Tu pez", NotificationManager.IMPORTANCE_DEFAULT));
        int icon = c.getResources().getIdentifier("qs_spark", "drawable", c.getPackageName());
        PendingIntent open = PendingIntent.getActivity(c, 3, new Intent(c, PezActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(icon != 0 ? icon : android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(77, n);
        Prefs.sp(c).edit().putLong("pez_notif_at", now).apply();
    }
}
