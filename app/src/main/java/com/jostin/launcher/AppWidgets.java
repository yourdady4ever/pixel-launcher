package com.jostin.launcher;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Collections;

/** Widgets de otras apps con las API oficiales de Android (AppWidgetHost). */
final class AppWidgets {
    static final int HOST_ID = 7310;
    private static AppWidgetHost host;

    private AppWidgets() {}

    static AppWidgetHost host(Context c) {
        if (host == null) host = new AppWidgetHost(c.getApplicationContext(), HOST_ID);
        return host;
    }

    static AppWidgetManager mgr(Context c) {
        return AppWidgetManager.getInstance(c);
    }

    static void start(Context c) {
        try {
            host(c).startListening();
        } catch (Exception ignored) {
            // nada
        }
    }

    static void stop(Context c) {
        try {
            host(c).stopListening();
        } catch (Exception ignored) {
            // nada
        }
    }

    static void delete(Context c, int id) {
        if (id < 0) return;
        try {
            host(c).deleteAppWidgetId(id);
        } catch (Exception ignored) {
            // nada
        }
    }

    /** Celdas que ocupa un widget según lo que pide su app. */
    static int[] span(AppWidgetProviderInfo info, float cellW, float cellH, int cols, int rows) {
        int w, h;
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            w = info.targetCellWidth;
            h = info.targetCellHeight;
        } else {
            w = (int) Math.ceil(info.minWidth / cellW);
            h = (int) Math.ceil(info.minHeight / cellH);
        }
        return new int[]{Math.max(1, Math.min(cols, w)), Math.max(1, Math.min(rows, h))};
    }

    static float[] minSpan(Context c, int id, float cellW, float cellH) {
        AppWidgetProviderInfo info = mgr(c).getAppWidgetInfo(id);
        if (info == null) return new float[]{1, 1};
        boolean rw = (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
        boolean rh = (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
        float mw = info.minResizeWidth > 0 ? info.minResizeWidth : info.minWidth;
        float mh = info.minResizeHeight > 0 ? info.minResizeHeight : info.minHeight;
        float w = rw ? Math.max(1, (float) Math.ceil(mw / cellW)) : Math.max(1, (float) Math.ceil(info.minWidth / cellW));
        float h = rh ? Math.max(1, (float) Math.ceil(mh / cellH)) : Math.max(1, (float) Math.ceil(info.minHeight / cellH));
        return new float[]{w, h};
    }

    static boolean resizable(Context c, int id) {
        AppWidgetProviderInfo info = mgr(c).getAppWidgetInfo(id);
        return info != null && info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE;
    }

    @SuppressWarnings("deprecation")
    static void updateSize(AppWidgetHostView v, int wpx, int hpx) {
        float d = v.getResources().getDisplayMetrics().density;
        int w = Math.round(wpx / d), h = Math.round(hpx / d);
        if (w <= 0 || h <= 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) v.updateAppWidgetSize(new Bundle(), Collections.singletonList(new SizeF(w, h)));
            else v.updateAppWidgetSize(null, w, h, w, h);
        } catch (Exception ignored) {
            // algunos widgets no aceptan cambios de tamaño
        }
    }

    /** Contenedor de un widget de otra app en la cuadrícula. */
    static final class Frame extends FrameLayout {
        final HomeModel.Item item;
        private AppWidgetHostView hv;

        Frame(MainActivity a, HomeModel.Item it) {
            super(a);
            item = it;
            int id = it.num("id", -1);
            AppWidgetProviderInfo info = id < 0 ? null : mgr(a).getAppWidgetInfo(id);
            if (info != null) {
                try {
                    hv = host(a).createView(a.getApplicationContext(), id, info);
                    hv.setPadding(0, 0, 0, 0);
                    addView(hv, new LayoutParams(-1, -1));
                } catch (Exception e) {
                    hv = null;
                }
            }
            if (hv == null) {
                TextView t = Ui.text(a, "Este widget ya no está disponible. Mantenlo presionado para quitarlo.", 13, Ui.MUTED, Ui.REGULAR);
                t.setGravity(Gravity.CENTER);
                int p = Ui.dp(a, 12);
                t.setPadding(p, p, p, p);
                Glass.apply(this, a.backdrop(), 20);
                addView(t, new LayoutParams(-1, -1));
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (hv != null && (w != ow || h != oh)) updateSize(hv, w, h);
        }
    }
}
