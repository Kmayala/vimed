package com.tesis.vimed.api;

import android.content.Context;

import com.tesis.vimed.SessionManager;
import com.tesis.vimed.models.CitaMedica;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.RegistroToma;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Acceso a los datos de la app en Supabase.
 *
 * Reemplaza a los DAO de SQLite. Dos familias de métodos:
 *
 *   - Los que reciben {@link Cb}: asíncronos, para usar desde Activities.
 *     El callback vuelve en el hilo principal (lo garantiza Retrofit).
 *
 *   - Los que terminan en "Sync": BLOQUEANTES. Solo para BroadcastReceivers
 *     u otros contextos que ya corren fuera del hilo principal. Llamarlos
 *     desde una Activity tira NetworkOnMainThreadException.
 *
 * Todas las consultas filtran por el id_usuario de public.usuarios
 * (SessionManager.getSupabaseIdUsuario), no por el id viejo de SQLite.
 */
public final class VimedRepo {

    private VimedRepo() {}

    /** Callback simple para las pantallas. */
    public interface Cb<T> {
        void onOk(T data);
        /** Se llama ante error de red o respuesta != 2xx. */
        default void onError(String msg) {}
    }

    // ── DTOs de parche ─────────────────────────────────────────
    // Gson omite los null pero NO los primitivos: un `int` sin setear
    // viaja como 0 y pisaría la columna. Por eso los parches usan
    // wrappers (Integer/Boolean) y solo llevan lo que cambia.

    private static class MedicamentoPatch {
        @com.google.gson.annotations.SerializedName("stock_actual")
        Integer stockActual;
        Boolean activo;
    }

    private static class RegistroTomaPatch {
        String estado;
        @com.google.gson.annotations.SerializedName("fecha_hora_confirmacion")
        String fechaHoraConfirmacion;
    }

    private static class CitaPatch {
        String estado;
    }

    // ═══════════════════════════════════════════════════════════
    //  MEDICAMENTOS
    // ═══════════════════════════════════════════════════════════

    public static void listarMedicamentos(Context ctx, Cb<List<Medicamento>> cb) {
        int idUsuario = idUsuario(ctx);
        if (idUsuario == -1) { cb.onError("Sesión no sincronizada"); return; }

        SupabaseClient.getService()
            .getMedicamentos("eq." + idUsuario, "eq.true", "nombre.asc")
            .enqueue(lista(cb));
    }

    /** Versión bloqueante para AlarmaReceiver. */
    public static List<Medicamento> listarMedicamentosSync(Context ctx) {
        int idUsuario = idUsuario(ctx);
        if (idUsuario == -1) return new ArrayList<>();
        return ejecutar(SupabaseClient.getService()
            .getMedicamentos("eq." + idUsuario, "eq.true", "nombre.asc"));
    }

    public static Medicamento buscarMedicamentoSync(int idMedicamento) {
        List<Medicamento> r = ejecutar(SupabaseClient.getService()
            .getMedicamentoPorId("eq." + idMedicamento));
        return r.isEmpty() ? null : r.get(0);
    }

