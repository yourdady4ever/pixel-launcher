package com.jostin.launcher;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Estudio de personalización: una hoja sobre la pantalla de inicio. Cada cambio se ve al
 * instante detrás, así que sirve de vista previa en vivo.
 */
final class Studio {
    interface Host {
        Backdrop backdrop();
        void applyWallpaper();
        void applyLook();
        void applyHome();
        void pickPhoto();
        void deletePhoto(String id);
        void showHiddenApps();
        void requestLock();
        void askCity();
        void askGreeting();
    }

    static final String[] TABS = {"Fondo", "Color", "Reloj", "Inicio", "Gestos", "Sonido"};
    static WallStrip strip;
    private static int lastTab = 0;

    private Studio() {}

    static void show(Activity a, Host h, int tab) {
        if (tab >= 0) lastTab = tab;
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(a, "PERSONALIZAR", 15, Ui.TEXT, Ui.DISPLAY);
        title.setLetterSpacing(0.25f);
        title.setPadding(Ui.dp(a, 8), 0, 0, Ui.dp(a, 12));
        header.addView(title);

        FrameLayout body = new FrameLayout(a);
        View tabs = Ctl.chips(a, TABS, lastTab, t -> {
            lastTab = t;
            fill(a, h, body, t, true);
        });
        header.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        View line = new View(a);
        line.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, 1);
        ll.topMargin = Ui.dp(a, 12);
        header.addView(line, ll);

