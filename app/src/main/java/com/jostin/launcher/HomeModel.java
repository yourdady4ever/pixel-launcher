package com.jostin.launcher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Qué hay en cada página de inicio, dónde y cómo. Las posiciones van en celdas con decimales:
 * en modo cuadrícula se usan enteros y no se enciman; en modo libre se ajustan a cuartos de celda
 * y se pueden encimar por capas.
 */
final class HomeModel {
    static final int MAX_PAGES = 7;
    static final float EPS = 0.01f;

    static final class Item {
        String id, type;
        int page, z;
        float x, y, w, h, alpha = 1f, rot = 0f;
        boolean locked;
        JSONObject data = new JSONObject();

        String str(String k, String d) {
            String v = data.optString(k, d);
            return v == null ? d : v;
        }

        int num(String k, int d) {
            return data.optInt(k, d);
        }

        boolean flag(String k, boolean d) {
            return data.optBoolean(k, d);
        }

        void put(String k, Object v) {
            try {
                data.put(k, v);
            } catch (JSONException ignored) {
                // nada
            }
        }

        JSONArray apps() {
            JSONArray a = data.optJSONArray("apps");
            if (a == null) {
                a = new JSONArray();
                put("apps", a);
            }
            return a;
        }
    }

    final List<Item> items = new ArrayList<>();
    int pages = 1, cols = 4, rows = 6;
    boolean fresh, free;
    private static long seq = System.currentTimeMillis();

    static String nextId() {
        return Long.toString(++seq, 36);
    }

    static HomeModel load(Context c) {
        HomeModel m = new HomeModel();
        String s = Prefs.sp(c).getString("home_v1", null);
        m.fresh = s == null || !m.fromJson(s);
        if (m.fresh) m.items.clear();
        return m;
    }

