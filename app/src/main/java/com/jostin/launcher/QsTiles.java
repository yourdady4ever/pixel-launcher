package com.jostin.launcher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Botones de Jostin OS para los ajustes rápidos de Android (la barra que baja desde arriba). */
public class QsTiles {
    static final String[] NAMES = {"Study", "Wall", "Jarvis"};

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