        fill(a, h, body, lastTab, false);
        Sheet.panel(a, h.backdrop(), header, body, 0.52f, 0x1F000000);
    }

    private static void fill(Activity a, Host h, FrameLayout body, int tab, boolean animate) {
        body.removeAllViews();
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 4), Ui.dp(a, 10));
        switch (tab) {
            case 0: wallpaper(a, h, col); break;
            case 1: color(a, h, col); break;
            case 2: clock(a, h, col); break;
            case 3: home(a, h, col); break;
            case 4: gestures(a, h, col); break;
            default: sound(a, h, col); break;
        }
        body.addView(col, new FrameLayout.LayoutParams(-1, -2));
        if (animate) {
            col.setAlpha(0f);
            col.setTranslationY(Ui.dp(a, 10));
            col.animate().alpha(1f).translationY(0).setDuration(260).start();
        }
    }

    // ---------- Fondo ----------

    private static void wallpaper(Activity a, Host h, LinearLayout col) {
        col.addView(Ctl.section(a, "Elige un fondo"));
        strip = new WallStrip(a, new WallStrip.Listener() {
            @Override
            public void onAdd() {
                h.pickPhoto();
            }

            @Override
            public void onPick(int kind, int scene, String photo) {
                Prefs.setWallKind(a, kind);
                if (kind == 0) Prefs.setWallScene(a, scene);
                else Prefs.setWallPhoto(a, photo);
                h.applyWallpaper();
            }

            @Override
            public void onDelete(String photo) {
                h.deletePhoto(photo);
            }
        });
        col.addView(strip, new LinearLayout.LayoutParams(-1, Ui.dp(a, 226)));
        strip.scrollToSelected();
        TextView tip = Ui.text(a, "Mantén presionada una foto para borrarla.", 12, Ui.MUTED, Ui.REGULAR);
        tip.setPadding(Ui.dp(a, 4), 0, 0, 0);
        col.addView(tip);

        col.addView(Ctl.section(a, "Efecto para tus fotos"));
        col.addView(Ctl.chips(a, Wallpaper.EFFECTS, Prefs.wallEffect(a), v -> {
            Prefs.setWallEffect(a, v);
            if (Prefs.wallKind(a) == 1) h.applyWallpaper();
        }));
        col.addView(Ctl.section(a, "Animación encima"));
        col.addView(Ctl.chips(a, Particles.NAMES, Prefs.wallOverlay(a), v -> {
            Prefs.setWallOverlay(a, v);
            h.backdrop().refreshOverlay();
        }));
        col.addView(Ctl.section(a, "Ajustes"));
        col.addView(Ctl.slider(a, "Oscurecer", 0, 60, Prefs.wallDim(a), "%", (v, done) -> {
            h.backdrop().setUserDim(v / 100f);
            if (done) {
                Prefs.setWallDim(a, v);
                SystemWall.sync(a);
            }
        }));
        col.addView(Ctl.toggle(a, "Fondo en movimiento", "Las escenas se mueven y tus fotos hacen un acercamiento lento.",
                Prefs.animated(a), v -> {
                    Prefs.setAnimated(a, v);
                    h.backdrop().setAnimated(v);
                }));
        col.addView(Ctl.toggle(a, "Usar en todo el teléfono", "Pone este fondo también en el sistema, para que el viejo no aparezca en apps recientes ni al cambiar de app. Android ajusta sus colores a este fondo.",
                Prefs.syncSystem(a), v -> {
                    Prefs.setSyncSystem(a, v);
                    Prefs.setSyncedKey(a, "");
                    SystemWall.sync(a);
                }));
        col.addView(Ctl.toggle(a, "También en la pantalla de bloqueo", null, Prefs.syncLock(a), v -> {
            Prefs.setSyncLock(a, v);
            SystemWall.sync(a);
        }));
        col.addView(Ctl.toggle(a, "Efecto al inclinar", "El fondo se mueve un poco cuando inclinas el teléfono.",
                Prefs.parallax(a), v -> {
                    Prefs.setParallax(a, v);
                    h.backdrop().setParallax(v);
                }));
    }

    // ---------- Color ----------

    private static void color(Activity a, Host h, LinearLayout col) {
        col.addView(Ctl.section(a, "Color de acento"));
        col.addView(Ctl.chips(a, new String[]{"Del fondo", "Monocromo", "Elegido"}, Prefs.accentMode(a), v -> {
            Prefs.setAccentMode(a, v);
            Theme.recompute(a);
            h.applyLook();
        }));
        TextView hint = Ui.text(a, "Con «Del fondo», cada fondo o foto pinta los botones, anillos y selecciones con su propio color.", 12, Ui.MUTED, Ui.REGULAR);
        hint.setLineSpacing(0, 1.2f);
        hint.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, Ui.dp(a, 4));
        col.addView(hint);
        col.addView(Ctl.section(a, "Elige un color"));
        col.addView(Ctl.swatches(a, Theme.SWATCHES, Prefs.accentMode(a) == 2 ? Prefs.accentColor(a) : 0, c -> {
            Prefs.setAccentColor(a, c);
            Prefs.setAccentMode(a, 2);
            Theme.recompute(a);
            h.applyLook();
        }));
        col.addView(Ctl.section(a, "Vidrio"));
        col.addView(Ctl.toggle(a, "Teñir el vidrio con el fondo", "Los paneles toman un poco del color de tu fondo.",
                Prefs.tintFromWall(a), v -> {
                    Prefs.setTintFromWall(a, v);
                    Theme.recompute(a);
                    h.applyLook();
                }));
        col.addView(Ctl.slider(a, "Opacidad del vidrio", 25, 90, Prefs.glassOpacity(a), "%", (v, done) -> {
            Prefs.setGlassOpacity(a, v);
            Theme.recompute(a);
            h.backdrop().invalidate();
            h.applyLook();
        }));
    }

    // ---------- Reloj ----------

    private static void clock(Activity a, Host h, LinearLayout col) {
        col.addView(Ctl.section(a, "Estilo"));
        col.addView(Ctl.chips(a, new String[]{"Espaciado", "Digital", "Apilado"}, Prefs.clockStyle(a), v -> {
            Prefs.setClockStyle(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Posición"));
        col.addView(Ctl.chips(a, new String[]{"Centro", "Izquierda"}, Prefs.clockAlign(a), v -> {
            Prefs.setClockAlign(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Letra"));
        col.addView(Ctl.chips(a, Ui.CLOCK_FONT_NAMES, Ui.CLOCK_FONTS, Prefs.clockFont(a), v -> {
            Prefs.setClockFont(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Color"));
        col.addView(Ctl.chips(a, new String[]{"Automático", "Color de acento"}, Prefs.clockColor(a), v -> {
            Prefs.setClockColor(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Texto"));
        String g = Prefs.greeting(a);
        col.addView(Ctl.action(a, Icons.SPARK, "Frase bajo el reloj", g.isEmpty() ? "Ninguna" : shorten(g), v -> h.askGreeting()));
    }

    // ---------- Inicio ----------

    private static void home(Activity a, Host h, LinearLayout col) {
        col.addView(Ctl.section(a, "Qué se ve en inicio"));
        col.addView(Ctl.toggle(a, "Atajos", "Calculadora, estudio, pendientes y Jarvis.", Prefs.showPanels(a), v -> {
            Prefs.setShowPanels(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.toggle(a, "Música", "Aparece cuando algo está sonando.", Prefs.showMusic(a), v -> {
            Prefs.setShowMusic(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.toggle(a, "Clima bajo el reloj", null, Prefs.showWeather(a), v -> {
            Prefs.setShowWeather(a, v);
            h.applyHome();
        }));
        String city = Prefs.city(a);
        col.addView(Ctl.action(a, Weather.SUN_CLOUD, "Ciudad del clima", city.isEmpty() ? "Elegir" : shorten(city), v -> h.askCity()));
        View gap = new View(a);
        col.addView(gap, new LinearLayout.LayoutParams(1, Ui.dp(a, 6)));
        col.addView(Ctl.toggle(a, "Dock", null, Prefs.showDock(a), v -> {
            Prefs.setShowDock(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Apps en el dock"));
        col.addView(Ctl.chips(a, new String[]{"4", "5", "6"}, Prefs.dockCount(a) - 4, v -> {
            Prefs.setDockCount(a, v + 4);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Íconos"));
        col.addView(Ctl.chips(a, new String[]{"Monocromos", "Originales", "De color"}, Prefs.iconStyle(a), v -> {
            Prefs.setIconStyle(a, v);
            Drawer.trim();
            h.applyHome();
        }));
        View gap2 = new View(a);
        col.addView(gap2, new LinearLayout.LayoutParams(1, Ui.dp(a, 14)));
        int n = Prefs.hidden(a).size();
        col.addView(Ctl.action(a, Icons.SHAPES, "Apps ocultas", n == 0 ? "Ninguna" : String.valueOf(n), v -> h.showHiddenApps()));
    }

    // ---------- Gestos ----------

    private static void gestures(Activity a, Host h, LinearLayout col) {
        col.addView(Ctl.section(a, "Deslizar hacia arriba"));
        col.addView(Ctl.chips(a, new String[]{"Cajón de apps", "Buscar", "Nada"}, Prefs.gUp(a), v -> Prefs.setGUp(a, v)));
        col.addView(Ctl.section(a, "Deslizar hacia abajo"));
        col.addView(Ctl.chips(a, new String[]{"Panel", "Notificaciones", "Nada"}, Prefs.gDown(a), v -> Prefs.setGDown(a, v)));
        col.addView(Ctl.section(a, "Doble toque"));
        col.addView(Ctl.chips(a, new String[]{"Bloquear pantalla", "Linterna", "Otro fondo", "Nada"}, Prefs.gDouble(a), v -> Prefs.setGDouble(a, v)));
        if (LockService.instance == null) {
            View gap = new View(a);
            col.addView(gap, new LinearLayout.LayoutParams(1, Ui.dp(a, 10)));
            col.addView(Ctl.action(a, Widgets.LOCK, "Activar bloqueo con doble toque", "Permiso", v -> h.requestLock()));
        }
        col.addView(Ctl.section(a, "Mantener presionado el fondo"));
        col.addView(Ctl.chips(a, new String[]{"Personalizar", "Fondos", "Nada"}, Prefs.gLong(a), v -> Prefs.setGLong(a, v)));
        TextView tip = Ui.text(a, "Aunque elijas «Nada», siempre puedes llegar aquí manteniendo presionado el reloj.", 12, Ui.MUTED, Ui.REGULAR);
        tip.setLineSpacing(0, 1.2f);
        tip.setPadding(Ui.dp(a, 4), Ui.dp(a, 12), 0, 0);
        col.addView(tip);
    }

    // ---------- Sonido y batería ----------

    private static void sound(Activity a, Host h, LinearLayout col) {
        col.addView(Ctl.section(a, "Sonido"));
        col.addView(Ctl.toggle(a, "Sonidos", null, Prefs.sounds(a), v -> Prefs.setSounds(a, v)));
        col.addView(Ctl.slider(a, "Volumen", 0, 100, Prefs.volume(a), "%", (v, done) -> {
            Prefs.setVolume(a, v);
            if (done) Sound.play(col, Sound.TAP);
        }));
        col.addView(Ctl.toggle(a, "Vibración", null, Prefs.haptics(a), v -> Prefs.setHaptics(a, v)));
        col.addView(Ctl.section(a, "Batería"));
        col.addView(Ctl.toggle(a, "Ahorro automático", "Si activas el ahorro de batería del teléfono, se apagan animaciones, partículas e inclinación.",
                Prefs.autoEco(a), v -> {
                    Prefs.setAutoEco(a, v);
                    h.applyWallpaper();
                }));
        col.addView(Ctl.toggle(a, "Modo fluido", "Fondos a 60 cuadros por segundo. Se ve más suave y gasta un poco más.",
                Prefs.smooth(a), v -> {
                    Prefs.setSmooth(a, v);
                    h.backdrop().setSmooth(v);
                }));
        View gap = new View(a);
        col.addView(gap, new LinearLayout.LayoutParams(1, Ui.dp(a, 12)));
        col.addView(Ctl.action(a, Icons.HOME, "Pantalla de inicio del teléfono", null, v -> {
            try {
                a.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception ignored) {
                // nada
            }
        }));
    }

    private static String shorten(String s) {
        return s.length() > 18 ? s.substring(0, 17) + "…" : s;
    }
}
