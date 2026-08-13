package com.tesis.vimed.api;

import com.tesis.vimed.models.CatalogoMedicamento;
import com.tesis.vimed.models.CitaMedica;
import com.tesis.vimed.models.Dispositivo;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.InteraccionCatalogo;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.MensajeChat;
import com.tesis.vimed.models.Notificacion;
import com.tesis.vimed.models.RegistroToma;
import com.tesis.vimed.models.Usuario;
import com.tesis.vimed.models.UsuarioSupabase;
import com.tesis.vimed.models.Vinculacion;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Endpoints REST contra el esquema vimed en Supabase.
 *
 * PostgREST traduce automáticamente: tabla = ruta, columnas = query params.
 * Operadores: eq.{valor}, gt.{valor}, in.(a,b), is.null, order=col.asc, etc.
 *
 * Ejemplo:
 *   getMedicamentos("eq.1", "eq.true", "nombre.asc")
 *   → GET /rest/v1/medicamentos?id_usuario=eq.1&activo=eq.true&order=nombre.asc
 */
public interface SupabaseService {

    // ── USUARIOS ──────────────────────────────────────────────
    @GET("usuarios")
    Call<List<Usuario>> getUsuarios(
        @Query("correo") String correoEq,
        @Query("select") String select
    );

    @POST("usuarios")
    Call<List<Usuario>> crearUsuario(@Body Usuario nuevo);

    @PATCH("usuarios")
    Call<List<Usuario>> actualizarUsuario(
        @Query("id_usuario") String idEq,
        @Body Usuario cambios
    );

    // ── MEDICAMENTOS ──────────────────────────────────────────
    @GET("medicamentos")
    Call<List<Medicamento>> getMedicamentos(
        @Query("id_usuario") String idUsuarioEq,
        @Query("activo")     String activoEq,
        @Query("order")      String order
    );

    @POST("medicamentos")
    Call<List<Medicamento>> crearMedicamento(@Body Medicamento nuevo);

    @GET("medicamentos")
    Call<List<Medicamento>> getMedicamentoPorId(@Query("id_medicamento") String idEq);

    /**
     * @param cambios DTO de parche con SOLO los campos a modificar
     *                (ver VimedRepo.MedicamentoPatch). No usar Medicamento:
     *                Gson serializa sus int en 0 y pisaría columnas.
     */
    @PATCH("medicamentos")
    Call<List<Medicamento>> actualizarMedicamento(
        @Query("id_medicamento") String idEq,
        @Body Object cambios
    );

    @DELETE("medicamentos")
    Call<Void> eliminarMedicamento(@Query("id_medicamento") String idEq);

    // ── HORARIOS ──────────────────────────────────────────────
    @GET("horarios")
    Call<List<Horario>> getHorarios(@Query("id_medicamento") String idMedicamentoEq);

    @POST("horarios")
    Call<List<Horario>> crearHorario(@Body Horario nuevo);

    @DELETE("horarios")
    Call<Void> eliminarHorario(@Query("id_horario") String idEq);

    // ── REGISTRO DE TOMAS ─────────────────────────────────────
    @GET("registro_tomas")
    Call<List<RegistroToma>> getRegistrosTomas(
        @Query("id_usuario")           String idUsuarioEq,
        @Query("fecha_hora_programada") String fechaFilter, // ej "gte.2026-01-01"
        @Query("order")                String order
    );

    @POST("registro_tomas")
    Call<List<RegistroToma>> crearRegistroToma(@Body RegistroToma nuevo);

    /** @param cambios DTO de parche — ver nota en actualizarMedicamento. */
    @PATCH("registro_tomas")
    Call<List<RegistroToma>> actualizarRegistroToma(
        @Query("id_registro") String idEq,
        @Body Object cambios
    );

    // ── CITAS MÉDICAS ─────────────────────────────────────────
    @GET("citas_medicas")
    Call<List<CitaMedica>> getCitas(
        @Query("id_usuario") String idUsuarioEq,
        @Query("order")      String order
    );

    @POST("citas_medicas")
    Call<List<CitaMedica>> crearCita(@Body CitaMedica nueva);

    /** @param cambios DTO de parche — ver nota en actualizarMedicamento. */
    @PATCH("citas_medicas")
    Call<List<CitaMedica>> actualizarCita(
        @Query("id_cita") String idEq,
        @Body Object cambios
    );

    @DELETE("citas_medicas")
    Call<Void> eliminarCita(@Query("id_cita") String idEq);

    // ── CHATBOT HISTORIAL ─────────────────────────────────────
    @GET("chatbot_historial")
    Call<List<MensajeChat>> getHistorialChat(
        @Query("id_usuario") String idUsuarioEq,
        @Query("order")      String order
    );

    @POST("chatbot_historial")
    Call<List<MensajeChat>> guardarMensajeChat(@Body MensajeChat mensaje);

