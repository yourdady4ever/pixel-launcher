package com.jostin.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * El panel propio que baja desde arriba: botones, brillo, música y notificaciones.
 * Si algo falla al abrirlo, se quita el bloqueo del panel de Android en ese momento.
 */
public class PanelActivity extends Activity implements Media.Listener {
    private static final ExecutorService BG = Executors.newSingleThreadExecutor();
    private static final String[] ICONS = {"qs_wifi", "qs_bt", "qs_plane"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LinearLayout sheet, notifs, media;
    private final List<TextView> tileLabels = new ArrayList<>();
    private final List<View> tileViews = new ArrayList<>();
    private float downY;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            build();
        } catch (Throwable t) {
            Bitacora.error("Panel propio", t);
            PanelPropio.emergency(this);
            finish();
        }
    }

    private void build() {
        Ui.init(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        Ui.edgeToEdge(this, false);
        root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(v -> close());

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 18);
        sheet.setPadding(p, p, p, p);
        sheet.setBackground(Ui.fill(this, 0xF2121419, 28));
        sheet.setClickable(true);
        sv.addView(sheet, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(sv, lp);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int side = Ui.dp(this, 8);
            sv.setPadding(side, in.getSystemWindowInsetTop() + side, side, side);
            return in;
        });

        // ---- Hora ----
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView clock = Ui.text(this, android.text.format.DateFormat.getTimeFormat(this).format(new Date()), 34, Ui.TEXT, Ui.MEDIUM);
        head.addView(clock);
        TextView date = Ui.text(this, "  " + new SimpleDateFormat("EEE d MMM", MainActivity.ES).format(new Date()), 14, Ui.MUTED, Ui.REGULAR);
        head.addView(date, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView gear = Ui.text(this, "⚙", 22, Ui.TEXT, Ui.REGULAR);
        gear.setPadding(p / 2, 0, p / 2, 0);
        gear.setOnClickListener(v -> go(new Intent(Settings.ACTION_SETTINGS)));
        head.addView(gear);
        sheet.addView(head);

        // ---- Botones ----
        LinearLayout tiles = new LinearLayout(this);
        tiles.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        for (int i = 0; i < 3; i++) tiles.addView(tile(i), new LinearLayout.LayoutParams(0, -2, 1f));
        tiles.addView(cleanTile(), new LinearLayout.LayoutParams(0, -2, 1f));
        sheet.addView(tiles);
        paintTiles();

        // ---- Brillo ----
        if (Switches.canBrightness(this)) {
            int cur = Switches.brightness(this);
            final long[] last = {0};
            sheet.addView(Ctl.slider(this, "Brillo", 1, 255, cur, "", (value, done) -> {
                long now = System.currentTimeMillis();
                if (!done && now - last[0] < 120) return;
                last[0] = now;
                BG.execute(() -> Switches.setBrightness(this, value));
            }));
        }

        // ---- Música ----
        media = new LinearLayout(this);
        media.setOrientation(LinearLayout.VERTICAL);
        sheet.addView(media);
        paintMedia();

        // ---- Notificaciones ----
        LinearLayout nh = new LinearLayout(this);
        nh.setGravity(Gravity.CENTER_VERTICAL);
        nh.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        nh.addView(Ui.text(this, "Notificaciones", 13, Ui.MUTED, Ui.SEMIBOLD), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clear = Ui.text(this, "Borrar todo", 13, Ui.TEXT, Ui.MEDIUM);
        clear.setOnClickListener(v -> {
            NotifService s = NotifService.instance;
            if (s != null) s.cancelAllNotifications();
            main.postDelayed(this::paintNotifs, 250);
        });
        nh.addView(clear);
        sheet.addView(nh);
        notifs = new LinearLayout(this);
        notifs.setOrientation(LinearLayout.VERTICAL);
        sheet.addView(notifs);
        paintNotifs();

        // Deslizar hacia arriba lo cierra
        sheet.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) downY = e.getRawY();
            else if (e.getActionMasked() == MotionEvent.ACTION_UP && downY - e.getRawY() > Ui.dp(this, 60)) close();
            return false;
        });

        setContentView(root);
        sv.setTranslationY(-Ui.dp(this, 500));
        sv.animate().translationY(0).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    // ---------- Botones ----------

    private View tile(int which) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView ic = new ImageView(this);
        int id = getResources().getIdentifier(ICONS[which], "drawable", getPackageName());
        ic.setImageResource(id);
        int s = Ui.dp(this, 52), pad = Ui.dp(this, 14);
        ic.setPadding(pad, pad, pad, pad);
        t.addView(ic, new LinearLayout.LayoutParams(s, s));
        TextView l = Ui.text(this, Switches.LABELS[which], 11, Ui.TEXT, Ui.MEDIUM);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Ui.dp(this, 6), 0, 0);
        t.addView(l);
        tileViews.add(ic);
        tileLabels.add(l);
        t.setOnClickListener(v -> {
            if (!Shizu.listo(this)) {
                go(Switches.settings(which));
                return;
            }
            boolean want = !Switches.on(this, which);
            paintTile(which, want);
            Shizu.run(this, Switches.cmd(which, want), r -> main.postDelayed(this::paintTiles, 1200));
        });
        return t;
    }

    private View cleanTile() {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView ic = new ImageView(this);
        ic.setImageResource(getResources().getIdentifier("qs_clean", "drawable", getPackageName()));
        int s = Ui.dp(this, 52), pad = Ui.dp(this, 14);
        ic.setPadding(pad, pad, pad, pad);
        ic.setBackground(Ui.fill(this, 0x22FFFFFF, 26));
        t.addView(ic, new LinearLayout.LayoutParams(s, s));
        TextView l = Ui.text(this, "Limpiar", 11, Ui.TEXT, Ui.MEDIUM);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Ui.dp(this, 6), 0, 0);
        t.addView(l);
        t.setOnClickListener(v -> go(SistemaActivity.intent(this, SistemaActivity.LIMPIAR)));
        return t;
    }

    private void paintTiles() {
        for (int i = 0; i < 3; i++) paintTile(i, Switches.on(this, i));
    }

    private void paintTile(int which, boolean on) {
        if (which >= tileViews.size()) return;
        int accent = Prefs.accentColor(this);
        View ic = tileViews.get(which);
        ic.setBackground(Ui.fill(this, on ? accent : 0x22FFFFFF, 26));
        ((ImageView) ic).setColorFilter(on ? 0xFF101216 : 0xFFFFFFFF);
    }

    // ---------- Música ----------

    @Override
    public void onMediaChanged() {
        main.post(this::paintMedia);
    }

    private void paintMedia() {
        if (media == null) return;
        media.removeAllViews();
        if (!Media.active()) return;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 12);
        card.setPadding(p, p, p, p);
        card.setBackground(Ui.fill(this, 0x1AFFFFFF, 20));
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, String.valueOf(Media.title()), 15, Ui.TEXT, Ui.MEDIUM);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(t);
        String ar = Media.artist();
        if (ar != null && !ar.isEmpty()) txt.addView(Ui.text(this, ar, 12, Ui.MUTED, Ui.REGULAR));
        card.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(mediaBtn("⏮", Media::previous));
        card.addView(mediaBtn(Media.playing() ? "⏸" : "▶", () -> {
            Media.playPause();
            main.postDelayed(this::paintMedia, 300);
        }));
        card.addView(mediaBtn("⏭", Media::next));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 10);
        media.addView(card, lp);
    }

    private TextView mediaBtn(String s, Runnable r) {
        TextView b = Ui.text(this, s, 20, Ui.TEXT, Ui.REGULAR);
        int p = Ui.dp(this, 10);
        b.setPadding(p, p / 2, p, p / 2);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    // ---------- Notificaciones ----------

    private void paintNotifs() {
        if (notifs == null) return;
        notifs.removeAllViews();
        NotifService s = NotifService.instance;
        StatusBarNotification[] all = null;
        try {
            if (s != null) all = s.getActiveNotifications();
        } catch (Exception ignored) {
            // el servicio se desconectó
        }
        if (all == null) {
            notifs.addView(Ui.text(this, s == null ? "Falta el acceso a notificaciones." : "No hay notificaciones", 13, Ui.MUTED, Ui.REGULAR));
            return;
        }
        List<StatusBarNotification> list = new ArrayList<>(Arrays.asList(all));
        list.removeIf(n -> (n.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0 || n.getPackageName().equals(getPackageName()) && n.isOngoing());
        list.sort((a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        if (list.isEmpty()) {
            notifs.addView(Ui.text(this, "No hay notificaciones", 13, Ui.MUTED, Ui.REGULAR));
            return;
        }
        for (StatusBarNotification n : list) notifs.addView(row(n));
    }

    private View row(StatusBarNotification n) {
        Notification no = n.getNotification();
        Bundle x = no.extras;
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 12);
        r.setPadding(p, p, p / 2, p);
        r.setBackground(Ui.fill(this, 0x14FFFFFF, 18));
        ImageView ic = new ImageView(this);
        try {
            Drawable d = no.getSmallIcon() == null ? null : no.getSmallIcon().loadDrawable(createPackageContext(n.getPackageName(), 0));
            if (d != null) {
                ic.setImageDrawable(d);
                ic.setColorFilter(0xFFFFFFFF);
            }
        } catch (Exception ignored) {
            // sin ícono
        }
        int is = Ui.dp(this, 22);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(is, is);
        il.rightMargin = p;
        r.addView(ic, il);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        String app = appName(n.getPackageName());
        CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE), text = x.getCharSequence(Notification.EXTRA_TEXT);
        col.addView(Ui.text(this, app, 11, Ui.MUTED, Ui.MEDIUM));
        if (title != null) {
            TextView t = Ui.text(this, title.toString(), 14, Ui.TEXT, Ui.MEDIUM);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t);
        }
        if (text != null) {
            TextView t = Ui.text(this, text.toString(), 13, Ui.MUTED, Ui.REGULAR);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (n.isClearable()) {
            TextView xb = Ui.text(this, "✕", 16, Ui.MUTED, Ui.REGULAR);
            xb.setPadding(p, p / 2, p / 2, p / 2);
            xb.setOnClickListener(v -> dismiss(n, r));
            r.addView(xb);
        }
        r.setOnClickListener(v -> openNotif(n));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 8);
        r.setLayoutParams(lp);
        return r;
    }

    private String appName(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private void dismiss(StatusBarNotification n, View row) {
        NotifService s = NotifService.instance;
        if (s != null) s.cancelNotification(n.getKey());
        row.animate().alpha(0).translationX(row.getWidth()).setDuration(180).withEndAction(() -> {
            ViewGroup g = (ViewGroup) row.getParent();
            if (g != null) g.removeView(row);
        }).start();
    }

    private void openNotif(StatusBarNotification n) {
        PendingIntent pi = n.getNotification().contentIntent;
        if (pi != null) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    ActivityOptions o = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    pi.send(this, 0, null, null, null, null, o.toBundle());
                } else {
                    pi.send();
                }
            } catch (Exception ignored) {
                // la app ya no la acepta
            }
        }
        if ((n.getNotification().flags & Notification.FLAG_AUTO_CANCEL) != 0 && NotifService.instance != null) {
            NotifService.instance.cancelNotification(n.getKey());
        }
        close();
    }

    private void go(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            // no hay quien lo abra
        }
        close();
    }

    // ---------- Ciclo ----------

    @Override
    protected void onResume() {
        super.onResume();
        Media.addListener(this, this);
        if (sheet != null) {
            paintTiles();
            paintNotifs();
        }
    }

    @Override
    protected void onPause() {
        Media.removeListener(this);
        super.onPause();
        // Al irte a otra app el panel se cierra, como el de Android
        if (!isFinishing()) finish();
    }

    private void close() {
        if (isFinishing()) return;
        View v = root == null ? null : root.getChildAt(0);
        if (v == null) {
            finish();
            return;
        }
        v.animate().translationY(-v.getHeight()).setDuration(180).withEndAction(this::finish).start();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        close();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
