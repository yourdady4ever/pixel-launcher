package com.jostin.launcher;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Apps que Jostin nunca cierra, congela ni desinstala. */
final class Protegidas {
    private Protegidas() {}

    private static final Set<String> FIJAS = new HashSet<>(Arrays.asList(
            Shizu.PKG,
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.android.systemui",
            "android",
            "com.android.shell",
            "com.android.settings",
            // Teléfono y mensajes del sistema
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.providers.telephony",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.messaging",
            "com.android.mms"));

    static boolean es(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return true;
        if (pkg.equals(c.getPackageName()) || FIJAS.contains(pkg)) return true;
        // Spin by OXXO: cualquier paquete que se llame así, por si cambia el nombre exacto
        String low = pkg.toLowerCase(Locale.ROOT);
        if (low.contains("spin") || low.contains("oxxo")) return true;
        return pkg.equals(dialer(c)) || pkg.equals(sms(c)) || pkg.equals(keyboard(c)) || pkg.equals(wallpaper(c));
    }

    private static String dialer(Context c) {
        try {
            TelecomManager t = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            return t == null ? null : t.getDefaultDialerPackage();
        } catch (Exception e) {
            return null;
        }
    }

    private static String sms(Context c) {
        try {
            return Telephony.Sms.getDefaultSmsPackage(c);
        } catch (Exception e) {
            return null;
        }
    }

    /** El teclado que estás usando (si se cierra te quedas sin poder escribir un rato). */
    private static String keyboard(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            ComponentName n = s == null ? null : ComponentName.unflattenFromString(s);
            return n == null ? null : n.getPackageName();
        } catch (Exception e) {
            return null;
        }
    }

    /** El fondo animado que tengas puesto. */
    private static String wallpaper(Context c) {
        try {
            WallpaperInfo w = WallpaperManager.getInstance(c).getWallpaperInfo();
            return w == null ? null : w.getPackageName();
        } catch (Exception e) {
            return null;
        }
    }
}
