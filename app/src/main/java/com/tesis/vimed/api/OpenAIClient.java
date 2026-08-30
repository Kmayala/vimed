package com.tesis.vimed.api;

import android.os.Handler;
import android.os.Looper;

import com.tesis.vimed.BuildConfig;
import com.tesis.vimed.models.MensajeChat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Las dos llamadas que Vita le hace a OpenAI: transcribir un audio y
 * responder un mensaje.
 *
 * UN SOLO PROVEEDOR. Antes el cerebro era de Anthropic y no había forma
 * de transcribir, así que sumar voz obligaba a un segundo servicio.
 * Acá las dos cosas salen de la misma clave y la misma factura. La voz de
 * SALIDA no está: la pone Android, que lo hace gratis, sin conexión y sin
 * ser un servicio más que pueda fallar.
 */
public class OpenAIClient {

    private static final String URL_CHAT  = "https://api.openai.com/v1/chat/completions";
    private static final String URL_AUDIO = "https://api.openai.com/v1/audio/transcriptions";

    /**
     * El modelo sale de BuildConfig y no de una constante para poder
     * cambiarlo sin tocar el código: durante las pruebas hay que correr
     * las mismas preguntas contra varios y comparar.
     */
    private static final String MODELO_CHAT = BuildConfig.OPENAI_MODELO;

    /** El más barato de los de transcripción; el que sigue es whisper-1. */
    private static final String MODELO_AUDIO = "gpt-4o-mini-transcribe";

    /**
     * Los GPT-5 son modelos de razonamiento: los tokens que "piensan"
     * cuentan como salida. Con un techo apretado, el razonamiento se come
     * el presupuesto y la respuesta llega cortada o vacía. 1200 deja aire
     * de sobra para una respuesta de 120 palabras.
     */
    private static final int MAX_TOKENS = 1200;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Perezoso a propósito: construir un Handler necesita el Looper del
     * hilo principal de Android, que en una JVM común no existe. Así el
     * banco de pruebas puede usar responderSync sin arrastrar Android.
     */
    private Handler mainHandler;

    private Handler handler() {
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        return mainHandler;
    }

    public interface Callback {
        void onRespuesta(String respuesta);
        void onError(String error);
    }

    public static boolean hayClave() {
        return BuildConfig.OPENAI_API_KEY != null
            && !BuildConfig.OPENAI_API_KEY.trim().isEmpty();
    }

    // ═══ Chat ══════════════════════════════════════════════════

    /**
     * @param systemPrompt las reglas de Vita más los medicamentos cargados.
     * @param historial    ya recortado por quien llama; acá no se recorta.
     */
    public void enviarMensaje(String systemPrompt, List<MensajeChat> historial,
                              Callback callback) {
        executor.execute(() -> {
            try {
                String texto = responderSync(systemPrompt, historial, MODELO_CHAT);
                if (texto.isEmpty()) {
                    fallar(callback, "El asistente no devolvió respuesta. Probá de nuevo.");
                    return;
                }
                handler().post(() -> callback.onRespuesta(texto));
            } catch (Exception e) {
                fallar(callback, mensajeDeError(e));
            }
        });
    }

    /**
     * La llamada en crudo, sin hilos ni callbacks.
     *
     * Existe aparte para que el banco de pruebas pueda usarla: ese corre en
     * una JVM común, donde el Handler del hilo principal de Android no
     * existe. De paso deja el transporte separado de cómo se despacha, que
     * es lo que corresponde igual.
     *
     * @param modelo se pasa suelto para poder correr las mismas preguntas
     *               contra varios modelos y comparar.
     */
    public static String responderSync(String systemPrompt, List<MensajeChat> historial,
                                String modelo) throws Exception {
        // OpenAI lleva el prompt del sistema como PRIMER MENSAJE del array,
        // no en un campo aparte como hacía Anthropic.
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
            .put("role", "system")
            .put("content", systemPrompt));

        for (MensajeChat msg : historial) {
            if (msg.getContenido() == null || msg.getContenido().isEmpty()) continue;
            messages.put(new JSONObject()
                .put("role", msg.esDelBot() ? "assistant" : "user")
                .put("content", msg.getContenido()));
        }

