package com.jostin.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

/** Pestaña «Temas» del estudio: guardar, aplicar con vista previa, compartir y pedirle uno a Jarvis. */
final class ThemeStudio {
    private ThemeStudio() {}

    static void fill(Activity act, Studio.Host h, LinearLayout col) {
        if (!(act instanceof MainActivity)) return;
        MainActivity a = (MainActivity) act;
        col.addView(Ctl.section(a, "Tu look"));
        col.addView(Ctl.action(a, Icons.PLUS, "Guardar mi look como tema", null, v -> askSave(a)));
        if (Themes.canUndo(a)) {
            col.addView(gap(a));
            col.addView(Ctl.action(a, Icons.HISTORY, "Volver al look anterior", null, v -> Sheet.dismiss(a, () -> {
                Themes.undo(a);
                Sheet.pill(a, a.backdrop(), "Regresó tu look anterior");
            })));
        }

        List<JSONObject> mine = Themes.saved(a);
        if (!mine.isEmpty()) {
            col.addView(Ctl.section(a, "Tus temas"));
            grid(a, col, mine, true);
        }

        col.addView(Ctl.section(a, "Listos para usar"));
        grid(a, col, Themes.presets(), false);

        col.addView(Ctl.section(a, "Jarvis te diseña uno"));
        col.addView(Ctl.action(a, Icons.SPARK, "Describir mi tema ideal", "Claude", v -> askJarvis(a)));
        col.addView(gap(a));
        col.addView(Ctl.action(a, Icons.SWAP, "Pegar lo que respondió Jarvis", null, v -> paste(a)));
        TextView tip = Ui.text(a, "Jarvis te contesta con un texto que empieza con { . Cópialo completo, regresa aquí y pégalo. También sirve para temas que te comparta alguien más. Solo se aceptan ajustes de esta lista; nada se instala ni se ejecuta.", 12, Ui.MUTED, Ui.REGULAR);
        tip.setLineSpacing(0, 1.2f);
        tip.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0);
        col.addView(tip);
    }

    private static View gap(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(c, 8)));
        return v;
    }

    private static void grid(MainActivity a, LinearLayout col, List<JSONObject> list, boolean mine) {
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(a);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) rl.topMargin = Ui.dp(a, 10);
                col.addView(row, rl);
            }
            JSONObject t = list.get(i);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 2 == 1) cl.leftMargin = Ui.dp(a, 10);
            row.addView(card(a, t, mine), cl);
        }
        if (row != null && row.getChildCount() == 1) {
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, 1, 1f);
            cl.leftMargin = Ui.dp(a, 10);
            row.addView(new View(a), cl);
        }
    }

    private static View card(MainActivity a, JSONObject t, boolean mine) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        Preview p = new Preview(a, t);
        c.addView(p, new LinearLayout.LayoutParams(-1, Ui.dp(a, 150)));
        TextView name = Ui.text(a, t.optString("name", "Tema"), 14, Ui.TEXT, Ui.SEMIBOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(a, 8);
        c.addView(name, nl);
        Ui.pressable(c, 0.96f);
        c.setOnClickListener(v -> {
            Sound.play(v, Sound.TAP);
            actions(a, t, mine);
        });
        return c;
    }

    private static void actions(MainActivity a, JSONObject t, boolean mine) {
        String name = t.optString("name", "Tema");
        Sheet.Item apply = Sheet.item("Probarlo", Icons.SPARK, () -> tryOn(a, t, false));
        if (!mine) {
            Sheet.show(a, a.backdrop(), name, apply,
                    Sheet.item("Guardarlo en mis temas", Icons.PLUS, () -> {
                        Themes.duplicate(a, t);
                        Sheet.pill(a, a.backdrop(), "Quedó en tus temas");
                    }));
            return;
        }
        String id = t.optString("id");
        Sheet.show(a, a.backdrop(), name, apply,
                Sheet.item("Duplicar", Icons.PLUS, () -> {
                    Themes.duplicate(a, t);
                    Studio.show(a, a, Studio.TAB_THEMES);
                }),
                Sheet.item("Cambiar nombre", Gadgets.TEXT, () -> Sheet.prompt(a, a.backdrop(), "Nombre del tema", "Ejemplo: Noche morada", name, v -> {
                    if (v.isEmpty()) return;
                    Themes.rename(a, id, v);
                    Studio.show(a, a, Studio.TAB_THEMES);
                })),
                Sheet.item("Compartir", Icons.SWAP, () -> share(a, t)),
                Sheet.item("Borrar", Icons.TRASH, () -> Sheet.confirm(a, a.backdrop(), "¿Borrar «" + name + "»?", null, "Borrar", true, () -> {
                    Themes.delete(a, id);
                    Studio.show(a, a, Studio.TAB_THEMES);
                })).danger());
    }

    /** Aplica el tema y pregunta si te lo quedas; si no, regresa todo como estaba. */
    static void tryOn(MainActivity a, JSONObject t, boolean offerSave) {
        JSONObject clean = Themes.clean(a, t);
        if (clean == null) {
            Sheet.pill(a, a.backdrop(), "Ese tema no se pudo leer");
            return;
        }
        Sheet.dismiss(a, null);
        Themes.apply(a, clean);
        a.handler.postDelayed(() -> Sheet.confirm(a, a.backdrop(), "¿Te quedas con «" + clean.optString("name", "este tema") + "»?",
                "Así se ve tu Jostin OS con este tema. Si no te gusta, todo regresa a como estaba.", "Me lo quedo", false, () -> {
                    if (offerSave) {
                        Themes.save(a, clean);
                        Sheet.pill(a, a.backdrop(), "Listo, también quedó en tus temas");
                    } else {
                        Sheet.pill(a, a.backdrop(), "Listo");
                    }
                }, () -> {
                    Themes.undo(a);
                    Sheet.pill(a, a.backdrop(), "Regresó tu look anterior");
                }), 700);
    }

    private static void askSave(MainActivity a) {
        Sheet.show(a, a.backdrop(), "¿Qué guardo?",
                Sheet.item("Solo el look", Icons.IMAGE, () -> nameAndSave(a, false)),
                Sheet.item("El look y el acomodo de inicio", Icons.SHAPES, () -> nameAndSave(a, true)));
    }

    private static void nameAndSave(MainActivity a, boolean layout) {
        Sheet.prompt(a, a.backdrop(), "Nombre del tema", "Ejemplo: Mi noche", "", v -> {
            String name = v.isEmpty() ? "Mi tema" : v;
            Themes.save(a, Themes.capture(a, name, layout));
            Sheet.pill(a, a.backdrop(), "«" + name + "» quedó guardado");
            a.handler.postDelayed(() -> Studio.show(a, a, Studio.TAB_THEMES), 400);
        });
    }

    private static void share(MainActivity a, JSONObject t) {
        String text = Themes.export(t);
        Themes.copy(a, "Tema de Jostin OS", text);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Tema de Jostin OS: " + t.optString("name", ""));
        i.putExtra(Intent.EXTRA_TEXT, text);
        try {
            a.startActivity(Intent.createChooser(i, "Compartir tema"));
        } catch (Exception e) {
            Sheet.pill(a, a.backdrop(), "Lo copié; pégalo donde quieras");
        }
    }

    private static void askJarvis(MainActivity a) {
        Sheet.prompt(a, a.backdrop(), "¿Cómo te imaginas tu tema?", "Ejemplo: oscuro, morado, reloj grande, que se vea futurista", "", true, idea -> {
            if (idea.isEmpty()) return;
            String prompt = Themes.jarvisPrompt(idea);
            Themes.copy(a, "Pedido para Jarvis", prompt);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, prompt);
            i.setPackage(MainActivity.CLAUDE_PKG);
            try {
                a.startActivity(i);
                Sheet.pill(a, a.backdrop(), "Cuando Jarvis conteste, copia su respuesta y pégala aquí");
                return;
            } catch (Exception ignored) {
                // la app de Claude no recibe textos compartidos; se abre normal con el texto copiado
            }
            Intent open = a.getPackageManager().getLaunchIntentForPackage(MainActivity.CLAUDE_PKG);
            if (open != null) {
                a.startActivity(open);
                Sheet.pill(a, a.backdrop(), "Copié el pedido; pégalo en Claude");
            } else {
                Sheet.pill(a, a.backdrop(), "Copié el pedido. Instala Claude o pégalo en otro asistente");
            }
        });
    }

    private static void paste(MainActivity a) {
        String text = Themes.clipboard(a);
        JSONObject t = Themes.parse(a, text);
        if (t == null) {
            Sheet.pill(a, a.backdrop(), text == null ? "No hay nada copiado" : "Lo copiado no es un tema de Jostin OS");
            return;
        }
        tryOn(a, t, true);
    }

    /** Vista previa en miniatura: el fondo del tema, su reloj y su color de acento. */
    static final class Preview extends View {
        private final JSONObject prefs;
        private Bitmap bmp;
        private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG), dim = new Paint();
        private final Path clip = new Path();
        private final Matrix m = new Matrix();
        private final int accent;

        Preview(Context c, JSONObject theme) {
            super(c);
            JSONObject p = theme.optJSONObject("prefs");
            prefs = p != null ? p : new JSONObject();
            accent = Themes.previewAccent(theme);
            int scene = Math.max(0, Math.min(Scenes.COUNT - 1, prefs.optInt("wall_scene", Prefs.wallScene(c))));
            Context app = c.getApplicationContext();
            Gadgets.BG.execute(() -> {
                Bitmap b = Wallpaper.sceneThumb(app, scene);
                Gadgets.MAIN.post(() -> {
                    bmp = b;
                    invalidate();
                });
            });
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            clip.reset();
            float r = Ui.dp(getContext(), 18);
            clip.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            c.save();
            c.clipPath(clip);
            if (bmp != null && !bmp.isRecycled()) {
                float sc = Math.max(w / bmp.getWidth(), h / bmp.getHeight());
                m.setScale(sc, sc);
                m.postTranslate((w - bmp.getWidth() * sc) / 2f, (h - bmp.getHeight() * sc) / 2f);
                c.drawBitmap(bmp, m, img);
            } else {
                c.drawColor(0x1AFFFFFF);
            }
            int dimPct = prefs.optInt("wall_dim", 0);
            if (dimPct > 0) {
                dim.setColor(Ui.alpha(0xFF000000, dimPct * 255 / 100));
                c.drawRect(0, 0, w, h, dim);
            }
            // Reloj con la letra y el estilo del tema
            int style = prefs.optInt("clock_style", 0), font = prefs.optInt("clock_font", 0);
            boolean left = prefs.optInt("clock_align", 0) == 1;
            int col = prefs.optInt("clock_color", 0) == 1 ? accent : 0xFFF5F6F8;
            txt.setTypeface(Ui.clockFont(font));
            txt.setColor(col);
            txt.setShadowLayer(6 * d, 0, 0, 0x66000000);
            String t = style == 0 ? "VIERNES" : "12:45";
            float size = style == 0 ? 15 * d : style == 1 ? 30 * d : 26 * d;
            txt.setTextSize(size);
            txt.setLetterSpacing(style == 0 ? 0.25f : 0f);
            float tw = txt.measureText(t);
            if (tw > w - 16 * d) {
                txt.setTextSize(size * (w - 16 * d) / tw);
                tw = txt.measureText(t);
            }
            float x = left ? 12 * d : (w - tw) / 2f;
            if (style == 2) {
                c.drawText("12", x, h * 0.34f, txt);
                txt.setColor(Ui.alpha(col, 0x99));
                c.drawText("45", x, h * 0.34f + txt.getTextSize() * 0.92f, txt);
            } else {
                c.drawText(t, x, h * 0.32f, txt);
            }
            // Dock: puntos con el color de acento
            boolean glass = prefs.optBoolean("dock_glass", true);
            float dy = h - 18 * d;
            if (glass) {
                dot.setColor(0x40FFFFFF);
                c.drawRoundRect(w * 0.12f, dy - 10 * d, w * 0.88f, dy + 10 * d, 10 * d, 10 * d, dot);
            }
            int n = 4;
            for (int i = 0; i < n; i++) {
                float cx = w * (0.22f + i * 0.56f / (n - 1));
                dot.setColor(i == 0 ? accent : 0xCCFFFFFF);
                c.drawCircle(cx, dy, 5.5f * d, dot);
            }
            c.restore();
            dot.setColor(Ui.alpha(accent, 0xAA));
            dot.setStyle(Paint.Style.STROKE);
            dot.setStrokeWidth(1.2f * d);
            c.drawPath(clip, dot);
            dot.setStyle(Paint.Style.FILL);
        }
    }
}
