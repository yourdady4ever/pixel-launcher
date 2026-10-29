package com.jostin.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Las apps del perfil de trabajo (el de tu empresa, Island o Shelter). Viven en otro perfil de
 * Android, así que se cargan, se abren y se pausan con LauncherApps en vez de por su paquete.
 */
final class WorkApps {
    private WorkApps() {}

    /** Los perfiles que no son el tuyo. El espacio privado no sale aquí: Android no se lo enseña a otros launchers. */
    static List<UserHandle> profiles(Context c) {
        List<UserHandle> out = new ArrayList<>();
        LauncherApps la = c.getSystemService(LauncherApps.class);
        if (la == null) return out;
        try {
            for (UserHandle u : la.getProfiles()) if (!u.equals(Process.myUserHandle())) out.add(u);
        } catch (Exception ignored) {
            // Android no dejó ver los perfiles: se queda sin sección de trabajo
        }
        return out;
    }

    /** Se llama fuera del hilo principal: los íconos ya traen el maletín de trabajo. */
    static List<Drawer.App> load(Context c) {
        List<Drawer.App> out = new ArrayList<>();
        LauncherApps la = c.getSystemService(LauncherApps.class);
        if (la == null) return out;
        for (UserHandle u : profiles(c)) {
            try {
                for (LauncherActivityInfo info : la.getActivityList(null, u)) {
                    Drawer.App x = new Drawer.App(String.valueOf(info.getLabel()), info.getComponentName().getPackageName(), u, info.getComponentName());
                    x.badged = info.getBadgedIcon(0);
                    out.add(x);
                }
            } catch (Exception ignored) {
                // ese perfil no se pudo leer; los demás sí
            }
        }
        Collator col = Collator.getInstance(MainActivity.ES);
        Collections.sort(out, (x, y) -> col.compare(x.label, y.label));
        return out;
    }

    /** En pausa, las apps de trabajo no corren ni mandan avisos. */
    static boolean paused(Context c) {
        UserManager um = c.getSystemService(UserManager.class);
        if (um == null) return false;
        for (UserHandle u : profiles(c)) {
            try {
                if (um.isQuietModeEnabled(u)) return true;
            } catch (Exception ignored) {
                // nada
            }
        }
        return false;
    }

    /** Pausa o reanuda. Para reanudar, Android puede pedir tu PIN del perfil con su propia ventana. */
    static boolean setPaused(Context c, boolean on) {
        if (Build.VERSION.SDK_INT < 28) return false;
        UserManager um = c.getSystemService(UserManager.class);
        if (um == null) return false;
        boolean any = false;
        for (UserHandle u : profiles(c)) {
            try {
                um.requestQuietModeEnabled(on, u);
                any = true;
            } catch (Exception ignored) {
                // solo el launcher predeterminado puede hacerlo
            }
        }
        return any;
    }

    static void launch(Activity a, Drawer.App x, View from, Bundle opts) {
        LauncherApps la = a.getSystemService(LauncherApps.class);
        if (la == null) return;
        Rect bounds = null;
        if (from != null && from.getWidth() > 0) {
            int[] p = new int[2];
            from.getLocationOnScreen(p);
            bounds = new Rect(p[0], p[1], p[0] + from.getWidth(), p[1] + from.getHeight());
        }
        try {
            la.startMainActivity(x.comp, x.user, bounds, opts);
        } catch (Exception ignored) {
            // la app se desinstaló o el perfil se apagó justo ahora
        }
    }

    static void info(Activity a, Drawer.App x) {
        LauncherApps la = a.getSystemService(LauncherApps.class);
        if (la == null) return;
        try {
            la.startAppDetailsActivity(x.comp, x.user, null, null);
        } catch (Exception ignored) {
            // nada
        }
    }
}
