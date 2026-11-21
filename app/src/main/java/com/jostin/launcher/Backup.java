package com.jostin.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Respaldo de la configuración en un archivo .json que tú guardas donde quieras (Drive incluido,
 * desde el selector de Android). Lleva widgets y su acomodo, dock, fondo, apps ocultas y ajustes.
 * Las fotos (de fondo, de widgets o íconos propios) no van: se quedan en el teléfono.
 */
final class Backup {
    static final int REQ_EXPORT = 401, REQ_IMPORT = 402;
    /** Lo que no tiene sentido llevarse: datos del momento o del aparato. */
    private static final String[] SKIP = {"pez_last", "update_checked", "update_tag", "w_cache", "w_time", "use_counts", "apps_share", "pez_key"};

    private Backup() {}

    static void export(Activity a) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "jostin-os-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json");
        try {
            a.startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            Sheet.pill(a, ((MainActivity) a).backdrop(), "No hay dónde guardar archivos");
        }
    }

    static void pick(Activity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        try {
            a.startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            Sheet.pill(a, ((MainActivity) a).backdrop(), "No hay dónde buscar archivos");
        }
    }

    private static boolean skip(String k) {
        for (String s : SKIP) if (k.startsWith(s)) return true;
        return false;
    }

    /** Lo que llega del selector. Devuelve true si era suyo. */
    static boolean onResult(MainActivity a, int req, int res, Intent data) {
        if (req != REQ_EXPORT && req != REQ_IMPORT) return false;
        if (res != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Uri uri = data.getData();
        if (req == REQ_EXPORT) {
            try (OutputStream out = a.getContentResolver().openOutputStream(uri, "wt")) {
                out.write(dump(a).toString(1).getBytes(StandardCharsets.UTF_8));
                Sheet.pill(a, a.backdrop(), "Configuración guardada");
            } catch (Exception e) {
                Bitacora.error("exportar configuración", e);
                Sheet.pill(a, a.backdrop(), "No se pudo guardar el archivo");
            }
        } else {
            try (InputStream in = a.getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                JSONObject o = new JSONObject(b.toString("UTF-8"));
                if (!"jostin-os".equals(o.optString("app"))) throw new Exception("no es un respaldo de Jostin OS");
                Sheet.show(a, a.backdrop(), "¿Cambiar todo por este respaldo?",
                        Sheet.item("Sí, cargarlo", Icons.CHECK, () -> restore(a, o)),
                        Sheet.item("Cancelar", Icons.CLOSE, () -> {}));
            } catch (Exception e) {
                Bitacora.error("importar configuración", e);
                Sheet.pill(a, a.backdrop(), "Ese archivo no es un respaldo de Jostin OS");
            }
        }
        return true;
    }

    private static JSONObject dump(Activity a) throws Exception {
        JSONObject prefs = new JSONObject();
        for (Map.Entry<String, ?> e : Prefs.sp(a).getAll().entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (skip(k) || v == null) continue;
            JSONObject x = new JSONObject();
            if (v instanceof Boolean) x.put("b", v);
            else if (v instanceof Integer) x.put("i", v);
            else if (v instanceof Long) x.put("l", v);
            else if (v instanceof Float) x.put("f", (double) (Float) v);
            else if (v instanceof Set) x.put("set", new JSONArray((Set<?>) v));
            else x.put("s", String.valueOf(v));
            prefs.put(k, x);
        }
        JSONObject o = new JSONObject();
        o.put("app", "jostin-os");
        o.put("version", Bitacora.version(a));
        o.put("fecha", System.currentTimeMillis());
        o.put("prefs", prefs);
        return o;
    }

    private static void restore(MainActivity a, JSONObject o) {
        try {
            JSONObject prefs = o.getJSONObject("prefs");
            // Antes de cambiar nada se guarda una copia del inicio actual, por si acaso
            Safety.backupNow(a, "antes-respaldo-" + System.currentTimeMillis());
            SharedPreferences.Editor ed = Prefs.sp(a).edit();
            for (Iterator<String> it = prefs.keys(); it.hasNext(); ) {
                String k = it.next();
                if (skip(k)) continue;
                JSONObject x = prefs.getJSONObject(k);
                if (x.has("b")) ed.putBoolean(k, x.getBoolean("b"));
                else if (x.has("i")) ed.putInt(k, x.getInt("i"));
                else if (x.has("l")) ed.putLong(k, x.getLong("l"));
                else if (x.has("f")) ed.putFloat(k, (float) x.getDouble("f"));
                else if (x.has("set")) {
                    JSONArray arr = x.getJSONArray("set");
                    Set<String> s = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) s.add(arr.getString(i));
                    ed.putStringSet(k, s);
                } else if (x.has("s")) ed.putString(k, x.getString("s"));
            }
            ed.commit();
            Drawer.invalidateApps();
            Drawer.trim();
            a.recreate();
        } catch (Exception e) {
            Bitacora.error("cargar respaldo", e);
            Sheet.pill(a, a.backdrop(), "No se pudo cargar el respaldo");
        }
    }
}
