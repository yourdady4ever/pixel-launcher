package com.jostin.launcher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Botones de Jostin OS para los ajustes rápidos de Android (la barra que baja desde arriba). */
public class QsTiles {
    static final String[] NAMES = {"Study", "Wall", "Jarvis", "Wifi", "Bt", "Plane", "Clean"};
    static final String[] LABELS = {"Modo estudio", "Otro fondo", "Jarvis", "Wi-Fi", "Bluetooth", "Modo avión", "Limpiar"};
    static final String[] ICONS = {"qs_study", "qs_wall", "qs_spark", "qs_wifi", "qs_bt", "qs_plane", "qs_clean"};
    /** Los de Google que los nuestros reemplazan al acomodar el panel. */
    static final String[][] REPLACES = {{"internet", "wifi"}, {"bt"}, {"airplane"}};
    static final String[] REPLACED_BY = {"Wifi", "Bt", "Plane"};

    static String spec(Context c, String name) {
        return "custom(" + new android.content.ComponentName(c, QsTiles.class.getName() + "$" + name).flattenToShortString() + ")";
    }

    @SuppressWarnings("deprecation")
    static void open(TileService s, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                s.startActivityAndCollapse(PendingIntent.getActivity(s, i.getComponent() == null ? 1 : i.getComponent().hashCode(), i,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else {
                s.startActivityAndCollapse(i);
            }
        } catch (Exception ignored) {
            // nada
        }
    }

    /**
     * Wi-Fi, Bluetooth y modo avión: con Shizuku se prenden y apagan aquí mismo;
     * sin Shizuku el botón abre el ajuste normal de Android.
     */
    abstract static class Toggle extends TileService {
        abstract String label();

        abstract boolean on();

        abstract String cmd(boolean turnOn);

        abstract Intent settings();

        @Override
        public void onStartListening() {
            paint(on());
        }

        void paint(boolean on) {
            Tile t = getQsTile();
            if (t == null) return;
            t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.setLabel(label());
            if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(on ? "Encendido" : "Apagado");
            t.updateTile();
        }

        @Override
        public void onClick() {
            if (!Shizu.listo(this)) {
                open(this, settings());
                return;
            }
            boolean want = !on();
            paint(want);
            Shizu.run(this, cmd(want), r -> {
                if (r == null || !r.ok()) open(this, settings());
                // Android tarda un poco en cambiar; se vuelve a leer el estado real
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> paint(on()), 1500);
            });
        }

        boolean global(String key) {
            return android.provider.Settings.Global.getInt(getContentResolver(), key, 0) != 0;
        }
    }

    public static class Wifi extends Toggle {
        @Override
        String label() {
            return "Wi-Fi";
        }

        @Override
        boolean on() {
            android.net.wifi.WifiManager w = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            return w != null && w.isWifiEnabled();
        }

        @Override
        String cmd(boolean turnOn) {
            return "svc wifi " + (turnOn ? "enable" : "disable");
        }

        @Override
        Intent settings() {
            return new Intent(Build.VERSION.SDK_INT >= 29 ? android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY : android.provider.Settings.ACTION_WIFI_SETTINGS);
        }
    }

    public static class Bt extends Toggle {
        @Override
        String label() {
            return "Bluetooth";
        }

        @Override
        boolean on() {
            return global("bluetooth_on");
        }

        @Override
        String cmd(boolean turnOn) {
            String v = turnOn ? "enable" : "disable";
            return "svc bluetooth " + v + " || cmd bluetooth_manager " + v;
        }

        @Override
        Intent settings() {
            return new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        }
    }

    public static class Plane extends Toggle {
        @Override
        String label() {
            return "Modo avión";
        }

        @Override
        boolean on() {
            return global(android.provider.Settings.Global.AIRPLANE_MODE_ON);
        }

        @Override
        String cmd(boolean turnOn) {
            return "cmd connectivity airplane-mode " + (turnOn ? "enable" : "disable");
        }

        @Override
        Intent settings() {
            return new Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        }
    }

    /** Abre «Limpiar» (siempre con la lista y confirmación antes de cerrar nada). */
    public static class Clean extends TileService {
        @Override
        public void onStartListening() {
            Tile t = getQsTile();
            if (t == null) return;
            boolean ok = Shizu.listo(this);
            t.setState(ok ? Tile.STATE_INACTIVE : Tile.STATE_UNAVAILABLE);
            t.setLabel("Limpiar");
            if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(ok ? "Cerrar apps" : "Falta Shizuku");
            t.updateTile();
        }

        @Override
        public void onClick() {
            open(this, SistemaActivity.intent(this, SistemaActivity.LIMPIAR));
        }
    }

    /** Modo estudio de 25 minutos (o terminarlo). */
    public static class Study extends TileService {
        @Override
        public void onStartListening() {
            update();
        }

        @Override
        public void onClick() {
            Controls.toggleStudy(this);
            update();
        }

        private void update() {
            Tile t = getQsTile();
            if (t == null) return;
            boolean on = Prefs.studying(this);
            t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.setLabel("Modo estudio");
            if (Build.VERSION.SDK_INT >= 29) {
                long left = Prefs.studyEnd(this) - System.currentTimeMillis();
                t.setSubtitle(on ? "Faltan " + (int) Math.ceil(left / 60_000.0) + " min" : "25 minutos");
            }
            t.updateTile();
        }
    }

    /** Siguiente fondo de Jostin OS. */
    public static class Wall extends TileService {
        @Override
        public void onStartListening() {
            Tile t = getQsTile();
            if (t == null) return;
            t.setState(Tile.STATE_INACTIVE);
            t.setLabel("Otro fondo");
            t.updateTile();
        }

        @Override
        public void onClick() {
            MainActivity.cycleWallpaper(this);
            MainActivity m = MainActivity.current.get();
            if (m != null && !m.isDestroyed()) m.applyWallpaper();
            else SystemWall.sync(this);
        }
    }

    /** Abre Claude. */
    public static class Jarvis extends TileService {
        @Override
        public void onStartListening() {
            Tile t = getQsTile();
            if (t == null) return;
            t.setState(Tile.STATE_INACTIVE);
            t.setLabel("Jarvis");
            t.updateTile();
        }

        @Override
        public void onClick() {
            Intent i = getPackageManager().getLaunchIntentForPackage(MainActivity.CLAUDE_PKG);
            if (i == null) i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + MainActivity.CLAUDE_PKG));
            open(this, i);
        }
    }
}
