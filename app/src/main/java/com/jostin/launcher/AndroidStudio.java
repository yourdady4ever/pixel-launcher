package com.jostin.launcher;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pestaña «Android»: lo que sí se puede cambiar de Android sin root. */
final class AndroidStudio {
    private AndroidStudio() {}

    private static TextView note(Activity a, String s) {
        TextView t = Ui.text(a, s, 12, Ui.MUTED, Ui.REGULAR);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), 0, Ui.dp(a, 4));
        return t;
    }

    static void fill(Activity a, Studio.Host h, LinearLayout col) {
        // ---------- Colores ----------
        col.addView(Ctl.section(a, "Colores de Android"));
        boolean granted = SystemColors.granted(a);
        if (!granted) {
            col.addView(note(a, "Para que la barra de notificaciones, los ajustes rápidos, el teclado y las apps de Google tomen el color de Jostin, Android pide un permiso que solo se da una vez desde la computadora, con el teléfono conectado y la depuración USB prendida:"));
            TextView cmd = Ui.text(a, SystemColors.ADB, 12, Ui.TEXT, Ui.MEDIUM);
            cmd.setTypeface(android.graphics.Typeface.MONOSPACE);
            cmd.setTextIsSelectable(true);
            int p = Ui.dp(a, 12);
            cmd.setPadding(p, p, p, p);
            cmd.setBackground(Ui.outline(a, Ui.SOFT, 0x1AFFFFFF, 14));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
            cl.topMargin = Ui.dp(a, 6);
            col.addView(cmd, cl);
            View gap = new View(a);
            col.addView(gap, new LinearLayout.LayoutParams(1, Ui.dp(a, 8)));
            col.addView(Ctl.action(a, Icons.SWAP, "Copiar el comando", null, v -> {
                Themes.copy(a, "Comando ADB", SystemColors.ADB);
                Sheet.pill(a, h.backdrop(), "Copiado");
            }));
        }
        col.addView(Ctl.toggle(a, "Pintar Android con el color de Jostin", granted ? "Se aplica cuando sales de inicio. Si lo apagas, Android regresa a los colores de tu fondo." : "Primero falta el permiso de arriba.",
                Prefs.androidColors(a) && granted, on -> {
                    if (!SystemColors.granted(a)) {
                        Sheet.pill(a, h.backdrop(), "Falta el permiso; usa el comando de arriba");
                        return;
                    }
                    Prefs.setAndroidColors(a, on);
                    SystemWall.sync(a);
                }));
        col.addView(Ctl.section(a, "Estilo de los colores"));
        col.addView(Ctl.chips(a, SystemColors.STYLES, Prefs.androidStyle(a), v -> {
            Prefs.setAndroidStyle(a, v);
            SystemWall.sync(a);
        }));

        // ---------- Botones ----------
        col.addView(Ctl.section(a, "Botones en los ajustes rápidos"));
        col.addView(Ctl.action(a, Icons.PLUS, "Agregar los botones de Jostin", "4", v -> addTiles(a, h)));
        col.addView(note(a, "Modo estudio, Otro fondo, Jarvis y Panel Jostin, junto a los de Wi-Fi y Bluetooth en la barra de arriba. Si no aparecen, baja la barra, toca el lápiz y arrástralos."));

        // ---------- Orilla ----------
        col.addView(Ctl.section(a, "Deslizar desde la orilla de arriba"));
        if (!LockService.running()) {
            col.addView(note(a, "Para que la orilla de arriba abra el panel de Jostin en cualquier app, activa «Jostin OS» en Accesibilidad (el mismo permiso del doble toque). Si Android dice que es una configuración restringida: Ajustes > Apps > Jostin OS > ⋮ > Permitir configuración restringida."));
            col.addView(Ctl.action(a, Widgets.LOCK, "Abrir Accesibilidad", null, v -> {
                try {
                    a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception ignored) {
                    // nada
                }
            }));
        }
        col.addView(Ctl.toggle(a, "Abrir el panel de Jostin", "Deslizando hacia abajo desde la orilla, en cualquier app.", Prefs.edgeShade(a), on -> {
            Prefs.setEdgeShade(a, on);
            LockService.updateEdge();
            if (on && !LockService.running()) Sheet.pill(a, h.backdrop(), "Falta activarlo en Accesibilidad");
        }));
        col.addView(Ctl.section(a, "¿En qué parte de la orilla?"));
        col.addView(Ctl.chips(a, new String[]{"Toda", "Mitad derecha", "Mitad izquierda"}, Prefs.edgeZone(a), v -> {
            Prefs.setEdgeZone(a, v);
            LockService.updateEdge();
        }));
        col.addView(note(a, "En la otra mitad sigue saliendo la barra de Android, por si la necesitas. En la pantalla de bloqueo siempre sale la de Android. En juegos o videos a pantalla completa, la franja de arriba puede estorbar; aquí la apagas."));
    }

    private static void addTiles(Activity a, Studio.Host h) {
        String[] specs = new String[QsTiles.NAMES.length];
        for (int i = 0; i < specs.length; i++) specs[i] = QsTiles.spec(a, QsTiles.NAMES[i]);
        if (SystemColors.addTiles(a, specs)) {
            Sheet.pill(a, h.backdrop(), "Listo, ya están en tus ajustes rápidos");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            request(a, h, 0);
            return;
        }
        Sheet.pill(a, h.backdrop(), "Baja la barra, toca el lápiz y arrastra los de Jostin");
    }

    /** Android 13+: pregunta uno por uno con su ventana oficial. */
    private static void request(Activity a, Studio.Host h, int i) {
        if (Build.VERSION.SDK_INT < 33 || i >= QsTiles.NAMES.length) return;
        StatusBarManager sb = a.getSystemService(StatusBarManager.class);
        if (sb == null) return;
        String[] labels = {"Modo estudio", "Otro fondo", "Jarvis", "Panel Jostin"};
        String[] icons = {"qs_study", "qs_wall", "qs_spark", "qs_panel"};
        ComponentName cn = new ComponentName(a, QsTiles.class.getName() + "$" + QsTiles.NAMES[i]);
        try {
            sb.requestAddTileService(cn, labels[i], Icon.createWithResource(a, a.getResources().getIdentifier(icons[i], "drawable", a.getPackageName())), a.getMainExecutor(), r -> a.runOnUiThread(() -> request(a, h, i + 1)));
        } catch (Exception e) {
            request(a, h, i + 1);
        }
    }
}
