package com.jostin.launcher;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Panel que baja desde arriba: hora, clima, batería, almacenamiento, memoria, atajos, la música
 * que suena y tus notificaciones. Solo se actualiza mientras está abierto.
 */
class Dashboard extends FrameLayout implements Media.Listener {
    interface Host {
        void personalize();
        void askCity();
        void quick(int action);
        void closeDashboard();
    }

    static final int Q_TORCH = 0, Q_STUDY = 1, Q_WALL = 2, Q_LOCK = 3, Q_NET = 4, Q_BT = 5, Q_DND = 6, Q_VOL = 7;

    private final Activity act;
    private final Backdrop backdrop;
    private final Host host;
    private final LinearLayout col;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean open = false;

    private TextView date, wTemp, wDesc, wPlace, wRange;
    private ImageView wIcon;
    private final Widgets.Ring[] rings = new Widgets.Ring[4];
    private LinearLayout music, notes;
    private Widgets.Art art;
    private TextView mTitle, mArtist, mPos, mDur;
    private ImageView mPlay;
    private Progress mBar;
    private final View[] quick = new View[8];
    private final ImageView[] quickIcons = new ImageView[8];
    private LinearLayout bright;
    private ScrollView scroll;
    private float downY;
    private boolean dragging = false;
    private android.view.VelocityTracker vt;

    private final Runnable fast = new Runnable() {
        @Override
        public void run() {
            if (!open) return;
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable slow = new Runnable() {
        @Override
        public void run() {
            if (!open) return;
            updateStats();
            handler.postDelayed(this, 10_000);
        }
    };

    Dashboard(Activity a, Backdrop b, Host h) {
        super(a);
        act = a;
        backdrop = b;
        host = h;
        setVisibility(GONE);
        setClickable(true);
        Glass g = new Glass(b, 0f, Glass.TINT_STRONG);
        setBackground(g);
        b.register(this);

        ScrollView sv = new ScrollView(a);
        scroll = sv;
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(OVER_SCROLL_NEVER);
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(a, 16);
        col.setPadding(side, Ui.dp(a, 56), side, Ui.dp(a, 40));
        sv.addView(col);
        addView(sv, new LayoutParams(-1, -1));
        // Franja oscura bajo la barra de estado para que lo que se desplaza no choque con la hora y los íconos
        View scrim = new View(a);
        android.graphics.drawable.GradientDrawable sg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xE60B0C0F, 0x000B0C0F});
        scrim.setBackground(sg);
        addView(scrim, new LayoutParams(-1, Ui.dp(a, 48), Gravity.TOP));
        setOnApplyWindowInsetsListener((v, in) -> {
            col.setPadding(side, in.getSystemWindowInsetTop() + Ui.dp(a, 16), side, in.getSystemWindowInsetBottom() + Ui.dp(a, 30));
            scrim.getLayoutParams().height = in.getSystemWindowInsetTop() + Ui.dp(a, 18);
            scrim.requestLayout();
            return in;
        });
        build();
    }

    boolean isOpen() {
        return open;
    }

