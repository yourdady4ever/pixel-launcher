package com.jostin.launcher;

import android.view.View;

/** Pantalla para elegir la app de un lugar del dock. */
public class AppsActivity extends Screen implements Drawer.Host {
    static final String EXTRA_PICK = "pick";
    static final String EXTRA_PKG = "pkg";

    private Drawer drawer;

    @Override
    String screenTitle() {
        return "Elige una app";
    }

    @Override
    float dim() {
        return 0.6f;
    }

    @Override
    View createBody() {
        drawer = new Drawer(this, backdrop, this, true);
        drawer.load(true);
        Ui.rise(drawer, 1, 18);
        return drawer;
    }

    @Override
    public void closeDrawer() {
        finish();
    }

    @Override
    public void appActions(Drawer.App app, View from) {}
}
