package com.jostin.launcher;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Corre dentro del proceso que Shizuku arranca como usuario «shell» (UserService), no en el launcher.
 * Solo existe mientras Jostin lo ocupa: Shizu lo cierra al rato de no usarlo.
 */
public class ShellService extends IShell.Stub {
    private static final int MAX = 512 * 1024;

    public ShellService() {
        // Shizuku lo crea con este constructor
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    // ---------- FabricatedOverlay (aquí sí se vale: este proceso corre como «shell») ----------

    private static final String SHELL_PKG = "com.android.shell";

    /** IOverlayManager del sistema (API oculta; solo se usa dentro de este proceso de Shizuku). */
    private static Object overlayManager() throws Exception {
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "overlay");
        return Class.forName("android.content.om.IOverlayManager$Stub").getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
    }

    private static Object txBuilder() throws Exception {
        return Class.forName("android.content.om.OverlayManagerTransaction$Builder").getConstructor().newInstance();
    }

    private static void commit(Object builder) throws Exception {
        Object tx = builder.getClass().getMethod("build").invoke(builder);
        Object om = overlayManager();
        om.getClass().getMethod("commit", android.content.om.OverlayManagerTransaction.class).invoke(om, tx);
    }

    private static void setEnabled(Object builder, android.content.om.OverlayIdentifier id, boolean on) throws Exception {
        try {
            builder.getClass().getMethod("setEnabled", android.content.om.OverlayIdentifier.class, boolean.class, int.class).invoke(builder, id, on, 0);
        } catch (NoSuchMethodException e) {
            builder.getClass().getMethod("setEnabled", android.content.om.OverlayIdentifier.class, boolean.class).invoke(builder, id, on);
        }
    }

    private static String why(Throwable t) {
        while (t.getCause() != null && (t instanceof java.lang.reflect.InvocationTargetException || t.getMessage() == null)) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    @Override
    public String fabricate(String name, String spec) {
        try {
            android.content.om.FabricatedOverlay fo = new android.content.om.FabricatedOverlay(name, "android");
            // Que quede a nombre de «shell» (es quien lo registra)
            android.content.om.FabricatedOverlay.class.getMethod("setOwningPackage", String.class).invoke(fo, SHELL_PKG);
            int n = 0;
            for (String kv : spec.split(";")) {
                int k = kv.indexOf('=');
                if (k <= 0) continue;
                int color = (int) Long.parseLong(kv.substring(k + 1), 16);
                fo.setResourceValue("android:color/" + kv.substring(0, k), android.util.TypedValue.TYPE_INT_COLOR_ARGB8, color, null);
                n++;
            }
            Object b = txBuilder();
            b.getClass().getMethod("registerFabricatedOverlay", android.content.om.FabricatedOverlay.class).invoke(b, fo);
            setEnabled(b, fo.getIdentifier(), true);
            commit(b);
            return "ok " + n;
        } catch (Throwable t) {
            return why(t);
        }
    }

    @Override
    public String unfabricate(String name) {
        try {
            android.content.om.OverlayIdentifier id = (android.content.om.OverlayIdentifier) Class.forName("android.content.om.OverlayIdentifier")
                    .getConstructor(String.class, String.class).newInstance(SHELL_PKG, name);
            Object b = txBuilder();
            b.getClass().getMethod("unregisterFabricatedOverlay", android.content.om.OverlayIdentifier.class).invoke(b, id);
            commit(b);
            return "ok";
        } catch (Throwable t) {
            return why(t);
        }
    }

    @Override
    public String statusBar(android.os.IBinder token, int flags) {
        try {
            Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "statusbar");
            Object sb = Class.forName("com.android.internal.statusbar.IStatusBarService$Stub").getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
            sb.getClass().getMethod("disable", int.class, android.os.IBinder.class, String.class).invoke(sb, flags, token, SHELL_PKG);
            return "ok";
        } catch (Throwable t) {
            return why(t);
        }
    }

    @Override
    public String run(String cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            final Process proc = p;
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream in = proc.getInputStream()) {
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        if (out.size() < MAX) out.write(b, 0, Math.min(n, MAX - out.size()));
                    }
                } catch (Exception ignored) {
                    // se cerró
                }
            });
            reader.start();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "-2\n(se tardó demasiado)";
            }
            reader.join(2000);
            return p.exitValue() + "\n" + out.toString("UTF-8");
        } catch (Exception e) {
            if (p != null) p.destroy();
            return "-1\n" + e;
        }
    }
}
