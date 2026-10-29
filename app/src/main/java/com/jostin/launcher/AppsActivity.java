package com.jostin.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cajón con todas las apps, con buscador. También sirve para elegir una app del dock. */
public class AppsActivity extends Activity {
    static final String EXTRA_PICK = "pick";
    static final String EXTRA_PKG = "pkg";

    private static final class App {
        final String label, pkg, lower;

        App(String label, String pkg) {
            this.label = label;
            this.pkg = pkg;
            this.lower = strip(label);
        }
    }

    private final List<App> all = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private Adapter adapter;
    private boolean pick;
    private TextView empty;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.edgeToEdge(this, false);
        pick = getIntent().getBooleanExtra(EXTRA_PICK, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xF20E0F11);
        int side = Ui.dp(this, 16);

        TextView title = Ui.text(this, pick ? "ELIGE UNA APP" : "APPS", 13, Ui.MUTED, Ui.MEDIUM);
        title.setLetterSpacing(0.35f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        EditText search = Ui.input(this, "Buscar", false);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(side, Ui.dp(this, 12), side, Ui.dp(this, 8));
        root.addView(search, slp);

        empty = Ui.text(this, "Cargando…", 14, Ui.MUTED, null);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new LinearLayout.LayoutParams(-1, -2));

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(Ui.dp(this, 6));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        grid.setClipToPadding(false);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        grid.setOnItemClickListener((p, v, pos, id) -> choose(shown.get(pos), v));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            Ui.open(v);
            MainActivity.appInfo(this, shown.get(pos).pkg);
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                filter(s.toString());
            }
        });
        search.setOnEditorActionListener((v, action, e) -> {
            if (!shown.isEmpty()) choose(shown.get(0), null);
            return true;
        });

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop() + Ui.dp(this, 16), 0, 0);
            grid.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), insets.getSystemWindowInsetBottom() + Ui.dp(this, 12));
            return insets;
        });

        setContentView(root);
        loadApps();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        new Thread(() -> {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            List<App> apps = new ArrayList<>();
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                apps.add(new App(String.valueOf(ri.loadLabel(pm)), pkg));
            }
            Collator col = Collator.getInstance(MainActivity.ES);
            Collections.sort(apps, (a, b) -> col.compare(a.label, b.label));
            runOnUiThread(() -> {
                all.clear();
                all.addAll(apps);
                filter("");
            });
        }).start();
    }

    private void filter(String q) {
        String s = strip(q.trim());
        shown.clear();
        for (App a : all) if (s.isEmpty() || a.lower.contains(s)) shown.add(a);
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(all.isEmpty() ? "Cargando…" : "No encontré esa app");
        adapter.notifyDataSetChanged();
    }

    private void choose(App a, View from) {
        if (pick) {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_PKG, a.pkg));
            finish();
            return;
        }
        if (from != null) Ui.open(from);
        MainActivity.launch(this, a.pkg, from);
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** Quita acentos y mayúsculas para buscar fácil. */
    static String strip(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase(MainActivity.ES);
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convert == null) {
                cell = new LinearLayout(AppsActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                int p = Ui.dp(AppsActivity.this, 6);
                cell.setPadding(p, p, p, p);
                icon = new ImageView(AppsActivity.this);
                cell.addView(icon, new LinearLayout.LayoutParams(Ui.dp(AppsActivity.this, 52), Ui.dp(AppsActivity.this, 52)));
                label = Ui.text(AppsActivity.this, "", 12, Ui.TEXT, null);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = Ui.dp(AppsActivity.this, 4);
                cell.addView(label, lp);
                cell.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            } else {
                cell = (LinearLayout) convert;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            }
            App a = shown.get(i);
            label.setText(a.label);
            Drawable d = icons.get(a.pkg);
            if (d == null) {
                try {
                    d = getPackageManager().getApplicationIcon(a.pkg);
                    icons.put(a.pkg, d);
                } catch (PackageManager.NameNotFoundException e) {
                    d = null;
                }
            }
            icon.setImageDrawable(d);
            Ui.grayscale(icon, Prefs.grayIcons(AppsActivity.this));
            return cell;
        }
    }
}
