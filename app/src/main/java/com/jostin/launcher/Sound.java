package com.jostin.launcher;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.util.Random;

/** Efectos de sonido y vibración. Cada sonido tiene su vibración propia. */
final class Sound {
    static final int TAP = 0, KEY = 1, OPEN = 2, BACK = 3, EQUALS = 4, THEME = 5,
            TOGGLE = 6, CHECK = 7, HOLD = 8, ERROR = 9;
    private static final String[] FILES = {"tap", "key", "open", "back", "equals", "theme",
            "toggle", "check", "hold", "error"};
    private static final float[] VOL = {0.55f, 0.6f, 0.7f, 0.6f, 0.65f, 0.7f, 0.55f, 0.65f, 0.7f, 0.6f};

    private static SoundPool pool;
    private static final int[] ids = new int[FILES.length];
    private static final Random rnd = new Random();

    private Sound() {}

    static void init(Context c) {
        if (pool != null) return;
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        for (int i = 0; i < FILES.length; i++) {
            try {
                AssetFileDescriptor fd = c.getAssets().openFd("sfx/" + FILES[i] + ".wav");
                ids[i] = pool.load(fd, 1);
            } catch (Exception e) {
                ids[i] = 0;
            }
        }
    }

    /** Reproduce el sonido y la vibración que le corresponde. */
    static void play(View v, int which) {
        if (v == null) return;
        if (Prefs.haptics(v.getContext())) v.performHapticFeedback(haptic(which));
        if (pool == null || ids[which] == 0 || !Prefs.sounds(v.getContext())) return;
        float vol = VOL[which] * Prefs.volume(v.getContext()) / 70f;
        vol = Math.max(0f, Math.min(1f, vol));
        // Las teclas varían un poco de tono para no sonar robóticas
        float rate = which == KEY || which == TAP ? 0.96f + rnd.nextFloat() * 0.08f : 1f;
        pool.play(ids[which], vol, vol, 1, 0, rate);
    }

    private static int haptic(int which) {
        boolean modern = android.os.Build.VERSION.SDK_INT >= 30;
        switch (which) {
            case KEY:
            case TAP:
                return HapticFeedbackConstants.KEYBOARD_TAP;
            case HOLD:
                return HapticFeedbackConstants.LONG_PRESS;
            case ERROR:
                return modern ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS;
            case CHECK:
            case EQUALS:
                return modern ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY;
            case TOGGLE:
                return HapticFeedbackConstants.CLOCK_TICK;
            default:
                return HapticFeedbackConstants.CONTEXT_CLICK;
        }
    }
}
