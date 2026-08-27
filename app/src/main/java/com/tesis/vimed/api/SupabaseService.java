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

    /** @param cambios DTO de parche — ver nota en actualizarMedicamento. */
    @PATCH("horarios")
    Call<List<Horario>> actualizarHorario(
        @Query("id_horario") String idEq,
        @Body Object cambios
    );

    @DELETE("horarios")
    Call<Void> eliminarHorario(@Query("id_horario") String idEq);

    // ── REGISTRO DE TOMAS ─────────────────────────────────────
    @GET("registro_tomas")
    Call<List<RegistroToma>> getRegistrosTomas(
        @Query("id_usuario")           String idUsuarioEq,
        @Query("fecha_hora_programada") String fechaFilter, // ej "gte.2026-01-01"
        @Query("order")                String order
    );

    /**
     * La fila de UNA toma concreta: un horario en un momento programado.
     *
     * Es la consulta que evita los duplicados. Sin ella, cada camino que
     * registra una toma —la alarma al sonar, el botón de la notificación,
     * el "ya tomé" del panel— insertaba su propia fila, y una misma dosis
     * terminaba apareciendo tres veces en el historial: pospuesta, sin
     * confirmar y tomada, las tres a la misma hora.
     */
    @GET("registro_tomas")
    Call<List<RegistroToma>> getRegistroDelSlot(
        @Query("id_horario")            String idHorarioEq,   // "eq.12"
        @Query("fecha_hora_programada") String fechaEq        // "eq.2026-08-27 07:50:00"
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

    /**
     * Guarda peso y año de nacimiento, propios o de un paciente vinculado.
     *
     * Va por RPC y no por PATCH sobre la tabla porque el cuidador no tiene
     * —ni debe tener— UPDATE sobre usuarios: el mismo permiso que le
     * dejaría tocar el peso de su paciente le dejaría cambiarle el rol.
     * Ver supabase_datos_clinicos_compartidos.sql.
     *
     * @param cuerpo claves p_id_usuario, p_peso_kg, p_anio_nacimiento.
     */
    @POST("rpc/guardar_datos_clinicos")
    Call<Void> guardarDatosClinicos(@Body java.util.Map<String, Object> cuerpo);

    /** Actualiza el rol elegido en RoleSelection. */
    @PATCH("usuarios")
    Call<List<UsuarioSupabase>> actualizarPerfil(
        @Query("id_usuario") String idEq,        // "eq.7"
        @Body UsuarioSupabase cambios
    );

    // ── VINCULACIÓN FAMILIAR ──────────────────────────────────
    /**
     * Busca un perfil por correo (para vincular por email).
     *
     * Va por RPC y no por la tabla a propósito: el RLS de usuarios
     * solo deja ver tu propia fila y la de los pacientes que YA
     * cuidás, asi que consultar la tabla para vincular devolvia
     * siempre vacio. La funcion es SECURITY DEFINER y acepta una
     * unica coincidencia exacta. Ver supabase_buscar_por_correo.sql.
     *
     * @param cuerpo mapa con la clave "p_correo".
     */
    @POST("rpc/buscar_usuario_por_correo")
    Call<List<UsuarioSupabase>> buscarUsuarioPorCorreo(
        @Body java.util.Map<String, String> cuerpo);

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

    /** Aceptar o rechazar una solicitud. @param cambios DTO con solo `estado`. */
    @PATCH("vinculacion_familiar")
    Call<List<Vinculacion>> actualizarVinculo(
        @Query("id_vinculo") String idEq,
        @Body Object cambios
    );

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

