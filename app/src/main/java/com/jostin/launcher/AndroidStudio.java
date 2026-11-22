package com.jostin.launcher;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.os.Build;
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
        // ---------- Shizuku ----------
        col.addView(Ctl.section(a, "Shizuku"));
        TextView shz = note(a, "");
        col.addView(shz);
        View ask = Ctl.action(a, Icons.CHECK, "Conectar con Shizuku", null, v -> {
            String m = Shizu.pedir(a);
            if (m != null) Sheet.pill(a, h.backdrop(), m);
        });
        col.addView(ask);
        // Funciones de sistema: solo existen con Shizuku listo
        LinearLayout sys = new LinearLayout(a);
        sys.setOrientation(LinearLayout.VERTICAL);
        sys.addView(Ctl.section(a, "Funciones de sistema"));
        sys.addView(Ctl.action(a, Icons.SPARK, "Limpiar", "Cerrar apps", v -> SistemaActivity.open(a, SistemaActivity.LIMPIAR)));
        sys.addView(Ctl.action(a, Icons.HISTORY, "Apps poco usadas", "Congelar o quitar", v -> SistemaActivity.open(a, SistemaActivity.POCO)));
        sys.addView(Ctl.action(a, Icons.BOX, "Congeladas", "Regresarlas", v -> SistemaActivity.open(a, SistemaActivity.CONGELADAS)));
        sys.addView(Ctl.action(a, Icons.GRID, "Memoria RAM", "Top 15", v -> SistemaActivity.open(a, SistemaActivity.RAM)));
        col.addView(sys);
        LinearLayout exact = exactColors(a, h);
        Runnable paint = () -> {
            Shizu.Estado e = Shizu.estado(a);
            shz.setText("● " + Shizu.texto(a));
            shz.setTextColor(e == Shizu.Estado.LISTO ? Ui.TEXT : Ui.MUTED);
            ask.setVisibility(e == Shizu.Estado.LISTO || e == Shizu.Estado.NO_INSTALADO ? View.GONE : View.VISIBLE);
            sys.setVisibility(e == Shizu.Estado.LISTO ? View.VISIBLE : View.GONE);
            exact.setVisibility(e == Shizu.Estado.LISTO ? View.VISIBLE : View.GONE);
        };
        paint.run();
        // Se repinta solo cuando Shizuku arranca, se cae o da el permiso (sin revisar cada rato)
        shz.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                Shizu.watch(paint);
                paint.run();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                Shizu.unwatch(paint);
            }
        });

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
        col.addView(exact);

        // ---------- Botones ----------
        // Poderes extra con el mismo permiso de arriba; todo se puede deshacer
        col.addView(Ctl.section(a, "Poderes extra"));
        if (!granted) col.addView(note(a, "Necesitan el mismo permiso de arriba (el comando de ADB)."));
        col.addView(Ctl.toggle(a, "Pantalla en blanco y negro", "Todo el celular en grises, para que distraiga menos.", granted && Poderes.grayOn(a), on -> {
            if (!Poderes.setGray(a, on)) Sheet.pill(a, h.backdrop(), "Falta el permiso; usa el comando de arriba");
        }));
        col.addView(Ctl.toggle(a, "Blanco y negro en modo estudio", "Se pone gris al empezar a estudiar y regresa al terminar.", Poderes.grayInStudy(a), on -> {
            if (!granted) {
                Sheet.pill(a, h.backdrop(), "Falta el permiso; usa el comando de arriba");
                return;
            }
            Poderes.setGrayInStudy(a, on);
        }));
        col.addView(Ctl.toggle(a, "Animaciones más rápidas", "Las de todo Android a la mitad de tiempo.", granted && Poderes.fastOn(a), on -> {
            if (!Poderes.setFast(a, on)) Sheet.pill(a, h.backdrop(), "Falta el permiso; usa el comando de arriba");
        }));
        if (granted && !Media.hasAccess(a)) {
            col.addView(Ctl.action(a, Icons.CHECK, "Darme el acceso a notificaciones", "Música y avisos", v -> {
                if (Poderes.grantNotifAccess(a)) Sheet.pill(a, h.backdrop(), "Listo, ya tengo acceso a las notificaciones");
            }));
        }
        col.addView(Ctl.action(a, Icons.HISTORY, "Deshacer todo", "Como estaba", v -> Sheet.show(a, h.backdrop(), "¿Dejar Android como estaba?",
                Sheet.item("Sí, deshacer todo", Icons.CHECK, () -> {
                    int n = Poderes.undoAll(a);
                    Sheet.pill(a, h.backdrop(), n == 0 ? "No había nada que deshacer" : "Listo, regresé " + n + " ajustes a como estaban");
                }),
                Sheet.item("Copiar el comando para quitar el permiso", Icons.SWAP, () -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("adb", Poderes.REVOKE));
                    Sheet.pill(a, h.backdrop(), "Copiado; pégalo en la terminal de tu PC");
                }))));
        col.addView(Ctl.section(a, "Botones en los ajustes rápidos"));
        col.addView(Ctl.action(a, Icons.PLUS, "Agregar los botones de Jostin", String.valueOf(QsTiles.NAMES.length), v -> addTiles(a, h)));
        if (Build.VERSION.SDK_INT >= 33) {
            for (int i = 0; i < QsTiles.NAMES.length; i++) {
                final int k = i;
                col.addView(Ctl.action(a, Icons.PLUS, QsTiles.LABELS[i], "Agregar", v -> request(a, h, k, true)));
            }
        }
        col.addView(note(a, "Wi-Fi, Bluetooth y Modo avión se prenden y apagan con Shizuku; sin Shizuku abren el ajuste de Android. Limpiar abre la lista para confirmar."));
        if (PanelQs.canWrite(a)) {
            col.addView(Ctl.action(a, Icons.SWAP, "Acomodar el panel solo", "Cambiar los de Google", v -> Sheet.show(a, h.backdrop(),
                    "¿Cambiar Internet, Bluetooth y Modo avión por los de Jostin? Se guarda cómo estaba.",
                    Sheet.item("Sí, acomodarlo", Icons.CHECK, () -> bg(a, h, () -> PanelQs.arrange(a), "Listo, ya están los de Jostin", "No pude cambiar el panel")))));
            col.addView(Ctl.action(a, Icons.HISTORY, "Regresar el panel como estaba", null, v -> {
                if (!PanelQs.saved(a)) {
                    Sheet.pill(a, h.backdrop(), "No lo he cambiado");
                    return;
                }
                bg(a, h, () -> PanelQs.restore(a), "Listo, quedó como estaba", "No pude regresarlo");
            }));
        }
    }

    /** Colores exactos con FabricatedOverlay: elegir, ver cómo quedan, aplicar o regresar. */
    private static LinearLayout exactColors(Activity a, Studio.Host h) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ctl.section(a, "Colores exactos (Shizuku)"));
        box.addView(note(a, "Pone en Android justo el color que elijas, con sus tonos claros y oscuros. Abajo ves cómo queda antes de aplicarlo."));
        int seed = ColorExacto.seed(a);
        int[] colors = new int[ColorExacto.PRESETS.length + 1];
        colors[0] = Prefs.accentColor(a);
        System.arraycopy(ColorExacto.PRESETS, 0, colors, 1, ColorExacto.PRESETS.length);
        int sel = 0;
        for (int i = 0; i < colors.length; i++) if (colors[i] == seed) sel = i;
        ColorExacto.Preview prev = new ColorExacto.Preview(a, colors[sel]);
        final int[] pick = {colors[sel]};
        box.addView(Ctl.swatches(a, colors, sel, i -> {
            pick[0] = colors[i];
            prev.setSeed(colors[i]);
        }));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 8);
        lp.bottomMargin = Ui.dp(a, 8);
        box.addView(prev, lp);
        box.addView(Ctl.action(a, Icons.CHECK, "Aplicar estos colores", null, v -> {
            Sheet.pill(a, h.backdrop(), "Aplicando…");
            new Thread(() -> {
                String err = ColorExacto.apply(a, pick[0]);
                a.runOnUiThread(() -> Sheet.pill(a, h.backdrop(), err == null ? "Listo, colores puestos" : err));
            }, "jostin-colores").start();
        }));
        box.addView(Ctl.action(a, Icons.HISTORY, "Regresar a los colores originales", null, v -> new Thread(() -> {
            String err = ColorExacto.restore(a);
            a.runOnUiThread(() -> Sheet.pill(a, h.backdrop(), err == null ? "Listo, regresé los colores de Android" : err));
        }, "jostin-colores").start()));
        return box;
    }

    private interface Job {
        boolean run();
    }

    /** Corre algo que puede usar Shizuku fuera del hilo principal y avisa cómo le fue. */
    private static void bg(Activity a, Studio.Host h, Job job, String ok, String bad) {
        new Thread(() -> {
            boolean r = job.run();
            a.runOnUiThread(() -> Sheet.pill(a, h.backdrop(), r ? ok : bad));
        }, "jostin-panel").start();
    }

    private static void addTiles(Activity a, Studio.Host h) {
        String[] specs = new String[QsTiles.NAMES.length];
        for (int i = 0; i < specs.length; i++) specs[i] = QsTiles.spec(a, QsTiles.NAMES[i]);
        if (SystemColors.addTiles(a, specs)) {
            Sheet.pill(a, h.backdrop(), "Listo, ya están en tus ajustes rápidos");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            request(a, h, 0, false);
            return;
        }
        Sheet.pill(a, h.backdrop(), "Baja la barra, toca el lápiz y arrastra los de Jostin");
    }

    /** Android 13+: pregunta uno por uno con su ventana oficial. */
    private static void request(Activity a, Studio.Host h, int i, boolean one) {
        if (Build.VERSION.SDK_INT < 33 || i >= QsTiles.NAMES.length) return;
        StatusBarManager sb = a.getSystemService(StatusBarManager.class);
        if (sb == null) return;
        ComponentName cn = new ComponentName(a, QsTiles.class.getName() + "$" + QsTiles.NAMES[i]);
        Runnable next = () -> {
            if (!one) request(a, h, i + 1, false);
        };
        try {
            sb.requestAddTileService(cn, QsTiles.LABELS[i], Icon.createWithResource(a, a.getResources().getIdentifier(QsTiles.ICONS[i], "drawable", a.getPackageName())),
                    a.getMainExecutor(), r -> a.runOnUiThread(next));
        } catch (Exception e) {
            next.run();
        }
    }
}
