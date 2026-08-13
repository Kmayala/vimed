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
        registrar(ctx, tipo, mensaje, null, true);
    }

    /**
     * @param pushAlCuidador false para eventos que solo interesan como
     *        historial. Sin este filtro el cuidador recibiría dos push por
     *        cada dosis ("sonó la alarma" + "la tomó"), y a las pocas horas
     *        silenciaría la app.
     */
    public static void registrar(Context ctx, String tipo, String mensaje,
                                 boolean pushAlCuidador) {
        registrar(ctx, tipo, mensaje, null, pushAlCuidador);
    }

    /**
     * @param idRegistroToma id de la fila en registro_tomas si aplica (puede ser null).
     *                       Solo se envía si es > 0 — el id local de SQLite no
     *                       necesariamente existe en Supabase todavía.
     */
    public static void registrar(Context ctx, String tipo, String mensaje,
                                 Integer idRegistroToma) {
        registrar(ctx, tipo, mensaje, idRegistroToma, true);
    }

    public static void registrar(Context ctx, String tipo, String mensaje,
                                 Integer idRegistroToma, boolean pushAlCuidador) {
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

        // Además del historial, push instantáneo a los familiares vinculados.
        // Solo tiene sentido si quien genera el evento es el adulto mayor:
        // el cuidador no se avisa a sí mismo.
        if (pushAlCuidador && ses.esAdultoMayor()) {
            PushManager.avisarACuidadores(ctx, tituloPara(tipo, ses.getNombre()), mensaje);
        }
    }

    /** Encabezado del push, para que el cuidador entienda de quién y de qué es. */
    private static String tituloPara(String tipo, String nombreAdulto) {
        String quien = nombreAdulto != null && !nombreAdulto.isEmpty()
            ? nombreAdulto : "Tu familiar";
        switch (tipo != null ? tipo : "") {
            case Notificacion.TIPO_STOCK:       return quien + " — medicamento por acabarse";
            case Notificacion.TIPO_INTERACCION: return quien + " — aviso de interacción";
            case Notificacion.TIPO_CITA:        return quien + " — cita médica";
            default:                            return quien + " — medicación";
        }
    }
}