    // Deslizar hacia arriba cierra el panel cuando el contenido ya no puede bajar más
    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                downY = e.getRawY();
                dragging = false;
                break;
            case android.view.MotionEvent.ACTION_MOVE:
                float dy = e.getRawY() - downY;
                if (dy < -Ui.dp(act, 18) && !scroll.canScrollVertically(1)) {
                    dragging = true;
                    vt = android.view.VelocityTracker.obtain();
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (!dragging) {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                downY = e.getRawY();
                dragging = true;
                vt = android.view.VelocityTracker.obtain();
            } else {
                return super.onTouchEvent(e);
            }
        }
        if (vt != null) vt.addMovement(e);
        float dy = e.getRawY() - downY;
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_MOVE:
                float f = 1f + Math.min(0f, dy) / Math.max(1f, getHeight() * 0.6f);
                setProgress(Math.max(0f, f));
                return true;
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                dragging = false;
                float vy = 0;
                if (vt != null) {
                    vt.computeCurrentVelocity(1000);
                    vy = vt.getYVelocity();
                    vt.recycle();
                    vt = null;
                }
                boolean close = dy < -getHeight() * 0.18f || vy < -1200;
                if (close) {
                    Sound.play(this, Sound.BACK);
                    host.closeDashboard();
                } else {
                    settle(true);
                }
                return true;
            default:
                return true;
        }
    }

    // ---------- Construcción ----------

    private void build() {
        Activity a = act;
        // Encabezado
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout ht = new LinearLayout(a);
        ht.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(a);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTypeface(Ui.THIN);
        time.setTextSize(58);
        time.setTextColor(Ui.TEXT);
        time.setIncludeFontPadding(false);
        ht.addView(time);
        date = Ui.text(a, "", 15, Ui.MUTED, Ui.MEDIUM);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = Ui.dp(a, 6);
        ht.addView(date, dl);
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView gear = Widgets.iconButton(a, Icons.GEAR, 46, 12, v -> {
            Sound.play(v, Sound.OPEN);
            host.personalize();
        });
        Glass.apply(gear, backdrop, 23);
        head.addView(gear);
        col.addView(head);

        // Clima
        LinearLayout weather = card(a);
        weather.setOrientation(LinearLayout.HORIZONTAL);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        wIcon = new ImageView(a);
        weather.addView(wIcon, new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
        LinearLayout wt = new LinearLayout(a);
        wt.setOrientation(LinearLayout.VERTICAL);
        wTemp = Ui.text(a, "", 38, Ui.TEXT, Ui.THIN);
        wDesc = Ui.text(a, "", 14, Ui.MUTED, Ui.MEDIUM);
        wt.addView(wTemp);
        wt.addView(wDesc);
        LinearLayout.LayoutParams wtl = new LinearLayout.LayoutParams(0, -2, 1f);
        wtl.leftMargin = Ui.dp(a, 14);
        weather.addView(wt, wtl);
        LinearLayout wr = new LinearLayout(a);
        wr.setOrientation(LinearLayout.VERTICAL);
        wr.setGravity(Gravity.END);
        wPlace = Ui.text(a, "", 14, Ui.TEXT, Ui.SEMIBOLD);
        wRange = Ui.text(a, "", 13, Ui.MUTED, Ui.REGULAR);
        wr.addView(wPlace);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.topMargin = Ui.dp(a, 4);
        wr.addView(wRange, rl);
        weather.addView(wr);
        Ui.pressable(weather, 0.98f);
        weather.setOnClickListener(v -> {
            Sound.play(v, Sound.TAP);
            host.askCity();
        });
        col.addView(weather, cardParams(a, 18));

        // Anillos
        LinearLayout ringCard = card(a);
        ringCard.setOrientation(LinearLayout.HORIZONTAL);
        for (int k = 0; k < 4; k++) {
            rings[k] = new Widgets.Ring(a);
            ringCard.addView(rings[k], new LinearLayout.LayoutParams(0, Ui.dp(a, 92), 1f));
        }
        col.addView(ringCard, cardParams(a, 10));

        // Centro de control: dos filas de atajos y el brillo
        Icons.Shape[] qi = {Widgets.TORCH, Icons.FOCUS, Icons.IMAGE, Widgets.LOCK, Weather.CLOUD, BT, Gadgets.MOON_OFF, Icons.SOUND};
        String[] ql = {"Linterna", "Estudio", "Otro fondo", "Bloquear", "Internet", "Bluetooth", "No molestar", "Volumen"};
        for (int r = 0; r < 2; r++) {
            LinearLayout q = new LinearLayout(a);
            for (int c = 0; c < 4; c++) {
                final int action = r * 4 + c;
                LinearLayout b = new LinearLayout(a);
                b.setOrientation(LinearLayout.VERTICAL);
                b.setGravity(Gravity.CENTER);
                int p = Ui.dp(a, 10);
                b.setPadding(0, p, 0, p);
                Glass.apply(b, backdrop, 22);
                ImageView ic = new ImageView(a);
                ic.setImageDrawable(Icons.of(qi[action]));
                int ip = Ui.dp(a, 6);
                ic.setPadding(ip, ip, ip, ip);
                b.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 36)));
                TextView t = Ui.text(a, ql[action], 12, Ui.TEXT, Ui.MEDIUM);
                t.setMaxLines(1);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
                tl.topMargin = Ui.dp(a, 4);
                b.addView(t, tl);
                Ui.pressable(b, 0.92f);
                b.setOnClickListener(v -> {
                    Sound.play(v, Sound.TOGGLE);
                    host.quick(action);
                    handler.postDelayed(this::refreshQuick, 350);
                });
                quick[action] = b;
                quickIcons[action] = ic;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                if (c > 0) lp.leftMargin = Ui.dp(a, 8);
                q.addView(b, lp);
            }
            col.addView(q, cardParams(a, r == 0 ? 10 : 8));
        }
        bright = card(a);
        bright.setOrientation(LinearLayout.HORIZONTAL);
        bright.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(bright, cardParams(a, 8));

        // Música
        music = card(a);
        music.setOrientation(LinearLayout.VERTICAL);
        music.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(music, cardParams(a, 10));

        // Notificaciones
        notes = new LinearLayout(a);
        notes.setOrientation(LinearLayout.VERTICAL);
        col.addView(notes, cardParams(a, 6));
    }

    private LinearLayout card(Activity a) {
        LinearLayout c = new LinearLayout(a);
        int p = Ui.dp(a, 16);
        c.setPadding(p, p, p, p);
        Glass.apply(c, backdrop, 26);
        return c;
    }

    private LinearLayout.LayoutParams cardParams(Activity a, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, top);
        return lp;
    }

    // ---------- Abrir y cerrar ----------

    /** Posición mientras se arrastra: 0 cerrado, 1 abierto. */
    void setProgress(float f) {
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            onOpen();
        }
        float h = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        setTranslationY(-(1f - f) * h * 0.35f);
        setAlpha(Math.max(0f, Math.min(1f, f * 1.4f)));
    }

    void settle(boolean toOpen) {
        if (toOpen) {
            if (getVisibility() != VISIBLE) setProgress(0f);
            boolean fresh = getAlpha() < 0.6f;
            animate().translationY(0).alpha(1f).setDuration(380).setInterpolator(new DecelerateInterpolator(2.4f)).withEndAction(null).start();
            if (fresh) {
                col.setAlpha(0f);
                col.setTranslationY(-Ui.dp(act, 12));
                col.animate().alpha(1f).translationY(0).setDuration(420).setStartDelay(40).start();
            }
        } else {
            float h = getHeight();
            animate().translationY(-h * 0.35f).alpha(0f).setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f))
                    .withEndAction(() -> {
                        setVisibility(GONE);
                        onClose();
                    }).start();
        }
    }

    static final Icons.Shape BT = p -> {
        p.moveTo(7f, 7.5f);
        p.lineTo(16.5f, 16f);
        p.lineTo(12f, 20f);
        p.lineTo(12f, 4f);
        p.lineTo(16.5f, 8f);
        p.lineTo(7f, 16.5f);
    };

    /** Pinta con el color de acento lo que está prendido (según Android, no según lo que tocaste). */
    private void refreshQuick() {
        boolean[] on = new boolean[8];
        on[Q_TORCH] = Device.torchOn();
        on[Q_STUDY] = Prefs.studying(act);
        on[Q_DND] = NotifService.dndOn(act);
        for (int i = 0; i < 8; i++) {
            if (quickIcons[i] == null) continue;
            Icons.Line d = Icons.of(i == Q_NET ? Weather.CLOUD : i == Q_BT ? BT : i == Q_DND ? Gadgets.MOON_OFF : i == Q_VOL ? Icons.SOUND
                    : i == Q_TORCH ? Widgets.TORCH : i == Q_STUDY ? Icons.FOCUS : i == Q_WALL ? Icons.IMAGE : Widgets.LOCK);
            d.setColor(on[i] ? Theme.onAccent : Ui.TEXT);
            quickIcons[i].setImageDrawable(d);
            quickIcons[i].setBackground(on[i] ? Ui.fill(act, Theme.accent, 18) : null);
        }
    }

    /** Brillo de la pantalla: Android pide un permiso especial para cambiarlo desde una app. */
    private void buildBrightness() {
        Activity a = act;
        bright.removeAllViews();
        ImageView sun = new ImageView(a);
        sun.setImageDrawable(Icons.of(Weather.SUN_CLOUD));
        bright.addView(sun, new LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)));
        android.content.ContentResolver cr = a.getContentResolver();
        if (!android.provider.Settings.System.canWrite(a)) {
            LinearLayout tx = new LinearLayout(a);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.addView(Ui.text(a, "Brillo", 15, Ui.TEXT, Ui.SEMIBOLD));
            TextView s = Ui.text(a, "Para moverlo desde aquí, Android pide permiso para cambiar ajustes del sistema.", 12, Ui.MUTED, Ui.REGULAR);
            s.setLineSpacing(0, 1.15f);
            tx.addView(s);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.leftMargin = Ui.dp(a, 12);
            bright.addView(tx, tl);
            TextView b = Sheet.button(a, "Permitir", Theme.accent, Theme.onAccent);
            int ph = Ui.dp(a, 14);
            b.setPadding(ph, 0, ph, 0);
            bright.addView(b, new LinearLayout.LayoutParams(-2, Ui.dp(a, 40)));
            b.setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                try {
                    a.startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            android.net.Uri.parse("package:" + a.getPackageName())));
                } catch (Exception ignored) {
                    // nada
                }
            });
            return;
        }
        int cur = android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128);
        Ctl.Slider s = new Ctl.Slider(a, 1, 255, cur, (v, done) -> {
            try {
                // Mover el brillo a mano apaga el automático, igual que en Android
                if (android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1) {
                    android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
                    if (done) buildBrightness();
                }
                android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS, v);
            } catch (Exception ignored) {
                // sin permiso
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, Ui.dp(a, 34), 1f);
        sl.leftMargin = Ui.dp(a, 12);
        sl.rightMargin = Ui.dp(a, 10);
        bright.addView(s, sl);
        boolean auto = android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1;
        TextView chip = Ui.text(a, "Auto", 13, auto ? Theme.onAccent : Ui.TEXT, Ui.SEMIBOLD);
        chip.setGravity(Gravity.CENTER);
        int ph = Ui.dp(a, 12);
        chip.setPadding(ph, Ui.dp(a, 8), ph, Ui.dp(a, 8));
        chip.setBackground(auto ? Ui.fill(a, Theme.accent, 16) : Ui.outline(a, Ui.SOFT, 0x26FFFFFF, 16));
        Ui.pressable(chip, 0.92f);
        chip.setOnClickListener(v -> {
            Sound.play(v, Sound.TOGGLE);
            try {
                android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, auto ? 0 : 1);
            } catch (Exception ignored) {
                // sin permiso
            }
            buildBrightness();
        });
        bright.addView(chip);
    }

    private void onOpen() {
        open = true;
        refreshQuick();
        buildBrightness();
        Media.addListener(act, this);
        refreshWeather();
        updateStats();
        date.setText(cap(new SimpleDateFormat("EEEE d 'de' MMMM", MainActivity.ES).format(new Date())));
        handler.removeCallbacks(fast);
        handler.removeCallbacks(slow);
        handler.post(fast);
        handler.postDelayed(slow, 10_000);
        Weather.refresh(act, false, n -> refreshWeather());
    }

    private void onClose() {
        open = false;
        Media.removeListener(this);
        handler.removeCallbacks(fast);
        handler.removeCallbacks(slow);
        if (art != null) art.set(null, false);
    }

    // ---------- Datos ----------

    void refreshWeather() {
        Weather.Now w = Weather.cached(act);
        if (w == null) {
            wIcon.setImageDrawable(new Icons.Line(Weather.SUN_CLOUD, Ui.TEXT, 1.4f));
            wTemp.setText("--°");
            boolean none = Prefs.city(act).isEmpty();
            wDesc.setText(none ? "Toca para elegir tu ciudad" : "Buscando el clima…");
            wPlace.setText(none ? "" : Prefs.city(act));
            wRange.setText("");
            return;
        }
        wIcon.setImageDrawable(new Icons.Line(Weather.icon(w.code, w.day), Ui.TEXT, 1.4f));
        wTemp.setText(w.temp + "°");
        wDesc.setText(Weather.describe(w.code));
        wPlace.setText(w.place);
        wRange.setText("Máx " + w.max + "°  ·  Mín " + w.min + "°");
    }

    private void updateStats() {
        Device.Stats s = Device.read(act);
        rings[0].set(s.battery / 100f, s.battery + "%", s.charging ? "CARGANDO" : "BATERÍA");
        rings[1].set(s.storageUsed, Math.round(s.storageUsed * 100) + "%", "ALMACÉN");
        rings[2].set(s.ramUsed, Math.round(s.ramUsed * 100) + "%", "MEMORIA");
        rings[3].set(Math.min(1f, s.batteryTemp / 50f), Math.round(s.batteryTemp) + "°", "TEMP.");
    }

    @Override
    public void onMediaChanged() {
        buildMusic();
        buildNotes();
    }

    private void buildMusic() {
        Activity a = act;
        music.removeAllViews();
        art = null;
        if (!Media.hasAccess(a)) {
            TextView t = Ui.text(a, "Música y notificaciones", 16, Ui.TEXT, Ui.SEMIBOLD);
            music.addView(t, new LinearLayout.LayoutParams(-1, -2));
            TextView s = Ui.text(a, "Para ver aquí lo que suena en Spotify o YouTube Music y tus avisos, Android pide que le des permiso de notificaciones a Jostin OS.", 14, Ui.MUTED, Ui.REGULAR);
            s.setLineSpacing(0, 1.25f);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.topMargin = Ui.dp(a, 8);
            music.addView(s, sl);
            TextView b = Sheet.button(a, "Dar permiso", Theme.accent, Theme.onAccent);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(a, 48));
            bl.topMargin = Ui.dp(a, 14);
            music.addView(b, bl);
            b.setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                Media.requestAccess(a);
            });
            return;
        }
        if (!Media.active()) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Widgets.Art ph = new Widgets.Art(a, false);
            row.addView(ph, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
            TextView t = Ui.text(a, "No hay música sonando", 15, Ui.MUTED, Ui.MEDIUM);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.leftMargin = Ui.dp(a, 14);
            row.addView(t, tl);
            music.addView(row, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        art = new Widgets.Art(a, true);
        music.addView(art, new LinearLayout.LayoutParams(Ui.dp(a, 220), Ui.dp(a, 220)));
        mTitle = Ui.text(a, Media.title(), 18, Ui.TEXT, Ui.SEMIBOLD);
        mTitle.setGravity(Gravity.CENTER);
        mTitle.setMaxLines(1);
        mTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        music.addView(mTitle, new LinearLayout.LayoutParams(-1, -2));
        mArtist = Ui.text(a, Media.artist(), 14, Ui.MUTED, Ui.REGULAR);
        mArtist.setGravity(Gravity.CENTER);
        mArtist.setMaxLines(1);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2);
        al.topMargin = Ui.dp(a, 4);
        music.addView(mArtist, al);
        mBar = new Progress(a);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(a, 4));
        bl.topMargin = Ui.dp(a, 18);
        music.addView(mBar, bl);
        LinearLayout times = new LinearLayout(a);
        mPos = Ui.text(a, "", 12, Ui.MUTED, Ui.MEDIUM);
        mDur = Ui.text(a, "", 12, Ui.MUTED, Ui.MEDIUM);
        times.addView(mPos, new LinearLayout.LayoutParams(0, -2, 1f));
        times.addView(mDur);
        LinearLayout.LayoutParams tsl = new LinearLayout.LayoutParams(-1, -2);
        tsl.topMargin = Ui.dp(a, 6);
        music.addView(times, tsl);
        LinearLayout ctl = new LinearLayout(a);
        ctl.setGravity(Gravity.CENTER);
        ctl.addView(Widgets.iconButton(a, Widgets.PREV, 56, 15, v -> {
            Sound.play(v, Sound.TAP);
            Media.previous();
        }));
        mPlay = Widgets.iconButton(a, Media.playing() ? Widgets.PAUSE : Widgets.PLAY, 64, 18, v -> {
            Sound.play(v, Sound.TAP);
            Media.playPause();
        });
        mPlay.setBackground(Ui.fill(a, 0x26FFFFFF, 32));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.dp(a, 64), Ui.dp(a, 64));
        pl.leftMargin = pl.rightMargin = Ui.dp(a, 22);
        ctl.addView(mPlay, pl);
        ctl.addView(Widgets.iconButton(a, Widgets.NEXT, 56, 15, v -> {
            Sound.play(v, Sound.TAP);
            Media.next();
        }));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.topMargin = Ui.dp(a, 10);
        music.addView(ctl, cl);
        art.set(Media.art(), Media.playing());
        art.setOnClickListener(v -> {
            Sound.play(v, Sound.OPEN);
            Media.open(a);
        });
        updateProgress();
    }

    private void updateProgress() {
        if (mBar == null || art == null || !Media.active()) return;
        long d = Media.duration(), p = Media.position();
        mBar.set(d > 0 ? p / (float) d : 0);
        mPos.setText(DateUtils.formatElapsedTime(p / 1000));
        mDur.setText(d > 0 ? DateUtils.formatElapsedTime(d / 1000) : "");
    }

    private void buildNotes() {
        Activity a = act;
        notes.removeAllViews();
        if (!Media.hasAccess(a) || !Prefs.notesInPanel(a)) return;
        List<Media.Note> list = Media.recent(a, 14);
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = Ui.label(a, list.isEmpty() ? "Sin notificaciones" : "Notificaciones");
        head.addView(h, new LinearLayout.LayoutParams(0, -2, 1f));
        boolean anyClear = false;
        for (Media.Note n : list) if (n.clearable) anyClear = true;
        if (anyClear) {
            TextView all = Ui.text(a, "Borrar todo", 13, Ui.TEXT, Ui.SEMIBOLD);
            int ph = Ui.dp(a, 14), pv = Ui.dp(a, 8);
            all.setPadding(ph, pv, ph, pv);
            all.setBackground(Ui.fill(a, 0x1FFFFFFF, 16));
            Ui.pressable(all, 0.94f);
            all.setOnClickListener(v -> {
                Sound.play(v, Sound.BACK);
                // Se van una por una con animación; Android quita las que se pueden quitar
                int k = 0;
                for (int i = 1; i < notes.getChildCount(); i++) {
                    View row = notes.getChildAt(i);
                    if (!(row.getTag() instanceof Media.Note) || !((Media.Note) row.getTag()).clearable) continue;
                    row.animate().translationX(row.getWidth()).alpha(0f).setStartDelay(k++ * 45L).setDuration(220).start();
                }
                handler.postDelayed(() -> {
                    if (!NotifService.cancelAll()) Sheet.pill(a, backdrop, "Android no dejó borrarlas");
                    buildNotes();
                }, 260 + k * 45L);
            });
            head.addView(all);
        }
        head.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), 0, Ui.dp(a, 8));
        notes.addView(head, new LinearLayout.LayoutParams(-1, -2));
        PackageManager pm = a.getPackageManager();
        for (Media.Note n : list) {
            LinearLayout row = new LinearLayout(a);
            row.setTag(n);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int p = Ui.dp(a, 12);
            row.setPadding(p, p, Ui.dp(a, 16), p);
            Glass.apply(row, backdrop, 20);
            ImageView ic = new ImageView(a);
            try {
                ic.setImageDrawable(pm.getApplicationIcon(n.pkg));
            } catch (Exception ignored) {
                ic.setImageDrawable(Icons.of(Widgets.BELL));
            }
            row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34)));
            LinearLayout tx = new LinearLayout(a);
            tx.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = new LinearLayout(a);
            TextView t = Ui.text(a, n.title, 14, Ui.TEXT, Ui.SEMIBOLD);
            t.setMaxLines(1);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            top.addView(Ui.text(a, DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(), 11, Ui.MUTED, Ui.REGULAR));
            tx.addView(top);
            boolean hide = Prefs.notesPrivate(a);
            TextView s = Ui.text(a, hide ? "Contenido oculto" : n.text, 13, Ui.MUTED, Ui.REGULAR);
            s.setMaxLines(2);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.topMargin = Ui.dp(a, 3);
            if (!n.text.isEmpty()) tx.addView(s, sl);
            LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, -2, 1f);
            txl.leftMargin = Ui.dp(a, 12);
            row.addView(tx, txl);
            row.setOnClickListener(v -> {
                Sound.play(v, Sound.OPEN);
                try {
                    if (n.intent != null) n.intent.send();
                    else MainActivity.launch(a, n.pkg, v);
                } catch (Exception e) {
                    MainActivity.launch(a, n.pkg, v);
                }
                if (n.autoCancel && n.clearable) NotifService.cancel(n.key);
                host.closeDashboard();
            });
            if (n.clearable) {
                // Deslizar a un lado la descarta, como en la barra de Android
                SwipeDismiss.attach(row, () -> {
                    if (!NotifService.cancel(n.key)) Sheet.pill(a, backdrop, "Android no dejó quitar esa notificación");
                });
            } else {
                Ui.pressable(row, 0.97f);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(a, 6);
            notes.addView(row, lp);
        }
        if (!list.isEmpty()) {
            TextView tip = Ui.text(a, "Desliza una notificación a un lado para quitarla.", 12, Ui.MUTED, Ui.REGULAR);
            tip.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), 0, 0);
            notes.addView(tip);
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Barra de progreso fina con el color de acento. */
    static final class Progress extends View {
        private float v;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        Progress(Activity a) {
            super(a);
            track.setColor(0x26FFFFFF);
        }

        void set(float f) {
            v = Math.max(0f, Math.min(1f, f));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), r = h / 2f;
            c.drawRoundRect(0, 0, getWidth(), h, r, r, track);
            fill.setColor(Theme.accent);
            c.drawRoundRect(0, 0, getWidth() * v, h, r, r, fill);
        }
    }
}
