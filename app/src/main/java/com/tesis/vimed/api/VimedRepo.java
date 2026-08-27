package com.tesis.vimed.api;

import android.content.Context;

import com.tesis.vimed.SessionManager;
import com.tesis.vimed.models.CitaMedica;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.PerfilClinico;
import com.tesis.vimed.models.RegistroToma;
import com.tesis.vimed.models.UsuarioSupabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        @com.google.gson.annotations.SerializedName("fecha_vencimiento")
        String fechaVencimiento;
    }

    private static class RegistroTomaPatch {
        String estado;
        @com.google.gson.annotations.SerializedName("fecha_hora_confirmacion")
        String fechaHoraConfirmacion;
        @com.google.gson.annotations.SerializedName("confirmado_por")
        Integer confirmadoPor;
    }

    private static class CitaPatch {
        String estado;
    }

    private static class HorarioPatch {
        @com.google.gson.annotations.SerializedName("hora_inicio")
        String horaInicio;
    }

    private static class VinculoPatch {
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

    /**
     * Corrige el stock —y opcionalmente el vencimiento— de un medicamento
     * que puede no ser propio: es lo que usa el cuidador cuando repone la
     * caja de su paciente.
     *
     * Va con el callback estricto porque acá el RLS SÍ puede rechazar la
     * escritura, y un "cero filas" que se lee como éxito le mostraría
     * "Guardado ✓" a alguien que no guardó nada.
     *
     * @param vencimiento "yyyy-MM-dd", o null para no tocar esa columna.
     */
    public static void corregirStock(int idMedicamento, int nuevoStock,
                                     String vencimiento, Cb<Void> cb) {
        MedicamentoPatch cambios = new MedicamentoPatch();
        cambios.stockActual = nuevoStock;
        cambios.fechaVencimiento = vencimiento;
        SupabaseClient.getService()
            .actualizarMedicamento("eq." + idMedicamento, cambios)
            .enqueue(VimedRepo.<Medicamento>voidCbEstricto(cb,
                "No se pudo guardar. Puede que ya no estés vinculado a esa persona."));
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

    /**
     * Cambia la hora de inicio de un horario. Lo usa el ajuste de
     * recordatorios: la nueva hora tiene que viajar al servidor porque el
     * celular del cuidador programa sus alarmas desde ahí.
     */
    public static void actualizarHoraInicio(int idHorario, String horaHHMM, Cb<Void> cb) {
        HorarioPatch cambios = new HorarioPatch();
        cambios.horaInicio = horaHHMM;
        SupabaseClient.getService()
            .actualizarHorario("eq." + idHorario, cambios)
            .enqueue(VimedRepo.<Horario>voidCb(cb));
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

    /**
     * Historial de tomas desde una fecha hacia acá, para analizar hábitos.
     * @param desdeYMD formato "yyyy-MM-dd".
     */
    public static void listarTomasDesde(Context ctx, String desdeYMD,
                                        Cb<List<RegistroToma>> cb) {
        // Mismo endpoint que las tomas del día: el filtro ya es "gte.".
        listarTomasDelDia(ctx, desdeYMD, cb);
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
    //  DATOS CLÍNICOS DEL PACIENTE (peso y edad)
    // ═══════════════════════════════════════════════════════════

    /**
     * Trae el perfil del paciente y, de paso, deja peso y edad cacheados en
     * la sesión: el chequeo de dosis los necesita sin poder esperar la red.
     *
     * @param idUsuario a quién mirar; -1 para el usuario logueado.
     */
    public static void cargarDatosClinicos(Context ctx, int idUsuario,
                                           Cb<UsuarioSupabase> cb) {
        final int id = idUsuario > 0 ? idUsuario : idUsuario(ctx);
        if (id == -1) { cb.onError("Sesión no sincronizada"); return; }

        final boolean esElPropio = idUsuario <= 0;

        SupabaseClient.getService().getPerfilPorId("eq." + id)
            .enqueue(new Callback<List<UsuarioSupabase>>() {
                @Override
                public void onResponse(Call<List<UsuarioSupabase>> c,
                                       Response<List<UsuarioSupabase>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        UsuarioSupabase perfil = r.body().get(0);
                        // Solo se cachea el propio: el peso del paciente que
                        // cuida el familiar no puede pisar el suyo.
                        if (esElPropio) guardarEnSesion(ctx, perfil);
                        cb.onOk(perfil);
                    } else {
                        cb.onError(mensajeDeError(r));
                    }
                }
                @Override
                public void onFailure(Call<List<UsuarioSupabase>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    /**
     * Guarda peso y año de nacimiento.
     *
     * @param pesoKg         0 para borrar el dato.
     * @param anioNacimiento 0 para borrar el dato.
     * @param idUsuario      a quién; -1 para el usuario logueado.
     */
    public static void guardarDatosClinicos(Context ctx, int idUsuario,
                                            float pesoKg, int anioNacimiento,
                                            Cb<Void> cb) {
        final int id = idUsuario > 0 ? idUsuario : idUsuario(ctx);
        if (id == -1) { cb.onError("Sesión no sincronizada"); return; }

        final boolean esElPropio = idUsuario <= 0;

        // Solo viajan estas dos columnas: Gson omite los null, así que el
        // resto del perfil (nombre, correo, rol) queda intacto.
        UsuarioSupabase cambios = new UsuarioSupabase();
        cambios.setPesoKg(pesoKg > 0 ? pesoKg : null);
        cambios.setAnioNacimiento(anioNacimiento > 0 ? anioNacimiento : null);

        SupabaseClient.getService()
            .actualizarPerfil("eq." + id, cambios)
            .enqueue(new Callback<List<UsuarioSupabase>>() {
                @Override
                public void onResponse(Call<List<UsuarioSupabase>> c,
                                       Response<List<UsuarioSupabase>> r) {
                    if (!r.isSuccessful()) { cb.onError(mensajeDeError(r)); return; }
                    if (esElPropio) {
                        new SessionManager(ctx)
                            .guardarDatosClinicos(pesoKg, anioNacimiento);
                    }
                    cb.onOk(null);
                }
                @Override
                public void onFailure(Call<List<UsuarioSupabase>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    private static void guardarEnSesion(Context ctx, UsuarioSupabase perfil) {
        PerfilClinico p = perfil.perfilClinico();
        new SessionManager(ctx).guardarDatosClinicos(p.getPesoKg(), p.getAnioNacimiento());
    }

    // ═══════════════════════════════════════════════════════════
    //  DOSIS OLVIDADAS
    // ═══════════════════════════════════════════════════════════

    /**
     * Tomas en 'omitida' desde {@code desdeYMD}, de la más reciente a la más
     * vieja.
     *
     * El filtro se hace acá y no en el servidor porque el endpoint de
     * registro_tomas ya está armado con el rango de fechas; agregarle un
     * parámetro de estado obligaría a tocar la firma que usan otras cuatro
     * pantallas. Son unas pocas decenas de filas.
     *
     * También se descartan las del FUTURO: la fila de una toma nace en
     * 'omitida' y recién pasa a 'confirmada' cuando la persona aprieta el
     * botón, así que la dosis de esta tarde ya existe y figura omitida sin
     * que nadie se haya olvidado de nada. Listarla sería acusar a alguien
     * por algo que todavía no pasó.
     *
     * @param idUsuario de quién; -1 para el usuario logueado.
     */
    public static void listarOlvidos(Context ctx, int idUsuario, String desdeYMD,
                                     Cb<List<RegistroToma>> cb) {
        final int id = idUsuario > 0 ? idUsuario : idUsuario(ctx);
        if (id == -1) { cb.onError("Sesión no sincronizada"); return; }

        final String ahora = SDF_TS_LOCAL.format(new java.util.Date());

        listarTomasDelDiaDe(id, desdeYMD, new Cb<List<RegistroToma>>() {
            @Override public void onOk(List<RegistroToma> todas) {
                List<RegistroToma> olvidos = new ArrayList<>();
                for (RegistroToma t : todas) {
                    String prog = t.getFechaHoraProgramada();
                    if (!t.estaOlvidada() || prog == null) continue;
                    if (prog.compareTo(ahora) > 0) continue;   // todavía no le tocaba
                    olvidos.add(t);
                }
                // Más reciente primero: lo de ayer se corrige, lo de hace
                // dos semanas ya no lo recuerda nadie.
                java.util.Collections.sort(olvidos, (a, b) ->
                    b.getFechaHoraProgramada().compareTo(a.getFechaHoraProgramada()));
                cb.onOk(olvidos);
            }
            @Override public void onError(String msg) { cb.onError(msg); }
        });
    }

    /**
     * Marca una dosis olvidada como tomada.
     *
     * @param idQuienConfirma 0 cuando la marca el propio paciente. Si la
     *        corrige el cuidador va SU id, y la fila queda diciendo que la
     *        confirmación no salió del paciente. Ver la nota de
     *        confirmado_por en RegistroToma: sin esa distinción el
     *        porcentaje de adherencia deja de significar algo.
     */
    public static void confirmarOlvido(int idRegistro, int idQuienConfirma, Cb<Void> cb) {
        RegistroTomaPatch cambios = new RegistroTomaPatch();
        cambios.estado = "confirmada";
        cambios.fechaHoraConfirmacion = SDF_TS_LOCAL.format(new java.util.Date());
        if (idQuienConfirma > 0) cambios.confirmadoPor = idQuienConfirma;

        SupabaseClient.getService()
            .actualizarRegistroToma("eq." + idRegistro, cambios)
            .enqueue(VimedRepo.<RegistroToma>voidCbEstricto(cb,
                "No se pudo corregir esta toma. Puede que ya no estés"
                    + " vinculado a esa persona."));
    }

    private static final java.text.SimpleDateFormat SDF_TS_LOCAL =
        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

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

    /** Versión bloqueante para AlarmaReceiver y AlarmaSync. */
    public static List<CitaMedica> listarCitasSync(Context ctx) {
        int idUsuario = idUsuario(ctx);
        if (idUsuario == -1) return new ArrayList<>();
        return ejecutar(SupabaseClient.getService()
            .getCitas("eq." + idUsuario, "fecha_hora.asc"));
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
                        // mensajeDeError trae el cuerpo de la respuesta: PostgREST
                        // dice ahí qué columna o restricción falló, y
                        // sin eso un 400 no se puede diagnosticar.
                        cb.onError(mensajeDeError(r));
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
        // Se busca por RPC y no consultando la tabla usuarios. El RLS
        // de esa tabla solo deja ver tu propia fila y la de los
        // pacientes que YA cuidás, así que para vincular a alguien
        // nuevo devolvía siempre vacío y la pantalla lo mostraba como
        // "no existe ninguna cuenta con ese correo". La función
        // compara en minúsculas y recortada, así que acá alcanza con
        // mandar lo que escribió la persona.
        String buscado = correo == null ? "" : correo.trim();
        java.util.Map<String, String> cuerpo = new java.util.HashMap<>();
        cuerpo.put("p_correo", buscado);
        SupabaseClient.getService().buscarUsuarioPorCorreo(cuerpo)
            .enqueue(new Callback<List<com.tesis.vimed.models.UsuarioSupabase>>() {
                @Override
                public void onResponse(Call<List<com.tesis.vimed.models.UsuarioSupabase>> c,
                                       Response<List<com.tesis.vimed.models.UsuarioSupabase>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        cb.onOk(r.body().get(0));
                    } else if (r.isSuccessful()) {
                        cb.onOk(null);   // no existe — el llamador decide qué mostrar
                    } else if (r.code() == 404) {
                        // PGRST202: la función no existe en la base. Pasa si
                        // no se corrió supabase_buscar_por_correo.sql. Sin
                        // este aviso el error se lee como un problema de red.
                        cb.onError("Falta crear la búsqueda por correo en la "
                            + "base de datos (supabase_buscar_por_correo.sql)");
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

    /**
     * Solo los pacientes que ACEPTARON. Es lo que tiene que usar la pantalla
     * del cuidador: un vínculo pendiente todavía no da acceso a nada, y
     * mostrarlo como paciente dejaría la pantalla intentando cargar datos
     * que el RLS no va a devolver.
     */
    public static void listarPacientesAceptados(
            Context ctx, Cb<List<com.tesis.vimed.models.Vinculacion>> cb) {
        listarMisPacientes(ctx, new Cb<List<com.tesis.vimed.models.Vinculacion>>() {
            @Override public void onOk(List<com.tesis.vimed.models.Vinculacion> todos) {
                List<com.tesis.vimed.models.Vinculacion> ok = new ArrayList<>();
                for (com.tesis.vimed.models.Vinculacion v : todos) {
                    if (v.estaAceptado()) ok.add(v);
                }
                cb.onOk(ok);
            }
            @Override public void onError(String msg) { cb.onError(msg); }
        });
    }

    /**
     * Vincula un CUIDADOR al usuario logueado, que es el adulto mayor.
     *
     * @param idFamiliar a quién le doy acceso a mi medicación.
     */
    public static void crearVinculo(Context ctx, int idFamiliar, Cb<Void> cb) {
        int idAdulto = idUsuario(ctx);
        if (idAdulto == -1) { cb.onError("Sesión no sincronizada"); return; }
        guardarVinculo(idAdulto, idFamiliar, idAdulto, cb);
    }

    /**
     * Vincula un PACIENTE al usuario logueado, que es el cuidador.
     *
     * Es la misma tabla con las columnas al revés, y esa distinción es todo:
     * una fila guardada del lado equivocado deja al cuidador buscándose a sí
     * mismo en id_familiar y su pantalla aparece vacía.
     *
     * @param idAdulto a quién voy a cuidar.
     */
    public static void crearVinculoComoCuidador(Context ctx, int idAdulto, Cb<Void> cb) {
        int idFamiliar = idUsuario(ctx);
        if (idFamiliar == -1) { cb.onError("Sesión no sincronizada"); return; }
        guardarVinculo(idAdulto, idFamiliar, idFamiliar, cb);
    }

    private static void guardarVinculo(int idAdulto, int idFamiliar,
                                       int solicitadoPor, Cb<Void> cb) {
        if (idAdulto == idFamiliar) {
            cb.onError("No se puede vincular una cuenta consigo misma");
            return;
        }
        // Nace pendiente; la otra punta tiene que aceptarlo. El RLS lo
        // exige además de esto, así que un cliente modificado tampoco puede
        // crear un vínculo ya aceptado.
        com.tesis.vimed.models.Vinculacion v =
            new com.tesis.vimed.models.Vinculacion(idAdulto, idFamiliar, solicitadoPor);
        SupabaseClient.getService().crearVinculo(v)
            .enqueue(VimedRepo.<com.tesis.vimed.models.Vinculacion>voidCb(cb));
    }

    /** Acepta una solicitud de vínculo. Solo funciona si no la pediste vos. */
    public static void aceptarVinculo(int idVinculo, Cb<Void> cb) {
        responderVinculo(idVinculo, com.tesis.vimed.models.Vinculacion.ACEPTADO, cb);
    }

    /** Rechaza una solicitud. La fila queda como registro, sin dar acceso. */
    public static void rechazarVinculo(int idVinculo, Cb<Void> cb) {
        responderVinculo(idVinculo, com.tesis.vimed.models.Vinculacion.RECHAZADO, cb);
    }

    private static void responderVinculo(int idVinculo, String estado, Cb<Void> cb) {
        VinculoPatch cambios = new VinculoPatch();
        cambios.estado = estado;
        SupabaseClient.getService()
            .actualizarVinculo("eq." + idVinculo, cambios)
            .enqueue(VimedRepo.<com.tesis.vimed.models.Vinculacion>voidCbEstricto(cb,
                "Esta solicitud ya no está pendiente. Actualizá la lista."));
    }

    /**
     * Solicitudes que esperan una respuesta MÍA, de los dos lados: puedo
     * recibir un pedido tanto de alguien que quiere cuidarme como de alguien
     * a quien me proponen cuidar.
     */
    public static void listarSolicitudesPendientes(
            Context ctx, Cb<List<com.tesis.vimed.models.Vinculacion>> cb) {
        final int yo = idUsuario(ctx);
        if (yo == -1) { cb.onError("Sesión no sincronizada"); return; }

        final List<com.tesis.vimed.models.Vinculacion> pendientes = new ArrayList<>();

        // Dos consultas porque son dos columnas distintas; la segunda se
        // encadena a la primera para no tener que coordinar dos callbacks.
        listarMisCuidadores(ctx, new Cb<List<com.tesis.vimed.models.Vinculacion>>() {
            @Override public void onOk(List<com.tesis.vimed.models.Vinculacion> mios) {
                agregarLosQueEsperan(mios, yo, pendientes);
                listarMisPacientes(ctx, new Cb<List<com.tesis.vimed.models.Vinculacion>>() {
                    @Override public void onOk(List<com.tesis.vimed.models.Vinculacion> otros) {
                        agregarLosQueEsperan(otros, yo, pendientes);
                        cb.onOk(pendientes);
                    }
                    @Override public void onError(String msg) { cb.onOk(pendientes); }
                });
            }
            @Override public void onError(String msg) { cb.onError(msg); }
        });
    }

    private static void agregarLosQueEsperan(
            List<com.tesis.vimed.models.Vinculacion> origen, int yo,
            List<com.tesis.vimed.models.Vinculacion> destino) {
        if (origen == null) return;
        for (com.tesis.vimed.models.Vinculacion v : origen) {
            if (v.esperaRespuestaDe(yo)) destino.add(v);
        }
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

    /**
     * Como {@link #voidCb}, pero una respuesta SIN filas cuenta como error.
     *
     * Cuando el RLS bloquea un UPDATE, PostgREST no devuelve un error:
     * devuelve 200 con una lista vacía, porque "cero filas cumplían la
     * condición" es una respuesta legítima. Con voidCb eso se lee como
     * éxito, y la pantalla llega a decir "Listo, ya podés ver la medicación
     * de Rosa" sin que haya cambiado nada.
     *
     * Sirve porque el cliente manda Prefer: return=representation en todas
     * las llamadas (ver SupabaseClient), así que las filas afectadas vienen
     * en el cuerpo.
     */
    private static <T> Callback<List<T>> voidCbEstricto(Cb<Void> cb, String siNoCambioNada) {
        return new Callback<List<T>>() {
            @Override
            public void onResponse(Call<List<T>> c, Response<List<T>> r) {
                if (!r.isSuccessful())              cb.onError(mensajeDeError(r));
                else if (r.body() == null || r.body().isEmpty()) cb.onError(siNoCambioNada);
                else                                cb.onOk(null);
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
