package com.tesis.vimed.api;

import com.tesis.vimed.models.CitaMedica;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.MensajeChat;
import com.tesis.vimed.models.RegistroToma;
import com.tesis.vimed.models.Usuario;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
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

    @PATCH("medicamentos")
    Call<List<Medicamento>> actualizarMedicamento(
        @Query("id_medicamento") String idEq,
        @Body Medicamento cambios
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

    @PATCH("registro_tomas")
    Call<List<RegistroToma>> actualizarRegistroToma(
        @Query("id_registro") String idEq,
        @Body RegistroToma cambios
    );

    // ── CITAS MÉDICAS ─────────────────────────────────────────
    @GET("citas_medicas")
    Call<List<CitaMedica>> getCitas(
        @Query("id_usuario") String idUsuarioEq,
        @Query("order")      String order
    );

    @POST("citas_medicas")
    Call<List<CitaMedica>> crearCita(@Body CitaMedica nueva);

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
}
