package com.tesis.vimed.api;

import android.os.Handler;
import android.os.Looper;

import com.tesis.vimed.models.MensajeChat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnthropicClient {

    // TODO: reemplazar con tu API key real de Anthropic
    private static final String API_KEY = "TU_API_KEY_AQUI";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-5";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onRespuesta(String respuesta);
        void onError(String error);
    }

    /**
     * Envía un mensaje al chatbot de Claude.
     * @param systemPrompt Contexto del sistema (medicamentos del usuario, instrucciones)
     * @param historial    Lista de mensajes anteriores de la conversación
     * @param callback     Retorna la respuesta o el error en el hilo principal
     */
    public void enviarMensaje(String systemPrompt, List<MensajeChat> historial, Callback callback) {
        executor.execute(() -> {
            try {
                // Construir array de mensajes (excluir mensajes de bienvenida sin acción de usuario)
                JSONArray messages = new JSONArray();
                for (MensajeChat msg : historial) {
                    // Saltar si el contenido está vacío
                    if (msg.getContenido() == null || msg.getContenido().isEmpty()) continue;
                    JSONObject m = new JSONObject();
                    m.put("role", msg.esDelBot() ? "assistant" : "user");
                    m.put("content", msg.getContenido());
                    messages.put(m);
                }

                // Construir body de la request
                JSONObject body = new JSONObject();
                body.put("model", MODEL);
                body.put("max_tokens", 1024);
                body.put("system", systemPrompt);
                body.put("messages", messages);

                // Conexión HTTP
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("x-api-key", API_KEY);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                if (code == 200) {
                    JSONObject response = new JSONObject(sb.toString());
                    String texto = response.getJSONArray("content")
                        .getJSONObject(0).getString("text");
                    mainHandler.post(() -> callback.onRespuesta(texto));
                } else {
                    mainHandler.post(() -> callback.onError("Error del servidor: " + code));
                }

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Error de conexión: " + e.getMessage()));
            }
        });
    }
}
