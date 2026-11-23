package com.jostin.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.net.Uri;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * La página del pez dentro del gadget (modo=gadget): juegas, le das de comer y lo limpias desde inicio.
 * Un solo WebView que se reutiliza. Se pausa (onPause + pauseTimers) cuando inicio no está al frente,
 * la pantalla se apaga o el gadget no se ve; al volver se reanuda y llama a window.pezResume().
 * Si la página no carga, el gadget vuelve a su versión nativa de solo lectura.
 */
final class PezWeb {
    interface Host {
        /** La página cargó bien (true) o falló (false). */
        void onWebState(boolean ok);

        /** Pulsación larga dentro de la página: el menú del widget, como en los demás. */
        void onWebLongPress();
    }

    private static final long RETRY = 5 * 60_000L;

    private final WebView web;
    private final Host host;
    private String loadedKey;
    private boolean ok, failed, running = true;
    private long failedAt;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    PezWeb(Context c, Host h) {
        host = h;
        web = new WebView(c);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        // Esquinas redondas como el vidrio del widget
        final float r = Ui.dp(c, 16);
        web.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        web.setClipToOutline(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                String hst = u.getHost();
                if (hst != null && (hst.endsWith("pez-estudio.web.app") || hst.endsWith("pez-estudio.firebaseapp.com"))) return false;
                try {
                    v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                    // no hay navegador
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) fail();
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest req, WebResourceResponse res) {
                if (req.isForMainFrame() && res.getStatusCode() >= 400) fail();
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (failed || loadedKey == null) return;
                ok = true;
                host.onWebState(true);
            }
        });

        // Los toques se quedan en la página (arrastrar la pelota, minijuegos) sin que inicio cambie de página.
        // Mantener presionado sin moverse abre el menú del widget, como en los demás.
        final int slop = ViewConfiguration.get(c).getScaledTouchSlop();
        final float[] down = new float[2];
        final Runnable longPress = () -> {
            web.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            MotionEvent cancel = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0, 0, 0);
            web.dispatchTouchEvent(cancel);
            cancel.recycle();
            host.onWebLongPress();
        };
        web.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getX();
                    down[1] = e.getY();
                    ViewGroup p = (ViewGroup) v.getParent();
                    if (p != null) p.requestDisallowInterceptTouchEvent(true);
                    v.postDelayed(longPress, ViewConfiguration.getLongPressTimeout() + 150);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - down[0]) > slop || Math.abs(e.getY() - down[1]) > slop) v.removeCallbacks(longPress);
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(longPress);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    WebView view() {
        return web;
    }

    boolean ok() {
        return ok;
    }

    private void fail() {
        failed = true;
        ok = false;
        failedAt = SystemClock.uptimeMillis();
        host.onWebState(false);
    }

    /** Carga la página si no está cargada con esta clave (o reintenta si falló hace más de 5 min). */
    void ensure(Context c) {
        String k = PezKey.get(c);
        if (k == null) {
            if (loadedKey != null) {
                loadedKey = null;
                ok = false;
                web.loadUrl("about:blank");
                host.onWebState(false);
            }
            return;
        }
        boolean retry = failed && SystemClock.uptimeMillis() - failedAt > RETRY;
        if (k.equals(loadedKey) && !retry) return;
        loadedKey = k;
        failed = false;
        ok = false;
        // La clave va en el fragmento (#k=): no viaja al servidor y la página la limpia sola
        web.loadUrl(PezActivity.URL + "/?modo=gadget#k=" + k);
    }

    /** Reanuda o pausa según se vea; pauseTimers es de todo el proceso, así que no se toca si la página grande está abierta. */
    void setRunning(boolean on) {
        if (on == running) return;
        running = on;
        if (on) {
            web.onResume();
            web.resumeTimers();
            web.evaluateJavascript("window.pezResume&&pezResume()", null);
        } else {
            web.onPause();
            if (!PezActivity.alive) web.pauseTimers();
        }
    }
}
