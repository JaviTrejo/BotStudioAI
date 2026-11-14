package com.botstudio.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import org.json.*;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private JSONArray bots = new JSONArray();
    private JSONArray tasks = new JSONArray();

    private SharedPreferences store;
    private LinearLayout content;

    private final int bg = Color.rgb(13, 19, 35);
    private final int fg = Color.WHITE;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        store = getSharedPreferences(
            "botstudio",
            MODE_PRIVATE
        );

        try {
            bots = new JSONArray(
                store.getString("bots", "[]")
            );
        } catch (Exception ignored) {}

        try {
            tasks = new JSONArray(
                store.getString("tasks", "[]")
            );
        } catch (Exception ignored) {}

        render();
    }

    private int dp(int n) {
        return (int) (
            n * getResources()
                .getDisplayMetrics()
                .density + .5f
        );
    }

    private void save() {
        store.edit()
            .putString("bots", bots.toString())
            .putString("tasks", tasks.toString())
            .apply();
    }

    private TextView txt(String s, int size) {
        TextView v = new TextView(this);

        v.setText(s);
        v.setTextColor(fg);
        v.setTextSize(size);
        v.setPadding(
            0, dp(7), 0, dp(7)
        );

        return v;
    }

    private void button(
        LinearLayout box,
        String label,
        View.OnClickListener cb
    ) {
        Button b = new Button(this);

        b.setAllCaps(false);
        b.setText(label);
        b.setOnClickListener(cb);

        box.addView(
            b,
            new LinearLayout.LayoutParams(
                -1, -2
            )
        );
    }

    private EditText field(
        String hint,
        String value
    ) {
        EditText e = new EditText(this);

        e.setSingleLine(false);
        e.setHint(hint);
        e.setText(value);

        return e;
    }

    private void render() {

        ScrollView scroll =
            new ScrollView(this);

        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        content =
            new LinearLayout(this);

        content.setOrientation(
            LinearLayout.VERTICAL
        );

        content.setPadding(
            dp(18),
            dp(22),
            dp(18),
            dp(24)
        );

        content.setOnApplyWindowInsetsListener(
            (v, insets) -> {

                v.setPadding(
                    dp(18),
                    insets.getSystemWindowInsetTop()
                        + dp(14),
                    dp(18),
                    insets.getSystemWindowInsetBottom()
                        + dp(24)
                );

                return insets;
            }
        );

        scroll.addView(content);
        setContentView(scroll);

        content.addView(
            txt("🤖 BotStudio AI", 27)
        );

        content.addView(
            txt("Tus bots y tareas en Android", 15)
        );

        button(
            content,
            "+ Crear bot",
            v -> createBot()
        );

        button(
            content,
            "+ Asignar tarea",
            v -> createTask()
        );

        button(
            content,
            "⚙ Configurar clave de Gemini",
            v -> apiDialog()
        );

        content.addView(
            txt(
                "BOTS (" + bots.length() + ")",
                19
            )
        );

        if (bots.length() == 0) {
            content.addView(
                txt(
                    "Crea tu primer bot para empezar.",
                    15
                )
            );
        }

        for (int i = 0; i < bots.length(); i++) {

            JSONObject bot =
                bots.optJSONObject(i);

            if (bot == null) continue;

            String n =
                bot.optString("name");

            String ins =
                bot.optString("instructions");

            content.addView(
                txt(
                    "• " + n + "\n   " + ins,
                    15
                )
            );
        }

        content.addView(
            txt(
                "TAREAS (" + tasks.length() + ")",
                19
            )
        );

        if (tasks.length() == 0) {
            content.addView(
                txt(
                    "Todavía no hay tareas asignadas.",
                    15
                )
            );
        }

        for (int i = 0; i < tasks.length(); i++) {

            JSONObject item =
                tasks.optJSONObject(i);

            if (item == null) continue;

            final int index = i;

            content.addView(
                txt(
                    "📋 " + item.optString("title")
                        + "\nBot: "
                        + item.optString("bot")
                        + " | "
                        + item.optString(
                            "status",
                            "Pendiente"
                        ),
                    15
                )
            );

            button(
                content,
                "▶ Ejecutar tarea",
                v -> execute(index)
            );

            if (!item.optString("result").isEmpty()) {

                button(
                    content,
                    "Ver resultado",
                    v -> new AlertDialog.Builder(this)
                        .setTitle("Resultado")
                        .setMessage(
                            item.optString("result")
                        )
                        .setPositiveButton(
                            "Cerrar",
                            null
                        )
                        .show()
                );
            }
        }
    }

    private void createBot() {

        LinearLayout form =
            new LinearLayout(this);

        form.setOrientation(1);

        form.setPadding(
            dp(20), 0, dp(20), 0
        );

        EditText name =
            field("Nombre del bot", "");

        EditText instructions =
            field(
                "Instrucciones del bot",
                "Eres un asistente útil. Responde en español."
            );

        form.addView(name);
        form.addView(instructions);

        new AlertDialog.Builder(this)
            .setTitle("Crear bot")
            .setView(form)
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "Guardar",
                (d, w) -> {

                    String n = name
                        .getText()
                        .toString()
                        .trim();

                    if (n.isEmpty()) {
                        toast("Escribe un nombre");
                        return;
                    }

                    try {
                        bots.put(
                            new JSONObject()
                                .put("name", n)
                                .put(
                                    "instructions",
                                    instructions
                                        .getText()
                                        .toString()
                                )
                        );
                    } catch (Exception ignored) {}

                    save();
                    render();
                }
            )
            .show();
    }

    private void createTask() {

        if (bots.length() == 0) {
            toast("Crea un bot primero");
            return;
        }

        LinearLayout form =
            new LinearLayout(this);

        form.setOrientation(1);

        form.setPadding(
            dp(20), 0, dp(20), 0
        );

        EditText task =
            field(
                "¿Qué trabajo debe realizar?",
                ""
            );

        Spinner spinner =
            new Spinner(this);

        String[] names =
            new String[bots.length()];

        for (int i = 0; i < bots.length(); i++) {
            names[i] = bots
                .optJSONObject(i)
                .optString("name");
        }

        spinner.setAdapter(
            new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                names
            )
        );

        form.addView(task);
        form.addView(spinner);

        new AlertDialog.Builder(this)
            .setTitle("Asignar tarea")
            .setView(form)
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "Guardar",
                (d, w) -> {

                    String title = task
                        .getText()
                        .toString()
                        .trim();

                    if (title.isEmpty()) {
                        toast("Escribe la tarea");
                        return;
                    }

                    try {
                        tasks.put(
                            new JSONObject()
                                .put("title", title)
                                .put(
                                    "bot",
                                    names[
                                        spinner.getSelectedItemPosition()
                                    ]
                                )
                                .put("status", "Pendiente")
                                .put("result", "")
                        );
                    } catch (Exception ignored) {}

                    save();
                    render();
                }
            )
            .show();
    }

    private void apiDialog() {

        EditText key = field(
            "Clave API de Gemini",
            store.getString("api_key", "")
        );

        key.setInputType(
            InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        new AlertDialog.Builder(this)
            .setTitle("Clave API personal")
            .setMessage(
                "Se guarda localmente en este teléfono. "
                    + "No la compartas ni la publiques en GitHub."
            )
            .setView(key)
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "Guardar",
                (d, w) -> {

                    store.edit()
                        .putString(
                            "api_key",
                            key.getText()
                                .toString()
                                .trim()
                        )
                        .apply();

                    toast("Clave guardada");
                }
            )
            .show();
    }

    private void execute(int index) {

        String key = store
            .getString("api_key", "")
            .trim();

        if (key.isEmpty()) {
            apiDialog();
            return;
        }

        JSONObject task =
            tasks.optJSONObject(index);

        if (task == null) return;

        String ins =
            "Eres un asistente útil. Responde en español.";

        for (int i = 0; i < bots.length(); i++) {

            JSONObject b =
                bots.optJSONObject(i);

            if (
                b != null
                && b.optString("name").equals(
                    task.optString("bot")
                )
            ) {
                ins = b.optString("instructions");
                break;
            }
        }

        String prompt =
            ins
                + "\n\nTarea asignada: "
                + task.optString("title");

        try {
            task.put("status", "Ejecutando");
            task.put("result", "");
        } catch (Exception ignored) {}

        save();
        render();

        new Thread(() -> {

            String answer;
            boolean success = true;

            HttpURLConnection conn = null;

            try {
                URL url = new URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
                );

                conn =
                    (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");

                conn.setRequestProperty(
                    "Content-Type",
                    "application/json"
                );

                conn.setRequestProperty(
                    "x-goog-api-key",
                    key
                );

                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setDoOutput(true);

                JSONObject body =
                    new JSONObject().put(
                        "contents",
                        new JSONArray().put(
                            new JSONObject().put(
                                "parts",
                                new JSONArray().put(
                                    new JSONObject().put(
                                        "text",
                                        prompt
                                    )
                                )
                            )
                        )
                    );

                try (
                    OutputStream os =
                        conn.getOutputStream()
                ) {
                    os.write(
                        body.toString().getBytes(
                            StandardCharsets.UTF_8
                        )
                    );
                }

                int status =
                    conn.getResponseCode();

                InputStream stream =
                    status >= 200 && status < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                StringBuilder raw =
                    new StringBuilder();

                if (stream != null) {
                    try (
                        BufferedReader br =
                            new BufferedReader(
                                new InputStreamReader(
                                    stream,
                                    StandardCharsets.UTF_8
                                )
                            )
                    ) {
                        String line;

                        while (
                            (line = br.readLine()) != null
                        ) {
                            raw.append(line);
                        }
                    }
                }

                if (status < 200 || status >= 300) {
                    throw new IOException(
                        "HTTP " + status + ": " + raw
                    );
                }

                answer =
                    new JSONObject(raw.toString())
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");

            } catch (Exception e) {

                success = false;
                answer = "Error: " + e.getMessage();

            } finally {

                if (conn != null) {
                    conn.disconnect();
                }
            }

            final String result = answer;

            final String state =
                success ? "Completada" : "Error";

            runOnUiThread(() -> {

                JSONObject t =
                    tasks.optJSONObject(index);

                if (t == null) return;

                try {
                    t.put("result", result);
                    t.put("status", state);
                } catch (Exception ignored) {}

                save();
                render();

                new AlertDialog.Builder(this)
                    .setTitle(state)
                    .setMessage(result)
                    .setPositiveButton(
                        "Cerrar",
                        null
                    )
                    .show();
            });

        }).start();
    }

    private void toast(String s) {
        Toast.makeText(
            this,
            s,
            Toast.LENGTH_SHORT
        ).show();
    }
}
