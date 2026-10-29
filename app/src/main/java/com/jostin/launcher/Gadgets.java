package com.jostin.launcher;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
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
        private Dashboard.Progress bar;
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
                bar = new Dashboard.Progress(act);
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
            p.setShader(new LinearGradient(0, getPaddingTop(), 0, getPaddingTop() + h, Theme.accent, Ui.alpha(Theme.accent, 0x55), Shader.TileMode.CLAMP));
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
            c.drawText(new SimpleDateFormat("MMMM yyyy", ES).format(now.getTime()).toUpperCase(ES), Ui.dp(getContext(), 2), headH * 0.7f, p);
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
            {"tasks", "Pendientes", "Ponte en el Mapa"}, {"panel", "Panel", "Clima, música y avisos"},
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
        private Dashboard.Progress bar;
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
                bar = new Dashboard.Progress(act);
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
