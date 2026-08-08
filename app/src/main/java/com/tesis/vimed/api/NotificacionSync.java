package com.tesis.vimed.api;

import android.content.Context;

import com.tesis.vimed.SessionManager;
import com.tesis.vimed.models.Notificacion;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Sube a Supabase las notificaciones que la app dispara localmente,
 * para que el familiar vinculado pueda ver el historial desde la nube.
 *
 * Fire-and-forget: si no hay red o falla el POST, la notificación local
 * ya se mostró igual. No reintenta ni encola.
 */
public final class NotificacionSync {

    private NotificacionSync() {}

    /**
     * Registra una notificación en public.notificaciones.
     *
     * @param tipo uno de Notificacion.TIPO_TOMA / TIPO_STOCK / TIPO_INTERACCION / TIPO_CITA
     */
    public static void registrar(Context ctx, String tipo, String mensaje) {
        registrar(ctx, tipo, mensaje, null);
    }

    /**
     * @param idRegistroToma id de la fila en registro_tomas si aplica (puede ser null).
     *                       Solo se envía si es > 0 — el id local de SQLite no
     *                       necesariamente existe en Supabase todavía.
     */
    public static void registrar(Context ctx, String tipo, String mensaje,
                                 Integer idRegistroToma) {
        SessionManager ses = new SessionManager(ctx);
        int idDestinatario = ses.getSupabaseIdUsuario();

        // Sin perfil sincronizado en la nube no hay FK válida — salimos.
        if (idDestinatario == -1) return;

        Notificacion n = new Notificacion(idDestinatario, tipo, mensaje);

        SupabaseClient.getService()
            .crearNotificacion(n)
            .enqueue(new Callback<List<Notificacion>>() {
                @Override public void onResponse(Call<List<Notificacion>> c,
                                                 Response<List<Notificacion>> r) {}
                @Override public void onFailure(Call<List<Notificacion>> c, Throwable t) {}
            });
    }
}
