package com.jostin.launcher;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Widgets propios de Jostin OS, íconos de apps y carpetas para la pantalla de inicio. */
final class Gadgets {
    static final ExecutorService BG = Executors.newSingleThreadExecutor();
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    static final Locale ES = MainActivity.ES;

    private Gadgets() {}

    // ==================== Catálogo ====================

    static final class Spec {
        final String type, name, desc;
        final Icons.Shape icon;
        final int w, h;
        final float minW, minH;
        final boolean decor;

        Spec(String type, String name, String desc, Icons.Shape icon, int w, int h, float minW, float minH, boolean decor) {
            this.type = type;
            this.name = name;
            this.desc = desc;
            this.icon = icon;
            this.w = w;
            this.h = h;
            this.minW = minW;
            this.minH = minH;
            this.decor = decor;
        }
    }

    static final Icons.Shape CAL = p -> {
        p.addRoundRect(new RectF(3.5f, 5f, 20.5f, 20f), 2.5f, 2.5f, Path.Direction.CW);
        p.moveTo(3.5f, 9.5f);
        p.lineTo(20.5f, 9.5f);
        p.moveTo(8f, 3f);
        p.lineTo(8f, 6.5f);
        p.moveTo(16f, 3f);
        p.lineTo(16f, 6.5f);
    };
    static final Icons.Shape BATT = p -> {
        p.addRoundRect(new RectF(3f, 7.5f, 19f, 16.5f), 2f, 2f, Path.Direction.CW);
        p.moveTo(21f, 10.5f);
        p.lineTo(21f, 13.5f);
        p.moveTo(6.5f, 10.5f);
        p.lineTo(6.5f, 13.5f);
        p.moveTo(9.5f, 10.5f);
        p.lineTo(9.5f, 13.5f);
    };
    static final Icons.Shape GLOBE = p -> {
        p.addCircle(12, 12, 8.5f, Path.Direction.CW);
        p.addOval(new RectF(8f, 3.5f, 16f, 20.5f), Path.Direction.CW);
        p.moveTo(3.5f, 12f);
        p.lineTo(20.5f, 12f);
    };
    static final Icons.Shape TEXT = p -> {
        p.moveTo(5f, 6f);
        p.lineTo(19f, 6f);
        p.moveTo(12f, 6f);
        p.lineTo(12f, 19f);
        p.moveTo(9f, 19f);
        p.lineTo(15f, 19f);
    };
    static final Icons.Shape STICKY = p -> {
        p.moveTo(4.5f, 4.5f);
        p.lineTo(19.5f, 4.5f);
        p.lineTo(19.5f, 14.5f);
        p.lineTo(14.5f, 19.5f);
        p.lineTo(4.5f, 19.5f);
        p.close();
        p.moveTo(14.5f, 19.5f);
        p.lineTo(14.5f, 14.5f);
        p.lineTo(19.5f, 14.5f);
    };
    static final Icons.Shape ANALOG = p -> {
        p.addCircle(12, 12, 8.5f, Path.Direction.CW);
        p.moveTo(12, 12);
        p.lineTo(12, 7f);
        p.moveTo(12, 12);
        p.lineTo(15.5f, 14f);
    };
    static final Icons.Shape HELLO = p -> {
        p.moveTo(5f, 15.5f);
        p.cubicTo(7f, 9f, 9f, 9f, 10f, 13f);
        p.cubicTo(11f, 17f, 13f, 17f, 14f, 12f);
        p.cubicTo(15f, 7f, 17.5f, 7f, 19f, 11f);
    };
    static final Icons.Shape FISH = p -> {
        p.addOval(new RectF(3f, 7.5f, 16f, 16.5f), Path.Direction.CW);
        p.moveTo(16f, 12f);
        p.lineTo(21f, 8f);
        p.lineTo(21f, 16f);
        p.close();
        p.addCircle(7f, 11f, 0.4f, Path.Direction.CW);
    };

    static final Spec[] SPECS = {
            new Spec("reloj", "Reloj", "Día, fecha, hora y clima", Icons.HISTORY, 4, 2, 2, 1, false),
            new Spec("analogico", "Reloj de manecillas", "Clásico, con segundero opcional", ANALOG, 2, 2, 1, 1, false),
            new Spec("hora_mundial", "Hora mundial", "La hora en otra ciudad", GLOBE, 2, 1, 1, 1, false),
            new Spec("saludo", "Saludo", "Buenos días con tu nombre", HELLO, 4, 1, 2, 1, false),
            new Spec("clima", "Clima", "Temperatura de tu ciudad", Weather.SUN_CLOUD, 2, 2, 1, 1, false),
            new Spec("musica", "Música", "Lo que suena, con controles", Widgets.NOTE, 4, 1, 2, 1, false),
            new Spec("ondas", "Ondas", "Barras que bailan con la música", Icons.WAVES, 4, 1, 1, 0.5f, false),
            new Spec("calendario", "Calendario", "El mes completo", CAL, 4, 3, 2, 2, false),
            new Spec("agenda", "Agenda", "Tus próximos eventos", CAL, 4, 2, 2, 1, false),
            new Spec("pendientes", "Pendientes", "Lista que puedes palomear", Icons.TASKS, 4, 2, 2, 1, false),
            new Spec("estudio", "Estudio", "Modo enfoque con temporizador", Icons.FOCUS, 2, 1, 1, 1, false),
            new Spec("jarvis", "Jarvis", "Pregúntale lo que sea a Claude", Icons.SPARK, 2, 1, 1, 1, false),
            new Spec("pez", "Pez de estudio", "Tu pez en vivo, el mismo de la PC", FISH, 4, 2, 2, 1, false),
            new Spec("atajo", "Atajo", "Calculadora, obra, linterna y más", Icons.CALC, 2, 1, 1, 1, false),
            new Spec("cuenta", "Cuenta regresiva", "Días que faltan para una fecha", Icons.HISTORY, 2, 1, 1, 1, false),
            new Spec("sistema", "Sistema", "Batería, almacenamiento y memoria", Icons.SHAPES, 2, 2, 2, 1, false),
            new Spec("bateria", "Batería", "Porcentaje y carga", BATT, 2, 1, 1, 1, false),
            new Spec("interruptores", "Controles", "Linterna, internet, volumen, no molestar", Widgets.TORCH, 4, 1, 2, 1, false),
            new Spec("buscar", "Buscador", "Apps y web en un toque", Icons.SEARCH, 4, 1, 2, 0.5f, false),
            new Spec("nota", "Nota", "Un texto tuyo a la vista", STICKY, 2, 2, 1, 1, true),
            new Spec("texto", "Texto", "Letras decorativas, giran y se enciman", TEXT, 3, 1, 0.5f, 0.5f, true),
            new Spec("forma", "Forma", "Círculos, líneas, anillos y estrellas", Icons.SHAPES, 1, 1, 0.25f, 0.25f, true),
            new Spec("foto", "Foto", "Una foto tuya en un marco", Icons.IMAGE, 2, 2, 0.5f, 0.5f, true),
    };

    static Spec spec(String type) {
        for (Spec s : SPECS) if (s.type.equals(type)) return s;
        return null;
    }

    static boolean decor(String type) {
        Spec s = spec(type);
        return s != null && s.decor;
    }

    static Gadget create(MainActivity a, HomeModel.Item it) {
        Gadget g;
        try {
            switch (it.type) {
                case "reloj": g = new ClockG(a, it); break;
                case "analogico": g = new AnalogG(a, it); break;
                case "hora_mundial": g = new WorldG(a, it); break;
                case "saludo": g = new HelloG(a, it); break;
                case "clima": g = new WeatherG(a, it); break;
                case "musica": g = new MusicG(a, it); break;
                case "ondas": g = new WaveG(a, it); break;
                case "calendario": g = new CalendarG(a, it); break;
                case "agenda": g = new AgendaG(a, it); break;
                case "pendientes": g = new TasksG(a, it); break;
                case "estudio": g = new StudyG(a, it); break;
                case "jarvis": g = new JarvisG(a, it); break;
                case "pez": g = new PezG(a, it); break;
                case "atajo": g = new ShortcutG(a, it); break;
                case "cuenta": g = new CountG(a, it); break;
                case "sistema": g = new SystemG(a, it); break;
                case "bateria": g = new BatteryG(a, it); break;
                case "interruptores": g = new TogglesG(a, it); break;
                case "buscar": g = new SearchG(a, it); break;
                case "nota": g = new NoteG(a, it); break;
                case "texto": g = new TextG(a, it); break;
                case "forma": g = new ShapeG(a, it); break;
                case "foto": g = new PhotoG(a, it); break;
                default: return null;
            }
            g.build();
        } catch (Throwable t) {
            Bitacora.error("crear widget " + it.type, t);
            g = new ErrorG(a, it);
            g.build();
        }
        return g;
    }

    // ==================== Base ====================

    abstract static class Gadget extends FrameLayout {
        final MainActivity act;
        final HomeModel.Item item;
        final Backdrop backdrop;
        boolean started;

        Gadget(MainActivity a, HomeModel.Item it) {
            super(a);
            act = a;
            item = it;
            backdrop = a.backdrop();
            applyBg();
        }

        boolean glassDefault() {
            return true;
        }

        boolean hasGlass() {
            return item.flag("bg", glassDefault());
        }

        void applyBg() {
            if (hasGlass()) Glass.apply(this, backdrop, 24);
            else setBackground(null);
            int p = hasGlass() ? Ui.dp(getContext(), 14) : 0;
            setPadding(p, p, p, p);
        }

        abstract void build();

        void rebuild() {
            try {
                removeAllViews();
                build();
            } catch (Throwable t) {
                Bitacora.error("rehacer widget " + item.type, t);
                removeAllViews();
                addView(text("Este widget tuvo un error", 13, Ui.MUTED, Ui.REGULAR));
            }
        }

        void start() {
            started = true;
        }

        void stop() {
            started = false;
        }

        void tick() {}

        /** Se pasó de página en inicio (los que se animan revisan si quedaron a la vista). */
        void onScroll() {}

        void onMedia() {}

        void options(List<Sheet.Item> out) {}

        int dp(float v) {
            return Ui.dp(getContext(), v);
        }

        TextView text(String s, float sp, int color, Typeface tf) {
            return Ui.text(getContext(), s, sp, color, tf);
        }

        LinearLayout col() {
            LinearLayout l = new LinearLayout(getContext());
            l.setOrientation(LinearLayout.VERTICAL);
            return l;
        }

        LinearLayout row() {
            LinearLayout l = new LinearLayout(getContext());
            l.setOrientation(LinearLayout.HORIZONTAL);
            l.setGravity(Gravity.CENTER_VERTICAL);
            return l;
        }

        LinearLayout.LayoutParams top(int dp) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = dp(dp);
            return lp;
        }

        ImageView glyph(Icons.Shape s, int color) {
            ImageView iv = new ImageView(getContext());
            iv.setImageDrawable(new Icons.Line(s, color, 1.6f));
            return iv;
        }

        /** Color de texto que se lee bien encima del fondo (para widgets sin vidrio). */
        int ink() {
            return hasGlass() ? Ui.TEXT : Theme.clock(getContext());
        }