        JSONObject body = new JSONObject()
            .put("model", modelo)
            // max_completion_tokens y NO max_tokens: la familia GPT-5
            // rechaza el parámetro viejo con un 400.
            .put("max_completion_tokens", MAX_TOKENS)
            .put("messages", messages);

        JSONObject json = new JSONObject(postJson(URL_CHAT, body.toString()));
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim();
    }

    // ═══ Transcripción ═════════════════════════════════════════

    /**
     * Convierte un audio grabado en texto.
     *
     * Se le pasa el idioma fijo en español: sin eso el transcriptor tiene
     * que adivinarlo, y en una frase corta —"me duele la cabeza"— a veces
     * la toma por portugués o italiano y devuelve cualquier cosa.
     */
    public void transcribir(File audio, Callback callback) {
        executor.execute(() -> {
            try {
                String limite = "----vimed" + System.nanoTime();
                HttpURLConnection conn = abrir(URL_AUDIO);
                conn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + limite);

                DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                campo(out, limite, "model", MODELO_AUDIO);
                campo(out, limite, "language", "es");
                archivo(out, limite, audio);
                out.writeBytes("--" + limite + "--\r\n");
                out.flush();
                out.close();

                String cuerpo = leer(conn);
                if (conn.getResponseCode() != 200) {
                    fallar(callback, "No se pudo transcribir el audio ("
                        + conn.getResponseCode() + ")");
                    return;
                }

                String texto = new JSONObject(cuerpo).optString("text", "").trim();
                if (texto.isEmpty()) {
                    fallar(callback, "No se entendió lo que dijiste. Probá de nuevo,"
                        + " más cerca del micrófono.");
                    return;
                }
                handler().post(() -> callback.onRespuesta(texto));

            } catch (Exception e) {
                fallar(callback, mensajeDeError(e));
            }
        });
    }

    // ═══ Plomería HTTP ═════════════════════════════════════════

    private static HttpURLConnection abrir(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.OPENAI_API_KEY);
        conn.setDoOutput(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(45000);
        return conn;
    }

    private static String postJson(String url, String body) throws Exception {
        HttpURLConnection conn = abrir(url);
        conn.setRequestProperty("Content-Type", "application/json");

        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();

        String cuerpo = leer(conn);
        if (conn.getResponseCode() != 200) {
            throw new Exception("HTTP " + conn.getResponseCode() + ": " + recorte(cuerpo));
        }
        return cuerpo;
    }

    private static String leer(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String linea;
        while ((linea = br.readLine()) != null) sb.append(linea);
        br.close();
        return sb.toString();
    }

    private void campo(DataOutputStream out, String limite, String nombre, String valor)
            throws Exception {
        out.writeBytes("--" + limite + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + nombre + "\"\r\n\r\n");
        out.write(valor.getBytes("UTF-8"));
        out.writeBytes("\r\n");
    }

    private void archivo(DataOutputStream out, String limite, File f) throws Exception {
        out.writeBytes("--" + limite + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\""
            + f.getName() + "\"\r\n");
        out.writeBytes("Content-Type: audio/m4a\r\n\r\n");

        FileInputStream in = new FileInputStream(f);
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        in.close();
        out.writeBytes("\r\n");
    }

    private void fallar(Callback cb, String msg) {
        handler().post(() -> cb.onError(msg));
    }

    /** El error, dicho de forma que se pueda actuar. */
    private String mensajeDeError(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) {
            return "El asistente tardó demasiado en responder. Probá de nuevo.";
        }
        if (e instanceof java.net.UnknownHostException) {
            return "No hay conexión a internet.";
        }
        String m = e.getMessage() != null ? e.getMessage() : "";
        if (m.contains("HTTP 401")) return "La clave del asistente no es válida.";
        if (m.contains("HTTP 429")) return "El asistente está saturado. Esperá un momento.";
        return "No se pudo conectar con el asistente.";
    }

    private static String recorte(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
