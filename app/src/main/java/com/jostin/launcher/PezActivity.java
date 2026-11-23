package com.jostin.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** La página del pez en pantalla completa: la misma que se abre en el celular y que sale de fondo en la PC. */
public class PezActivity extends Activity {
    static final String URL = "https://pez-estudio.web.app";

    private WebView web;
    /** Abierta: el gadget no debe usar pauseTimers (es de todo el proceso y la congelaría). */
    static volatile boolean alive;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        alive = true;
        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String host = u.getHost();
                if (host != null && (host.endsWith("pez-estudio.web.app") || host.endsWith("pez-estudio.firebaseapp.com"))) return false;
                // Cualquier otro enlace se abre en el navegador, no aquí
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                    // no hay navegador
                }
                return true;
            }
        });
        setContentView(web);
        if (saved != null) web.restoreState(saved);
        else {
            // La página guarda su clave en su propio almacenamiento; esta ventana no comparte el de Chrome,
            // así que se le pasa en el fragmento (#k=…, no viaja al servidor y la página lo limpia sola)
            String k = PezKey.get(this);
            web.loadUrl(k == null ? URL : URL + "/#k=" + k);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    /** Pantalla completa: las barras salen un momento si deslizas desde la orilla. */
    @SuppressWarnings("deprecation")
    private void hideBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        alive = true;
        web.onResume();
        // Por si el gadget de inicio pausó los temporizadores al irse a segundo plano
        web.resumeTimers();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(Screen.anim(this, "stay_in"), Screen.anim(this, "screen_exit"));
    }

    @Override
    protected void onDestroy() {
        alive = false;
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
