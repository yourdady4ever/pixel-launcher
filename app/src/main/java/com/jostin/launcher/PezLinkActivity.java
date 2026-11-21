package com.jostin.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Recibe jostinos://pez?k=CLAVE desde la página del pez. Siempre pregunta antes de guardar, para
 * que otra página o app no pueda cambiarte el pez (y llevarse tus minutos de apps) sin que lo veas.
 */
public class PezLinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Uri d = getIntent() == null ? null : getIntent().getData();
        String k = d == null ? null : d.getQueryParameter("k");
        if (!PezKey.valid(k)) {
            Toast.makeText(this, "Ese enlace no trae una clave válida", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        String cur = PezKey.get(this);
        if (k.equals(cur)) {
            Toast.makeText(this, "Tu pez ya estaba conectado ✅", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(cur == null ? "¿Conectar tu pez?" : "¿Cambiar de pez?")
                .setMessage(cur == null
                        ? "Jostin OS va a mostrar este pez en su widget y, si compartes tus apps, guardará ahí tus minutos de TikTok y Duolingo."
                        : "Ya tenías un pez conectado. Acepta solo si tú abriste este enlace desde tu página del pez.")
                .setPositiveButton("Conectar", (x, w) -> {
                    PezKey.set(this, k);
                    Toast.makeText(this, "Pez conectado ✅", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancelar", (x, w) -> finish())
                .setOnCancelListener(x -> finish())
                .show();
    }
}
