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
        void editHome();
        void openWidgets();
        void restoreHome();
        void resetHome();
        void previewTransition();
        void chooseIconPack();
    }

    static final String[] TABS = {"Temas", "Fondo", "Color", "Inicio", "Íconos", "Dock", "Reloj", "Gestos", "Avisos", "Android", "Sonido"};
    static final int TAB_THEMES = 0, TAB_WALL = 1, TAB_HOME = 3, TAB_ICONS = 4, TAB_DOCK = 5;
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
            case 0: ThemeStudio.fill(a, h, col); break;
            case 1: wallpaper(a, h, col); break;
            case 2: color(a, h, col); break;
            case 3: home(a, h, col); break;
            case 4: icons(a, h, col); break;
            case 5: dock(a, h, col); break;
            case 6: clock(a, h, col); break;
            case 7: gestures(a, h, col); break;
            case 8: notices(a, h, col); break;
            case 9: AndroidStudio.fill(a, h, col); break;
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
        col.addView(Ctl.toggle(a, "Usar en todo el teléfono", "Pone este fondo también en el sistema para que el viejo no aparezca en apps recientes ni al cambiar de app. Se aplica cuando sales de inicio, porque Android ajusta sus colores a este fondo y vuelve a abrir las pantallas.",
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
        col.addView(Ctl.toggle(a, "Clima bajo el reloj", null, Prefs.showWeather(a), v -> {
            Prefs.setShowWeather(a, v);
            h.applyHome();
        }));
        String city = Prefs.city(a);
        col.addView(Ctl.action(a, Weather.SUN_CLOUD, "Ciudad del clima", city.isEmpty() ? "Elegir" : shorten(city), v -> h.askCity()));
        TextView tip = Ui.text(a, "Esto cambia todos los widgets de reloj que tengas en inicio.", 12, Ui.MUTED, Ui.REGULAR);
        tip.setPadding(Ui.dp(a, 4), Ui.dp(a, 12), 0, 0);
        col.addView(tip);
    }

    // ---------- Inicio ----------

    private static void home(Activity a, Host h, LinearLayout col) {
        col.addView(Ctl.section(a, "Tu pantalla de inicio"));
        col.addView(Ctl.action(a, Icons.SHAPES, "Editar inicio", "Mover y acomodar", v -> Sheet.dismiss(a, h::editHome)));
        col.addView(Ctl.action(a, Icons.PLUS, "Agregar widget", "Más de 20", v -> Sheet.dismiss(a, h::openWidgets)));
        col.addView(Ctl.section(a, "Acomodo"));
        col.addView(Ctl.chips(a, new String[]{"Cuadrícula", "Libre"}, Prefs.homeFree(a) ? 1 : 0, v -> {
            Prefs.setHomeFree(a, v == 1);
            h.applyHome();
        }));
        TextView hint = Ui.text(a, "En «Libre» los widgets se acomodan en cuartos de celda, se pueden encimar, girar los decorativos y ordenar por capas. Al volver a «Cuadrícula» se reacomoda lo que quede encimado.", 12, Ui.MUTED, Ui.REGULAR);
        hint.setLineSpacing(0, 1.2f);
        hint.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, Ui.dp(a, 4));
        col.addView(hint);
        col.addView(Ctl.section(a, "Transición entre páginas"));
        col.addView(Ctl.chips(a, HomeView.TRANSITIONS, Prefs.pageAnim(a), v -> {
            Prefs.setPageAnim(a, v);
            h.previewTransition();
        }));
        col.addView(Ctl.section(a, "Detalles"));
        col.addView(Ctl.toggle(a, "Nombres de las apps", null, Prefs.homeLabels(a), v -> {
            Prefs.setHomeLabels(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.toggle(a, "Puntos de notificación", "Un punto en el ícono de las apps con avisos sin ver.", Prefs.badges(a), v -> {
            Prefs.setBadges(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.toggle(a, "Bloquear diseño", "Nada se mueve por accidente. Para acomodar, apágalo aquí.", Prefs.homeLocked(a), v -> {
            Prefs.setHomeLocked(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Copias de seguridad"));
        col.addView(Ctl.action(a, Icons.HISTORY, "Restaurar una copia", String.valueOf(Safety.tags(a).size()), v -> h.restoreHome()));
        col.addView(Ctl.action(a, Icons.TRASH, "Empezar de cero", null, v -> h.resetHome()));
        int n = Prefs.hidden(a).size();
        col.addView(Ctl.action(a, Icons.SHAPES, "Apps ocultas", n == 0 ? "Ninguna" : String.valueOf(n), v -> h.showHiddenApps()));
    }

    // ---------- Íconos ----------

    private static void icons(Activity a, Host h, LinearLayout col) {
        col.addView(Ctl.section(a, "Estilo"));
        col.addView(Ctl.chips(a, new String[]{"Monocromos", "Originales", "De color"}, Prefs.iconStyle(a), v -> {
            Prefs.setIconStyle(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Forma"));
        col.addView(Ctl.chips(a, IconEngine.SHAPES, Prefs.iconShape(a), v -> {
            Prefs.setIconShape(a, v);
            h.applyHome();
        }));
        if (android.os.Build.VERSION.SDK_INT < 33) {
            TextView t = Ui.text(a, "En esta versión de Android, Squircle y Gota se ven como cuadro redondeado.", 12, Ui.MUTED, Ui.REGULAR);
            t.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), 0, 0);
            col.addView(t);
        }
        col.addView(Ctl.section(a, "Tamaño en inicio"));
        col.addView(Ctl.slider(a, "Tamaño", 70, 130, Prefs.iconScale(a), "%", (v, done) -> {
            if (done) {
                Prefs.setIconScale(a, v);
                h.applyHome();
            }
        }));
        col.addView(Ctl.toggle(a, "Brillo detrás del ícono", "Un halo suave con tu color de acento.", Prefs.iconGlow(a), v -> {
            Prefs.setIconGlow(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Paquete de íconos"));
        String pack = Prefs.iconPack(a);
        String name = "Ninguno";
        if (pack != null) {
            for (String[] p : IconEngine.Pack.installed(a)) if (p[0].equals(pack)) name = p[1];
        }
        col.addView(Ctl.action(a, Icons.SHAPES, "Paquete", shorten(name), v -> h.chooseIconPack()));
        TextView tip = Ui.text(a, "Funciona con paquetes de Play Store hechos para Nova o ADW. Para cambiar el ícono o el nombre de una sola app, mantenla presionada en inicio.", 12, Ui.MUTED, Ui.REGULAR);
        tip.setLineSpacing(0, 1.2f);
        tip.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0);
        col.addView(tip);
    }

    // ---------- Dock ----------

    private static void dock(Activity a, Host h, LinearLayout col) {
        col.addView(Ctl.toggle(a, "Mostrar el dock", null, Prefs.showDock(a), v -> {
            Prefs.setShowDock(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Apps en el dock"));
        col.addView(Ctl.chips(a, new String[]{"4", "5", "6"}, Prefs.dockCount(a) - 4, v -> {
            Prefs.setDockCount(a, v + 4);
            h.applyHome();
        }));
        col.addView(Ctl.slider(a, "Tamaño de los íconos", 36, 56, Prefs.dockIconSize(a), " dp", (v, done) -> {
            if (done) {
                Prefs.setDockIconSize(a, v);
                h.applyHome();
            }
        }));
        col.addView(Ctl.slider(a, "Separación", 0, 10, Prefs.dockSpacing(a), " dp", (v, done) -> {
            if (done) {
                Prefs.setDockSpacing(a, v);
                h.applyHome();
            }
        }));
        col.addView(Ctl.toggle(a, "Fondo de vidrio", null, Prefs.dockGlass(a), v -> {
            Prefs.setDockGlass(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.toggle(a, "Sugerencias en lugares vacíos", "Las apps que más abres llenan los huecos del dock. Mantén presionada una para dejarla fija.", Prefs.dockSuggest(a), v -> {
            Prefs.setDockSuggest(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.toggle(a, "Sugerencias en el cajón", "Tus apps más usadas arriba de la lista.", Prefs.drawerSuggest(a), v -> {
            Prefs.setDrawerSuggest(a, v);
            h.applyHome();
        }));
        TextView tip = Ui.text(a, "Lo que abres se cuenta solo en tu teléfono para las sugerencias; no sale de aquí.", 12, Ui.MUTED, Ui.REGULAR);
        tip.setLineSpacing(0, 1.2f);
        tip.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0);
        col.addView(tip);
    }

    // ---------- Avisos ----------

    private static void notices(Activity a, Host h, LinearLayout col) {
        if (!Media.hasAccess(a)) {
            col.addView(Ctl.section(a, "Permiso"));
            col.addView(Ctl.action(a, Widgets.BELL, "Dar permiso de notificaciones", "Necesario", v -> Media.requestAccess(a)));
            TextView t = Ui.text(a, "Sin este permiso no se pueden ver tus avisos, la música que suena ni los puntos en los íconos. Jostin OS no guarda ni manda tus notificaciones a ningún lado.", 12, Ui.MUTED, Ui.REGULAR);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0);
            col.addView(t);
        }
        col.addView(Ctl.section(a, "En el panel"));
        col.addView(Ctl.toggle(a, "Mostrar notificaciones", "En el panel que baja deslizando desde el centro de inicio.", Prefs.notesInPanel(a), v -> Prefs.setNotesInPanel(a, v)));
        col.addView(Ctl.toggle(a, "Ocultar el contenido", "Solo se ve de qué app es y el título, no el mensaje. Útil si alguien más ve tu pantalla.", Prefs.notesPrivate(a), v -> Prefs.setNotesPrivate(a, v)));
        col.addView(Ctl.section(a, "En los íconos"));
        col.addView(Ctl.toggle(a, "Puntos de notificación", null, Prefs.badges(a), v -> {
            Prefs.setBadges(a, v);
            h.applyHome();
        }));
        TextView t = Ui.text(a, "La barra que baja desde el borde de arriba es la de Android: su diseño no lo puede cambiar ninguna app. Deslizar una notificación en el panel de Jostin sí la quita de Android también.", 12, Ui.MUTED, Ui.REGULAR);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(Ui.dp(a, 4), Ui.dp(a, 14), 0, 0);
        col.addView(t);
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
        col.addView(Ctl.section(a, "Mantener presionado un espacio vacío"));
        col.addView(Ctl.chips(a, new String[]{"Menú", "Editar inicio", "Personalizar", "Nada"}, Prefs.gLong(a), v -> Prefs.setGLong(a, v)));
        TextView tip = Ui.text(a, "Aunque elijas «Nada», puedes llegar aquí con el engrane del panel que baja desde arriba.", 12, Ui.MUTED, Ui.REGULAR);
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
        col.addView(Ctl.section(a, "Animaciones"));
        col.addView(Ctl.chips(a, new String[]{"Normales", "Rápidas", "Reducidas", "Apagadas"}, Prefs.motion(a), v -> {
            Prefs.setMotion(a, v);
            h.applyHome();
        }));
        col.addView(Ctl.section(a, "Al abrir una app"));
        col.addView(Ctl.chips(a, new String[]{"Desde el ícono", "Revelar", "Suave", "Del sistema"}, Prefs.appAnim(a), v -> Prefs.setAppAnim(a, v)));
        TextView mt = Ui.text(a, "«Reducidas» quita los giros y acercamientos para quien se marea con el movimiento. Si en Android activas «Quitar animaciones», también se respeta.", 12, Ui.MUTED, Ui.REGULAR);
        mt.setLineSpacing(0, 1.2f);
        mt.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0);
        col.addView(mt);
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
        col.addView(Ctl.section(a, "Pantalla de bloqueo y carga"));
        col.addView(Ctl.action(a, Icons.HISTORY, "Reloj mientras carga", "Protector", v -> {
            try {
                a.startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
            } catch (Exception ignored) {
                // nada
            }
        }));
        TextView lk = Ui.text(a, "En «Protector de pantalla» elige «Reloj de Jostin OS» y se verá tu fondo con el reloj cuando cargues el teléfono. El fondo de la pantalla de bloqueo se cambia en Fondo > También en la pantalla de bloqueo.", 12, Ui.MUTED, Ui.REGULAR);
        lk.setLineSpacing(0, 1.2f);
        lk.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0);
        col.addView(lk);
        View gap = new View(a);
        col.addView(gap, new LinearLayout.LayoutParams(1, Ui.dp(a, 12)));
        col.addView(Ctl.action(a, Icons.INFO, "Lo que Android no deja cambiar", null, v -> limits(a, h)));
        View gap3 = new View(a);
        col.addView(gap3, new LinearLayout.LayoutParams(1, Ui.dp(a, 8)));
        col.addView(Ctl.action(a, Icons.HOME, "Pantalla de inicio del teléfono", null, v -> {
            try {
                a.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception ignored) {
                // nada
            }
        }));
    }

    /** Lo que una app de inicio no puede hacer en Android, dicho claro. */
    private static void limits(Activity a, Host h) {
        String[][] items = {
                {"Pantalla de bloqueo", "Ninguna app puede reemplazarla ni cambiar cómo pides tu PIN o huella. Jostin OS solo le pone tu fondo y el reloj de carga."},
                {"Barra de notificaciones y ajustes rápidos", "La que baja desde el borde de arriba es de Android. Su diseño no se puede cambiar; el panel de Jostin baja deslizando desde el centro."},
                {"Wi-Fi, Bluetooth y datos", "Desde Android 10 las apps no pueden prenderlos o apagarlos directo. Jostin abre el panel de Android para que lo hagas con un toque."},
                {"Brillo", "Funciona solo si le das el permiso de «Modificar ajustes del sistema»."},
                {"Bloquear con doble toque", "Android solo deja hacerlo con un servicio de accesibilidad, que tú activas y puedes quitar cuando quieras."},
                {"Widgets de otras apps", "Algunos no cambian de tamaño o no se pueden configurar de nuevo; eso lo decide cada app."},
                {"Atajos de las apps", "Android solo se los da a la app de inicio que tengas como predeterminada."},
                {"Play Protect", "Por instalarse fuera de Play Store, Android puede pedir confirmación extra al dar permisos de notificaciones o accesibilidad."}};
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 8), Ui.dp(a, 10));
        for (String[] it : items) {
            TextView t = Ui.text(a, it[0], 15, Ui.TEXT, Ui.SEMIBOLD);
            t.setPadding(0, Ui.dp(a, 14), 0, 0);
            col.addView(t);
            TextView d = Ui.text(a, it[1], 13, Ui.MUTED, Ui.REGULAR);
            d.setLineSpacing(0, 1.2f);
            d.setPadding(0, Ui.dp(a, 4), 0, 0);
            col.addView(d);
        }
        TextView head = Ui.label(a, "Lo que Android no deja cambiar");
        head.setPadding(Ui.dp(a, 8), 0, 0, Ui.dp(a, 4));
        Sheet.panel(a, h.backdrop(), head, col, 0.6f, 0x33000000);
    }

    private static String shorten(String s) {
        return s.length() > 18 ? s.substring(0, 17) + "…" : s;
    }
}
