package com.jostin.launcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Motor de temas: un tema es una lista de ajustes conocidos (fondo, color, reloj, íconos, dock,
 * transición) y, si quieres, el acomodo de inicio. Se guarda, duplica, exporta e importa como
 * texto. Todo lo que llega de fuera se revisa contra esta lista: lo desconocido se ignora y los
 * números se recortan a su rango. Nada de un tema se ejecuta.
 */
final class Themes {
    static final int VERSION = 1;
    private static final String STORE = "themes", UNDO = "theme_undo";

    static final class Key {
        final String key, doc;
        final char type; // i número, b sí/no, s texto, c color
        final int min, max;
        final Object def;

        Key(String key, char type, int min, int max, Object def, String doc) {
            this.key = key;
            this.type = type;
            this.min = min;
            this.max = max;
            this.def = def;
            this.doc = doc;
        }
    }

    private static String list(String[] names) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) b.append(", ");
            b.append(i).append(' ').append(names[i]);
        }
        return b.toString();
    }

    static final Key[] KEYS = {
            new Key("wall_scene", 'i', 0, Scenes.COUNT - 1, 0, "Fondo animado: " + list(Scenes.NAMES)),
            new Key("wall_overlay", 'i', 0, Particles.NAMES.length - 1, 0, "Animación encima del fondo: " + list(Particles.NAMES)),
            new Key("wall_effect", 'i', 0, Wallpaper.EFFECTS.length - 1, 0, "Efecto si el fondo es foto: " + list(Wallpaper.EFFECTS)),
            new Key("wall_dim", 'i', 0, 60, 0, "Oscurecer el fondo, de 0 a 60 (%)"),
            new Key("anim", 'b', 0, 0, true, "Fondo en movimiento"),
            new Key("accent_mode", 'i', 0, 2, 0, "Color de acento: 0 tomado del fondo, 1 monocromo, 2 el de accent_color"),
            new Key("accent_color", 'c', 0, 0, 0xFF7FD4FF, "Color de acento como \"#RRGGBB\" (se usa con accent_mode 2)"),
            new Key("tint_wall", 'b', 0, 0, true, "Teñir el vidrio con el color del fondo"),
            new Key("glass_opacity", 'i', 25, 90, 55, "Opacidad del vidrio, de 25 a 90 (%)"),
            new Key("clock_style", 'i', 0, 2, 0, "Reloj: 0 espaciado con el día, 1 digital grande, 2 horas y minutos apilados"),
            new Key("clock_align", 'i', 0, 1, 0, "Reloj: 0 centrado, 1 a la izquierda"),
            new Key("clock_font", 'i', 0, Ui.CLOCK_FONT_NAMES.length - 1, 0, "Letra del reloj: " + list(Ui.CLOCK_FONT_NAMES)),
            new Key("clock_color", 'i', 0, 1, 0, "Color del reloj: 0 automático, 1 color de acento"),
            new Key("greeting", 's', 0, 80, "", "Frase corta bajo el reloj (puede ir vacía)"),
            new Key("show_weather", 'b', 0, 0, true, "Clima bajo el reloj"),
            new Key("icon_style", 'i', 0, 2, 0, "Íconos: 0 monocromos, 1 originales, 2 de color"),
            new Key("icon_shape", 'i', 0, IconEngine.SHAPES.length - 1, 0, "Forma de íconos: " + list(IconEngine.SHAPES)),
            new Key("icon_scale", 'i', 70, 130, 100, "Tamaño de íconos, de 70 a 130 (%)"),
            new Key("icon_glow", 'b', 0, 0, false, "Halo de color detrás de los íconos"),
            new Key("home_labels", 'b', 0, 0, true, "Nombres bajo los íconos"),
            new Key("dock_icon", 'i', 36, 56, 44, "Tamaño de íconos del dock, de 36 a 56 (dp)"),
            new Key("dock_space", 'i', 0, 10, 3, "Separación entre íconos del dock, de 0 a 10 (dp)"),
            new Key("dock_glass", 'b', 0, 0, true, "Dock con fondo de vidrio"),
            new Key("dock_count", 'i', 4, 6, 6, "Apps en el dock, de 4 a 6"),
            new Key("show_dock", 'b', 0, 0, true, "Mostrar el dock"),
            new Key("page_anim", 'i', 0, HomeView.TRANSITIONS.length - 1, 0, "Transición entre páginas: " + list(HomeView.TRANSITIONS)),
            new Key("home_free", 'b', 0, 0, false, "Acomodo libre (widgets encimados) en lugar de cuadrícula"),
    };

    private Themes() {}

    private static Key key(String k) {
        for (Key x : KEYS) if (x.key.equals(k)) return x;
        return null;
    }

    // ==================== Leer y aplicar ====================

    /** Toma el look actual. Con layout incluye el acomodo de inicio. */
    static JSONObject capture(Context c, String name, boolean layout) {
        SharedPreferences sp = Prefs.sp(c);
        JSONObject o = new JSONObject();
        try {
            o.put("jostin_theme", VERSION);
            o.put("name", name);
            JSONObject p = new JSONObject();
            for (Key k : KEYS) {
                switch (k.type) {
                    case 'i': p.put(k.key, sp.getInt(k.key, (Integer) k.def)); break;
                    case 'b': p.put(k.key, sp.getBoolean(k.key, (Boolean) k.def)); break;
                    case 'c': p.put(k.key, String.format("#%06X", sp.getInt(k.key, (Integer) k.def) & 0xFFFFFF)); break;
                    default: p.put(k.key, sp.getString(k.key, (String) k.def)); break;
                }
            }
            // La foto de fondo solo sirve en este teléfono
            if (Prefs.wallKind(c) == 1 && Prefs.wallPhoto(c) != null) p.put("wall_photo", Prefs.wallPhoto(c));
            o.put("prefs", p);
            if (layout) {
                String home = sp.getString("home_v1", null);
                if (home != null) o.put("home", new JSONObject(home));
            }
        } catch (Exception ignored) {
            // se regresa lo que se alcanzó
        }
        return o;
    }

    /**
     * Revisa un tema que llegó de fuera. Regresa uno limpio con solo valores válidos, o null si
     * no es un tema. Lo que no se reconoce se ignora.
     */
    static JSONObject clean(Context c, JSONObject in) {
        if (in == null) return null;
        JSONObject p = in.optJSONObject("prefs");
        if (p == null) return null;
        JSONObject out = new JSONObject();
        JSONObject cp = new JSONObject();
        try {
            String name = in.optString("name", "Tema").trim();
            if (name.isEmpty()) name = "Tema";
            out.put("jostin_theme", VERSION);
            out.put("name", name.length() > 40 ? name.substring(0, 40) : name);
            Iterator<String> it = p.keys();
            while (it.hasNext()) {
                String k = it.next();
                Key key = key(k);
                Object v = p.get(k);
                if (key == null) {
                    if (k.equals("wall_photo") && v instanceof String && Wallpaper.file(c, (String) v).exists()) cp.put(k, v);
                    continue;
                }
                switch (key.type) {
                    case 'i':
                        if (v instanceof Number) cp.put(k, Math.max(key.min, Math.min(key.max, ((Number) v).intValue())));
                        break;
                    case 'b':
                        if (v instanceof Boolean) cp.put(k, v);
                        break;
                    case 'c': {
                        Integer col = parseColor(v);
                        if (col != null) cp.put(k, String.format("#%06X", col & 0xFFFFFF));
                        break;
                    }
                    default:
                        if (v instanceof String) {
                            String s = ((String) v).replaceAll("[\\p{Cntrl}]", " ").trim();
                            cp.put(k, s.length() > key.max ? s.substring(0, key.max) : s);
                        }
                        break;
                }
            }
            if (cp.length() == 0) return null;
            out.put("prefs", cp);
            JSONObject home = in.optJSONObject("home");
            if (home != null) {
                JSONObject h = cleanHome(c, home);
                if (h != null) out.put("home", h);
            }
        } catch (Exception e) {
            return null;
        }
        return out;
    }

    /** Del acomodo de inicio se quedan solo widgets propios y apps que sí están instaladas. */
    private static JSONObject cleanHome(Context c, JSONObject home) {
        HomeModel m = new HomeModel();
        if (!m.fromJson(home.toString())) return null;
        List<HomeModel.Item> keep = new ArrayList<>();
        for (HomeModel.Item it : m.items) {
            if (it.type.equals("aw")) continue; // los widgets de otras apps no viajan
            if (it.type.equals("app")) {
                if (c.getPackageManager().getLaunchIntentForPackage(it.str("pkg", "")) == null) continue;
            } else if (it.type.equals("folder")) {
                JSONArray a = it.apps(), ok = new JSONArray();
                for (int i = 0; i < a.length(); i++) {
                    if (c.getPackageManager().getLaunchIntentForPackage(a.optString(i)) != null) ok.put(a.optString(i));
                }
                if (ok.length() == 0) continue;
                it.put("apps", ok);
            } else if (Gadgets.spec(it.type) == null) {
                continue;
            }
            keep.add(it);
        }
        if (keep.isEmpty()) return null;
        m.items.clear();
        m.items.addAll(keep);
        try {
            return new JSONObject(m.toJson());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseColor(Object v) {
        try {
            if (v instanceof Number) return ((Number) v).intValue() | 0xFF000000;
            if (v instanceof String) {
                String s = ((String) v).trim();
                if (!s.startsWith("#")) s = "#" + s;
                return Color.parseColor(s) | 0xFF000000;
            }
        } catch (Exception ignored) {
            // no es color
        }
        return null;
    }

    /** Aplica un tema ya revisado. Guarda el look anterior para poder deshacer. */
    static void apply(MainActivity a, JSONObject theme) {
        JSONObject before = capture(a, "Antes", theme.has("home"));
        Prefs.sp(a).edit().putString(UNDO, before.toString()).apply();
        write(a, theme);
    }

    private static void write(MainActivity a, JSONObject theme) {
        JSONObject p = theme.optJSONObject("prefs");
        if (p == null) return;
        SharedPreferences.Editor e = Prefs.sp(a).edit();
        Iterator<String> it = p.keys();
        boolean scene = false;
        while (it.hasNext()) {
            String k = it.next();
            Key key = key(k);
            if (key == null) continue;
            switch (key.type) {
                case 'i':
                    e.putInt(k, p.optInt(k));
                    if (k.equals("wall_scene")) scene = true;
                    break;
                case 'b': e.putBoolean(k, p.optBoolean(k)); break;
                case 'c': {
                    Integer col = parseColor(p.opt(k));
                    if (col != null) e.putInt(k, col);
                    break;
                }
                default: e.putString(k, p.optString(k)); break;
            }
        }
        String photo = p.optString("wall_photo", null);
        if (photo != null && Wallpaper.file(a, photo).exists()) {
            e.putInt("wall_kind", 1);
            e.putString("wall_photo", photo);
        } else if (scene) {
            e.putInt("wall_kind", 0);
        }
        e.commit();
        JSONObject home = theme.optJSONObject("home");
        if (home != null) {
            Safety.backupNow(a, "antes-tema");
            a.home.restore(home.toString());
            a.purgeWidgets(a.home.model);
        }
        Theme.recompute(a);
        a.applyWallpaper();
        a.applyLook();
        a.applyHome();
    }

    static boolean canUndo(Context c) {
        return Prefs.sp(c).contains(UNDO);
    }

    static void undo(MainActivity a) {
        String s = Prefs.sp(a).getString(UNDO, null);
        if (s == null) return;
        Prefs.sp(a).edit().remove(UNDO).apply();
        try {
            write(a, new JSONObject(s));
        } catch (Exception ignored) {
            // nada
        }
    }

    // ==================== Tus temas guardados ====================

    static List<JSONObject> saved(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.sp(c).getString(STORE, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i));
        } catch (Exception ignored) {
            // vacío
        }
        return out;
    }

    private static void store(Context c, List<JSONObject> list) {
        JSONArray a = new JSONArray();
        for (JSONObject o : list) a.put(o);
        Prefs.sp(c).edit().putString(STORE, a.toString()).apply();
    }

    static void save(Context c, JSONObject theme) {
        List<JSONObject> list = saved(c);
        try {
            theme.put("id", Long.toString(System.currentTimeMillis(), 36));
        } catch (Exception ignored) {
            // nada
        }
        list.add(0, theme);
        while (list.size() > 30) list.remove(list.size() - 1);
        store(c, list);
    }

    static void delete(Context c, String id) {
        List<JSONObject> list = saved(c);
        list.removeIf(o -> id.equals(o.optString("id")));
        store(c, list);
    }

    static void rename(Context c, String id, String name) {
        List<JSONObject> list = saved(c);
        for (JSONObject o : list) {
            if (id.equals(o.optString("id"))) {
                try {
                    o.put("name", name);
                } catch (Exception ignored) {
                    // nada
                }
            }
        }
        store(c, list);
    }

    static JSONObject duplicate(Context c, JSONObject src) {
        try {
            JSONObject copy = new JSONObject(src.toString());
            copy.put("name", src.optString("name", "Tema") + " (copia)");
            save(c, copy);
            return copy;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== Listos para usar ====================

    static List<JSONObject> presets() {
        String[][] p = {
                {"Jostin clásico", "{\"wall_scene\":0,\"wall_overlay\":0,\"accent_mode\":0,\"clock_style\":0,\"clock_align\":0,\"clock_font\":0,\"icon_style\":0,\"icon_shape\":0,\"icon_glow\":false,\"page_anim\":0,\"dock_glass\":true,\"home_labels\":true}"},
                {"Medianoche", "{\"wall_scene\":2,\"wall_overlay\":4,\"accent_mode\":2,\"accent_color\":\"#8C9EFF\",\"clock_style\":1,\"clock_align\":0,\"clock_font\":4,\"clock_color\":1,\"icon_style\":2,\"icon_shape\":1,\"icon_glow\":true,\"page_anim\":3,\"glass_opacity\":65}"},
                {"Aurora", "{\"wall_scene\":5,\"wall_overlay\":3,\"accent_mode\":0,\"clock_style\":2,\"clock_align\":1,\"clock_font\":2,\"icon_style\":0,\"icon_shape\":2,\"page_anim\":6,\"glass_opacity\":45}"},
                {"Minimal", "{\"wall_scene\":8,\"wall_overlay\":0,\"accent_mode\":1,\"clock_style\":1,\"clock_align\":1,\"clock_font\":1,\"icon_style\":1,\"icon_shape\":3,\"home_labels\":false,\"dock_glass\":false,\"page_anim\":1,\"glass_opacity\":40}"},
                {"Brasa", "{\"wall_scene\":4,\"wall_overlay\":3,\"accent_mode\":2,\"accent_color\":\"#FF5A5F\",\"clock_style\":0,\"clock_font\":3,\"icon_style\":2,\"icon_shape\":4,\"page_anim\":7}"},
                {"Constelación", "{\"wall_scene\":7,\"wall_overlay\":4,\"accent_mode\":2,\"accent_color\":\"#4DF2C3\",\"clock_style\":2,\"clock_align\":0,\"clock_font\":0,\"icon_style\":0,\"icon_shape\":1,\"page_anim\":2}"},
        };
        List<JSONObject> out = new ArrayList<>();
        for (String[] x : p) {
            try {
                JSONObject o = new JSONObject();
                o.put("jostin_theme", VERSION);
                o.put("name", x[0]);
                o.put("prefs", new JSONObject(x[1]));
                out.add(o);
            } catch (Exception ignored) {
                // nada
            }
        }
        return out;
    }

    // ==================== Exportar e importar ====================

    static String export(JSONObject theme) {
        try {
            return theme.toString(2);
        } catch (Exception e) {
            return theme.toString();
        }
    }

    /** Busca un tema dentro de un texto (por ejemplo la respuesta de Jarvis, que puede traer más texto). */
    static JSONObject parse(Context c, String text) {
        if (text == null) return null;
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return clean(c, new JSONObject(text.substring(start, end + 1)));
        } catch (Exception e) {
            return null;
        }
    }

    static String clipboard(Context c) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return null;
        ClipData d = cm.getPrimaryClip();
        if (d == null || d.getItemCount() == 0) return null;
        CharSequence t = d.getItemAt(0).coerceToText(c);
        return t == null ? null : t.toString();
    }

    static void copy(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    /** Instrucciones para que Jarvis (Claude) diseñe un tema que Jostin OS pueda leer. */
    static String jarvisPrompt(String idea) {
        StringBuilder b = new StringBuilder();
        b.append("Eres Jarvis y diseñas temas para Jostin OS, un launcher de Android. Diseña un tema para esta idea: \"")
                .append(idea).append("\".\n\n");
        b.append("Responde solo con un objeto JSON, sin texto antes ni después, con esta forma:\n");
        b.append("{\"jostin_theme\": 1, \"name\": \"nombre corto\", \"prefs\": { ... }}\n\n");
        b.append("Dentro de \"prefs\" usa solo estas claves (todas son opcionales; las que no pongas se quedan como están):\n");
        for (Key k : KEYS) {
            b.append("- ").append(k.key).append(": ");
            switch (k.type) {
                case 'i': b.append("número entero de ").append(k.min).append(" a ").append(k.max).append(". "); break;
                case 'b': b.append("true o false. "); break;
                case 'c': b.append("texto \"#RRGGBB\". "); break;
                default: b.append("texto de hasta ").append(k.max).append(" letras. "); break;
            }
            b.append(k.doc).append('\n');
        }
        b.append("\nNo agregues otras claves. Cuida que el color de acento se lea bien sobre el fondo elegido.");
        return b.toString();
    }

    /** Color de acento que tendría el tema (para la vista previa). */
    static int previewAccent(JSONObject theme) {
        JSONObject p = theme.optJSONObject("prefs");
        if (p == null) return Theme.accent;
        int mode = p.optInt("accent_mode", 0);
        if (mode == 1) return 0xFFF5F6F8;
        if (mode == 2) {
            Integer c = parseColor(p.opt("accent_color"));
            if (c != null) return c;
        }
        return Theme.accent;
    }
}
