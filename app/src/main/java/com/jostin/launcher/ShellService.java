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
