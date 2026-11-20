package com.jostin.launcher;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

/** Pide tu huella o el bloqueo del celular (PIN, patrón o contraseña) antes de enseñar algo privado. */
final class Candado {
    static final int REQ = 420;
    private static Runnable pending;

    private Candado() {}

    @SuppressWarnings("deprecation")
    static void verify(Activity a, String title, Runnable ok) {
        KeyguardManager km = a.getSystemService(KeyguardManager.class);
        // Sin bloqueo de pantalla configurado no hay nada que pedir
        if (km == null || !km.isDeviceSecure()) {
            ok.run();
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(a).setTitle(title).setSubtitle("Usa tu huella o el bloqueo del celular");
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else {
                b.setDeviceCredentialAllowed(true);
            }
            try {
                b.build().authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        ok.run();
                    }
                });
            } catch (Exception e) {
                Bitacora.error("pedir huella", e);
            }
            return;
        }
        Intent i = km.createConfirmDeviceCredentialIntent(title, "Usa el bloqueo del celular");
        if (i == null) {
            ok.run();
            return;
        }
        pending = ok;
        a.startActivityForResult(i, REQ);
    }

    /** Respuesta de la pantalla de PIN en Android 8 y 9. */
    static void onResult(boolean ok) {
        Runnable r = pending;
        pending = null;
        if (ok && r != null) r.run();
    }
}