        void tapSound(View v) {
            Sound.play(v, Sound.TAP);
        }
    }

    static final class ErrorG extends Gadget {
        ErrorG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            TextView t = text("Este widget no se pudo mostrar. Mantenlo presionado para quitarlo.", 13, Ui.MUTED, Ui.REGULAR);
            addView(t, new LayoutParams(-1, -1));
        }
    }

    // ==================== Relojes ====================

    static final class ClockG extends Gadget {
        ClockView cv;

        ClockG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        boolean glassDefault() {
            return false;
        }

        @Override
        void build() {
            cv = new ClockView(act, backdrop);
            int g = Prefs.clockAlign(act) == 1 ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER;
            addView(cv, new LayoutParams(-2, -2, g));
            setOnClickListener(v -> {
                tapSound(v);
                act.showCalendar();
            });
            if (getWidth() > 0) cv.setAvail(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight());
            else cv.rebuild();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (cv != null) post(() -> cv.setAvail(w - getPaddingLeft() - getPaddingRight(), h - getPaddingTop() - getPaddingBottom()));
        }

        @Override
        void tick() {
            if (cv != null) cv.refresh();
        }
    }

    /** Reloj de manecillas dibujado. Se redibuja cada minuto (o cada segundo con segundero). */
    static final class AnalogG extends Gadget {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable tickR = this::invalidate;

        AnalogG(MainActivity a, HomeModel.Item it) {
            super(a, it);
            setWillNotDraw(false);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        boolean glassDefault() {
            return false;
        }

        @Override
        void build() {
            setOnClickListener(v -> {
                tapSound(v);
                act.shortcut("alarma");
            });
        }

        @Override
        void stop() {
            super.stop();
            removeCallbacks(tickR);
        }

        @Override
        void start() {
            super.start();
            invalidate();
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Segundero", Icons.HISTORY, () -> {
                item.put("sec", !item.flag("sec", false));
                act.home.saveModel();
                invalidate();
            }).value(() -> item.flag("sec", false) ? "Sí" : "No"));
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth() - getPaddingLeft() - getPaddingRight(), h = getHeight() - getPaddingTop() - getPaddingBottom();
            float r = Math.min(w, h) / 2f - dp(2);
            float cx = getPaddingLeft() + w / 2f, cy = getPaddingTop() + h / 2f;
            if (r <= 0) return;
            int ink = ink();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.alpha(0xFF000000, hasGlass() ? 0 : 0x40));
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 60; i++) {
                boolean big = i % 5 == 0;
                p.setColor(Ui.alpha(ink, big ? 0xE6 : 0x55));
                p.setStrokeWidth(big ? r * 0.035f : r * 0.015f);
                double a = Math.PI * 2 * i / 60;
                float r1 = r * (big ? 0.84f : 0.9f), r2 = r * 0.96f;
                c.drawLine(cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1, cx + (float) Math.sin(a) * r2, cy - (float) Math.cos(a) * r2, p);
            }
            Calendar now = Calendar.getInstance();
            float sec = now.get(Calendar.SECOND), min = now.get(Calendar.MINUTE) + sec / 60f, hr = now.get(Calendar.HOUR) + min / 60f;
            hand(c, cx, cy, hr / 12f, r * 0.5f, r * 0.07f, ink);
            hand(c, cx, cy, min / 60f, r * 0.74f, r * 0.045f, ink);
            boolean secs = item.flag("sec", false);
            if (secs) hand(c, cx, cy, sec / 60f, r * 0.82f, r * 0.02f, Theme.accent);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.accent);
            c.drawCircle(cx, cy, r * 0.05f, p);
            if (started && isShown()) {
                long delay = secs ? 1000 - now.get(Calendar.MILLISECOND) : (60 - now.get(Calendar.SECOND)) * 1000L;
                removeCallbacks(tickR);
                postDelayed(tickR, delay);
            }
        }

        private void hand(Canvas c, float cx, float cy, float f, float len, float width, int color) {
            double a = Math.PI * 2 * f;
            p.setStyle(Paint.Style.STROKE);
            p.setColor(color);
            p.setStrokeWidth(width);
            c.drawLine(cx, cy, cx + (float) Math.sin(a) * len, cy - (float) Math.cos(a) * len, p);
        }
    }

    static final String[][] CITIES = {
            {"Nueva York", "America/New_York"}, {"Los Ángeles", "America/Los_Angeles"}, {"Chicago", "America/Chicago"},
            {"Toronto", "America/Toronto"}, {"Bogotá", "America/Bogota"}, {"Buenos Aires", "America/Argentina/Buenos_Aires"},
            {"São Paulo", "America/Sao_Paulo"}, {"Madrid", "Europe/Madrid"}, {"Londres", "Europe/London"},
            {"París", "Europe/Paris"}, {"Berlín", "Europe/Berlin"}, {"Dubái", "Asia/Dubai"},
            {"Shanghái", "Asia/Shanghai"}, {"Tokio", "Asia/Tokyo"}, {"Seúl", "Asia/Seoul"}, {"Sídney", "Australia/Sydney"}};

    static final class WorldG extends Gadget {
        WorldG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            String tz = item.str("tz", "America/New_York"), city = item.str("city", "Nueva York");
            LinearLayout c = col();
            c.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(city.toUpperCase(ES), 11, Ui.MUTED, Ui.SEMIBOLD);
            name.setLetterSpacing(0.15f);
            c.addView(name);
            TextClock t = new TextClock(getContext());
            t.setTimeZone(tz);
            t.setFormat24Hour("HH:mm");
            t.setFormat12Hour("h:mm a");
            t.setTypeface(Ui.LIGHT);
            t.setTextColor(Ui.TEXT);
            t.setTextSize(item.h >= 2 ? 40 : 26);
            t.setIncludeFontPadding(false);
            c.addView(t, top(4));
            c.addView(text(diff(tz), 12, Ui.MUTED, Ui.REGULAR), top(4));
            addView(c, new LayoutParams(-1, -1));
            setOnClickListener(v -> {
                tapSound(v);
                act.worldCity(item);
            });
        }

        static String diff(String tz) {
            long now = System.currentTimeMillis();
            int off = TimeZone.getTimeZone(tz).getOffset(now) - TimeZone.getDefault().getOffset(now);
            float hours = off / 3600000f;
            LocalDate here = LocalDate.now(), there = Instant.ofEpochMilli(now).atZone(ZoneId.of(tz)).toLocalDate();
            String day = there.isAfter(here) ? "Mañana" : there.isBefore(here) ? "Ayer" : "Hoy";
            String h = hours == Math.round(hours) ? String.valueOf(Math.round(hours)) : String.format(Locale.US, "%.1f", hours);
            return day + "  ·  " + (hours >= 0 ? "+" : "") + h + " h";
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Cambiar ciudad", GLOBE, () -> act.worldCity(item)));
        }
    }

    static final class HelloG extends Gadget {
        private TextView first;
        private String shownGreeting = "";

        HelloG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        boolean glassDefault() {
            return false;
        }

        static String greeting() {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            return h >= 5 && h < 12 ? "Buenos días" : h < 19 && h >= 12 ? "Buenas tardes" : "Buenas noches";
        }

        @Override
        void build() {
            LinearLayout c = col();
            c.setGravity(Gravity.CENTER_VERTICAL);
            shownGreeting = greeting();
            first = text(shownGreeting + ",", 20, Ui.alpha(ink(), 0xCC), Ui.LIGHT);
            c.addView(first);
            TextView name = text(item.str("name", Prefs.userName(act)), 30, ink(), Ui.DISPLAY);
            name.setLetterSpacing(0.06f);
            name.setMaxLines(1);
            name.setAutoSizeTextTypeUniformWithConfiguration(14, 34, 1, TypedValue.COMPLEX_UNIT_SP);
            c.addView(name, new LinearLayout.LayoutParams(-1, dp(40)));
            addView(c, new LayoutParams(-1, -1));
            setOnClickListener(v -> {
                tapSound(v);
                act.editName(item);
            });
        }

        @Override
        void tick() {
            if (!greeting().equals(shownGreeting)) rebuild();
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Cambiar nombre", TEXT, () -> act.editName(item)));
        }
    }

    // ==================== Clima y música ====================

    static final class WeatherG extends Gadget {
        private long shown;

        WeatherG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            shown = Prefs.weatherTime(act);
            Weather.Now w = Weather.cached(act);
            boolean big = item.h >= 2 && item.w >= 2;
            Icons.Shape shape = w == null ? Weather.SUN_CLOUD : Weather.icon(w.code, w.day);
            String temp = w == null ? "--°" : w.temp + "°";
            String desc = w == null ? (Prefs.city(act).isEmpty() ? "Toca para elegir tu ciudad" : "Buscando el clima…") : Weather.describe(w.code);
            String place = w == null ? Prefs.city(act) : w.place;
            if (big) {
                LinearLayout c = col();
                LinearLayout head = row();
                head.addView(glyph(shape, Ui.TEXT), new LinearLayout.LayoutParams(dp(30), dp(30)));
                TextView pl = text(place, 13, Ui.TEXT, Ui.SEMIBOLD);
                pl.setGravity(Gravity.END);
                pl.setMaxLines(1);
                pl.setEllipsize(TextUtils.TruncateAt.END);
                head.addView(pl, new LinearLayout.LayoutParams(0, -2, 1f));
                c.addView(head, new LinearLayout.LayoutParams(-1, -2));
                c.addView(text(temp, 46, Ui.TEXT, Ui.THIN), top(6));
                TextView d = text(desc, 13, Ui.MUTED, Ui.MEDIUM);
                d.setMaxLines(2);
                c.addView(d, top(2));
                if (w != null) c.addView(text("Máx " + w.max + "°  ·  Mín " + w.min + "°", 12, Ui.MUTED, Ui.REGULAR), top(4));
                addView(c, new LayoutParams(-1, -1));
            } else {
                LinearLayout r = row();
                r.addView(glyph(shape, Ui.TEXT), new LinearLayout.LayoutParams(dp(26), dp(26)));
                TextView t = text(temp, 26, Ui.TEXT, Ui.THIN);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
                tl.leftMargin = dp(8);
                r.addView(t, tl);
                if (item.w >= 2) {
                    LinearLayout c = col();
                    TextView d = text(desc, 12, Ui.TEXT, Ui.MEDIUM);
                    d.setMaxLines(1);
                    d.setEllipsize(TextUtils.TruncateAt.END);
                    c.addView(d);
                    TextView p = text(place, 11, Ui.MUTED, Ui.REGULAR);
                    p.setMaxLines(1);
                    c.addView(p);
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
                    cl.leftMargin = dp(10);
                    r.addView(c, cl);
                }
                addView(r, new LayoutParams(-1, -1));
            }
            setOnClickListener(v -> {
                tapSound(v);
                act.weatherTap();
            });
        }

        @Override
        void tick() {
            if (Prefs.weatherTime(act) != shown) rebuild();
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Cambiar ciudad", Weather.SUN_CLOUD, act::askCity));
        }
    }

    static final class MusicG extends Gadget {
        private Widgets.Art art;
        private TextView title, artist;
        private ImageView play;
        private Progress bar;
        private final Runnable progress = new Runnable() {
            @Override
            public void run() {
                if (bar == null || !started || !Media.playing()) return;
                long d = Media.duration();
                bar.set(d > 0 ? Media.position() / (float) d : 0);
                postDelayed(this, 1000);
            }
        };

        MusicG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            art = null;
            bar = null;
            if (!Media.hasAccess(act)) {
                LinearLayout r = row();
                r.addView(glyph(Widgets.NOTE, Ui.TEXT), new LinearLayout.LayoutParams(dp(26), dp(26)));
                LinearLayout c = col();
                c.addView(text("Música", 15, Ui.TEXT, Ui.SEMIBOLD));
                c.addView(text("Toca para dar permiso de notificaciones", 12, Ui.MUTED, Ui.REGULAR), top(3));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
                cl.leftMargin = dp(12);
                r.addView(c, cl);
                addView(r, new LayoutParams(-1, -1));
                setOnClickListener(v -> Media.requestAccess(act));
                return;
            }
            boolean tall = item.h >= 2, wide = item.w >= 3;
            art = new Widgets.Art(act, false);
            title = text("", 15, Ui.TEXT, Ui.SEMIBOLD);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            artist = text("", 12, Ui.MUTED, Ui.REGULAR);
            artist.setMaxLines(1);
            artist.setEllipsize(TextUtils.TruncateAt.END);
            play = Widgets.iconButton(act, Widgets.PLAY, 44, 11, v -> {
                tapSound(v);
                Media.playPause();
            });
            LinearLayout controls = row();
            controls.setGravity(Gravity.CENTER);
            if (item.w >= 2) controls.addView(Widgets.iconButton(act, Widgets.PREV, 40, 10, v -> {
                tapSound(v);
                Media.previous();
            }));
            controls.addView(play);
            if (item.w >= 2) controls.addView(Widgets.iconButton(act, Widgets.NEXT, 40, 10, v -> {
                tapSound(v);
                Media.next();
            }));
            LinearLayout texts = col();
            texts.addView(title);
            texts.addView(artist, top(3));
            if (!tall) {
                LinearLayout r = row();
                if (wide) r.addView(art, new LinearLayout.LayoutParams(dp(46), dp(46)));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
                tl.leftMargin = wide ? dp(12) : 0;
                r.addView(texts, tl);
                r.addView(controls);
                addView(r, new LayoutParams(-1, -1));
            } else if (wide) {
                LinearLayout r = row();
                int side = Math.max(dp(60), (int) (act.home.cellH * item.h) - dp(40));
                r.addView(art, new LinearLayout.LayoutParams(side, side));
                LinearLayout c = col();
                c.addView(texts);
                bar = new Progress(act);
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(4));
                bl.topMargin = dp(12);
                c.addView(bar, bl);
                c.addView(controls, top(6));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
                cl.leftMargin = dp(14);
                r.addView(c, cl);
                addView(r, new LayoutParams(-1, -1));
            } else {
                LinearLayout c = col();
                c.setGravity(Gravity.CENTER_HORIZONTAL);
                int side = Math.max(dp(48), (int) (act.home.cellH * item.h * 0.42f));
                c.addView(art, new LinearLayout.LayoutParams(side, side));
                title.setGravity(Gravity.CENTER);
                artist.setGravity(Gravity.CENTER);
                c.addView(texts, new LinearLayout.LayoutParams(-1, -2));
                c.addView(controls, top(4));
                addView(c, new LayoutParams(-1, -1));
            }
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                if (Media.active()) Media.open(act);
                else act.openMusicApp();
            });
            onMedia();
        }

        @Override
        void onMedia() {
            if (art == null) {
                if (Media.hasAccess(act)) rebuild();
                return;
            }
            boolean active = Media.active();
            title.setText(active ? Media.title() : "Nada sonando");
            artist.setText(active ? Media.artist() : "Toca para abrir tu música");
            art.set(active ? Media.art() : null, active && Media.playing() && started);
            play.setImageDrawable(Icons.of(Media.playing() ? Widgets.PAUSE : Widgets.PLAY));
            removeCallbacks(progress);
            if (bar != null) {
                long d = Media.duration();
                bar.set(active && d > 0 ? Media.position() / (float) d : 0);
                if (started && Media.playing()) postDelayed(progress, 1000);
            }
        }

        @Override
        void start() {
            super.start();
            onMedia();
        }

        @Override
        void stop() {
            super.stop();
            removeCallbacks(progress);
            if (art != null) art.set(Media.active() ? Media.art() : null, false);
        }
    }

    /** Barras que se mueven solo mientras suena música y el inicio está a la vista. */
    static final class WaveG extends Gadget {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] cur = new float[28], target = new float[28];
        private final Random rnd = new Random();
        private boolean anim;
        private Shader shader;
        private float shaderH = -1;
        private int shaderColor;

        WaveG(MainActivity a, HomeModel.Item it) {
            super(a, it);
            setWillNotDraw(false);
            for (int i = 0; i < cur.length; i++) cur[i] = target[i] = 0.12f + 0.08f * (float) Math.abs(Math.sin(i * 0.7));
        }

        @Override
        boolean glassDefault() {
            return false;
        }

        @Override
        void build() {
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                if (Media.active()) Media.open(act);
                else act.openMusicApp();
            });
        }

        @Override
        void onMedia() {
            boolean want = started && Media.playing();
            if (want && !anim) {
                anim = true;
                invalidate();
            } else if (!want) {
                anim = false;
            }
        }

        @Override
        void start() {
            super.start();
            onMedia();
        }

        @Override
        void stop() {
            super.stop();
            anim = false;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int n = cur.length;
            float w = getWidth() - getPaddingLeft() - getPaddingRight(), h = getHeight() - getPaddingTop() - getPaddingBottom();
            float slot = w / n, bw = slot * 0.55f;
            if (shader == null || shaderH != h || shaderColor != Theme.accent) {
                shaderH = h;
                shaderColor = Theme.accent;
                shader = new LinearGradient(0, getPaddingTop(), 0, getPaddingTop() + h, Theme.accent, Ui.alpha(Theme.accent, 0x55), Shader.TileMode.CLAMP);
            }
            p.setShader(shader);
            for (int i = 0; i < n; i++) {
                if (anim) {
                    if (Math.abs(cur[i] - target[i]) < 0.03f) target[i] = 0.15f + rnd.nextFloat() * 0.85f;
                    cur[i] += (target[i] - cur[i]) * 0.25f;
                } else {
                    cur[i] += (0.12f + 0.08f * (float) Math.abs(Math.sin(i * 0.7)) - cur[i]) * 0.2f;
                }
                float bh = Math.max(bw, h * cur[i]);
                float x = getPaddingLeft() + i * slot + (slot - bw) / 2f, y = getPaddingTop() + (h - bh) / 2f;
                c.drawRoundRect(x, y, x + bw, y + bh, bw / 2f, bw / 2f, p);
            }
            if (anim && isShown()) postInvalidateDelayed(33);
        }
    }

    // ==================== Calendario y agenda ====================

    static final class CalendarG extends Gadget {
        CalendarG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            addView(new MonthView(getContext()), new LayoutParams(-1, -1));
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                openCalendar(act, -1, 0);
            });
        }

        @Override
        void tick() {
            if (getChildCount() > 0) getChildAt(0).invalidate();
        }
    }

    static final class MonthView extends View {
        private static final SimpleDateFormat MONTH = new SimpleDateFormat("MMMM yyyy", ES);
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        MonthView(Context c) {
            super(c);
            p.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            Calendar now = Calendar.getInstance();
            Calendar m = (Calendar) now.clone();
            m.set(Calendar.DAY_OF_MONTH, 1);
            int offset = (m.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            int days = m.getActualMaximum(Calendar.DAY_OF_MONTH);
            int weeks = (int) Math.ceil((offset + days) / 7f);
            float headH = Math.min(h * 0.16f, Ui.dp(getContext(), 24));
            float cw = w / 7f, ch = (h - headH) / (weeks + 1);
            float fs = Math.min(cw, ch) * 0.42f;
            p.setTypeface(Ui.SEMIBOLD);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(Math.min(fs * 0.95f, Ui.dp(getContext(), 13)));
            p.setLetterSpacing(0.15f);
            p.setColor(Ui.MUTED);
            c.drawText(MONTH.format(now.getTime()).toUpperCase(ES), Ui.dp(getContext(), 2), headH * 0.7f, p);
            p.setLetterSpacing(0);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(fs);
            String[] heads = {"L", "M", "M", "J", "V", "S", "D"};
            for (int i = 0; i < 7; i++) {
                p.setColor(Ui.MUTED);
                p.setTypeface(Ui.SEMIBOLD);
                c.drawText(heads[i], cw * i + cw / 2f, headH + ch / 2f + fs * 0.35f, p);
            }
            int today = now.get(Calendar.DAY_OF_MONTH);
            for (int d = 1; d <= days; d++) {
                int idx = offset + d - 1, col = idx % 7, row = idx / 7 + 1;
                float cx = cw * col + cw / 2f, cy = headH + ch * row + ch / 2f;
                if (d == today) {
                    p.setColor(Theme.accent);
                    c.drawCircle(cx, cy, Math.min(cw, ch) * 0.42f, p);
                    p.setColor(Theme.onAccent);
                    p.setTypeface(Ui.SEMIBOLD);
                } else {
                    p.setColor(col >= 5 ? 0xFF8C9099 : Ui.TEXT);
                    p.setTypeface(Ui.REGULAR);
                }
                c.drawText(String.valueOf(d), cx, cy + fs * 0.35f, p);
            }
        }
    }

    static void openCalendar(Context c, long eventId, long begin) {
        Intent i;
        if (eventId > 0) {
            i = new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId));
            i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);
        } else {
            Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
            ContentUris.appendId(b, System.currentTimeMillis());
            i = new Intent(Intent.ACTION_VIEW, b.build());
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception ignored) {
            // no hay app de calendario
        }
    }

    static final class AgendaG extends Gadget {
        private LinearLayout list;
        private long loadedAt;

        AgendaG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        boolean granted() {
            return act.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        }

        @Override
        void build() {
            LinearLayout c = col();
            c.addView(Ui.label(act, "Agenda"));
            list = col();
            c.addView(list, new LinearLayout.LayoutParams(-1, -2));
            addView(c, new LayoutParams(-1, -1));
            if (!granted()) {
                list.addView(text("Toca para ver los eventos de tu calendario", 14, Ui.TEXT, Ui.REGULAR), top(10));
                setOnClickListener(v -> {
                    tapSound(v);
                    act.requestCalendar();
                });
                return;
            }
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                openCalendar(act, -1, 0);
            });
            load();
        }

        private void load() {
            loadedAt = System.currentTimeMillis();
            int max = Math.max(1, Math.round(item.h * 2) - 1);
            Context app = act.getApplicationContext();
            BG.execute(() -> {
                List<Object[]> evs = query(app, max);
                MAIN.post(() -> fill(evs));
            });
        }

        static List<Object[]> query(Context c, int max) {
            List<Object[]> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(b, now - 6 * 3600_000L);
            ContentUris.appendId(b, now + 8L * 86400_000L);
            String[] proj = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR};
            try (Cursor cur = c.getContentResolver().query(b.build(), proj, CalendarContract.Instances.END + " >= ?",
                    new String[]{String.valueOf(now)}, CalendarContract.Instances.BEGIN + " ASC")) {
                while (cur != null && cur.moveToNext() && out.size() < max) {
                    out.add(new Object[]{cur.getLong(0), cur.getString(1), cur.getLong(2), cur.getInt(3) == 1, cur.getInt(4)});
                }
            } catch (Exception ignored) {
                // sin permiso o sin calendario
            }
            return out;
        }

        private void fill(List<Object[]> evs) {
            if (list == null) return;
            list.removeAllViews();
            if (evs.isEmpty()) {
                list.addView(text("Sin eventos esta semana", 14, Ui.MUTED, Ui.REGULAR), top(10));
                return;
            }
            for (Object[] e : evs) {
                LinearLayout r = row();
                View bar = new View(getContext());
                bar.setBackground(Ui.fill(getContext(), 0xFF000000 | (int) e[4], 2));
                r.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(30)));
                LinearLayout c = col();
                TextView t = text(e[1] == null ? "(Sin título)" : (String) e[1], 14, Ui.TEXT, Ui.MEDIUM);
                t.setMaxLines(1);
                t.setEllipsize(TextUtils.TruncateAt.END);
                c.addView(t);
                c.addView(text(when(getContext(), (long) e[2], (boolean) e[3]), 12, Ui.MUTED, Ui.REGULAR), top(2));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
                cl.leftMargin = dp(10);
                r.addView(c, cl);
                long id = (long) e[0], begin = (long) e[2];
                r.setOnClickListener(v -> {
                    Sound.play(v, Sound.OPEN);
                    openCalendar(act, id, begin);
                });
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = dp(10);
                list.addView(r, rl);
            }
        }

        static String when(Context ctx, long begin, boolean allDay) {
            LocalDate today = LocalDate.now();
            LocalDate d = allDay ? Instant.ofEpochMilli(begin).atZone(ZoneId.of("UTC")).toLocalDate()
                    : Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).toLocalDate();
            long diff = ChronoUnit.DAYS.between(today, d);
            String day = diff <= 0 ? "Hoy" : diff == 1 ? "Mañana" : cap(new SimpleDateFormat("EEE d", ES).format(new Date(begin)));
            if (allDay) return day + "  ·  todo el día";
            return day + "  ·  " + DateFormat.getTimeFormat(ctx).format(new Date(begin));
        }

        @Override
        void tick() {
            if (granted() && System.currentTimeMillis() - loadedAt > 15 * 60_000L) load();
        }
    }

    static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ==================== Productividad ====================

    static final class TasksG extends Gadget {
        private String shown = "";

        TasksG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            shown = Prefs.tasks(act);
            JSONArray arr;
            try {
                arr = new JSONArray(shown);
            } catch (Exception e) {
                arr = new JSONArray();
            }
            int pending = 0;
            for (int i = 0; i < arr.length(); i++) if (!arr.optJSONObject(i).optBoolean("d")) pending++;
            LinearLayout c = col();
            LinearLayout head = row();
            head.addView(Ui.label(act, "Pendientes  ·  " + pending), new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView plus = Widgets.iconButton(act, Icons.PLUS, 30, 6, v -> {
                Sound.play(v, Sound.OPEN);
                act.shortcut("tasks");
            });
            head.addView(plus);
            c.addView(head, new LinearLayout.LayoutParams(-1, -2));
            int max = Math.max(1, Math.round(item.h * 2.4f) - 1);
            int shownRows = 0;
            JSONArray all = arr;
            for (int i = 0; i < arr.length() && shownRows < max; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                shownRows++;
                LinearLayout r = row();
                boolean done = o.optBoolean("d");
                PendientesActivity.CheckView ch = new PendientesActivity.CheckView(getContext(), done);
                r.addView(ch, new LinearLayout.LayoutParams(dp(22), dp(22)));
                TextView t = text(o.optString("t"), 14, done ? Ui.MUTED : Ui.TEXT, Ui.REGULAR);
                t.setMaxLines(1);
                t.setEllipsize(TextUtils.TruncateAt.END);
                if (done) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
                tl.leftMargin = dp(10);
                r.addView(t, tl);
                r.setOnClickListener(v -> {
                    boolean now = !o.optBoolean("d");
                    try {
                        o.put("d", now);
                    } catch (Exception ignored) {
                        return;
                    }
                    Sound.play(v, now ? Sound.CHECK : Sound.TAP);
                    ch.setChecked(now, true);
                    Prefs.setTasks(act, all.toString());
                    shown = all.toString();
                    postDelayed(this::rebuild, 450);
                });
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(32));
                rl.topMargin = dp(4);
                c.addView(r, rl);
            }
            if (shownRows == 0) c.addView(text("Todo al día", 14, Ui.MUTED, Ui.REGULAR), top(10));
            addView(c, new LayoutParams(-1, -1));
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                act.shortcut("tasks");
            });
        }

        @Override
        void tick() {
            if (!Prefs.tasks(act).equals(shown)) rebuild();
        }
    }

    static final class StudyG extends Gadget {
        private MainActivity.StudyIcon icon;
        private TextView sub, big;

        StudyG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            if (item.h >= 2 && item.w >= 2) {
                LinearLayout c = col();
                c.setGravity(Gravity.CENTER);
                icon = new MainActivity.StudyIcon(getContext());
                c.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
                big = text("", 26, Ui.TEXT, Ui.LIGHT);
                c.addView(big, top(8));
                sub = text("", 12, Ui.MUTED, Ui.REGULAR);
                c.addView(sub, top(2));
                addView(c, new LayoutParams(-1, -1));
            } else {
                LinearLayout r = row();
                icon = new MainActivity.StudyIcon(getContext());
                r.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
                if (item.w >= 2) {
                    LinearLayout c = col();
                    c.addView(text("Estudio", 15, Ui.TEXT, Ui.SEMIBOLD));
                    sub = text("", 12, Ui.MUTED, Ui.REGULAR);
                    c.addView(sub, top(4));
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
                    cl.leftMargin = dp(12);
                    r.addView(c, cl);
                }
                addView(r, new LayoutParams(-1, -1, Gravity.CENTER_VERTICAL));
                big = null;
            }
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                act.toggleStudy();
            });
            tick();
        }

        @Override
        void tick() {
            boolean on = Prefs.studying(act);
            long left = Prefs.studyEnd(act) - System.currentTimeMillis();
            int min = (int) Math.ceil(left / 60_000.0);
            long total = 90 * 60_000L;
            for (int m : new int[]{25, 50, 90}) {
                if (left <= m * 60_000L) {
                    total = m * 60_000L;
                    break;
                }
            }
            if (icon != null) icon.setProgress(on ? left / (float) total : 0);
            if (sub != null) sub.setText(on ? (big != null ? "minutos de enfoque" : "Faltan " + min + " min") : "Modo enfoque");
            if (big != null) big.setText(on ? String.valueOf(min) : "Estudio");
        }
    }

    static final String[][] ACTIONS = {
            {"calc", "Calculadora", "Científica y obra"}, {"obra", "Obra", "Materiales de construcción"},
            {"tasks", "Pendientes", "Ponte en el Mapa"}, {"panel", "Avisos", "La barra de Android"},
            {"buscar", "Buscar", "Apps y web"}, {"linterna", "Linterna", "Prender o apagar"},
            {"fondo", "Otro fondo", "Cambiar ahora"}, {"personalizar", "Personalizar", "Tu Jostin OS"},
            {"editar", "Editar inicio", "Mover y agregar"}, {"alarma", "Alarmas", "Reloj del teléfono"},
            {"ajustes", "Ajustes", "Del teléfono"}};

    static Icons.Shape actionIcon(String a) {
        switch (a) {
            case "obra": return Icons.WALL;
            case "tasks": return Icons.TASKS;
            case "panel": return Widgets.BELL;
            case "buscar": return Icons.SEARCH;
            case "linterna": return Widgets.TORCH;
            case "fondo": return Icons.IMAGE;
            case "personalizar": return Icons.GEAR;
            case "editar": return Icons.SHAPES;
            case "alarma": return ANALOG;
            case "ajustes": return Icons.GEAR;
            default: return Icons.CALC;
        }
    }

    static String[] action(String a) {
        for (String[] x : ACTIONS) if (x[0].equals(a)) return x;
        return ACTIONS[0];
    }

    /** Ícono en cuadro de vidrio con título y subtítulo, para atajos. */
    static View tile(Gadget g, Icons.Shape icon, String title, String sub) {
        Context c = g.getContext();
        if (g.item.w < 1.5f) {
            LinearLayout col = g.col();
            col.setGravity(Gravity.CENTER);
            ImageView iv = g.glyph(icon, Ui.TEXT);
            int p = g.dp(10);
            iv.setPadding(p, p, p, p);
            iv.setBackground(Ui.fill(c, 0x1AFFFFFF, 15));
            col.addView(iv, new LinearLayout.LayoutParams(g.dp(42), g.dp(42)));
            if (g.item.h >= 1.5f) {
                TextView t = g.text(title, 12, Ui.TEXT, Ui.MEDIUM);
                t.setMaxLines(1);
                col.addView(t, g.top(6));
            }
            return col;
        }
        LinearLayout r = g.row();
        ImageView iv = g.glyph(icon, Ui.TEXT);
        int p = g.dp(10);
        iv.setPadding(p, p, p, p);
        iv.setBackground(Ui.fill(c, 0x1AFFFFFF, 15));
        r.addView(iv, new LinearLayout.LayoutParams(g.dp(42), g.dp(42)));
        LinearLayout col = g.col();
        TextView t = g.text(title, 15, Ui.TEXT, Ui.SEMIBOLD);
        t.setMaxLines(1);
        col.addView(t);
        TextView s = g.text(sub, 12, Ui.MUTED, Ui.REGULAR);
        s.setMaxLines(1);
        s.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(s, g.top(4));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.leftMargin = g.dp(12);
        r.addView(col, cl);
        return r;
    }

    static final class ShortcutG extends Gadget {
        private String shownSub = "";

        ShortcutG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        private String sub(String[] a) {
            if (a[0].equals("tasks")) {
                int n = 0;
                try {
                    JSONArray arr = new JSONArray(Prefs.tasks(act));
                    for (int i = 0; i < arr.length(); i++) if (!arr.getJSONObject(i).optBoolean("d")) n++;
                } catch (Exception ignored) {
                    n = 0;
                }
                if (n > 0) return n + (n == 1 ? " pendiente" : " pendientes");
            }
            return a[2];
        }

        @Override
        void build() {
            String[] a = action(item.str("action", "calc"));
            shownSub = sub(a);
            addView(tile(this, actionIcon(a[0]), a[1], shownSub), new LayoutParams(-1, -1, Gravity.CENTER_VERTICAL));
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                act.shortcut(a[0]);
            });
        }

        @Override
        void tick() {
            if (!sub(action(item.str("action", "calc"))).equals(shownSub)) rebuild();
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Cambiar atajo", Icons.SWAP, () -> act.chooseShortcut(item)));
        }
    }

    static final class JarvisG extends Gadget {
        JarvisG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            if (item.w >= 3) {
                LinearLayout r = row();
                ImageView iv = glyph(Icons.SPARK, Theme.accent);
                r.addView(iv, new LinearLayout.LayoutParams(dp(26), dp(26)));
                TextView t = text("Pregúntale a Jarvis…", 15, Ui.MUTED, Ui.REGULAR);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
                tl.leftMargin = dp(12);
                r.addView(t, tl);
                addView(r, new LayoutParams(-1, -1));
            } else {
                addView(tile(this, Icons.SPARK, "Jarvis", "Pregúntale a Claude"), new LayoutParams(-1, -1, Gravity.CENTER_VERTICAL));
            }
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                act.askJarvis();
            });
        }
    }

    static final class CountG extends Gadget {
        CountG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            String date = item.str("date", ""), title = item.str("title", "Mi fecha");
            LinearLayout c = col();
            c.setGravity(Gravity.CENTER_VERTICAL);
            if (date.isEmpty()) {
                c.addView(text("Cuenta regresiva", 15, Ui.TEXT, Ui.SEMIBOLD));
                c.addView(text("Toca para elegir la fecha", 12, Ui.MUTED, Ui.REGULAR), top(4));
            } else {
                long days;
                try {
                    days = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(date));
                } catch (Exception e) {
                    days = 0;
                }
                String num = days == 0 ? "¡Hoy!" : String.valueOf(Math.abs(days));
                String unit = days == 0 ? "" : Math.abs(days) == 1 ? (days > 0 ? "día" : "día atrás") : (days > 0 ? "días" : "días atrás");
                boolean big = item.h >= 2;
                if (item.w >= 2 && !big) {
                    LinearLayout r = row();
                    r.addView(text(num, 34, Theme.accent, Ui.THIN));
                    LinearLayout cc = col();
                    cc.addView(text(unit, 12, Ui.MUTED, Ui.MEDIUM));
                    TextView t = text(title, 14, Ui.TEXT, Ui.SEMIBOLD);
                    t.setMaxLines(1);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    cc.addView(t, top(2));
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
                    cl.leftMargin = dp(12);
                    r.addView(cc, cl);
                    c.addView(r, new LinearLayout.LayoutParams(-1, -2));
                } else {
                    c.setGravity(Gravity.CENTER);
                    c.addView(text(num, big ? 52 : 30, Theme.accent, Ui.THIN));
                    if (!unit.isEmpty()) c.addView(text(unit, 12, Ui.MUTED, Ui.MEDIUM));
                    TextView t = text(title, 14, Ui.TEXT, Ui.SEMIBOLD);
                    t.setMaxLines(2);
                    t.setGravity(Gravity.CENTER);
                    c.addView(t, top(4));
                }
            }
            addView(c, new LayoutParams(-1, -1));
            setOnClickListener(v -> {
                tapSound(v);
                act.editCountdown(item);
            });
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Cambiar fecha y título", Icons.HISTORY, () -> act.editCountdown(item)));
        }
    }

    // ==================== Sistema ====================

    static final class SystemG extends Gadget {
        private final List<Widgets.Ring> rings = new ArrayList<>();

        SystemG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            rings.clear();
            LinearLayout outer = col();
            boolean grid = item.w < 3 && item.h >= 2;
            int n = item.w >= 3 || item.h >= 2 ? 4 : 2;
            LinearLayout r = row();
            for (int i = 0; i < n; i++) {
                if (grid && i == 2) {
                    outer.addView(r, new LinearLayout.LayoutParams(-1, 0, 1f));
                    r = row();
                }
                Widgets.Ring ring = new Widgets.Ring(getContext());
                rings.add(ring);
                r.addView(ring, new LinearLayout.LayoutParams(0, -1, 1f));
            }
            outer.addView(r, new LinearLayout.LayoutParams(-1, 0, 1f));
            addView(outer, new LayoutParams(-1, -1));
            setOnClickListener(v -> {
                tapSound(v);
                act.shortcut("panel");
            });
            tick();
        }

        @Override
        void tick() {
            Device.Stats s = Device.read(act);
            for (int i = 0; i < rings.size(); i++) {
                Widgets.Ring r = rings.get(i);
                switch (i) {
                    case 0: r.set(s.battery / 100f, s.battery + "%", s.charging ? "CARGANDO" : "BATERÍA"); break;
                    case 1: r.set(rings.size() == 2 ? s.ramUsed : s.storageUsed, Math.round((rings.size() == 2 ? s.ramUsed : s.storageUsed) * 100) + "%", rings.size() == 2 ? "MEMORIA" : "ALMACÉN"); break;
                    case 2: r.set(s.ramUsed, Math.round(s.ramUsed * 100) + "%", "MEMORIA"); break;
                    default: r.set(Math.min(1f, s.batteryTemp / 50f), Math.round(s.batteryTemp) + "°", "TEMP."); break;
                }
            }
        }
    }

    static final class BatteryG extends Gadget {
        private TextView pct, status;
        private Progress bar;
        private Widgets.Ring ring;

        BatteryG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            ring = null;
            if (item.w < 2) {
                ring = new Widgets.Ring(getContext());
                addView(ring, new LayoutParams(-1, -1));
            } else {
                LinearLayout c = col();
                c.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout r = row();
                r.addView(glyph(BATT, Ui.TEXT), new LinearLayout.LayoutParams(dp(24), dp(24)));
                pct = text("", 26, Ui.TEXT, Ui.LIGHT);
                LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
                pl.leftMargin = dp(8);
                r.addView(pct, pl);
                status = text("", 12, Ui.MUTED, Ui.MEDIUM);
                status.setGravity(Gravity.END);
                r.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
                c.addView(r, new LinearLayout.LayoutParams(-1, -2));
                bar = new Progress(act);
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(6));
                bl.topMargin = dp(10);
                c.addView(bar, bl);
                addView(c, new LayoutParams(-1, -1));
            }
            tick();
        }

        @Override
        void tick() {
            Device.Stats s = Device.read(act);
            if (ring != null) ring.set(s.battery / 100f, s.battery + "%", s.charging ? "CARGA" : "BATERÍA");
            if (pct != null) {
                pct.setText(s.battery + "%");
                status.setText(s.charging ? "Cargando" : s.battery <= 15 ? "Batería baja" : "");
                bar.set(s.battery / 100f);
            }
        }
    }

    static final class TogglesG extends Gadget {
        TogglesG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            String[] names = {"Linterna", "Internet", "Volumen", "No molestar"};
            Icons.Shape[] icons = {Widgets.TORCH, Weather.CLOUD, Icons.SOUND, MOON_OFF};
            boolean grid = item.w < 3 && item.h >= 2;
            boolean labels = item.w >= 4 || grid;
            LinearLayout outer = col();
            LinearLayout r = row();
            for (int i = 0; i < 4; i++) {
                if (grid && i == 2) {
                    outer.addView(r, new LinearLayout.LayoutParams(-1, 0, 1f));
                    r = row();
                }
                final int k = i;
                LinearLayout b = col();
                b.setGravity(Gravity.CENTER);
                ImageView iv = glyph(icons[i], active(i) ? Theme.onAccent : Ui.TEXT);
                int p = dp(12);
                iv.setPadding(p, p, p, p);
                iv.setBackground(Ui.fill(getContext(), active(i) ? Theme.accent : 0x26FFFFFF, 24));
                b.addView(iv, new LinearLayout.LayoutParams(dp(46), dp(46)));
                if (labels) {
                    TextView t = text(names[i], 11, Ui.TEXT, Ui.MEDIUM);
                    t.setMaxLines(1);
                    b.addView(t, top(5));
                }
                Ui.pressable(b, 0.9f);
                b.setOnClickListener(v -> {
                    Sound.play(v, Sound.TOGGLE);
                    act.toggle(k);
                    postDelayed(this::rebuild, 250);
                });
                r.addView(b, new LinearLayout.LayoutParams(0, -1, 1f));
            }
            outer.addView(r, new LinearLayout.LayoutParams(-1, 0, 1f));
            addView(outer, new LayoutParams(-1, -1));
        }

        private boolean active(int i) {
            if (i == 0) return Device.torchOn();
            if (i == 3) return NotifService.dndOn(getContext());
            return false;
        }
    }

    static final Icons.Shape MOON_OFF = p -> {
        p.moveTo(15.5f, 4.2f);
        p.cubicTo(9.5f, 4.5f, 6.5f, 11f, 9.8f, 16f);
        p.cubicTo(12.5f, 20f, 18f, 20.2f, 20.5f, 16.5f);
        p.cubicTo(15f, 17f, 11.8f, 10.5f, 15.5f, 4.2f);
        p.close();
    };

    static final class SearchG extends Gadget {
        SearchG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void applyBg() {
            Glass.apply(this, backdrop, 40);
            int p = dp(16);
            setPadding(p, 0, dp(8), 0);
        }

        @Override
        void build() {
            LinearLayout r = row();
            r.addView(glyph(Icons.SEARCH, Ui.TEXT), new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView t = text("Buscar apps y en internet", 15, Ui.MUTED, Ui.REGULAR);
            t.setMaxLines(1);
            t.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.leftMargin = dp(12);
            r.addView(t, tl);
            if (item.w >= 3) {
                ImageView mic = Widgets.iconButton(act, MIC, 42, 10, v -> {
                    Sound.play(v, Sound.OPEN);
                    act.voiceSearch();
                });
                r.addView(mic);
            }
            addView(r, new LayoutParams(-1, -1));
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                act.shortcut("buscar");
            });
        }
    }

    static final Icons.Shape MIC = p -> {
        p.addRoundRect(new RectF(9f, 3.5f, 15f, 14.5f), 3f, 3f, Path.Direction.CW);
        p.moveTo(6f, 11.5f);
        p.cubicTo(6f, 19f, 18f, 19f, 18f, 11.5f);
        p.moveTo(12f, 17.5f);
        p.lineTo(12f, 20.5f);
    };

    // ==================== Decorativos ====================

    static final class NoteG extends Gadget {
        NoteG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        void build() {
            LinearLayout r = new LinearLayout(getContext());
            View bar = new View(getContext());
            bar.setBackground(Ui.fill(getContext(), Theme.accent, 2));
            r.addView(bar, new LinearLayout.LayoutParams(dp(4), -1));
            String s = item.str("text", "");
            TextView t = text(s.isEmpty() ? "Toca para escribir una nota" : s, 16, s.isEmpty() ? Ui.MUTED : Ui.TEXT, Ui.REGULAR);
            t.setLineSpacing(0, 1.15f);
            t.setAutoSizeTextTypeUniformWithConfiguration(11, 26, 1, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -1, 1f);
            tl.leftMargin = dp(12);
            r.addView(t, tl);
            addView(r, new LayoutParams(-1, -1));
            setOnClickListener(v -> {
                tapSound(v);
                act.editNote(item);
            });
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Editar nota", STICKY, () -> act.editNote(item)));
        }
    }

    static final String[] TEXT_COLORS = {"Automático", "Acento", "Blanco", "Negro"};
    static final String[] ALIGNS = {"Izquierda", "Centro", "Derecha"};

    static int textColor(Gadget g, int i) {
        switch (i) {
            case 1: return Theme.accent;
            case 2: return 0xFFFFFFFF;
            case 3: return 0xFF0E0F12;
            default: return g.ink();
        }
    }

    static final class TextG extends Gadget {
        TextG(MainActivity a, HomeModel.Item it) {
            super(a, it);
        }

        @Override
        boolean glassDefault() {
            return false;
        }

        @Override
        void build() {
            String s = item.str("text", "Jostin OS");
            int font = item.num("font", 0);
            TextView t = text(s, 30, textColor(this, item.num("color", 0)), Ui.clockFont(font));
            t.setLetterSpacing(font == 0 || font == 2 ? 0.12f : 0.02f);
            int al = item.num("align", 1);
            t.setGravity(Gravity.CENTER_VERTICAL | (al == 0 ? Gravity.START : al == 2 ? Gravity.END : Gravity.CENTER_HORIZONTAL));
            t.setAutoSizeTextTypeUniformWithConfiguration(8, 200, 1, TypedValue.COMPLEX_UNIT_SP);
            t.setMaxLines(item.h >= 1.5f ? 3 : 1);
            if (!hasGlass()) t.setShadowLayer(dp(10), 0, 0, Theme.wallLight ? 0 : 0x55000000);
            addView(t, new LayoutParams(-1, -1));
            setOnClickListener(v -> {
                tapSound(v);
                act.editText(item);
            });
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Cambiar texto", TEXT, () -> act.editText(item)));
            out.add(Sheet.item("Letra", TEXT, () -> act.chooseFrom(item, "font", Ui.CLOCK_FONT_NAMES)).value(() -> Ui.CLOCK_FONT_NAMES[Math.min(item.num("font", 0), Ui.CLOCK_FONT_NAMES.length - 1)]));
            out.add(Sheet.item("Color", Icons.IMAGE, () -> act.chooseFrom(item, "color", TEXT_COLORS)).value(() -> TEXT_COLORS[item.num("color", 0)]));
            out.add(Sheet.item("Alineación", Icons.TASKS, () -> act.chooseFrom(item, "align", ALIGNS)).value(() -> ALIGNS[item.num("align", 1)]));
        }
    }

    static final String[] SHAPE_NAMES = {"Círculo", "Cuadro redondeado", "Línea", "Anillo", "Estrella", "Rombo"};
    static final String[] SHAPE_COLORS = {"Acento", "Blanco", "Degradado", "Vidrio"};

    static final class ShapeG extends Gadget {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        ShapeG(MainActivity a, HomeModel.Item it) {
            super(a, it);
            setWillNotDraw(false);
        }

        @Override
        boolean glassDefault() {
            return false;
        }

        @Override
        void build() {}

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            int shape = item.num("shape", 0), col = item.num("color", 0);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            switch (col) {
                case 1: p.setColor(0xFFFFFFFF); break;
                case 2:
                    p.setColor(0xFFFFFFFF);
                    p.setShader(new LinearGradient(0, 0, w, h, Theme.accent, 0xFF7C5CFF, Shader.TileMode.CLAMP));
                    break;
                case 3: p.setColor(0x33FFFFFF); break;
                default: p.setColor(Theme.accent); break;
            }
            float m = Math.min(w, h);
            switch (shape) {
                case 1: c.drawRoundRect(0, 0, w, h, m * 0.22f, m * 0.22f, p); break;
                case 2:
                    float th = Math.max(dp(2), h * 0.12f);
                    c.drawRoundRect(0, h / 2f - th / 2f, w, h / 2f + th / 2f, th / 2f, th / 2f, p);
                    break;
                case 3:
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(m * 0.1f);
                    c.drawOval(m * 0.05f, m * 0.05f, w - m * 0.05f, h - m * 0.05f, p);
                    break;
                case 4:
                    path.reset();
                    for (int i = 0; i < 10; i++) {
                        double a = -Math.PI / 2 + Math.PI * i / 5;
                        float r = (i % 2 == 0 ? 0.5f : 0.22f);
                        float x = w / 2f + (float) Math.cos(a) * w * r, y = h / 2f + (float) Math.sin(a) * h * r;
                        if (i == 0) path.moveTo(x, y);
                        else path.lineTo(x, y);
                    }
                    path.close();
                    c.drawPath(path, p);
                    break;
                case 5:
                    path.reset();
                    path.moveTo(w / 2f, 0);
                    path.lineTo(w, h / 2f);
                    path.lineTo(w / 2f, h);
                    path.lineTo(0, h / 2f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                default: c.drawOval(0, 0, w, h, p); break;
            }
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Forma", Icons.SHAPES, () -> act.chooseFrom(item, "shape", SHAPE_NAMES)).value(() -> SHAPE_NAMES[item.num("shape", 0)]));
            out.add(Sheet.item("Color", Icons.IMAGE, () -> act.chooseFrom(item, "color", SHAPE_COLORS)).value(() -> SHAPE_COLORS[item.num("color", 0)]));
        }
    }

    static final class PhotoG extends Gadget {
        private ImageView img;

        PhotoG(MainActivity a, HomeModel.Item it) {
            super(a, it);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(v.getContext(), 24));
                }
            });
            setClipToOutline(true);
        }

        @Override
        boolean glassDefault() {
            return false;
        }

        static File file(Context c, String id) {
            File d = new File(c.getFilesDir(), "gadgets");
            if (!d.exists()) d.mkdirs();
            return new File(d, id + ".jpg");
        }

        @Override
        void build() {
            File f = file(act, item.id);
            if (!f.exists()) {
                setBackground(Ui.fill(getContext(), 0x26FFFFFF, 24));
                LinearLayout c = col();
                c.setGravity(Gravity.CENTER);
                c.addView(glyph(Icons.IMAGE, Ui.TEXT), new LinearLayout.LayoutParams(dp(28), dp(28)));
                c.addView(text("Toca para elegir una foto", 12, Ui.MUTED, Ui.REGULAR), top(6));
                addView(c, new LayoutParams(-1, -1));
                setOnClickListener(v -> {
                    tapSound(v);
                    act.pickGadgetPhoto(item);
                });
                return;
            }
            if (!hasGlass()) setBackground(null);
            img = new ImageView(getContext());
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(img, new LayoutParams(-1, -1));
            int target = Math.max(dp(160), (int) (act.home.cellW * Math.max(item.w, item.h)));
            String path = f.getPath();
            BG.execute(() -> {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, o);
                int s = 1;
                while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= target) s *= 2;
                BitmapFactory.Options d = new BitmapFactory.Options();
                d.inSampleSize = s;
                Bitmap b = BitmapFactory.decodeFile(path, d);
                MAIN.post(() -> {
                    if (img != null) img.setImageBitmap(b);
                });
            });
            setOnClickListener(v -> v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(140)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(260).start()).start());
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Cambiar foto", Icons.IMAGE, () -> act.pickGadgetPhoto(item)));
        }

        /** Guarda la foto elegida reducida a 1400 px como máximo. */
        static void importPhoto(Context c, Uri uri, File out, Runnable done) {
            Context app = c.getApplicationContext();
            BG.execute(() -> {
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                        BitmapFactory.decodeStream(in, null, o);
                    }
                    int s = 1;
                    while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1400) s *= 2;
                    BitmapFactory.Options d = new BitmapFactory.Options();
                    d.inSampleSize = s;
                    Bitmap b;
                    try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                        b = BitmapFactory.decodeStream(in, null, d);
                    }
                    if (b != null) {
                        try (FileOutputStream f = new FileOutputStream(out)) {
                            b.compress(Bitmap.CompressFormat.JPEG, 88, f);
                        }
                        b.recycle();
                    }
                } catch (Exception ignored) {
                    // se queda sin foto
                }
                MAIN.post(done);
            });
        }
    }

    // ==================== Pez de estudio ====================

    /** Lo que trae /pez.json. El launcher solo lo lee: nunca escribe en la base. */
    static final class Pez {
        final double food, happy;
        final int fat, size;
        final long t;
        final String color, hat, fill, by, ev;
        /** Experiencia (para el nivel), suciedad de la pecera y si se viste según la fecha. */
        final double xp, dirt;
        final boolean season;

        Pez(JSONObject o) {
            xp = Math.max(0, o.optDouble("xp", 0));
            double d = o.optDouble("dirt", 0);
            dirt = Double.isNaN(d) ? 0 : d;
            food = num(o, "food");
            happy = num(o, "happy");
            fat = Math.max(0, Math.min(5, o.optInt("fat", 0)));
            t = o.optLong("t", 0);
            by = str(o, "by", "");
            ev = str(o, "ev", "");
            JSONObject look = o.optJSONObject("look");
            if (look == null) look = new JSONObject();
            color = str(look, "color", "#ffb02e");
            hat = str(look, "hat", "none");
            String f = str(look, "fill", "$");
            fill = f.isEmpty() ? "$" : f.substring(0, Character.charCount(f.codePointAt(0)));
            int s = look.optInt("size", 28);
            size = s <= 22 ? 22 : s >= 34 ? 34 : 28;
            season = look.optBoolean("season", true);
        }

        /** El mayor L con 25·L·(L-1) <= xp. */
        int level() {
            int l = (int) Math.floor((1 + Math.sqrt(1 + 4 * (Double.isNaN(xp) ? 0 : xp) / 25.0)) / 2);
            return Math.max(1, l);
        }

        /** La pecera se ensucia sola con el tiempo. */
        float dirt(long now) {
            double gone = t > 0 ? Math.max(0, now - t) / 1000.0 / 180 : 0;
            return (float) Math.max(0, Math.min(100, dirt + gone));
        }

        private static double num(JSONObject o, String k) {
            double v = o.optDouble(k, 50);
            return Double.isNaN(v) ? 50 : v;
        }

        private static String str(JSONObject o, String k, String def) {
            if (!o.has(k) || o.isNull(k)) return def;
            return o.optString(k, def).trim();
        }

        /** Duerme de 10 de la noche a 6 de la mañana (igual que la web y el fondo). */
        static boolean night(long ms) {
            long local = ms + java.util.TimeZone.getDefault().getOffset(ms);
            int h = (int) ((local / 3_600_000L) % 24);
            return h >= 22 || h < 6;
        }

        /** {segundos de noche, segundos de día} entre t0 y t1, en pasos de 15 min y máximo 7 días (splitNight de la web). */
        static double[] splitNight(long t0, long t1) {
            double n = 0, d = 0;
            t0 = Math.max(t0 > 0 ? t0 : t1, t1 - 7 * 86_400_000L);
            for (long x = t0; x < t1; x += 900_000L) {
                double sec = Math.min(900_000L, t1 - x) / 1000.0;
                if (night(x)) n += sec;
                else d += sec;
            }
            return new double[]{n, d};
        }

        // El dibujo lo pide en cada cuadro: se recalcula cada 5 s como mucho
        private long spT = -1, spKey = -1;
        private double[] spVal;

        private double[] split(long now) {
            long key = now / 5000;
            if (spVal == null || spT != t || spKey != key) {
                spVal = splitNight(t, now);
                spT = t;
                spKey = key;
            }
            return spVal;
        }

        private static float clamp(double v) {
            return (float) Math.max(0, Math.min(100, v));
        }

        /** Panza y ánimo bajan solos desde la última vez que alguien lo cuidó (t); de noche la panza baja lento y el ánimo no. */
        float food(long now) {
            if (t <= 0) return clamp(food);
            double[] sp = split(now);
            return clamp(food - sp[1] / 288 - sp[0] / 864);
        }

        float happy(long now) {
            if (t <= 0) return clamp(happy);
            double[] sp = split(now);
            double h = happy - sp[1] / 360;
            // Si durmió y ya amaneció, despierta con el ánimo a la mitad como mínimo
            if (sp[0] > 0 && !night(now)) h = Math.max(h, 50);
            return clamp(h);
        }
    }

    /**
     * El pez de estudio en una terminal verde: el mismo del fondo de la PC y de la página del
     * celular. Lee /pez.json cada ~15 s solo mientras se ve; sin internet se queda el último.
     */
    static final class PezG extends Gadget {
        /** Se cambió de pez (o se conectó): se olvida el anterior y se lee el nuevo ya. */
        static void reset() {
            pez = null;
            raw = null;
            fetchedAt = 0;
            appsAt = 0;
            offline = false;
        }
        private static final ExecutorService NET = Executors.newSingleThreadExecutor();
        private static final int GREEN = 0xFF3DFF7A, RED = 0xFFFF3B3B;
        private static final long EVERY = 30_000;
        private static final String[] PCT = new String[101];
        /** Corazón como letra (FE0E), no como emoji: así toma el color que le toca. */
        private static final String HEART = "♥︎";
        // sleep, hungry, sad, bored, party, full, love, happy, normal, angry, dizzy, surprised, proud
        private static final String[] MOOD_KEY = {"sleep", "hungry", "sad", "bored", "party", "full", "love", "happy", "normal", "angry", "dizzy", "surprised", "proud"};
        private static final String[] MOOD_EYE = {"-", "@", ";", "~", "^", "°", HEART, "^", "°", "ò", "x", "O", "^"};
        private static final String[] MOOD_LBL = {"zzz", "¡HAMBRE!", "TRISTE", "ABURRIDO", "♪ BAILANDO ♪", "LLENITO", "♥ ENAMORADO", "♥ FELIZ", "TRANQUILO", "ENOJADO", "MAREADO", "¡¿?!", "★ CAMPEÓN"};
        private static final float[] MOOD_SP = {.4f, .85f, .6f, .7f, 1.3f, .75f, 1.1f, 1.1f, 1f, 1.5f, .5f, 1.6f, 1.2f};
        private static final String[] MOOD_EMO = {"z", "!", "…", "…", "♪", "~", HEART, "", "", "#", "@", "!", "★"};
        private static final String[] MOOD_OFF = new String[MOOD_LBL.length];
        private static final int LOVE = 6, ANGRY = 9, DIZZY = 10, SURPRISED = 11;
        private static final Pez BLANK = new Pez(new JSONObject());

        static {
            for (int i = 0; i <= 100; i++) PCT[i] = i + "%";
            for (int i = 0; i < MOOD_LBL.length; i++) MOOD_OFF[i] = MOOD_LBL[i] + " · SIN RED";
        }

        // Compartido por todos los peces de inicio: una sola descarga para todos
        private static Pez pez;
        private static String raw;
        private static long fetchedAt;
        private static boolean loading, offline;

        private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scan = new Paint();
        private final Paint fish = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ui = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hat = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();
        private final android.graphics.Rect vis = new android.graphics.Rect();
        private final float[] hsv = {0f, 0.75f, 1f};
        private final Runnable frame = this::invalidate;
        /** Ancho de una letra de la fuente monoespaciada por cada punto de tamaño. */
        private final float adv;
        private boolean still, asleep;
        private long sleepAt;
        // El pez en celdas (una letra por celda); solo se rehace si cambia algo
        private String[] cells = new String[0];
        private int cDir, cN = -1;
        private String cEye = "", cFill = "";
        private String colorOf;
        private int colorVal = 0xFFFFB02E;
        private Pez headFor;
        private boolean headMade, headOffline, headAsleep;
        private String head = "";
        // Lo que dice: una burbuja blanca unos segundos
        private String say, sayFor;
        private long sayUntil, nextChat, lastPcT, seenDuo;
        private boolean wantGreet = true;
        private android.text.StaticLayout sayLayout;
        private int sayW;
        private final android.text.TextPaint sayPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        /** Dónde está el pez (para saber si lo tocaste a él o al resto del cuadro). */
        private final RectF fishBox = new RectF();
        private float downX = -1, downY = -1;
        // Dónde va el pez y a qué velocidad (px por cuadro de 60 fps, en dp)
        private float fx = Float.NaN, fy, fishV, swimPh, swimSv;
        private int fdir = -1;
        private long lastUp;
        private final float[] bubX = new float[3];
        private final long[] bubN = {-1, -1, -1};
        private boolean slow;
        private int hourNow = -1;
        private long battAt;
        private String seasonHat;
        private int lvlFor = -1;
        private String lvlText = "";
        // Basurita de la pecera: posiciones fijas, se enseñan más mientras más sucia
        private static final float[] SPX = new float[90], SPY = new float[90];
        private static final String[] SPC = new String[90];
        private static final int[] SPCOL = new int[90];
        private static final boolean[] SPALGA = new boolean[90];

        static {
            Random rr = new Random(42);
            String[] dots = {"·", ".", ",", "°", "·"}, algae = {"≈", "~", "≈"};
            int[] cols = {0xFF7A8F3A, 0xFF8A7A3A, 0xFF6B7F4A};
            for (int i = 0; i < 90; i++) {
                SPALGA[i] = rr.nextFloat() < .3f;
                SPX[i] = 0.03f + rr.nextFloat() * 0.94f;
                SPY[i] = SPALGA[i] ? 0.75f + rr.nextFloat() * 0.25f : rr.nextFloat() * 0.9f;
                SPC[i] = SPALGA[i] ? algae[rr.nextInt(3)] : dots[rr.nextInt(5)];
                SPCOL[i] = SPALGA[i] ? 0xFF4F8F3A : cols[rr.nextInt(3)];
            }
        }
        // Ánimo del momento, ánimo temporal (enojado, mareado, sorprendido) y emotes que flotan
        private int tempMood = -1;
        private long tempUntil, lastTouch = SystemClock.uptimeMillis(), nextEmo, travelUp;
        private float travelPh;
        private final long[] tapTimes = new long[5];
        private final float[] emoX = new float[6], emoY = new float[6];
        private final long[] emoT = new long[6];
        private final String[] emoCh = new String[6];
        // Brillo de cada letra ya difuminado (se hace una vez por letra y tamaño, no en cada cuadro)
        private final java.util.HashMap<String, Bitmap> glyphGlow = new java.util.HashMap<>();
        private float glyphTs = -1;
        private final Paint blurP = new Paint(Paint.ANTI_ALIAS_FLAG), glyphP = new Paint(Paint.FILTER_BITMAP_FLAG);
        private boolean paintFailed;
        // Brillos (se crean una vez y se reacomodan con una matriz, sin objetos nuevos por cuadro)
        private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Matrix glowM = new android.graphics.Matrix();
        private RadialGradient bgGlow, fishGlow;
        private float bgW, bgH;
        private int glowCol;
        private String sandRow;
        private static long appsAt;

        PezG(MainActivity a, HomeModel.Item it) {
            super(a, it);
            setWillNotDraw(false);
            Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
            fish.setTypeface(mono);
            fish.setTextAlign(Paint.Align.CENTER);
            fish.setTextSize(100);
            adv = fish.measureText("0") / 100f;
            ui.setTypeface(mono);
            // Rayitas de monitor viejo
            Bitmap b = Bitmap.createBitmap(1, Math.max(3, dp(3)), Bitmap.Config.ARGB_8888);
            b.setPixel(0, 0, Ui.alpha(GREEN, 0x12));
            scan.setShader(new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            if (pez == null && PezKey.get(a) != null) pez = saved(a);
            PezTalk.preload(a);
            PezAviso.mark(a);
            setContentDescription("Pez de estudio");
        }

        private static Pez saved(Context c) {
            String s = Prefs.sp(c).getString("pez_last", null);
            if (s == null) return null;
            try {
                Pez p = new Pez(new JSONObject(s));
                raw = s;
                return p;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        boolean glassDefault() {
            return false;
        }

        /** Es su propio cuadro de terminal: nunca lleva vidrio. */
        @Override
        boolean hasGlass() {
            return false;
        }

        @Override
        void build() {
            // Tocar al pez lo hace hablar; tocar fuera de él abre su página
            setOnClickListener(v -> {
                if (fishBox.contains(downX, downY)) {
                    tapSound(v);
                    long t = SystemClock.uptimeMillis();
                    System.arraycopy(tapTimes, 1, tapTimes, 0, tapTimes.length - 1);
                    tapTimes[tapTimes.length - 1] = t;
                    int taps = 0;
                    for (long x : tapTimes) if (x > 0 && t - x < 4000) taps++;
                    if (taps >= 5 && setMood(DIZZY, 5)) return;
                    if (taps == 3 && setMood(ANGRY, 6)) return;
                    // Enamorado ya no es permanente: solo un ratito después de unos mimos
                    Pez pz = pez;
                    if (taps < 3 && pz != null && pz.happy(System.currentTimeMillis()) > 80 && Math.random() < .25 && setMood(LOVE, 22)) return;
                    speak(PezTalk.clickLine(act));
                } else {
                    open();
                }
            });
        }

        @Override
        public boolean dispatchTouchEvent(android.view.MotionEvent e) {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                lastTouch = SystemClock.uptimeMillis();
                PezTalk.wake();
            }
            return super.dispatchTouchEvent(e);
        }

        /** Al pasar de página se redibuja, para que vuelva a moverse si quedó a la vista. */
        @Override
        void onScroll() {
            if (started) invalidate();
        }

        /** Ánimo temporal; a veces lo dice. true si habló. */
        private boolean setMood(int k, int secs) {
            long up = SystemClock.uptimeMillis();
            tempMood = k;
            tempUntil = up + secs * 1000L;
            invalidate();
            if (up > sayUntil && Math.random() < .6) {
                String m = PezTalk.moodLine(act, MOOD_KEY[k]);
                if (m != null) {
                    speak(m);
                    return true;
                }
            }
            return false;
        }

        private int mood(Pez p, float food, float happy, long up) {
            if (asleep) return 0;
            if (tempMood >= 0 && up < tempUntil) return tempMood;
            if (p == null) return 8;
            if (food < 20) return 1;
            if (happy < 25) return 2;
            if (p.fat >= 3) return 5;
            if (happy > 75) return 7;
            if (happy < 40 && up - lastTouch > 600_000) return 3;
            return 8;
        }

        /** Brillo difuminado de una letra (en gris, se pinta del color con el Paint al dibujarlo). */
        private Bitmap glowOf(String ch, float ts) {
            if (ts != glyphTs) {
                for (Bitmap b : glyphGlow.values()) b.recycle();
                glyphGlow.clear();
                glyphTs = ts;
            }
            Bitmap b = glyphGlow.get(ch);
            if (b == null) {
                int rr = (int) Math.ceil(ts * 0.4f) + 1, size = (int) Math.ceil(ts * 1.4f) + rr * 2;
                b = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
                blurP.setTypeface(fish.getTypeface());
                blurP.setTextSize(ts);
                blurP.setTextAlign(Paint.Align.CENTER);
                blurP.setColor(0xFFFFFFFF);
                blurP.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(1f, ts * 0.3f), android.graphics.BlurMaskFilter.Blur.NORMAL));
                new Canvas(b).drawText(ch, size / 2f, size / 2f + ts * 0.35f, blurP);
                glyphGlow.put(ch, b);
            }
            return b;
        }

        private void speak(String s) {
            if (s == null || s.isEmpty()) return;
            say = s;
            sayUntil = SystemClock.uptimeMillis() + Math.min(7000, Math.max(4000, s.length() * 70L));
            invalidate();
        }

        private void open() {
            Sound.play(this, Sound.OPEN);
            Screen.open(act, new Intent(act, PezActivity.class));
        }

        @Override
        void options(List<Sheet.Item> out) {
            out.add(Sheet.item("Abrir el pez", FISH, this::open));
            Sheet.Item key = Sheet.item("Conectar mi pez", Icons.LOCK, () -> PezKey.ask(act, backdrop));
            key.value = () -> PezKey.get(act) == null ? "Sin conectar" : "Conectado";
            key.keepOpen = false;
            out.add(key);
            Sheet.Item apps = Sheet.item("El pez y tus apps", Icons.EYE, () -> AppUsage.explain(act, backdrop));
            apps.value = () -> AppUsage.enabled(act) ? "Sí" : "No";
            out.add(apps);
            out.add(Sheet.item("Tiempo en pantalla", Icons.HISTORY, () -> AppUsage.panel(act)));
            Sheet.Item av = Sheet.item("Avisos cuando necesite algo", Widgets.BELL, () -> {
                boolean off = !Prefs.sp(act).getBoolean("pez_notify_off", false);
                Prefs.sp(act).edit().putBoolean("pez_notify_off", off).apply();
                if (!off) act.askNotifications();
            });
            av.value = () -> Prefs.sp(act).getBoolean("pez_notify_off", false) ? "No" : "Sí";
            out.add(av);
            out.add(Sheet.item("Actualizar ahora", Icons.HISTORY, () -> {
                fetchedAt = 0;
                fetch();
            }));
        }

        @Override
        void start() {
            super.start();
            // Con animaciones reducidas o el ahorro de batería, el pez se queda quieto
            android.os.PowerManager pm = (android.os.PowerManager) act.getSystemService(Context.POWER_SERVICE);
            // Antes el ahorro de batería lo dejaba congelado; ahora solo anima más lento
            still = Prefs.motion(act) >= 2;
            slow = pm != null && pm.isPowerSaveMode();
            PezTalk.wake();
            lastUp = 0;
            sleepAt = 0;
            // Al volver a inicio saluda (cuando de verdad se vea)
            wantGreet = true;
            invalidate();
        }

        @Override
        void stop() {
            super.stop();
            removeCallbacks(frame);
        }

        /** Descarga en su propio hilo, nunca en el principal; con error se queda el último pez. */
        private void fetch() {
            if (loading) return;
            loading = true;
            fetchedAt = System.currentTimeMillis();
            Context app = act.getApplicationContext();
            // Todo vive en /u/<tu clave>/; sin clave el pez funciona sin red
            String urlPez = PezKey.pez(app), urlApps = PezKey.apps(app);
            if (urlPez == null) {
                loading = false;
                return;
            }
            NET.execute(() -> {
                String got = null;
                Pez p = null;
                try {
                    got = Weather.get(urlPez);
                    p = new Pez(new JSONObject(got));
                } catch (Throwable ignored) {
                    // sin internet, base vacía o respuesta rara
                }
                // Lo de tus apps cambia despacio: basta con leerlo cada minuto
                JSONObject a = null;
                if (System.currentTimeMillis() - appsAt > 60_000) {
                    appsAt = System.currentTimeMillis();
                    try {
                        a = new JSONObject(Weather.get(urlApps));
                    } catch (Throwable ignored) {
                        // sin datos de apps
                    }
                }
                final JSONObject apps = a;
                final String body = got;
                final Pez fresh = p;
                MAIN.post(() -> {
                    loading = false;
                    offline = fresh == null;
                    if (apps != null) {
                        PezTalk.appsPc = apps.optJSONObject("pc");
                        JSONObject cel = apps.optJSONObject("cel");
                        if (cel != null) PezTalk.appsCel = cel;
                    }
                    if (fresh != null) {
                        pez = fresh;
                        // Si lo cuidaron desde la compu hace un momento, lo cuenta
                        String ev = fresh.ev;
                        if ("pc".equals(fresh.by) && fresh.t != lastPcT && System.currentTimeMillis() - fresh.t < 20_000
                                && ("feed".equals(ev) || "play".equals(ev) || "look".equals(ev) || "talk".equals(ev) || "clean".equals(ev))) {
                            lastPcT = fresh.t;
                            boolean mimo = ("feed".equals(ev) || "play".equals(ev) || "clean".equals(ev))
                                    && fresh.happy(System.currentTimeMillis()) > 85 && Math.random() < .4;
                            setMood(mimo ? LOVE : SURPRISED, mimo ? 22 : 4);
                            speak(PezTalk.fromPc(act, ev));
                        }
                        // Se guarda solo si cambió, para no escribir en disco cada 15 s
                        if (!body.equals(raw)) {
                            raw = body;
                            Prefs.sp(app).edit().putString("pez_last", body).apply();
                        }
                        long now = System.currentTimeMillis();
                        setContentDescription("Pez de estudio: panza " + Math.round(fresh.food(now)) + "%, ánimo " + Math.round(fresh.happy(now)) + "%");
                    }
                    invalidate();
                });
            });
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            // En la página de al lado sigue "visible" para Android, pero fuera de la pantalla
            boolean seen = isShown() && getGlobalVisibleRect(vis);
            try {
                paint(c, seen);
            } catch (Throwable e) {
                // un dato raro no debe tumbar la pantalla de inicio, pero se anota
                if (!paintFailed) Bitacora.error("dibujar el pez", e);
                paintFailed = true;
            }
            // Solo se anima mientras se ve; fuera de la pantalla revisa despacito por si vuelves
            if (started && seen) {
                removeCallbacks(frame);
                if (still) postDelayed(frame, 1000);
                else postOnAnimationDelayed(frame, MainActivity.idle() ? 64 : slow || PezTalk.battery() >= 0 && PezTalk.battery() < 20 ? 30 : 22);
            } else {
                removeCallbacks(frame);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            lastUp = 0;
            invalidate();
        }

        /** hsl(h, s, l) a color, como en la página (para el arcoíris letra por letra). */
        private static int hsl(float h, float s, float l) {
            float c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = l - c / 2;
            float r, g, b;
            switch ((int) (h / 60f) % 6) {
                case 0: r = c; g = x; b = 0; break;
                case 1: r = x; g = c; b = 0; break;
                case 2: r = 0; g = c; b = x; break;
                case 3: r = 0; g = x; b = c; break;
                case 4: r = x; g = 0; b = c; break;
                default: r = c; g = 0; b = x; break;
            }
            return Color.rgb(Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
        }

        @Override
        public void onVisibilityAggregated(boolean visible) {
            super.onVisibilityAggregated(visible);
            if (visible) invalidate();
            else removeCallbacks(frame);
        }

        private void paint(Canvas c, boolean seen) {
            float L = getPaddingLeft(), T = getPaddingTop();
            float W = getWidth() - L - getPaddingRight(), H = getHeight() - T - getPaddingBottom();
            if (W <= 0 || H <= 0) return;
            long now = System.currentTimeMillis(), up = SystemClock.uptimeMillis();
            if (started && seen && !loading && now - fetchedAt > EVERY) fetch();
            if (now - sleepAt > 30_000) {
                sleepAt = now;
                Calendar cal = Calendar.getInstance();
                hourNow = cal.get(Calendar.HOUR_OF_DAY);
                int mo = cal.get(Calendar.MONTH) + 1, dd = cal.get(Calendar.DAY_OF_MONTH);
                seasonHat = mo == 10 && dd >= 25 ? "witch" : mo == 11 && dd <= 2 ? "flower" : mo == 9 && (dd == 15 || dd == 16) ? "sombrero"
                        : mo == 2 && dd == 14 ? "heart" : (mo == 12 && dd == 31) || (mo == 1 && dd <= 2) ? "party" : mo == 12 ? "santa" : null;
            }
            asleep = (hourNow >= 22 || hourNow < 6) && SystemClock.elapsedRealtime() > PezTalk.wakeUntil;
            Pez p = pez;
            float food = p == null ? 100 : p.food(now), happy = p == null ? 100 : p.happy(now);
            boolean alert = p != null && (food < 25 || happy < 25);

            // Cuadro negro de terminal, con marco rojo que parpadea si necesita algo
            float rad = dp(20);
            r.set(L, T, L + W, T + H);
            box.setStyle(Paint.Style.FILL);
            box.setColor(0xF7020604);
            c.drawRoundRect(r, rad, rad, box);
            if (bgGlow == null || bgW != W || bgH != H) {
                bgW = W;
                bgH = H;
                bgGlow = new RadialGradient(L + W / 2f, T + H * 0.4f, Math.max(W, H) * 0.7f, 0x3300D8B0, 0x00000000, Shader.TileMode.CLAMP);
                glowP.setShader(bgGlow);
            }
            glowP.setShader(bgGlow);
            c.drawRoundRect(r, rad, rad, glowP);
            c.drawRoundRect(r, rad, rad, scan);
            float bw = Math.max(1f, dp(1.2f));
            r.inset(bw / 2f, bw / 2f);
            box.setStyle(Paint.Style.STROKE);
            box.setStrokeWidth(bw);
            box.setColor(Ui.alpha(GREEN, 0x55));
            c.drawRoundRect(r, rad, rad, box);
            if (alert && (up / 450) % 2 == 0) {
                float aw = Math.max(2f, dp(2.6f));
                r.set(L + aw / 2f, T + aw / 2f, L + W - aw / 2f, T + H - aw / 2f);
                box.setStrokeWidth(aw);
                box.setColor(RED);
                c.drawRoundRect(r, rad, rad, box);
            }

            // Encabezado: lo último que pasó (y un ! si tiene hambre o está triste)
            float pad = Math.min(dp(14), Math.min(W, H) * 0.1f);
            float x0 = L + pad, x1 = L + W - pad, y0 = T + pad, y1 = T + H - pad;
            float hs = Math.max(dp(8), Math.min(dp(11), H * 0.1f));
            ui.setStyle(Paint.Style.FILL);
            ui.setTextSize(hs);
            ui.setTextAlign(Paint.Align.LEFT);
            int mk = mood(p, food, happy, up);
            String mood = p == null && offline ? "SIN RED" : offline ? MOOD_OFF[mk] : MOOD_LBL[mk];
            int moodCol = alert ? RED : GREEN;
            ui.clearShadowLayer();
            ui.setTextAlign(Paint.Align.RIGHT);
            ui.setColor(moodCol);
            c.drawText(mood, x1, y0 + hs * 0.8f, ui);
            float moodW = ui.measureText(mood) + hs;
            ui.setTextAlign(Paint.Align.LEFT);
            ui.setColor(Ui.alpha(GREEN, 0xC0));
            int lv = p == null ? 0 : p.level();
            if (lv != lvlFor) {
                lvlFor = lv;
                lvlText = lv > 0 ? "C:\\ESTUDIO\\PEZ.EXE  NV " + lv : "C:\\ESTUDIO\\PEZ.EXE";
            }
            String h = PezKey.get(act) == null ? "Conecta tu pez desde la página 🔒" : lvlText;
            int fit = ui.breakText(h, true, Math.max(0, x1 - x0 - moodW), null);
            c.drawText(h, 0, fit, x0, y0 + hs * 0.8f, ui);
            ui.clearShadowLayer();

            // Barras abajo: en una fila si el widget es chaparrito
            boolean compact = H < dp(118);
            float ls = Math.max(dp(7), Math.min(dp(10), H * 0.085f));
            float barH = Math.max(dp(4), ls * 0.75f);
            float rowH = Math.max(ls, barH) + dp(5);
            float barsTop;
            if (compact) {
                float half = (x1 - x0 - dp(10)) / 2f;
                bar(c, "PANZA", food, x0, y1 - rowH / 2f, half, ls, barH, p != null);
                bar(c, "ÁNIMO", happy, x0 + half + dp(10), y1 - rowH / 2f, half, ls, barH, p != null);
                barsTop = y1 - rowH;
            } else {
                bar(c, "PANZA", food, x0, y1 - rowH * 1.5f, x1 - x0, ls, barH, p != null);
                bar(c, "ÁNIMO", happy, x0, y1 - rowH * 0.5f, x1 - x0, ls, barH, p != null);
                barsTop = y1 - rowH * 2f;
            }

            // El pez
            float top = y0 + hs * 1.4f, areaH = barsTop - dp(2) - top, areaW = x1 - x0;
            float sTs = Math.max(dp(8), Math.min(dp(11), areaH * 0.14f)), floorY = top + areaH;
            areaH -= sTs * 1.2f;
            if (areaH < dp(10) || areaW < dp(20)) return;
            scenery(c, x0, x1, top, floorY, sTs, up);
            Pez look = p != null ? p : BLANK;
            int n = 3 + look.fat, len = 7 + n;
            String eye = MOOD_EYE[mk];
            // El pez más gordo (15 celdas) cabe a lo ancho según el tamaño que eligieron
            float k = look.size == 22 ? 0.6f : look.size == 34 ? 0.92f : 0.76f;
            float ts = Math.min(areaW * k / (15 * adv), areaH * 0.5f);
            if (ts < 4) return;
            fish.setTextSize(ts);
            float cw = adv * ts, fw = len * cw;
            float travel = Math.max(0, areaW - fw);
            float tSec = up / 1000f, dens = getResources().getDisplayMetrics().density;
            float midY = top + areaH * 0.5f + ts * 0.62f, lowY = Math.max(midY, top + areaH - ts * 0.35f);
            // Despierto nada de orilla a orilla; dormido baja al fondo y se mece despacio (velocidad 0.3)
            float tx, ty, maxSp;
            if (still) {
                tx = x0 + travel * 0.5f;
                ty = midY;
                maxSp = 1e6f;
            } else if (asleep) {
                tx = x0 + travel * 0.5f + (float) Math.sin(tSec * 0.2) * Math.min(60 * dens, travel * 0.5f);
                ty = lowY;
                maxSp = 0.3f * dens;
            } else {
                float dts = travelUp == 0 ? 0 : Math.min(0.1f, (up - travelUp) / 1000f);
                travelUp = up;
                travelPh = (travelPh + dts * MOOD_SP[mk] / 16f) % 1f;
                float tf = travelPh < 0.5f ? travelPh * 2f : 2f - travelPh * 2f;
                tx = x0 + travel * tf * tf * (3f - 2f * tf);
                ty = midY + (float) Math.sin(tSec * 2.4) * cw * 0.22f;
                maxSp = 3f * dens * Math.max(1f, MOOD_SP[mk]);
            }
            float dt = lastUp == 0 ? 1f : Math.max(0.1f, Math.min(6f, (up - lastUp) / 16.7f));
            lastUp = up;
            if (Float.isNaN(fx) || still) {
                fx = tx;
                fy = ty;
            }
            float px0 = fx, py0 = fy, ddx = tx - fx, ddy = ty - fy, dist = (float) Math.hypot(ddx, ddy), step = maxSp * dt;
            if (dist > step) {
                fx += ddx / dist * step;
                fy += ddy / dist * step;
            } else {
                fx = tx;
                fy = ty;
            }
            fx = Math.max(x0, Math.min(x0 + travel, fx));
            float movedX = fx - px0, moved = (float) Math.hypot(movedX, fy - py0);
            // fish.v: la velocidad del último paso; si no se mueve, baja poco a poco
            if (moved > 0.01f) fishV = moved / dt / dens;
            else fishV *= (float) Math.pow(0.9, dt);
            if (still) fdir = -1;
            else if (movedX > 0.05f * dt) fdir = 1;
            else if (movedX < -0.05f * dt) fdir = -1;
            int dir = fdir;
            float left = fx, base = fy;
            cells(dir, eye, look.fill, n);
            boolean rainbow = "rainbow".equalsIgnoreCase(look.color);
            int col = rainbow ? GREEN : color(look.color);
            if (fishGlow == null || glowCol != col) {
                glowCol = col;
                fishGlow = new RadialGradient(0, 0, 1f, Ui.alpha(col, 0x40), Ui.alpha(col, 0), Shader.TileMode.CLAMP);
            }
            glowM.setScale(fw * 0.7f, ts * 1.2f);
            glowM.postTranslate(left + fw / 2f, base - ts * 0.35f);
            fishGlow.setLocalMatrix(glowM);
            glowP.setShader(fishGlow);
            c.drawRect(left - fw * 0.25f, base - ts * 1.6f, left + fw * 1.25f, base + ts * 0.9f, glowP);
            // Nado ondulado letra por letra: la onda nace en la cabeza y crece hacia la cola
            // La fase se acumula (si no, al cambiar la velocidad brinca y el pez tiembla)
            float dtS = dt * 0.0167f;
            swimSv += (Math.min(1f, fishV / 2f) - swimSv) * Math.min(1f, dtS * 5f);
            swimPh = (swimPh + dtS * (3f + swimSv * 11f)) % (float) (Math.PI * 200);
            float amp = still ? 0 : (1.1f + swimSv * 2.6f) * ts / 28f;
            fish.clearShadowLayer();
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < len; i++) {
                    int kk = dir < 0 ? i : len - 1 - i;
                    float w = kk / (float) (len - 1);
                    float yo = (float) Math.sin(swimPh - kk * 0.6f) * amp * (0.15f + w * 0.9f);
                    int ci = rainbow ? hsl((tSec * 90f + i * 28f) % 360f, 1f, 0.62f) : col;
                    float cx = left + cw * (i + 0.5f), cy = base + yo;
                    if (pass == 0) {
                        Bitmap g = glowOf(cells[i], ts);
                        glyphP.setColor(ci);
                        glyphP.setAlpha(0xD0);
                        c.drawBitmap(g, cx - g.getWidth() / 2f, cy - ts * 0.35f - g.getHeight() / 2f, glyphP);
                    } else if (cells[i] == HEART) {
                        // El ojo de enamorado va más chico y del color del pez
                        fish.setColor(ci);
                        fish.setTextSize(ts * 0.62f);
                        c.drawText(HEART, cx, cy - ts * 0.14f, fish);
                        fish.setTextSize(ts);
                    } else {
                        fish.setColor(ci);
                        c.drawText(cells[i], cx, cy, fish);
                    }
                }
            }

            // Gorro sobre la cabeza (encima del ojo)
            int eyeAt = dir < 0 ? 1 : len - 2;
            float headX = left + cw * (eyeAt + 0.5f);
            drawHat(c, look.season && seasonHat != null ? seasonHat : look.hat, headX, base - ts * 0.74f, cw, dir);
            fishBox.set(left - cw, base - ts * 1.6f, left + fw + cw, base + ts * 0.4f);
            String emo = MOOD_EMO[mk];
            if (!still && !emo.isEmpty() && up > nextEmo) {
                nextEmo = up + 1100 + (long) (Math.random() * 600);
                for (int e = 0; e < emoT.length; e++) {
                    if (emoT[e] != 0 && up - emoT[e] < 2000) continue;
                    emoT[e] = up;
                    emoCh[e] = emo;
                    emoX[e] = headX + (float) (Math.random() - 0.5) * cw * 0.8f;
                    emoY[e] = base - ts * 1.25f;
                    break;
                }
            }
            ui.setTextAlign(Paint.Align.CENTER);
            ui.setTextSize(ts * 0.55f);
            for (int e = 0; e < emoT.length; e++) {
                if (emoT[e] == 0) continue;
                float a = (up - emoT[e]) / 1000f;
                if (a > 2f) {
                    emoT[e] = 0;
                    continue;
                }
                String ch = emoCh[e];
                int ec = HEART.equals(ch) ? 0xFFFF2E3E : "#".equals(ch) ? 0xFFFF4040 : "★".equals(ch) ? 0xFFFFD54A : "♪".equals(ch) ? 0xFF8FDCFF : 0xFFE8F5E9;
                ui.setColor(Ui.alpha(ec, (int) (255 * Math.max(0f, 1f - a / 2f))));
                c.drawText(ch, emoX[e] + (float) Math.sin(a * 3 + e) * cw * 0.3f, emoY[e] - a * ts * 0.85f, ui);
            }

            // Habla: saluda al verse y luego platica solo cada 18 a 38 s mientras está a la vista
            if (seen && up - battAt > 5000) {
                battAt = up;
                PezTalk.pollBattery(act);
            }
            String now0 = seen && !asleep && up > sayUntil ? PezTalk.urgent(act) : null;
            if (now0 != null) {
                speak(now0);
                nextChat = Math.max(nextChat, up + 20_000);
            } else if (seen) {
                if (wantGreet) {
                    wantGreet = false;
                    speak(PezTalk.greet(act, food, happy));
                    nextChat = up + 25_000 + (long) (Math.random() * 20_000);
                } else if (up >= nextChat && up > sayUntil) {
                    nextChat = up + 25_000 + (long) (Math.random() * 20_000);
                    String duo = PezTalk.duoAlert(act, seenDuo);
                    if (duo != null) {
                        seenDuo = PezTalk.duoAlertAt;
                        speak(duo);
                    } else {
                        speak(PezTalk.chatter(act, food, happy, MOOD_KEY[mk]));
                    }
                }
            }

            // Dormido le salen zetas de la cabeza
            if (!still && asleep) {
                long per = asleep ? 3600 : 2600;
                for (int b = 0; b < 3; b++) {
                    long age = Math.floorMod(up + b * per / 3, per);
                    float a = age / (float) per;
                    // Cada burbuja nace en la boca y ahí se queda; ya no sigue al pez
                    long cycle = Math.floorDiv(up + b * per / 3, per);
                    if (cycle != bubN[b]) {
                        bubN[b] = cycle;
                        bubX[b] = (dir > 0 ? left + fw : left) + dir * cw * 0.4f;
                    }
                    float bx = bubX[b] + (float) Math.sin(a * 6.0 + b) * cw * 0.3f;
                    float by = base - ts * 0.45f - a * areaH * 0.55f;
                    if (by < top) continue;
                    int alpha = (int) (0xAA * (1f - a));
                    if (asleep) {
                        ui.setStyle(Paint.Style.FILL);
                        ui.setTextAlign(Paint.Align.CENTER);
                        ui.setTextSize(ts * (0.35f + a * 0.3f));
                        ui.setColor(Ui.alpha(GREEN, alpha));
                        c.drawText("z", bx, by, ui);
                    } else {
                        ui.setStyle(Paint.Style.STROKE);
                        ui.setStrokeWidth(Math.max(1f, cw * 0.07f));
                        ui.setColor(Ui.alpha(GREEN, alpha));
                        c.drawCircle(bx, by, cw * (0.12f + a * 0.12f), ui);
                    }
                }
                ui.setStyle(Paint.Style.FILL);
            }
            float dirt = p == null ? 0 : p.dirt(now);
            if (dirt > 8) {
                box.setStyle(Paint.Style.FILL);
                box.setColor(Ui.alpha(0xFF465F1E, (int) (255 * Math.min(.28f, dirt / 360f))));
                c.drawRect(x0, top, x1, floorY, box);
            }
            int specks = Math.min(SPX.length, Math.round(dirt * 0.9f));
            if (specks > 0) {
                ui.setStyle(Paint.Style.FILL);
                ui.setTextAlign(Paint.Align.CENTER);
                ui.setTextSize(sTs * 1.1f);
                for (int i = 0; i < specks; i++) {
                    ui.setColor(SPCOL[i]);
                    c.drawText(SPC[i], x0 + (x1 - x0) * SPX[i], top + sTs + (floorY - top - sTs) * SPY[i], ui);
                }
            }
            if (say != null && up < sayUntil) drawSay(c, L, T, W, H, headX, base - ts * 1.55f, base + ts * 0.35f);
        }

        private static final String SAND = ".,:;·";
        private static final float[] PLANTS = {0.06f, 0.16f, 0.92f};
        private static final int[] PLANT_H = {4, 3, 5};

        /** Arena de puntitos, plantitas ( ) que se mecen y el cofre [$$$] con burbujas, como en la página. */
        private void scenery(Canvas c, float x0, float x1, float top, float floorY, float sTs, long up) {
            float tS = up / 1000f, wdt = x1 - x0;
            ui.setStyle(Paint.Style.FILL);
            ui.setTextSize(sTs);
            ui.clearShadowLayer();
            float scw = ui.measureText("0");
            int cols = Math.max(1, (int) (wdt / scw));
            if (sandRow == null || sandRow.length() != cols) {
                Random rr = new Random(7);
                StringBuilder sb = new StringBuilder(cols);
                for (int i = 0; i < cols; i++) sb.append(SAND.charAt(rr.nextInt(SAND.length())));
                sandRow = sb.toString();
            }
            ui.setTextAlign(Paint.Align.LEFT);
            ui.setColor(0xFFB89F6A);
            c.drawText(sandRow, x0, floorY, ui);
            // Plantitas: se mecen más arriba que abajo
            ui.setTextAlign(Paint.Align.CENTER);
            ui.setColor(Ui.alpha(GREEN, 0xCC));
            int room = Math.max(1, (int) ((floorY - top) * 0.55f / (sTs * 0.9f)));
            for (int j = 0; j < PLANTS.length; j++) {
                float px = x0 + wdt * PLANTS[j];
                int rows = Math.min(PLANT_H[j], room);
                for (int rw = 0; rw < rows; rw++) {
                    float sway = still ? 0 : (float) Math.sin(tS * 1.3 + j * 1.7 + rw * 0.5) * scw * 0.4f * (rw + 1) / rows;
                    c.drawText(rw % 2 == 0 ? "(" : ")", px + sway, floorY - sTs * 0.9f * (rw + 1), ui);
                }
            }
            // Cofre dorado
            float chestX = x0 + wdt * 0.72f, chestY = floorY - sTs * 0.9f;
            ui.setColor(0xFFFFD54A);
            c.drawText("[$$$]", chestX, chestY, ui);
            // Burbujas ° o O que suben desde el cofre
            if (still) return;
            float rise = chestY - sTs - top;
            for (int b = 0; b < 4; b++) {
                long per = 4200;
                float a = Math.floorMod(up + b * per / 4, per) / (float) per;
                float by = chestY - sTs - a * rise;
                float bx = chestX + (float) Math.sin(a * 7 + b * 2) * scw * 0.8f;
                ui.setColor(Ui.alpha(0xFFBFF6FF, (int) (0xB0 * (1f - a))));
                c.drawText(a < 0.33f ? "°" : a < 0.66f ? "o" : "O", bx, by, ui);
            }
        }

        /** Burbuja blanca con texto oscuro y colita hacia el pez; arriba de él, o abajo si no cabe. */
        private void drawSay(Canvas c, float bl, float bt, float bw, float bh, float tx, float fTop, float fBot) {
            float ts = Math.max(dp(10), Math.min(dp(12), bh * 0.09f));
            int maxW = (int) Math.max(dp(60), bw * 0.8f - dp(18));
            if (sayLayout == null || !say.equals(sayFor) || sayW != maxW || sayPaint.getTextSize() != ts) {
                sayPaint.setTextSize(ts);
                sayPaint.setColor(0xFF15171A);
                sayPaint.setTypeface(Ui.MEDIUM);
                int w = (int) Math.min(maxW, Math.ceil(sayPaint.measureText(say)));
                sayLayout = android.text.StaticLayout.Builder.obtain(say, 0, say.length(), sayPaint, Math.max(1, w))
                        .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).build();
                sayFor = say;
                sayW = maxW;
            }
            float padX = dp(9), padY = dp(6), tail = dp(7);
            float w = sayLayout.getWidth() + padX * 2, h = sayLayout.getHeight() + padY * 2;
            float x = Math.max(bl + dp(6), Math.min(bl + bw - dp(6) - w, tx - w / 2f));
            boolean above = fTop - tail - h >= bt + dp(4);
            float y = above ? fTop - tail - h : Math.min(fBot + tail, bt + bh - h - dp(4));
            hat.setStyle(Paint.Style.FILL);
            hat.setColor(0xFFFFFFFF);
            r.set(x, y, x + w, y + h);
            c.drawRoundRect(r, dp(10), dp(10), hat);
            float cx = Math.max(x + dp(12), Math.min(x + w - dp(12), tx));
            path.reset();
            if (above) {
                path.moveTo(cx - tail * 0.7f, y + h - 1);
                path.lineTo(cx + tail * 0.7f, y + h - 1);
                path.lineTo(cx, y + h + tail);
            } else {
                path.moveTo(cx - tail * 0.7f, y + 1);
                path.lineTo(cx + tail * 0.7f, y + 1);
                path.lineTo(cx, y - tail);
            }
            path.close();
            c.drawPath(path, hat);
            c.save();
            c.translate(x + padX, y + padY);
            sayLayout.draw(c);
            c.restore();
        }

        /** Posición de 0 a 1 en su ida y vuelta (frena suave en las orillas). */
        private static float posAt(long t, long period) {
            float ph = Math.floorMod(t, period) / (float) period;
            float f = ph < 0.5f ? ph * 2f : 2f - ph * 2f;
            return f * f * (3f - 2f * f);
        }

        /** 1 si va hacia la derecha, -1 si va hacia la izquierda. */
        private static int dirAt(long t, long period) {
            return Math.floorMod(t, period) < period / 2 ? 1 : -1;
        }

        /** Mirando a la izquierda: <°$$$)))><   ·   a la derecha: ><((($$$°> */
        private void cells(int dir, String eye, String fill, int n) {
            if (dir == cDir && n == cN && eye.equals(cEye) && fill.equals(cFill)) return;
            cDir = dir;
            cN = n;
            cEye = eye;
            cFill = fill;
            String[] a = new String[7 + n];
            int k = 0;
            if (dir < 0) {
                a[k++] = "<";
                a[k++] = eye;
                for (int i = 0; i < n; i++) a[k++] = fill;
                a[k++] = ")";
                a[k++] = ")";
                a[k++] = ")";
                a[k++] = ">";
                a[k] = "<";
            } else {
                a[k++] = ">";
                a[k++] = "<";
                a[k++] = "(";
                a[k++] = "(";
                a[k++] = "(";
                for (int i = 0; i < n; i++) a[k++] = fill;
                a[k++] = eye;
                a[k] = ">";
            }
            cells = a;
        }

        private int color(String s) {
            if (!s.equals(colorOf)) {
                colorOf = s;
                try {
                    colorVal = Color.parseColor(s) | 0xFF000000;
                } catch (Exception e) {
                    colorVal = 0xFFFFB02E;
                }
            }
            return colorVal;
        }

        private String header(Pez p) {
            if (headMade && headFor == p && headOffline == offline && headAsleep == asleep) return head;
            headMade = true;
            headFor = p;
            headOffline = offline;
            headAsleep = asleep;
            String s;
            if (p == null) s = offline ? "> sin conexión" : "> buscando al pez…";
            else if (asleep) s = "> zzz… está dormido";
            else if (!p.ev.isEmpty()) s = "> " + (p.by.isEmpty() ? "" : p.by + ": ") + p.ev;
            else s = "> todo tranquilo";
            if (p != null && offline) s += "  · sin conexión";
            head = s;
            return s;
        }

        /** Barra de 10 bloques con su nombre y porcentaje; en rojo si va abajo de 25. */
        private void bar(Canvas c, String label, float v, float x, float cy, float w, float ls, float bh, boolean known) {
            boolean low = known && v < 25;
            ui.setStyle(Paint.Style.FILL);
            ui.setTextSize(ls);
            ui.setTextAlign(Paint.Align.LEFT);
            ui.setColor(Ui.alpha(GREEN, 0xCC));
            float base = cy + ls * 0.36f;
            c.drawText(label, x, base, ui);
            float lw = ui.measureText("ÁNIMO") + ls * 0.6f, pw = ui.measureText("100%") + ls * 0.4f;
            ui.setTextAlign(Paint.Align.RIGHT);
            ui.setColor(low ? RED : Ui.alpha(GREEN, 0xCC));
            c.drawText(known ? PCT[Math.max(0, Math.min(100, Math.round(v)))] : "--", x + w, base, ui);
            float bx0 = x + lw, bx1 = x + w - pw;
            if (bx1 - bx0 < dp(10)) return;
            int n = 10;
            float gap = Math.max(1f, dp(2));
            float sw = (bx1 - bx0 - gap * (n - 1)) / n;
            int on = known ? (int) Math.ceil(v / 10f) : 0;
            box.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                float sx = bx0 + i * (sw + gap);
                box.setColor(i < on ? (low ? RED : GREEN) : Ui.alpha(GREEN, 0x1F));
                c.drawRect(sx, cy - bh / 2f, sx + sw, cy + bh / 2f, box);
            }
        }

        private void drawHat(Canvas c, String type, float hx, float hy, float s, int dir) {
            hat.setStyle(Paint.Style.FILL);
            path.reset();
            switch (type) {
                case "santa": {
                    // Triángulo rojo con la punta caída hacia atrás, banda blanca y pompón
                    float tipX = hx - dir * s * 0.95f, tipY = hy - s * 1.45f;
                    hat.setColor(0xFFE02C2C);
                    path.moveTo(hx - s * 0.8f, hy);
                    path.lineTo(hx + s * 0.8f, hy);
                    path.lineTo(tipX, tipY);
                    path.close();
                    c.drawPath(path, hat);
                    hat.setColor(0xFFFFFFFF);
                    r.set(hx - s * 0.95f, hy - s * 0.24f, hx + s * 0.95f, hy + s * 0.12f);
                    c.drawRoundRect(r, s * 0.16f, s * 0.16f, hat);
                    c.drawCircle(tipX, tipY, s * 0.24f, hat);
                    break;
                }
                case "crown": {
                    // Corona dorada en zigzag
                    float w = s * 0.8f, h = s * 0.95f;
                    hat.setColor(0xFFFFD54A);
                    path.moveTo(hx - w, hy);
                    path.lineTo(hx - w, hy - h);
                    path.lineTo(hx - w * 0.5f, hy - h * 0.45f);
                    path.lineTo(hx, hy - h);
                    path.lineTo(hx + w * 0.5f, hy - h * 0.45f);
                    path.lineTo(hx + w, hy - h);
                    path.lineTo(hx + w, hy);
                    path.close();
                    c.drawPath(path, hat);
                    hat.setColor(0xFFE02C2C);
                    c.drawCircle(hx, hy - h * 0.22f, s * 0.11f, hat);
                    break;
                }
                case "cap": {
                    // Gorra azul con la visera hacia donde mira
                    hat.setColor(0xFF2A6CFF);
                    r.set(hx - s * 0.75f, hy - s * 0.85f, hx + s * 0.75f, hy + s * 0.85f);
                    path.arcTo(r, 180, 180, true);
                    path.close();
                    c.drawPath(path, hat);
                    float v0 = hx + dir * s * 0.45f, v1 = hx + dir * s * 1.35f;
                    r.set(Math.min(v0, v1), hy - s * 0.18f, Math.max(v0, v1), hy + s * 0.02f);
                    c.drawRoundRect(r, s * 0.1f, s * 0.1f, hat);
                    c.drawCircle(hx, hy - s * 0.85f, s * 0.1f, hat);
                    break;
                }
                case "bow": {
                    // Moño rosa: dos alas y el nudo
                    float ky = hy - s * 0.35f, ww = s * 0.85f, wh = s * 0.5f;
                    hat.setColor(0xFFFF4FA0);
                    path.moveTo(hx, ky);
                    path.lineTo(hx - ww, ky - wh);
                    path.lineTo(hx - ww, ky + wh);
                    path.close();
                    path.moveTo(hx, ky);
                    path.lineTo(hx + ww, ky - wh);
                    path.lineTo(hx + ww, ky + wh);
                    path.close();
                    c.drawPath(path, hat);
                    c.drawCircle(hx, ky, s * 0.2f, hat);
                    break;
                }
                default:
                    drawHat2(c, type, hx, hy, s, dir);
                    break;
            }
        }

        /** Coordenadas de la página (pez de 28 px, base del gorro en y = -12) pasadas al tamaño del widget. */
        private void drawHat2(Canvas c, String type, float hx, float hy, float s, int dir) {
            float u = s / 16.8f;
            hat.setStyle(Paint.Style.FILL);
            path.reset();
            switch (type) {
                case "party":
                    hat.setColor(0xFF7C4DFF);
                    path.moveTo(hx - 11 * u, hy);
                    path.lineTo(hx + 11 * u, hy);
                    path.lineTo(hx, hy - 28 * u);
                    path.close();
                    c.drawPath(path, hat);
                    hat.setStyle(Paint.Style.STROKE);
                    hat.setStrokeWidth(2 * u);
                    hat.setColor(0xFFFFD54A);
                    c.drawLine(hx - 7 * u, hy - 8 * u, hx + 7 * u, hy - 8 * u, hat);
                    c.drawLine(hx - 4 * u, hy - 17 * u, hx + 4 * u, hy - 17 * u, hat);
                    hat.setStyle(Paint.Style.FILL);
                    hat.setColor(0xFFFF4FA0);
                    c.drawCircle(hx, hy - 29 * u, 4 * u, hat);
                    break;
                case "wizard":
                    hat.setColor(0xFF5E35B1);
                    path.moveTo(hx - 15 * u, hy);
                    path.lineTo(hx + 15 * u, hy);
                    path.lineTo(hx - dir * 10 * u, hy - 38 * u);
                    path.close();
                    c.drawPath(path, hat);
                    hat.setColor(0xFFFFD54A);
                    hat.setTextAlign(Paint.Align.CENTER);
                    hat.setTextSize(12 * u);
                    c.drawText("★", hx - dir * 2 * u, hy - 10 * u, hat);
                    break;
                case "halo":
                    hat.setStyle(Paint.Style.STROKE);
                    hat.setStrokeWidth(3 * u);
                    hat.setColor(0xFFFFD54A);
                    r.set(hx - 14 * u, hy - 22 * u, hx + 14 * u, hy - 14 * u);
                    c.drawOval(r, hat);
                    hat.setStyle(Paint.Style.FILL);
                    break;
                case "horns":
                    hat.setColor(0xFFE53935);
                    path.moveTo(hx - 12 * u, hy);
                    path.lineTo(hx - 15 * u, hy - 18 * u);
                    path.lineTo(hx - 4 * u, hy - u);
                    path.close();
                    path.moveTo(hx + 12 * u, hy);
                    path.lineTo(hx + 15 * u, hy - 18 * u);
                    path.lineTo(hx + 4 * u, hy - u);
                    path.close();
                    c.drawPath(path, hat);
                    break;
                case "witch":
                    hat.setColor(0xFF1B1B1B);
                    r.set(hx - 22 * u, hy - 6 * u, hx + 22 * u, hy + 4 * u);
                    c.drawOval(r, hat);
                    path.moveTo(hx - 12 * u, hy - 2 * u);
                    path.lineTo(hx + 12 * u, hy - 2 * u);
                    path.lineTo(hx - dir * 14 * u, hy - 36 * u);
                    path.close();
                    c.drawPath(path, hat);
                    hat.setColor(0xFF8E24AA);
                    c.drawRect(hx - 12 * u, hy - 8 * u, hx + 12 * u, hy - 3 * u, hat);
                    break;
                case "flower":
                    hat.setColor(0xFFFF8C00);
                    for (int i = 0; i < 7; i++) {
                        double a = i / 7.0 * Math.PI * 2;
                        c.drawCircle(hx + (float) Math.cos(a) * 7 * u, hy - 12 * u + (float) Math.sin(a) * 7 * u, 5 * u, hat);
                    }
                    hat.setColor(0xFFFFCC00);
                    c.drawCircle(hx, hy - 12 * u, 4 * u, hat);
                    break;
                case "sombrero":
                    hat.setColor(0xFFC8A24A);
                    r.set(hx - 28 * u, hy - 8 * u, hx + 28 * u, hy + 4 * u);
                    c.drawOval(r, hat);
                    r.set(hx - 11 * u, hy - 15 * u, hx + 11 * u, hy + 7 * u);
                    c.drawArc(r, 180, 180, true, hat);
                    hat.setColor(0xFF1B8A3A);
                    c.drawRect(hx - 11 * u, hy - 7 * u, hx - 4 * u, hy - 4 * u, hat);
                    hat.setColor(0xFFFFFFFF);
                    c.drawRect(hx - 4 * u, hy - 7 * u, hx + 4 * u, hy - 4 * u, hat);
                    hat.setColor(0xFFD32F2F);
                    c.drawRect(hx + 4 * u, hy - 7 * u, hx + 11 * u, hy - 4 * u, hat);
                    break;
                case "heart":
                    hat.setColor(0xFFFF4F7B);
                    hat.setTextAlign(Paint.Align.CENTER);
                    hat.setTextSize(22 * u);
                    c.drawText("\u2665\uFE0E", hx, hy - 7 * u, hat);
                    break;
                default:
                    break;
            }
        }
    }

    // ==================== Ícono de app ====================

    static final class AppIcon extends LinearLayout {
        final HomeModel.Item item;
        final String pkg;
        private final ImageView icon;
        private final View badge, glow;
        private final TextView label;

        AppIcon(MainActivity a, HomeModel.Item it) {
            super(a);
            item = it;
            pkg = it.str("pkg", "");
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            int size = Math.round(Ui.dp(a, 54) * Prefs.iconScale(a) / 100f);
            FrameLayout box = new FrameLayout(a);
            glow = new View(a);
            box.addView(glow, new FrameLayout.LayoutParams(-1, -1));
            icon = new ImageView(a);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
            box.addView(icon, il);
            badge = new View(a);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(a, 11), Ui.dp(a, 11), Gravity.TOP | Gravity.END);
            box.addView(badge, bl);
            int boxSize = size + Ui.dp(a, 12);
            addView(box, new LayoutParams(boxSize, boxSize));
            label = Ui.text(a, "", 12, Ui.TEXT, Ui.MEDIUM);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams ll = new LayoutParams(-1, -2);
            ll.topMargin = Ui.dp(a, 2);
            addView(label, ll);
            refresh();
            setOnClickListener(v -> a.launchApp(pkg, icon));
        }

        void refresh() {
            Context c = getContext();
            IconEngine.bind(icon, pkg);
            label.setText(IconEngine.label(c, pkg));
            label.setVisibility(Prefs.homeLabels(c) ? VISIBLE : GONE);
            boolean light = Theme.wallLight;
            label.setTextColor(light ? 0xFF16171A : 0xFFF5F6F8);
            label.setShadowLayer(light ? 0 : Ui.dp(c, 4), 0, Ui.dp(c, 1), light ? 0 : 0xAA000000);
            badge.setBackground(Ui.outline(c, Theme.accent, 0xFF0E0F12, 6));
            if (Prefs.iconGlow(c)) {
                GradientDrawable g = new GradientDrawable();
                g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
                g.setColors(new int[]{Ui.alpha(Theme.accent, 0x66), Ui.alpha(Theme.accent, 0)});
                g.setGradientRadius(Ui.dp(c, 40));
                glow.setBackground(g);
            } else {
                glow.setBackground(null);
            }
        }

        void setBadge(boolean on) {
            badge.setVisibility(on ? VISIBLE : GONE);
        }

        View iconView() {
            return icon;
        }

        // Al presionar, el ícono se hunde un poco (sin pelearse con el arrastre de inicio)
        @Override
        protected void dispatchSetPressed(boolean pressed) {
            super.dispatchSetPressed(pressed);
            icon.animate().scaleX(pressed ? 0.88f : 1f).scaleY(pressed ? 0.88f : 1f).setDuration(pressed ? 90 : 220).start();
        }
    }

    // ==================== Carpeta ====================

    static final class FolderIcon extends LinearLayout {
        final HomeModel.Item item;
        private final FolderBox box;
        private final TextView label;
        private final View badge;

        FolderIcon(MainActivity a, HomeModel.Item it) {
            super(a);
            item = it;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            int size = Math.round(Ui.dp(a, 54) * Prefs.iconScale(a) / 100f);
            FrameLayout f = new FrameLayout(a);
            box = new FolderBox(a);
            box.setBackground(Ui.outline(a, 0x33FFFFFF, 0x26FFFFFF, 16));
            f.addView(box, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            badge = new View(a);
            f.addView(badge, new FrameLayout.LayoutParams(Ui.dp(a, 11), Ui.dp(a, 11), Gravity.TOP | Gravity.END));
            addView(f, new LayoutParams(size + Ui.dp(a, 12), size + Ui.dp(a, 12)));
            label = Ui.text(a, "", 12, Ui.TEXT, Ui.MEDIUM);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams ll = new LayoutParams(-1, -2);
            ll.topMargin = Ui.dp(a, 2);
            addView(label, ll);
            refresh();
            setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                a.openFolder(item, box);
            });
        }

        void refresh() {
            Context c = getContext();
            label.setText(item.str("name", "Carpeta"));
            label.setVisibility(Prefs.homeLabels(c) ? VISIBLE : GONE);
            boolean light = Theme.wallLight;
            label.setTextColor(light ? 0xFF16171A : 0xFFF5F6F8);
            label.setShadowLayer(light ? 0 : Ui.dp(c, 4), 0, Ui.dp(c, 1), light ? 0 : 0xAA000000);
            badge.setBackground(Ui.outline(c, Theme.accent, 0xFF0E0F12, 6));
            List<Drawable> icons = new ArrayList<>();
            JSONArray apps = item.apps();
            for (int i = 0; i < apps.length() && icons.size() < 4; i++) {
                Drawable d = IconEngine.icon(c, apps.optString(i));
                if (d != null) icons.add(d);
            }
            box.set(icons);
        }

        @Override
        protected void dispatchSetPressed(boolean pressed) {
            super.dispatchSetPressed(pressed);
            box.animate().scaleX(pressed ? 0.9f : 1f).scaleY(pressed ? 0.9f : 1f).setDuration(pressed ? 90 : 220).start();
        }

        void setBadges(Set<String> set) {
            boolean on = false;
            JSONArray apps = item.apps();
            for (int i = 0; i < apps.length(); i++) if (set.contains(apps.optString(i))) on = true;
            badge.setVisibility(on ? VISIBLE : GONE);
        }
    }

    static final class FolderBox extends View {
        private List<Drawable> icons = new ArrayList<>();

        FolderBox(Context c) {
            super(c);
        }

        void set(List<Drawable> list) {
            icons = list;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), pad = w * 0.14f, gap = w * 0.06f;
            float s = (w - 2 * pad - gap) / 2f;
            for (int i = 0; i < icons.size(); i++) {
                float x = pad + (i % 2) * (s + gap), y = pad + (i / 2) * (s + gap);
                Drawable d = icons.get(i);
                d.setBounds((int) x, (int) y, (int) (x + s), (int) (y + s));
                d.draw(c);
            }
        }
    }
}
