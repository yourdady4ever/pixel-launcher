package com.jostin.launcher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.GridLayoutAnimationController;
import android.view.animation.ScaleAnimation;
import android.view.animation.Animation;
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

/** Todas las apps sobre vidrio, con buscador. También sirve para elegir una app del dock. */
public class AppsActivity extends Screen {
    static final String EXTRA_PICK = "pick";
    static final String EXTRA_PKG = "pkg";

    private static final class App {
        final String label, pkg, key;

        App(String label, String pkg) {
            this.label = label;
            this.pkg = pkg;
            this.key = strip(label);
        }
    }

    private final List<App> all = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private Adapter adapter;
    private GridView grid;
    private TextView empty;
    private boolean pick, mono;

    @Override
    String screenTitle() {
        return getIntent().getBooleanExtra(EXTRA_PICK, false) ? "Elige una app" : "Apps";
    }

    @Override
    float dim() {
        return 0.6f;
    }

    @Override
    View createBody() {
        pick = getIntent().getBooleanExtra(EXTRA_PICK, false);
        mono = Prefs.monoIcons(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout search = new LinearLayout(this);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        Glass.apply(search, backdrop, 26);
        ImageView si = new ImageView(this);
        Icons.Line sd = Icons.of(Icons.SEARCH);
        sd.setColor(Ui.MUTED);
        si.setImageDrawable(sd);
        search.addView(si, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        EditText q = new EditText(this);
        q.setHint("Buscar apps");
        q.setHintTextColor(0x80FFFFFF);
        q.setTextColor(Ui.TEXT);
        q.setTextSize(16);
        q.setTypeface(Ui.REGULAR);
        q.setBackground(null);
        q.setSingleLine(true);
        q.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(0, -2, 1f);
        qlp.leftMargin = Ui.dp(this, 10);
        search.addView(q, qlp);
        body.addView(search, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));

        empty = Ui.text(this, "", 15, Ui.MUTED, Ui.REGULAR);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(this, 40), 0, 0);
        body.addView(empty, new LinearLayout.LayoutParams(-1, -2));

        grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(Ui.dp(this, 10));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 24));
        adapter = new Adapter();
        grid.setAdapter(adapter);
        body.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        AnimationSet a = new AnimationSet(true);
        a.addAnimation(new AlphaAnimation(0f, 1f));
        a.addAnimation(new ScaleAnimation(0.7f, 1f, 0.7f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f));
        a.setDuration(360);
        a.setInterpolator(new DecelerateInterpolator(2f));
        GridLayoutAnimationController ctl = new GridLayoutAnimationController(a, 0.12f, 0.16f);
        ctl.setDirection(GridLayoutAnimationController.DIRECTION_TOP_TO_BOTTOM | GridLayoutAnimationController.DIRECTION_LEFT_TO_RIGHT);
        grid.setLayoutAnimation(ctl);

        grid.setOnItemClickListener((p, v, pos, id) -> choose(shown.get(pos), v));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pick) return false;
            Sound.play(v, Sound.HOLD);
            actions(shown.get(pos));
            return true;
        });
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a1, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a1, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                filter(s.toString(), false);
            }
        });
        q.setOnEditorActionListener((v, action, e) -> {
            if (!shown.isEmpty()) choose(shown.get(0), null);
            return true;
        });

        Ui.rise(search, 1, 14);
        loadApps();
        return body;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        new Thread(() -> {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            List<App> apps = new ArrayList<>();
            Map<String, Drawable> loaded = new HashMap<>();
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || loaded.containsKey(pkg)) continue;
                apps.add(new App(String.valueOf(ri.loadLabel(pm)), pkg));
                Drawable d = Icons.app(this, pkg, mono);
                if (d != null) loaded.put(pkg, d);
            }
            Collator col = Collator.getInstance(MainActivity.ES);
            Collections.sort(apps, (x, y) -> col.compare(x.label, y.label));
            runOnUiThread(() -> {
                all.clear();
                all.addAll(apps);
                icons.putAll(loaded);
                filter("", true);
            });
        }).start();
    }

    private void filter(String q, boolean animate) {
        String s = strip(q.trim());
        shown.clear();
        for (App a : all) if (s.isEmpty() || a.key.contains(s)) shown.add(a);
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText("No encontré esa app");
        adapter.notifyDataSetChanged();
        if (animate) grid.scheduleLayoutAnimation();
    }

    private void choose(App a, View from) {
        if (pick) {
            Sound.play(from != null ? from : grid, Sound.TAP);
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_PKG, a.pkg));
            finish();
            return;
        }
        Sound.play(from != null ? from : grid, Sound.OPEN);
        MainActivity.launch(this, a.pkg, from);
        grid.postDelayed(this::finish, 400);
    }

    private void actions(App a) {
        Sheet.show(this, backdrop, a.label,
                Sheet.item("Poner en el dock", Icons.HOME, () -> chooseSlot(a)),
                Sheet.item("Información de la app", Icons.INFO, () -> MainActivity.appInfo(this, a.pkg)),
                Sheet.item("Desinstalar", Icons.TRASH, () -> {
                    Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg));
                    try {
                        startActivity(i);
                    } catch (Exception e) {
                        MainActivity.appInfo(this, a.pkg);
                    }
                }).danger());
    }

    private void chooseSlot(App a) {
        List<Sheet.Item> items = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (int k = 0; k < Prefs.SLOTS; k++) {
            final int slot = k;
            String cur = Prefs.slot(this, k);
            String name = "Vacío";
            if (cur != null) {
                try {
                    name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(cur, 0)));
                } catch (PackageManager.NameNotFoundException e) {
                    name = "Vacío";
                }
            }
            final String n = name;
            Sheet.Item it = Sheet.item("Lugar " + (k + 1), Icons.SWAP, () -> {
                Prefs.setSlot(this, slot, a.pkg);
                Sheet.pill(this, backdrop, a.label + " quedó en el dock");
            });
            it.value = () -> n;
            it.keepOpen = false;
            items.add(it);
        }
        Sheet.show(this, backdrop, "¿En qué lugar?", items);
    }

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
                int p = Ui.dp(AppsActivity.this, 4);
                cell.setPadding(p, p, p, p);
                icon = new ImageView(AppsActivity.this);
                cell.addView(icon, new LinearLayout.LayoutParams(Ui.dp(AppsActivity.this, 56), Ui.dp(AppsActivity.this, 56)));
                label = Ui.text(AppsActivity.this, "", 12, 0xE6FFFFFF, Ui.MEDIUM);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = Ui.dp(AppsActivity.this, 8);
                cell.addView(label, lp);
                cell.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            } else {
                cell = (LinearLayout) convert;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            }
            App a = shown.get(i);
            label.setText(a.label);
            icon.setImageDrawable(icons.get(a.pkg));
            return cell;
        }
    }
}
