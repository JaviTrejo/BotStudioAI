
package com.botstudio.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private JSONArray bots = new JSONArray();
    private JSONArray tasks = new JSONArray();
    private SharedPreferences store;
    private LinearLayout screen;
    private final Set<String> running = new HashSet<>();
    private static final int BG = Color.rgb(13, 19, 35);
    private static final int PANEL = Color.rgb(27, 38, 61);
    private static final String API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = getSharedPreferences("botstudio", MODE_PRIVATE);
        try { bots = new JSONArray(store.getString("bots", "[]")); } catch (Exception ignored) {}
        try { tasks = new JSONArray(store.getString("tasks", "[]")); } catch (Exception ignored) {}
        // Compatible con los datos guardados por la versión 1.0.
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject t = tasks.optJSONObject(i);
            if (t == null) continue;
            try {
                if (t.optString("id").isEmpty()) t.put("id", UUID.randomUUID().toString());
                if ("Ejecutando".equals(t.optString("status"))) t.put("status", "Interrumpida");
            } catch (Exception ignored) {}
        }
        save();
        render();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private void save() { store.edit().putString("bots", bots.toString()).putString("tasks", tasks.toString()).apply(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private TextView text(String message, int size) {
        TextView v = new TextView(this);
        v.setText(message);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        v.setPadding(0, dp(6), 0, dp(6));
        return v;
    }
    private Button button(LinearLayout parent, String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setOnClickListener(click);
        parent.addView(b, new LinearLayout.LayoutParams(-1, -2));
        return b;
    }
    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(false);
        return e;
    }
    private LinearLayout form() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(20), dp(4), dp(20), dp(4));
        return v;
    }
    private LinearLayout panel() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(13), dp(9), dp(13), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        bg.setCornerRadius(dp(14));
        v.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(12));
        screen.addView(v, p);
        return v;
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(18), dp(24), dp(18), dp(30));
        screen.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(18), insets.getSystemWindowInsetTop() + dp(18), dp(18),
                insets.getSystemWindowInsetBottom() + dp(24));
            return insets;
        });
        scroll.addView(screen);
        setContentView(scroll);

        screen.addView(text("🤖 BotStudio AI 2.0", 26));
        screen.addView(text("Organiza tus bots y tareas en Android", 14));
        button(screen, "+ Crear bot", v -> editBot(-1));
        button(screen, "+ Asignar tarea", v -> editTask(-1));
        button(screen, "⚙ Clave de Gemini", v -> apiDialog());
        button(screen, "📤 Compartir resultados", v -> shareResults());

        screen.addView(text("BOTS (" + bots.length() + ")", 20));
        if (bots.length() == 0) screen.addView(text("Crea tu primer bot.", 15));
        for (int i = 0; i < bots.length(); i++) {
            final int index = i;
            JSONObject b = bots.optJSONObject(i);
            if (b == null) continue;
            LinearLayout p = panel();
            p.addView(text("🤖 " + b.optString("name"), 18));
            p.addView(text(b.optString("instructions"), 14));
            button(p, "Editar bot", v -> editBot(index));
            button(p, "Eliminar bot", v -> deleteBot(index));
        }

        screen.addView(text("TAREAS (" + tasks.length() + ")", 20));
        if (tasks.length() == 0) screen.addView(text("Todavía no hay tareas.", 15));
        for (int i = tasks.length() - 1; i >= 0; i--) {
            final int index = i;
            JSONObject t = tasks.optJSONObject(i);
            if (t == null) continue;
            LinearLayout p = panel();
            p.addView(text("📋 " + t.optString("title"), 17));
            p.addView(text("Bot: " + t.optString("bot") + "  ·  " +
                t.optString("status", "Pendiente"), 14));
            boolean busy = running.contains(t.optString("id"));
            Button run = button(p, busy ? "Ejecutando…" : "▶ Ejecutar tarea", v -> execute(index));
            run.setEnabled(!busy);
            if (!t.optString("result").isEmpty()) {
                button(p, "Ver resultado", v -> new AlertDialog.Builder(this)
                    .setTitle("Resultado").setMessage(t.optString("result"))
                    .setPositiveButton("Cerrar", null).show());
            }
            button(p, "Editar tarea", v -> editTask(index));
            button(p, "Eliminar tarea", v -> deleteTask(index));
        }
    }

    private boolean duplicateBot(String name, int except) {
        for (int i = 0; i < bots.length(); i++) {
            JSONObject b = bots.optJSONObject(i);
            if (b != null && i != except && name.equalsIgnoreCase(b.optString("name"))) return true;
        }
        return false;
    }
    private void editBot(int index) {
        JSONObject old = index < 0 ? null : bots.optJSONObject(index);
        if (index >= 0 && old == null) return;
        LinearLayout f = form();
        EditText name = field("Nombre del bot", old == null ? "" : old.optString("name"));
        EditText instructions = field("Instrucciones", old == null ?
            "Eres un asistente útil. Responde en español." : old.optString("instructions"));
        f.addView(name);
        f.addView(instructions);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(old == null ? "Crear bot" : "Editar bot")
            .setView(f).setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { toast("El nombre es obligatorio"); return; }
            if (duplicateBot(n, index)) { toast("Ya existe un bot con ese nombre"); return; }
            String oldName = old == null ? "" : old.optString("name");
            try {
                if (old == null) bots.put(new JSONObject().put("name", n)
                    .put("instructions", instructions.getText().toString()));
                else {
                    old.put("name", n);
                    old.put("instructions", instructions.getText().toString());
                    for (int i = 0; i < tasks.length(); i++) {
                        JSONObject t = tasks.optJSONObject(i);
                        if (t != null && oldName.equals(t.optString("bot"))) t.put("bot", n);
                    }
                }
            } catch (Exception e) { toast("No se pudo guardar"); return; }
            save(); dialog.dismiss(); render();
        }));
        dialog.show();
    }
    private void deleteBot(int index) {
        JSONObject bot = bots.optJSONObject(index);
        if (bot == null) return;
        new AlertDialog.Builder(this).setTitle("Eliminar bot")
            .setMessage("¿Eliminar '" + bot.optString("name") + "'? Las tareas guardadas permanecerán, pero tendrás que asignarles otro bot para ejecutarlas.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (d, w) -> { bots.remove(index); save(); render(); }).show();
    }

    private void editTask(int index) {
        if (bots.length() == 0) { toast("Crea un bot primero"); return; }
        JSONObject old = index < 0 ? null : tasks.optJSONObject(index);
        if (index >= 0 && old == null) return;
        LinearLayout f = form();
        EditText title = field("¿Qué debe hacer el bot?", old == null ? "" : old.optString("title"));
        Spinner spinner = new Spinner(this);
        String[] names = new String[bots.length()];
        int selected = 0;
        for (int i = 0; i < bots.length(); i++) {
            JSONObject b = bots.optJSONObject(i);
            names[i] = b == null ? "" : b.optString("name");
            if (old != null && names[i].equals(old.optString("bot"))) selected = i;
        }
        spinner.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, names));
        spinner.setSelection(selected);
        f.addView(title); f.addView(spinner);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(old == null ? "Asignar tarea" : "Editar tarea")
            .setView(f).setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            if (t.isEmpty()) { toast("Escribe la tarea"); return; }
            if (old != null && running.contains(old.optString("id"))) {
                toast("Espera a que termine la ejecución"); return;
            }
            try {
                if (old == null) tasks.put(new JSONObject()
                    .put("id", UUID.randomUUID().toString()).put("title", t)
                    .put("bot", names[spinner.getSelectedItemPosition()])
                    .put("status", "Pendiente").put("result", ""));
                else old.put("title", t).put("bot", names[spinner.getSelectedItemPosition()])
                    .put("status", "Pendiente").put("result", "");
            } catch (Exception e) { toast("No se pudo guardar la tarea"); return; }
            save(); dialog.dismiss(); render();
        }));
        dialog.show();
    }
    private void deleteTask(int index) {
        JSONObject t = tasks.optJSONObject(index);
        if (t == null) return;
        new AlertDialog.Builder(this).setTitle("Eliminar tarea")
            .setMessage("¿Eliminar esta tarea y su resultado guardado?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (d, w) -> { tasks.remove(index); save(); render(); }).show();
    }

    private void apiDialog() {
        EditText key = field("Clave de Gemini", store.getString("api_key", ""));
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Clave API de Gemini")
            .setMessage("La clave se guarda en las preferencias privadas de esta aplicación. No la publiques en GitHub.")
            .setView(key).setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (d, w) -> {
                store.edit().putString("api_key", key.getText().toString().trim()).apply();
                toast("Clave guardada");
            }).show();
    }
    private JSONObject findTask(String id) {
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject t = tasks.optJSONObject(i);
            if (t != null && id.equals(t.optString("id"))) return t;
        }
        return null;
    }
    private void execute(int index) {
        JSONObject task = tasks.optJSONObject(index);
        if (task == null) return;
        String id = task.optString("id");
        if (running.contains(id)) return;
        String key = store.getString("api_key", "").trim();
        if (key.isEmpty()) { apiDialog(); return; }
        JSONObject bot = null;
        for (int i = 0; i < bots.length(); i++) {
            JSONObject b = bots.optJSONObject(i);
            if (b != null && b.optString("name").equals(task.optString("bot"))) { bot = b; break; }
        }
        if (bot == null) { toast("El bot ya no existe. Edita la tarea y asigna otro."); return; }
        String prompt = bot.optString("instructions") + "\n\nTarea asignada: " + task.optString("title");
        try { task.put("status", "Ejecutando").put("result", ""); } catch (Exception ignored) {}
        running.add(id); save(); render();
        new Thread(() -> {
            boolean success = true;
            String answer;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(API_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("x-goog-api-key", key);
                conn.setConnectTimeout(15000); conn.setReadTimeout(60000);
                conn.setDoOutput(true);
                JSONObject body = new JSONObject().put("contents", new JSONArray().put(
                    new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int status = conn.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ?
                    conn.getInputStream() : conn.getErrorStream();
                StringBuilder raw = new StringBuilder();
                if (stream != null) try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) raw.append(line);
                }
                if (status < 200 || status >= 300) throw new IOException("HTTP " + status + ": " + raw);
                answer = new JSONObject(raw.toString()).getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text");
            } catch (Exception e) { success = false; answer = "Error: " + e.getMessage(); }
            finally { if (conn != null) conn.disconnect(); }
            final String result = answer;
            final String state = success ? "Completada" : "Error";
            runOnUiThread(() -> {
                running.remove(id);
                JSONObject current = findTask(id);
                if (current == null) { render(); return; }
                try { current.put("result", result).put("status", state); } catch (Exception ignored) {}
                save(); render();
                if (!isFinishing()) new AlertDialog.Builder(this)
                    .setTitle(state).setMessage(result).setPositiveButton("Cerrar", null).show();
            });
        }).start();
    }
    private void shareResults() {
        StringBuilder out = new StringBuilder("BotStudio AI — Resultados\n\n");
        int count = 0;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject t = tasks.optJSONObject(i);
            if (t == null || t.optString("result").isEmpty()) continue;
            count++;
            out.append("Tarea: ").append(t.optString("title"))
                .append("\nBot: ").append(t.optString("bot"))
                .append("\nEstado: ").append(t.optString("status"))
                .append("\nResultado:\n").append(t.optString("result")).append("\n\n");
        }
        if (count == 0) { toast("Todavía no tienes resultados guardados"); return; }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, out.toString());
        startActivity(Intent.createChooser(intent, "Compartir resultados"));
    }
}
 
