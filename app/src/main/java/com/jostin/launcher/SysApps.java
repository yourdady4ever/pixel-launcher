package com.jostin.launcher;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que hacen las pantallas de sistema con Shizuku: cerrar, congelar, desinstalar y medir RAM.
 * Todo lo que lee del sistema lo lee en el momento (nada se revisa en segundo plano).
 * Los métodos que corren comandos van en un hilo de fondo.
 */
final class SysApps {
    private SysApps() {}

    static final class App {
        final String pkg;
        String label;
        boolean system;
        long last;   // última vez que se usó (0 = nunca)
        long kb;     // memoria (pantalla de RAM)
        boolean removed; // app del sistema quitada para tu usuario

        App(String pkg) {
            this.pkg = pkg;
        }
    }

    static String label(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS);
            CharSequence l = ai.loadLabel(pm);
            return l == null ? pkg : l.toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private static boolean isSystem(Context c, String pkg) {
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return true;
        }
    }

    /** Apps con ícono en el cajón (las que tú ves); de ahí salen las que se pueden cerrar o congelar. */
    static Set<String> launchable(Context c) {
        Set<String> out = new HashSet<>();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : c.getPackageManager().queryIntentActivities(i, 0)) out.add(r.activityInfo.packageName);
        return out;
    }

    private static App make(Context c, String pkg) {
        App a = new App(pkg);
        a.label = label(c, pkg);
        a.system = isSystem(c, pkg);
        return a;
    }

    private static void sortByLabel(List<App> l) {
        Collections.sort(l, (x, y) -> x.label.compareToIgnoreCase(y.label));
    }

    /** Apps tuyas que tienen algo corriendo ahorita, sin las protegidas. */
    static List<App> running(Context c) {
        List<App> out = new ArrayList<>();
        Shizu.Res r = Shizu.runSync(c, "ps -A -o NAME");
        if (r == null) return null;
        Set<String> apps = launchable(c);
        Set<String> seen = new HashSet<>();
        for (String line : r.out.split("\n")) {
            String n = line.trim();
            int k = n.indexOf(':');
            if (k > 0) n = n.substring(0, k);
            if (!apps.contains(n) || !seen.add(n) || Protegidas.es(c, n)) continue;
            out.add(make(c, n));
        }
        sortByLabel(out);
        return out;
    }

    /** Las que no abres desde hace «days» días (las recién instaladas no cuentan). */
    static List<App> unused(Context c, int days) {
        List<App> out = new ArrayList<>();
        UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (um == null) return out;
        long now = System.currentTimeMillis(), cutoff = now - days * 86_400_000L;
        Map<String, UsageStats> st;
        try {
            st = um.queryAndAggregateUsageStats(now - Math.max(400L, days * 2L) * 86_400_000L, now);
        } catch (Exception e) {
            st = new HashMap<>();
        }
        PackageManager pm = c.getPackageManager();
        for (String pkg : launchable(c)) {
            if (Protegidas.es(c, pkg)) continue;
            long last = 0;
            UsageStats u = st.get(pkg);
            if (u != null) {
                last = u.getLastTimeUsed();
                if (Build.VERSION.SDK_INT >= 29) last = Math.max(last, u.getLastTimeVisible());
            }
            long installed;
            try {
                installed = pm.getPackageInfo(pkg, 0).firstInstallTime;
            } catch (Exception e) {
                continue;
            }
            if (Math.max(last, installed) >= cutoff) continue;
            App a = make(c, pkg);
            a.last = last;
            out.add(a);
        }
        // Las más olvidadas primero
        Collections.sort(out, (x, y) -> Long.compare(x.last, y.last));
        return out;
    }

    /** Congeladas (desactivadas) y apps del sistema que quitaste para tu usuario. */
    static List<App> frozen(Context c) {
        List<App> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        List<ApplicationInfo> all = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS);
        for (ApplicationInfo ai : all) {
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean installed = (ai.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
            App a = null;
            if (!installed) {
                if (!system) continue; // datos que dejó una app ya borrada
                a = new App(ai.packageName);
                a.removed = true;
            } else {
                int s;
                try {
                    s = pm.getApplicationEnabledSetting(ai.packageName);
                } catch (Exception e) {
                    continue;
                }
                if (s != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER && s != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) continue;
                a = new App(ai.packageName);
            }
            CharSequence l = ai.loadLabel(pm);
            a.label = l == null ? ai.packageName : l.toString();
            a.system = system;
            out.add(a);
        }
        sortByLabel(out);
        return out;
    }

    private static final Pattern MEM = Pattern.compile("^\\s*([\\d,]+)K: (\\S+) \\(pid \\d+");

    /** Las 15 apps que más memoria usan ahorita (PSS de dumpsys meminfo, juntando sus procesos). */
    static List<App> ram(Context c) {
        Shizu.Res r = Shizu.runSync(c, "dumpsys meminfo");
        if (r == null) return null;
        String sec = section(r.out, "Total PSS by process:");
        if (sec == null) sec = section(r.out, "Total RSS by process:");
        Map<String, Long> kb = new LinkedHashMap<>();
        if (sec != null) {
            for (String line : sec.split("\n")) {
                Matcher m = MEM.matcher(line);
                if (!m.find()) continue;
                String p = m.group(2);
                int k = p.indexOf(':');
                if (k > 0) p = p.substring(0, k);
                long v;
                try {
                    v = Long.parseLong(m.group(1).replace(",", ""));
                } catch (NumberFormatException e) {
                    continue;
                }
                Long old = kb.get(p);
                kb.put(p, old == null ? v : old + v);
            }
        }
        List<App> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : kb.entrySet()) {
            App a = new App(e.getKey());
            a.kb = e.getValue();
            out.add(a);
        }
        Collections.sort(out, (x, y) -> Long.compare(y.kb, x.kb));
        if (out.size() > 15) out = new ArrayList<>(out.subList(0, 15));
        for (App a : out) {
            a.label = label(c, a.pkg);
            a.system = isSystem(c, a.pkg);
        }
        return out;
    }

    private static String section(String all, String head) {
        int i = all.indexOf(head);
        if (i < 0) return null;
        int start = i + head.length();
        int end = all.indexOf("\n\n", start);
        return end < 0 ? all.substring(start) : all.substring(start, end);
    }

    // ---------- Acciones (regresan cuántas salieron bien; -1 = sin Shizuku) ----------

    private static int each(Context c, List<App> apps, Cmd cmd) {
        int ok = 0;
        for (App a : apps) {
            if (Protegidas.es(c, a.pkg)) continue; // doble seguro
            Shizu.Res r = Shizu.runSync(c, cmd.of(a));
            if (r == null) return -1;
            if (r.ok() && !r.out.toLowerCase().contains("failure") && !r.out.toLowerCase().contains("error")) ok++;
            else Bitacora.error("Shizuku " + cmd.of(a), new RuntimeException(r.out.trim()));
        }
        return ok;
    }

    private interface Cmd {
        String of(App a);
    }

    static int forceStop(Context c, List<App> apps) {
        return each(c, apps, a -> "am force-stop " + Shizu.q(a.pkg));
    }

    static int freeze(Context c, List<App> apps) {
        return each(c, apps, a -> "pm disable-user --user 0 " + Shizu.q(a.pkg));
    }

    /** Las del sistema solo se quitan para tu usuario (se pueden regresar); las tuyas se desinstalan. */
    static int uninstall(Context c, List<App> apps) {
        return each(c, apps, a -> a.system ? "pm uninstall --user 0 " + Shizu.q(a.pkg) : "pm uninstall " + Shizu.q(a.pkg));
    }

    static int unfreeze(Context c, List<App> apps) {
        return each(c, apps, a -> "pm enable " + Shizu.q(a.pkg));
    }

    static int reinstall(Context c, List<App> apps) {
        return each(c, apps, a -> "cmd package install-existing --user 0 " + Shizu.q(a.pkg));
    }
}