    public static void crearMedicamento(Context ctx, Medicamento med, Cb<Medicamento> cb) {
        int idUsuario = idUsuario(ctx);
        if (idUsuario == -1) { cb.onError("Sesión no sincronizada"); return; }
        med.setIdUsuario(idUsuario);

        SupabaseClient.getService().crearMedicamento(med)
            .enqueue(new Callback<List<Medicamento>>() {
                @Override
                public void onResponse(Call<List<Medicamento>> c, Response<List<Medicamento>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        cb.onOk(r.body().get(0));
                    } else {
                        cb.onError(mensajeDeError(r));
                    }
                }
                @Override
                public void onFailure(Call<List<Medicamento>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    /** Baja lógica: activo = false. */
    public static void eliminarMedicamento(int idMedicamento, Cb<Void> cb) {
        MedicamentoPatch cambios = new MedicamentoPatch();
        cambios.activo = false;
        SupabaseClient.getService()
            .actualizarMedicamento("eq." + idMedicamento, cambios)
            .enqueue(VimedRepo.<Medicamento>voidCb(cb));
    }

    public static void actualizarStock(int idMedicamento, int nuevoStock, Cb<Void> cb) {
        MedicamentoPatch cambios = new MedicamentoPatch();
        cambios.stockActual = nuevoStock;
        SupabaseClient.getService()
            .actualizarMedicamento("eq." + idMedicamento, cambios)
            .enqueue(VimedRepo.<Medicamento>voidCb(cb));
    }

    public static void actualizarStockSync(int idMedicamento, int nuevoStock) {
        MedicamentoPatch cambios = new MedicamentoPatch();
        cambios.stockActual = nuevoStock;
        ejecutar(SupabaseClient.getService()
            .actualizarMedicamento("eq." + idMedicamento, cambios));
    }

    // ═══════════════════════════════════════════════════════════
    //  HORARIOS
    // ═══════════════════════════════════════════════════════════

    public static void listarHorarios(int idMedicamento, Cb<List<Horario>> cb) {
        SupabaseClient.getService()
            .getHorarios("eq." + idMedicamento)
            .enqueue(lista(cb));
    }

    public static List<Horario> listarHorariosSync(int idMedicamento) {
        return ejecutar(SupabaseClient.getService().getHorarios("eq." + idMedicamento));
    }

    public static void crearHorario(Horario h, Cb<Horario> cb) {
        SupabaseClient.getService().crearHorario(h)
            .enqueue(new Callback<List<Horario>>() {
                @Override
                public void onResponse(Call<List<Horario>> c, Response<List<Horario>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        cb.onOk(r.body().get(0));
                    } else {
                        cb.onError(mensajeDeError(r));
                    }
                }
                @Override
                public void onFailure(Call<List<Horario>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    // ═══════════════════════════════════════════════════════════
    //  REGISTRO DE TOMAS
    // ═══════════════════════════════════════════════════════════

    /** Tomas de un día concreto. @param fechaYMD formato "yyyy-MM-dd". */
    public static void listarTomasDelDia(Context ctx, String fechaYMD,
                                         Cb<List<RegistroToma>> cb) {
        int idUsuario = idUsuario(ctx);
        if (idUsuario == -1) { cb.onError("Sesión no sincronizada"); return; }

        SupabaseClient.getService()
            .getRegistrosTomas("eq." + idUsuario, "gte." + fechaYMD,
                "fecha_hora_programada.asc")
            .enqueue(lista(cb));
    }

    public static RegistroToma crearTomaSync(RegistroToma r) {
        List<RegistroToma> res = ejecutar(SupabaseClient.getService().crearRegistroToma(r));
        return res.isEmpty() ? null : res.get(0);
    }

    public static void actualizarEstadoToma(int idRegistro, String estado,
                                            String fechaConfirmacion, Cb<Void> cb) {
        RegistroTomaPatch cambios = new RegistroTomaPatch();
        cambios.estado = estado;
        cambios.fechaHoraConfirmacion = fechaConfirmacion;
        SupabaseClient.getService()
            .actualizarRegistroToma("eq." + idRegistro, cambios)
            .enqueue(VimedRepo.<RegistroToma>voidCb(cb));
    }

    public static void actualizarEstadoTomaSync(int idRegistro, String estado,
                                                String fechaConfirmacion) {
        RegistroTomaPatch cambios = new RegistroTomaPatch();
        cambios.estado = estado;
        cambios.fechaHoraConfirmacion = fechaConfirmacion;
        ejecutar(SupabaseClient.getService()
            .actualizarRegistroToma("eq." + idRegistro, cambios));
    }

    /** Crea la fila de la toma si no existe todavía, y la deja confirmada. */
    public static void crearTomaConfirmada(RegistroToma r, Cb<RegistroToma> cb) {
        SupabaseClient.getService().crearRegistroToma(r)
            .enqueue(new Callback<List<RegistroToma>>() {
                @Override
                public void onResponse(Call<List<RegistroToma>> c, Response<List<RegistroToma>> res) {
                    if (res.isSuccessful() && res.body() != null && !res.body().isEmpty()) {
                        cb.onOk(res.body().get(0));
                    } else {
                        cb.onError("No se pudo registrar la toma (código " + res.code() + ")");
                    }
                }
                @Override
                public void onFailure(Call<List<RegistroToma>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    // ═══════════════════════════════════════════════════════════
    //  CITAS
    // ═══════════════════════════════════════════════════════════

    public static void listarCitas(Context ctx, Cb<List<CitaMedica>> cb) {
        int idUsuario = idUsuario(ctx);
        if (idUsuario == -1) { cb.onError("Sesión no sincronizada"); return; }
        SupabaseClient.getService()
            .getCitas("eq." + idUsuario, "fecha_hora.asc")
            .enqueue(lista(cb));
    }

    public static void crearCita(Context ctx, CitaMedica cita, Cb<CitaMedica> cb) {
        int idUsuario = idUsuario(ctx);
        if (idUsuario == -1) { cb.onError("Sesión no sincronizada"); return; }
        cita.setIdUsuario(idUsuario);

        SupabaseClient.getService().crearCita(cita)
            .enqueue(new Callback<List<CitaMedica>>() {
                @Override
                public void onResponse(Call<List<CitaMedica>> c, Response<List<CitaMedica>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        cb.onOk(r.body().get(0));
                    } else {
                        cb.onError("No se pudo guardar la cita (código " + r.code() + ")");
                    }
                }
                @Override
                public void onFailure(Call<List<CitaMedica>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    /** @param estado pendiente | confirmada | cancelada */
    public static void actualizarEstadoCita(int idCita, String estado, Cb<Void> cb) {
        CitaPatch cambios = new CitaPatch();
        cambios.estado = estado;
        SupabaseClient.getService()
            .actualizarCita("eq." + idCita, cambios)
            .enqueue(VimedRepo.<CitaMedica>voidCb(cb));
    }

    public static void eliminarCita(int idCita, Cb<Void> cb) {
        SupabaseClient.getService().eliminarCita("eq." + idCita)
            .enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {
                    if (r.isSuccessful()) cb.onOk(null);
                    else cb.onError("No se pudo eliminar (código " + r.code() + ")");
                }
                @Override public void onFailure(Call<Void> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    // ═══════════════════════════════════════════════════════════
    //  VINCULACIÓN FAMILIAR + vistas del cuidador
    // ═══════════════════════════════════════════════════════════

    public static void buscarPerfilPorCorreo(String correo,
                                             Cb<com.tesis.vimed.models.UsuarioSupabase> cb) {
        SupabaseClient.getService().getPerfilPorCorreo("eq." + correo)
            .enqueue(new Callback<List<com.tesis.vimed.models.UsuarioSupabase>>() {
                @Override
                public void onResponse(Call<List<com.tesis.vimed.models.UsuarioSupabase>> c,
                                       Response<List<com.tesis.vimed.models.UsuarioSupabase>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        cb.onOk(r.body().get(0));
                    } else if (r.isSuccessful()) {
                        cb.onOk(null);   // no existe — el llamador decide qué mostrar
                    } else {
                        cb.onError("Error del servidor (código " + r.code() + ")");
                    }
                }
                @Override
                public void onFailure(Call<List<com.tesis.vimed.models.UsuarioSupabase>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    public static void buscarPerfilPorId(int idUsuario,
                                         Cb<com.tesis.vimed.models.UsuarioSupabase> cb) {
        SupabaseClient.getService().getPerfilPorId("eq." + idUsuario)
            .enqueue(new Callback<List<com.tesis.vimed.models.UsuarioSupabase>>() {
                @Override
                public void onResponse(Call<List<com.tesis.vimed.models.UsuarioSupabase>> c,
                                       Response<List<com.tesis.vimed.models.UsuarioSupabase>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        cb.onOk(r.body().get(0));
                    } else {
                        cb.onOk(null);
                    }
                }
                @Override
                public void onFailure(Call<List<com.tesis.vimed.models.UsuarioSupabase>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    /** Vínculos donde el usuario logueado es el ADULTO (sus cuidadores). */
    public static void listarMisCuidadores(Context ctx,
                                           Cb<List<com.tesis.vimed.models.Vinculacion>> cb) {
        int id = idUsuario(ctx);
        if (id == -1) { cb.onError("Sesión no sincronizada"); return; }
        SupabaseClient.getService().getVinculosDeAdulto("eq." + id).enqueue(lista(cb));
    }

    /** Vínculos donde el usuario logueado es el FAMILIAR (a quiénes cuida). */
    public static void listarMisPacientes(Context ctx,
                                          Cb<List<com.tesis.vimed.models.Vinculacion>> cb) {
        int id = idUsuario(ctx);
        if (id == -1) { cb.onError("Sesión no sincronizada"); return; }
        SupabaseClient.getService().getVinculosDeFamiliar("eq." + id).enqueue(lista(cb));
    }

    public static void crearVinculo(Context ctx, int idFamiliar, Cb<Void> cb) {
        int idAdulto = idUsuario(ctx);
        if (idAdulto == -1) { cb.onError("Sesión no sincronizada"); return; }
        com.tesis.vimed.models.Vinculacion v =
            new com.tesis.vimed.models.Vinculacion(idAdulto, idFamiliar);
        SupabaseClient.getService().crearVinculo(v)
            .enqueue(VimedRepo.<com.tesis.vimed.models.Vinculacion>voidCb(cb));
    }

    public static void eliminarVinculo(int idVinculo, Cb<Void> cb) {
        SupabaseClient.getService().eliminarVinculo("eq." + idVinculo)
            .enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {
                    if (r.isSuccessful()) cb.onOk(null);
                    else cb.onError("No se pudo desvincular (código " + r.code() + ")");
                }
                @Override public void onFailure(Call<Void> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    // ── Datos DE OTRO usuario (lo que ve el cuidador) ─────────

    public static void listarMedicamentosDe(int idUsuario, Cb<List<Medicamento>> cb) {
        SupabaseClient.getService()
            .getMedicamentos("eq." + idUsuario, "eq.true", "nombre.asc")
            .enqueue(lista(cb));
    }

    public static void listarTomasDelDiaDe(int idUsuario, String fechaYMD,
                                           Cb<List<RegistroToma>> cb) {
        SupabaseClient.getService()
            .getRegistrosTomas("eq." + idUsuario, "gte." + fechaYMD,
                "fecha_hora_programada.asc")
            .enqueue(lista(cb));
    }

    public static void listarNotificacionesDe(int idUsuario,
                                              Cb<List<com.tesis.vimed.models.Notificacion>> cb) {
        SupabaseClient.getService()
            .getNotificaciones("eq." + idUsuario, "fecha_envio.desc")
            .enqueue(lista(cb));
    }

    public static void listarCitasDe(int idUsuario, Cb<List<CitaMedica>> cb) {
        SupabaseClient.getService()
            .getCitas("eq." + idUsuario, "fecha_hora.asc")
            .enqueue(lista(cb));
    }

    /**
     * Horarios de VARIOS medicamentos en una sola consulta (PostgREST "in.(...)").
     * El cuidador lo usa para saber a qué medicamento pertenece cada toma:
     * registro_tomas guarda id_horario, no id_medicamento.
     */
    public static void listarHorariosDe(List<Integer> idsMedicamentos, Cb<List<Horario>> cb) {
        if (idsMedicamentos == null || idsMedicamentos.isEmpty()) {
            cb.onOk(new ArrayList<>());
            return;
        }
        StringBuilder in = new StringBuilder("in.(");
        for (int i = 0; i < idsMedicamentos.size(); i++) {
            if (i > 0) in.append(',');
            in.append(idsMedicamentos.get(i));
        }
        in.append(')');
        SupabaseClient.getService().getHorarios(in.toString()).enqueue(lista(cb));
    }

    // ── Escritura EN NOMBRE del adulto (lo que carga el cuidador) ──
    // Mismos endpoints, pero el id_usuario lo decide el llamador en vez
    // de tomarlo de la sesión. Quien manda es RLS: si el vínculo no
    // existe, Supabase rechaza con 403.

    public static void crearMedicamentoPara(int idUsuarioDestino, Medicamento med,
                                            Cb<Medicamento> cb) {
        med.setIdUsuario(idUsuarioDestino);
        SupabaseClient.getService().crearMedicamento(med)
            .enqueue(new Callback<List<Medicamento>>() {
                @Override
                public void onResponse(Call<List<Medicamento>> c, Response<List<Medicamento>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        cb.onOk(r.body().get(0));
                    } else {
                        cb.onError(mensajeDeError(r));
                    }
                }
                @Override
                public void onFailure(Call<List<Medicamento>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    public static void crearCitaPara(int idUsuarioDestino, CitaMedica cita, Cb<CitaMedica> cb) {
        cita.setIdUsuario(idUsuarioDestino);
        SupabaseClient.getService().crearCita(cita)
            .enqueue(new Callback<List<CitaMedica>>() {
                @Override
                public void onResponse(Call<List<CitaMedica>> c, Response<List<CitaMedica>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        cb.onOk(r.body().get(0));
                    } else {
                        cb.onError(mensajeDeError(r));
                    }
                }
                @Override
                public void onFailure(Call<List<CitaMedica>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers internos
    // ═══════════════════════════════════════════════════════════

    private static int idUsuario(Context ctx) {
        return new SessionManager(ctx).getSupabaseIdUsuario();
    }

    /**
     * Traduce una respuesta de error a algo accionable.
     * PostgREST manda el detalle en el body ({"message":…,"hint":…}),
     * y sin eso un "error 401" no dice nada.
     */
    public static String mensajeDeError(Response<?> r) {
        String detalle = "";
        try {
            if (r.errorBody() != null) detalle = r.errorBody().string();
        } catch (Exception ignored) {}

        android.util.Log.w("VimedRepo", "HTTP " + r.code() + " → " + detalle);

        switch (r.code()) {
            case 401:
                return "Tu sesión venció. Cerrá sesión y volvé a entrar.";
            case 403:
                return "Sin permisos para esta operación (RLS). " + detalle;
            case 409:
                return "Ese registro ya existe.";
            default:
                return "Error " + r.code() + (detalle.isEmpty() ? "" : ": " + detalle);
        }
    }

    /** Callback genérico para endpoints que devuelven una lista. */
    private static <T> Callback<List<T>> lista(Cb<List<T>> cb) {
        return new Callback<List<T>>() {
            @Override
            public void onResponse(Call<List<T>> c, Response<List<T>> r) {
                if (r.isSuccessful() && r.body() != null) cb.onOk(r.body());
                else cb.onError(mensajeDeError(r));
            }
            @Override
            public void onFailure(Call<List<T>> c, Throwable t) {
                cb.onError("Sin conexión: " + t.getMessage());
            }
        };
    }

    /** Callback para operaciones donde solo importa si salió bien. */
    private static <T> Callback<List<T>> voidCb(Cb<Void> cb) {
        return new Callback<List<T>>() {
            @Override
            public void onResponse(Call<List<T>> c, Response<List<T>> r) {
                if (r.isSuccessful()) cb.onOk(null);
                else cb.onError(mensajeDeError(r));
            }
            @Override
            public void onFailure(Call<List<T>> c, Throwable t) {
                cb.onError("Sin conexión: " + t.getMessage());
            }
        };
    }

    /** Ejecuta una llamada de forma bloqueante. Devuelve lista vacía si falla. */
    private static <T> List<T> ejecutar(Call<List<T>> call) {
        try {
            Response<List<T>> r = call.execute();
            if (r.isSuccessful() && r.body() != null) return r.body();
        } catch (IOException ignored) {
            // Sin red: devolvemos vacío, el llamador decide qué hacer.
        }
        return new ArrayList<>();
    }
}
