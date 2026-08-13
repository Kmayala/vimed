package com.tesis.vimed.api;

import android.content.Context;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;
import com.tesis.vimed.BuildConfig;
import com.tesis.vimed.SessionManager;
import com.tesis.vimed.models.Dispositivo;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Callback;

/**
 * Push al cuidador (Firebase Cloud Messaging).
 *
 * Dos responsabilidades:
 *
 *  1. {@link #registrarToken} — guarda en Supabase el token de ESTE celular,
 *     para que la Edge Function sepa a dónde mandarle los avisos.
 *
 *  2. {@link #avisarACuidadores} — le pide a la Edge Function que despache un
 *     push a los familiares vinculados. La app NUNCA habla directo con FCM:
 *     eso exigiría la clave del servidor dentro del APK, donde cualquiera
 *     podría extraerla y mandar notificaciones en nombre de Vimed.
 *
 * Todo es "fire and forget": si falla, el evento igual quedó registrado en
 * la tabla notificaciones y el cuidador lo va a ver al abrir la app.
 */
public final class PushManager {

    private static final String TAG = "PushManager";

    /** Nombre de la función desplegada en Supabase. */
    private static final String EDGE_FUNCTION = "notificar-cuidador";

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final OkHttpClient HTTP = new OkHttpClient();

    private PushManager() {}

    // ═══ 1. Registrar el token de este celular ═════════════════

    /**
     * Pide el token a Firebase y lo sube a Supabase.
     * Llamar después de cada login y al arrancar la app.
     */
    public static void registrarToken(Context ctx) {
        final Context appCtx = ctx.getApplicationContext();
        try {
            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.w(TAG, "Sin token de FCM todavía", task.getException());
                        return;
                    }
                    guardarToken(appCtx, task.getResult());
                });
        } catch (Throwable t) {
            // Pasa si falta google-services.json: Firebase no llega a
            // inicializarse. La app funciona igual, solo sin push.
            Log.w(TAG, "Firebase no disponible: push deshabilitado", t);
        }
    }

    /** Sube el token a public.dispositivos (upsert por token). */
    public static void guardarToken(Context ctx, String token) {
        int idUsuario = new SessionManager(ctx).getSupabaseIdUsuario();
        if (idUsuario == -1 || token == null || token.isEmpty()) return;

        SupabaseClient.getService()
            .registrarDispositivo("resolution=merge-duplicates",
                new Dispositivo(idUsuario, token))
            .enqueue(new Callback<List<Dispositivo>>() {
                @Override public void onResponse(Call<List<Dispositivo>> c,
                                                 retrofit2.Response<List<Dispositivo>> r) {
                    if (!r.isSuccessful()) {
                        Log.w(TAG, "No se pudo registrar el dispositivo: " + r.code());
                    }
                }
                @Override public void onFailure(Call<List<Dispositivo>> c, Throwable t) {
                    Log.w(TAG, "Sin red al registrar el dispositivo", t);
                }
            });
    }

    /** Al cerrar sesión: este celular deja de recibir avisos de esa persona. */
    public static void borrarToken(Context ctx) {
        try {
            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) return;
                    SupabaseClient.getService()
                        .eliminarDispositivo("eq." + task.getResult())
                        .enqueue(new Callback<Void>() {
                            @Override public void onResponse(Call<Void> c,
                                                             retrofit2.Response<Void> r) {}
                            @Override public void onFailure(Call<Void> c, Throwable t) {}
                        });
                });
        } catch (Throwable ignored) {}
    }

    // ═══ 2. Disparar el aviso a los cuidadores ═════════════════

    /**
     * Le pide a la Edge Function que notifique a los familiares vinculados.
     * La función resuelve quiénes son a partir del JWT: la app no elige
     * destinatarios, así nadie puede pedir que se le mande push a un tercero.
     */
    public static void avisarACuidadores(Context ctx, String titulo, String mensaje) {
        final Context appCtx = ctx.getApplicationContext();

        POOL.execute(() -> {
            String jwt = com.tesis.vimed.api.auth.TokenManager.tokenValido(appCtx);
            if (jwt == null) return;   // sin sesión no hay a quién avisar

            String base = BuildConfig.SUPABASE_URL;
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);

            String cuerpo = "{\"titulo\":" + jsonString(titulo)
                + ",\"mensaje\":" + jsonString(mensaje) + "}";

            Request req = new Request.Builder()
                .url(base + "/functions/v1/" + EDGE_FUNCTION)
                .addHeader("Authorization", "Bearer " + jwt)
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .post(RequestBody.create(cuerpo, JSON))
                .build();

            try (Response r = HTTP.newCall(req).execute()) {
                if (!r.isSuccessful()) {
                    Log.w(TAG, "Edge Function respondió " + r.code());
                }
            } catch (IOException e) {
                Log.w(TAG, "Sin red para avisar al cuidador", e);
            }
        });
    }

    /** Escapa un texto para meterlo en el JSON a mano. */
    private static String jsonString(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