    // ── CATÁLOGO DE MEDICAMENTOS (referencia pública) ─────────
    /** Trae todo el catálogo activo, ordenado por nombre. */
    @GET("catalogo_medicamentos")
    Call<List<CatalogoMedicamento>> getCatalogo(
        @Query("activo") String activoEq,       // "eq.true"
        @Query("order")  String order           // "nombre_comercial.asc"
    );

    /** Busca por nombre comercial (substring, case-insensitive). */
    @GET("catalogo_medicamentos")
    Call<List<CatalogoMedicamento>> buscarCatalogo(
        @Query("nombre_comercial") String iLike, // "ilike.*losart*"
        @Query("order")            String order
    );

    /** Filtra por categoría (cardiovascular, diabetes, gastro, etc.). */
    @GET("catalogo_medicamentos")
    Call<List<CatalogoMedicamento>> getCatalogoPorCategoria(
        @Query("categoria") String categoriaEq,  // "eq.cardiovascular"
        @Query("order")     String order
    );

    // ── INTERACCIONES DEL CATÁLOGO ────────────────────────────
    /** Trae interacciones donde participa un med específico del catálogo. */
    @GET("interacciones_catalogo")
    Call<List<InteraccionCatalogo>> getInteraccionesDeMed(
        @Query("or") String or                   // "or=(id_catalogo_a.eq.5,id_catalogo_b.eq.5)"
    );

    /** Trae interacciones entre un conjunto de meds (in.(...)). */
    @GET("interacciones_catalogo")
    Call<List<InteraccionCatalogo>> getInteraccionesEntre(
        @Query("id_catalogo_a") String aIn,      // "in.(5,7,12)"
        @Query("id_catalogo_b") String bIn       // "in.(5,7,12)"
    );

    // ── PERFIL EN public.usuarios (sync con Supabase Auth) ────
    /** Busca el perfil por su auth_user_id (UUID de auth.users). */
    @GET("usuarios")
    Call<List<UsuarioSupabase>> getPerfilPorAuthId(
        @Query("auth_user_id") String authIdEq   // "eq.<uuid>"
    );

    /** Crea el perfil en public.usuarios luego del signUp de Auth. */
    @POST("usuarios")
    Call<List<UsuarioSupabase>> crearPerfil(@Body UsuarioSupabase nuevo);

    /** Actualiza el rol elegido en RoleSelection. */
    @PATCH("usuarios")
    Call<List<UsuarioSupabase>> actualizarPerfil(
        @Query("id_usuario") String idEq,        // "eq.7"
        @Body UsuarioSupabase cambios
    );

    // ── VINCULACIÓN FAMILIAR ──────────────────────────────────
    /** Busca un perfil por correo (para vincular por email). */
    @GET("usuarios")
    Call<List<UsuarioSupabase>> getPerfilPorCorreo(@Query("correo") String correoEq);

    /** Busca un perfil por su id_usuario. */
    @GET("usuarios")
    Call<List<UsuarioSupabase>> getPerfilPorId(@Query("id_usuario") String idEq);

    @GET("vinculacion_familiar")
    Call<List<Vinculacion>> getVinculosDeAdulto(@Query("id_adulto") String idEq);

    @GET("vinculacion_familiar")
    Call<List<Vinculacion>> getVinculosDeFamiliar(@Query("id_familiar") String idEq);

    @POST("vinculacion_familiar")
    Call<List<Vinculacion>> crearVinculo(@Body Vinculacion nuevo);

    @DELETE("vinculacion_familiar")
    Call<Void> eliminarVinculo(@Query("id_vinculo") String idEq);

    // ── DISPOSITIVOS (tokens de push FCM) ─────────────────────

    /**
     * Alta o actualización del token de este celular.
     *
     * Usa UPSERT sobre la constraint única de la columna token: si el mismo
     * aparato vuelve a registrarse (o cambia de dueño al iniciar sesión otra
     * persona), se pisa la fila en vez de duplicarla.
     * El header Prefer lo pone el llamador.
     */
    @POST("dispositivos")
    Call<List<Dispositivo>> registrarDispositivo(
        @Header("Prefer") String prefer,          // "resolution=merge-duplicates"
        @Body Dispositivo nuevo
    );

    @DELETE("dispositivos")
    Call<Void> eliminarDispositivo(@Query("token") String tokenEq);

    // ── NOTIFICACIONES (historial en la nube) ─────────────────
    @POST("notificaciones")
    Call<List<Notificacion>> crearNotificacion(@Body Notificacion n);

    @GET("notificaciones")
    Call<List<Notificacion>> getNotificaciones(
        @Query("id_destinatario") String idEq,   // "eq.7"
        @Query("order")           String order   // "fecha_envio.desc"
    );
}

