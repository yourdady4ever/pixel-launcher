package com.jostin.launcher;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Saca los colores de una imagen: uno vivo para resaltar y uno dominante para teñir el vidrio. */
final class Palette {
    final int vibrant, dominant;
    final boolean lightTop;

    private Palette(int vibrant, int dominant, boolean lightTop) {
        this.vibrant = vibrant;
        this.dominant = dominant;
        this.lightTop = lightTop;
    }

    static Palette of(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 48, 48, true);
        int[] px = new int[48 * 48];
        s.getPixels(px, 0, 48, 0, 0, 48, 48);
        if (s != src) s.recycle();

        // Histograma con 5 bits por canal
        int[] count = new int[32 * 32 * 32];
        double topLum = 0;
        int topN = 0;
        for (int k = 0; k < px.length; k++) {
            int c = px[k];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            count[((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3)]++;
            if (k < 48 * 16) {
                topLum += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
                topN++;
            }
        }
        float[] hsl = new float[3];
        int best = -1, dom = -1;
        double bestScore = 0;
        int domCount = 0;
        for (int key = 0; key < count.length; key++) {
            int n = count[key];
            if (n == 0) continue;
            int r = ((key >> 10) & 31) * 8 + 4, g = ((key >> 5) & 31) * 8 + 4, b = (key & 31) * 8 + 4;
            if (n > domCount) {
                domCount = n;
                dom = Color.rgb(r, g, b);
            }
            toHsl(r, g, b, hsl);
            float sat = hsl[1], lum = hsl[2];
            if (sat < 0.28f || lum < 0.18f || lum > 0.88f) continue;
            double score = n * sat * sat * (1.0 - Math.abs(lum - 0.55) * 1.4);
            if (score > bestScore) {
                bestScore = score;
                best = Color.rgb(r, g, b);
            }
        }
        // Sin colores vivos suficientes: la foto es casi gris
        if (best == -1 || bestScore < px.length * 0.004) best = 0xFFE9EAEC;
        if (dom == -1) dom = 0xFF101214;
        return new Palette(best, dom, topN > 0 && topLum / topN > 0.62);
    }

    static void toHsl(int r, int g, int b, float[] out) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf));
        float l = (max + min) / 2f, h = 0, s = 0;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == rf) h = (gf - bf) / d + (gf < bf ? 6 : 0);
            else if (max == gf) h = (bf - rf) / d + 2;
            else h = (rf - gf) / d + 4;
            h *= 60f;
        }
        out[0] = h;
        out[1] = s;
        out[2] = l;
    }

    static int fromHsl(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = l - c / 2;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return Color.rgb(Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }
}
