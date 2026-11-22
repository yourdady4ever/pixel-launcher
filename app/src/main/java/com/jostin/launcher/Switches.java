package com.jostin.launcher;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

/** Wi-Fi, Bluetooth, Modo avión y brillo: los usan los botones del panel de Android y el panel propio. */
final class Switches {
    private Switches() {}

    static final int WIFI = 0, BT = 1, PLANE = 2;
    static final String[] LABELS = {"Wi-Fi", "Bluetooth", "Modo avión"};

    static boolean on(Context c, int which) {
        switch (which) {
            case WIFI:
                WifiManager w = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                return w != null && w.isWifiEnabled();
            case BT:
                return Settings.Global.getInt(c.getContentResolver(), "bluetooth_on", 0) != 0;
            default:
                return Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        }
    }

    static String cmd(int which, boolean turnOn) {
        String v = turnOn ? "enable" : "disable";
        switch (which) {
            case WIFI:
                return "svc wifi " + v;
            case BT:
                return "svc bluetooth " + v + " || cmd bluetooth_manager " + v;
            default:
                return "cmd connectivity airplane-mode " + v;
        }
    }

    /** Sin Shizuku: el ajuste normal de Android. */
    static Intent settings(int which) {
        switch (which) {
            case WIFI:
                return new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : Settings.ACTION_WIFI_SETTINGS);
            case BT:
                return new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            default:
                return new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        }
    }

    // ---------- Brillo (0–255) ----------

    static int brightness(Context c) {
        return Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
    }

    /** Con el permiso de «modificar ajustes del sistema» directo; si no, con Shizuku. En hilo de fondo. */
    static boolean setBrightness(Context c, int v) {
        v = Math.max(1, Math.min(255, v));
        if (Settings.System.canWrite(c)) {
            try {
                return Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, v);
            } catch (Exception ignored) {
                // se intenta con Shizuku
            }
        }
        Shizu.Res r = Shizu.runSync(c, "settings put system screen_brightness " + v);
        return r != null && r.ok();
    }

    static boolean canBrightness(Context c) {
        return Settings.System.canWrite(c) || Shizu.listo(c);
    }
}