    boolean fromJson(String s) {
        try {
            JSONObject o = new JSONObject(s);
            List<Item> list = new ArrayList<>();
            JSONArray a = o.optJSONArray("items");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                Item it = new Item();
                it.id = j.optString("id", nextId());
                it.type = j.optString("type", "");
                if (it.type.isEmpty()) continue;
                it.page = Math.max(0, j.optInt("page"));
                it.x = (float) j.optDouble("x", 0);
                it.y = (float) j.optDouble("y", 0);
                it.w = Math.max(0.5f, (float) j.optDouble("w", 1));
                it.h = Math.max(0.5f, (float) j.optDouble("h", 1));
                it.z = j.optInt("z", 0);
                it.alpha = (float) Math.max(0.15, Math.min(1, j.optDouble("alpha", 1)));
                it.rot = (float) j.optDouble("rot", 0);
                it.locked = j.optBoolean("locked", false);
                JSONObject d = j.optJSONObject("data");
                if (d != null) it.data = d;
                list.add(it);
            }
            items.clear();
            items.addAll(list);
            pages = Math.max(1, Math.min(MAX_PAGES, o.optInt("pages", 1)));
            rows = o.optInt("rows", rows);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("pages", pages);
            o.put("rows", rows);
            JSONArray a = new JSONArray();
            for (Item it : items) {
                JSONObject j = new JSONObject();
                j.put("id", it.id);
                j.put("type", it.type);
                j.put("page", it.page);
                j.put("x", it.x);
                j.put("y", it.y);
                j.put("w", it.w);
                j.put("h", it.h);
                if (it.z != 0) j.put("z", it.z);
                if (it.alpha < 1f) j.put("alpha", it.alpha);
                if (it.rot != 0f) j.put("rot", it.rot);
                if (it.locked) j.put("locked", true);
                j.put("data", new JSONObject(it.data.toString()));
                a.put(j);
            }
            o.put("items", a);
            return o.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    void save(Context c) {
        Prefs.sp(c).edit().putString("home_v1", toJson()).apply();
        Safety.maybeBackup(c);
    }

    List<Item> on(int page) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) if (it.page == page) out.add(it);
        return out;
    }

    Item find(String id) {
        for (Item it : items) if (it.id.equals(id)) return it;
        return null;
    }

    /** El elemento de 1×1 que está justo en esa celda (para hacer carpetas). */
    Item cellAt(int page, float x, float y, Item ignore) {
        for (Item it : items) {
            if (it == ignore || it.page != page) continue;
            if (Math.abs(it.x - x) < EPS && Math.abs(it.y - y) < EPS && Math.abs(it.w - 1) < EPS && Math.abs(it.h - 1) < EPS) return it;
        }
        return null;
    }

    boolean inBounds(float x, float y, float w, float h) {
        return x > -EPS && y > -EPS && x + w <= cols + EPS && y + h <= rows + EPS;
    }

    /** ¿Cabe sin salirse ni encimarse? En modo libre basta con que no se salga. */
    boolean fits(int page, float x, float y, float w, float h, Item ignore) {
        if (!inBounds(x, y, w, h)) return false;
        if (free) return true;
        for (Item it : items) {
            if (it == ignore || it.page != page) continue;
            if (overlap(x, y, w, h, it.x, it.y, it.w, it.h)) return false;
        }
        return true;
    }

    private static boolean overlap(float x, float y, float w, float h, float x2, float y2, float w2, float h2) {
        return x < x2 + w2 - EPS && x2 < x + w - EPS && y < y2 + h2 - EPS && y2 < y + h - EPS;
    }

    /** Primer hueco libre (sin encimarse, aun en modo libre) para w×h, o null. */
    int[] spot(int page, float w, float h) {
        for (int y = 0; y + h <= rows + EPS; y++) {
            for (int x = 0; x + w <= cols + EPS; x++) {
                boolean ok = true;
                for (Item it : items) {
                    if (it.page == page && overlap(x, y, w, h, it.x, it.y, it.w, it.h)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) return new int[]{x, y};
            }
        }
        return null;
    }

    int maxZ(int page) {
        int z = 0;
        for (Item it : items) if (it.page == page) z = Math.max(z, it.z);
        return z;
    }

    int minZ(int page) {
        int z = 0;
        for (Item it : items) if (it.page == page) z = Math.min(z, it.z);
        return z;
    }

    Item add(String type, int page, float x, float y, float w, float h) {
        Item it = new Item();
        it.id = nextId();
        it.type = type;
        it.page = page;
        it.x = x;
        it.y = y;
        it.w = w;
        it.h = h;
        it.z = maxZ(page) + 1;
        items.add(it);
        pages = Math.max(pages, page + 1);
        return it;
    }

    Item copy(Item src) {
        Item it = new Item();
        it.id = nextId();
        it.type = src.type;
        it.page = src.page;
        it.x = src.x;
        it.y = src.y;
        it.w = src.w;
        it.h = src.h;
        it.alpha = src.alpha;
        it.rot = src.rot;
        it.z = maxZ(src.page) + 1;
        try {
            it.data = new JSONObject(src.data.toString());
        } catch (JSONException e) {
            it.data = new JSONObject();
        }
        items.add(it);
        return it;
    }

    /** Acomoda todo a la cuadrícula actual: recorta tamaños y reubica lo que se encime (en cuadrícula). */
    void normalize() {
        List<Item> placed = new ArrayList<>();
        for (Item it : new ArrayList<>(items)) {
            it.w = Math.max(0.5f, Math.min(it.w, cols));
            it.h = Math.max(0.5f, Math.min(it.h, rows));
            it.x = Math.max(0, Math.min(it.x, cols - it.w));
            it.y = Math.max(0, Math.min(it.y, rows - it.h));
            it.page = Math.max(0, Math.min(it.page, MAX_PAGES - 1));
            if (!free) {
                it.x = Math.round(it.x);
                it.y = Math.round(it.y);
                it.w = Math.max(1, Math.round(it.w));
                it.h = Math.max(1, Math.round(it.h));
                if (it.x + it.w > cols) it.x = cols - it.w;
                if (it.y + it.h > rows) it.y = rows - it.h;
                if (!freeIn(placed, it)) {
                    if (!relocate(placed, it)) {
                        items.remove(it);
                        continue;
                    }
                }
            }
            placed.add(it);
            pages = Math.max(pages, it.page + 1);
        }
    }

    private static boolean freeIn(List<Item> list, Item it) {
        for (Item o : list) {
            if (o.page == it.page && overlap(it.x, it.y, it.w, it.h, o.x, o.y, o.w, o.h)) return false;
        }
        return true;
    }

    private boolean relocate(List<Item> placed, Item it) {
        for (int p = it.page; p < MAX_PAGES; p++) {
            for (int y = 0; y + it.h <= rows; y++) {
                for (int x = 0; x + it.w <= cols; x++) {
                    int op = it.page;
                    float ox = it.x, oy = it.y;
                    it.page = p;
                    it.x = x;
                    it.y = y;
                    if (freeIn(placed, it)) return true;
                    it.page = op;
                    it.x = ox;
                    it.y = oy;
                }
            }
        }
        return false;
    }

    /** Quita las páginas vacías (deja al menos una). */
    void trimPages() {
        for (int p = pages - 1; p >= 0 && pages > 1; p--) {
            if (on(p).isEmpty()) {
                for (Item it : items) if (it.page > p) it.page--;
                pages--;
            }
        }
    }

    /** Diseño inicial: parecido al anterior en la primera página y una segunda con más widgets. */
    void buildDefault(Context c) {
        items.clear();
        pages = 2;
        int r = rows;
        add("reloj", 0, 0, 0, 4, 2);
        boolean tiles = Prefs.showPanels(c), music = Prefs.showMusic(c);
        int musicRow = tiles ? r - 3 : r - 1;
        if (music && musicRow >= 2) add("musica", 0, 0, musicRow, 4, 1);
        if (tiles) {
            add("atajo", 0, 0, r - 2, 2, 1).put("action", "calc");
            add("estudio", 0, 2, r - 2, 2, 1);
            add("atajo", 0, 0, r - 1, 2, 1).put("action", "tasks");
            add("jarvis", 0, 2, r - 1, 2, 1);
        }
        add("saludo", 1, 0, 0, 4, 1);
        add("clima", 1, 0, 1, 2, 2);
        add("sistema", 1, 2, 1, 2, 2);
        if (r >= 6) add("calendario", 1, 0, 3, 4, 3);
        if (r >= 7) add("interruptores", 1, 0, 6, 4, 1);
        fresh = false;
    }
}
