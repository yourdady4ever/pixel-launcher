package com.jostin.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;

/**
 * El panel de Jostin OS abierto encima de cualquier app: lo abre el gesto en la orilla de arriba
 * (si lo activas) o el botón «Panel Jostin» de los ajustes rápidos. Es el mismo panel de inicio.
 */
public class ShadeActivity extends Activity implements Dashboard.Host {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Backdrop backdrop;
    private Dashboard dashboard;
    private boolean closing = false;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        Sound.init(this);
        Theme.recompute(this);
        Ui.edgeToEdge(this, false);
        FrameLayout root = new FrameLayout(this);
        backdrop = new Backdrop(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        dashboard = new Dashboard(this, backdrop, this);
        root.addView(dashboard, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        backdrop.setParallax(false);
        backdrop.useCurrent();
        backdrop.setScreenDim(0.3f);
        root.post(() -> {
            dashboard.setProgress(0f);
            dashboard.settle(true);
        });
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        backdrop.setAnimated(Prefs.animated(this));
        backdrop.setRunning(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        backdrop.setRunning(false);
        // Si te vas a otra app, el panel ya no se queda abierto atrás
        if (!isFinishing()) finish();
    }

    @Override
    public void onBackPressed() {
        if (Sheet.dismissTop(this)) return;
        closeDashboard();
    }

    @Override
    public void closeDashboard() {
        if (closing) return;
        closing = true;
        dashboard.settle(false);
        handler.postDelayed(() -> {
            finish();
            overridePendingTransition(0, 0);
        }, 280);
    }

    @Override
    public void personalize() {
        Intent i = new Intent(this, MainActivity.class).putExtra("jostin_studio", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    @Override
    public void askCity() {
        Sheet.prompt(this, backdrop, "¿De qué ciudad quieres el clima?", "Ejemplo: Cuautla", Prefs.city(this), v -> {
            Prefs.setCity(this, v);
            dashboard.refreshWeather();
            if (!v.isEmpty()) Weather.refresh(this, true, n -> dashboard.refreshWeather());
        });
    }

    @Override
    public void quick(int action) {
        if (action >= Dashboard.Q_ROTATE) {
            // Para grabar se cierra el panel primero, si no saldría en el video
            if (action == Dashboard.Q_RECORD && !Recorder.recording) closeDashboard();
            Controls.extra(this, backdrop, action);
            return;
        }
        switch (action) {
            case Dashboard.Q_TORCH: Controls.toggle(this, backdrop, 0); break;
            case Dashboard.Q_STUDY:
                Sheet.pill(this, backdrop, Controls.toggleStudy(this) ? "Modo estudio: 25 minutos" : "Modo estudio terminado");
                break;
            case Dashboard.Q_WALL: {
                MainActivity.cycleWallpaper(this);
                backdrop.useCurrent();
                MainActivity m = MainActivity.current.get();
                if (m != null && !m.isDestroyed()) m.applyWallpaper();
                else SystemWall.sync(this);
                break;
            }
            case Dashboard.Q_NET: Controls.toggle(this, backdrop, 1); break;
            case Dashboard.Q_BT: Controls.start(this, backdrop, new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), null); break;
            case Dashboard.Q_DND: Controls.toggle(this, backdrop, 3); break;
            case Dashboard.Q_VOL: Controls.toggle(this, backdrop, 2); break;
            default:
                closeDashboard();
                handler.postDelayed(() -> {
                    if (!LockService.lock()) Sheet.pill(this, backdrop, "Activa «Jostin OS» en Accesibilidad para bloquear");
                }, 320);
                break;
        }
    }
}
