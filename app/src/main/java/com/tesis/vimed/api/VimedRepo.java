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

    /** Los campos que edita el formulario de cita; el estado va aparte. */
    private static class CitaEdicion {
        String medico;
        String especialidad;
        @com.google.gson.annotations.SerializedName("fecha_hora")
        String fechaHora;
        String lugar;
        String notas;
        Double latitud;
        Double longitud;
    }

    private static class HorarioPatch {
        @com.google.gson.annotations.SerializedName("hora_inicio")
        String horaInicio;
        @com.google.gson.annotations.SerializedName("intervalo_horas")
        Integer intervaloHoras;
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

    /**
     * Un medicamento por su id, sin filtrar por activo.
     *
     * Sin filtro a propósito: la pantalla de detalle se puede abrir para uno
     * que se acaba de dar de baja —desde el historial, por ejemplo— y
     * mostrarle "ya no está" a alguien que lo está mirando en la lista
     * sería mentirle.
     */
    public static void buscarMedicamento(int idMedicamento, Cb<Medicamento> cb) {
        SupabaseClient.getService().getMedicamentoPorId("eq." + idMedicamento)
            .enqueue(new Callback<List<Medicamento>>() {
                @Override
                public void onResponse(Call<List<Medicamento>> c,
                                       Response<List<Medicamento>> r) {
                    if (!r.isSuccessful()) { cb.onError(mensajeDeError(r)); return; }
                    List<Medicamento> body = r.body();
                    cb.onOk(body == null || body.isEmpty() ? null : body.get(0));
                }
                @Override
                public void onFailure(Call<List<Medicamento>> c, Throwable t) {
                    cb.onError(mensajeDeFallo(t));
                }
            });
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
                    cb.onError(mensajeDeFallo(t));
                }
            });
    }

    /**
     * Guarda los cambios de la pantalla de edición.
     *
     * Manda el medicamento ENTERO y no un parche: acá se está guardando un
     * formulario en el que la persona pudo tocar cualquier campo, y armar
     * un parche con "solo lo que cambió" obligaría a comparar campo por
     * campo contra el original. Vaciar un campo a propósito —borrar el
     * vencimiento, por ejemplo— es indistinguible de "no lo toqué" en un
     * parche, y ese es justo el caso que se rompe en silencio.
     *
     * Va con el callback estricto: si es el medicamento de un paciente y el
     * RLS lo rechaza, PostgREST devuelve 200 con lista vacía y sin esto la
     * pantalla diría "Guardado" sin haber guardado.
     */
    public static void actualizarMedicamento(int idMedicamento, Medicamento med,
                                             Cb<Void> cb) {
        // Se manda una COPIA armada acá y no el objeto que llega. Dos
        // motivos, los dos silenciosos si se pasan por alto:
        //
        //   · el id no puede viajar en el cuerpo —es la clave por la que se
        //     filtra, y PostgREST rechaza reescribir la PK—, y el modelo no
        //     tiene forma de volverlo a null una vez seteado;
        //   · `activo` es un boolean primitivo, así que Gson lo manda
        //     SIEMPRE. Un objeto al que nadie le puso activo = true daría de
        //     baja el medicamento al guardarlo. El constructor lo deja en
        //     true, que es lo correcto acá: se está editando uno vigente, y
        //     la baja tiene su propio camino (eliminarMedicamento).
        Medicamento copia = new Medicamento(
            med.getIdUsuario(), med.getNombre(), med.getPresentacion(),
            med.getDosis(), med.getUnidad(), med.getInstrucciones(),
            med.getColorIcono(), med.getStockActual(), med.getStockMinimo());
        copia.setFechaVencimiento(med.getFechaVencimiento());

        SupabaseClient.getService()
            .actualizarMedicamentoCompleto("eq." + idMedicamento, copia)
            .enqueue(VimedRepo.<Medicamento>voidCbEstricto(cb,
                "No se pudo guardar. Puede que el vínculo con esa persona ya no exista."));
    }

    /** Cambia la hora de inicio y la frecuencia de un horario. */
    public static void actualizarHorario(int idHorario, String horaHHMM,
                                         int intervaloHoras, Cb<Void> cb) {
        HorarioPatch cambios = new HorarioPatch();
        cambios.horaInicio = horaHHMM;
        cambios.intervaloHoras = intervaloHoras;
        SupabaseClient.getService()
            .actualizarHorario("eq." + idHorario, cambios)
            .enqueue(VimedRepo.<Horario>voidCbEstricto(cb,
                "No se pudo guardar el horario."));
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
                "No se pudo guardar. Puede que el vínculo con esa persona ya no exista."));
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
                    cb.onError(mensajeDeFallo(t));
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

    /**
     * La fila de esa toma si ya existe, o null.
     *
     * @param fechaProgramada "yyyy-MM-dd HH:mm:ss", igual que la escribe
     *                        {@link com.tesis.vimed.utils.TomaManager#fechaHoyCon}.
     */
    public static RegistroToma buscarTomaDelSlotSync(int idHorario, String fechaProgramada) {
        if (idHorario <= 0 || fechaProgramada == null) return null;
        List<RegistroToma> res = ejecutar(SupabaseClient.getService()
            .getRegistroDelSlot("eq." + idHorario, "eq." + fechaProgramada));
        return res.isEmpty() ? null : res.get(0);
    }

    /**
     * Devuelve la fila de esa toma, creándola solo si no existía.
     *
     * Todos los caminos que registran una toma pasan por acá. Antes cada
     * uno insertaba lo suyo: la alarma creaba la fila "omitida" al sonar,
     * el snooze la volvía a crear al re-disparar, y confirmar desde el
     * panel —que no conocía el id de ninguna— insertaba una tercera ya
     * confirmada. En el historial del cuidador la misma dosis de las 07:50
     * aparecía dos y tres veces, con estados que se contradecían.
     *
     * Si el INSERT rebota por el índice único (dos caminos a la vez), se
     * vuelve a buscar: la fila que ganó la carrera es la buena.
     */
    public static RegistroToma asegurarTomaSync(RegistroToma r) {
        RegistroToma existente = buscarTomaDelSlotSync(
            r.getIdHorario(), r.getFechaHoraProgramada());
        if (existente != null) return existente;

        List<RegistroToma> res = ejecutar(SupabaseClient.getService().crearRegistroToma(r));
        if (!res.isEmpty()) return res.get(0);

        return buscarTomaDelSlotSync(r.getIdHorario(), r.getFechaHoraProgramada());
    }

    /**
     * @deprecated Inserta siempre, sin mirar si la toma ya estaba
     *             registrada. Usar {@link #asegurarTomaSync(RegistroToma)}.
     */
    @Deprecated
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
    /**
     * Marca la toma como confirmada actualizando su fila si ya existe, y
     * creándola solo si no. Versión asíncrona de la misma idea que
     * {@link #asegurarTomaSync(RegistroToma)}, para el panel.
     *
     * Este es el camino que más duplicaba: el panel arma la lista del día
     * cuando se abre la pantalla, así que si la alarma sonó DESPUÉS, el
     * botón "Ya tomé" no tiene el id de la fila que creó la alarma y
     * terminaba insertando una segunda.
     */
    public static void confirmarTomaDelSlot(RegistroToma r, String fechaConfirmacion,
                                            Cb<Void> cb) {
        SupabaseClient.getService()
            .getRegistroDelSlot("eq." + r.getIdHorario(),
                                "eq." + r.getFechaHoraProgramada())
            .enqueue(new Callback<List<RegistroToma>>() {
                @Override
                public void onResponse(Call<List<RegistroToma>> c,
                                       Response<List<RegistroToma>> res) {
                    List<RegistroToma> filas = res.isSuccessful() && res.body() != null
                        ? res.body() : new ArrayList<>();

                    if (filas.isEmpty()) {
                        crearTomaConfirmada(r, new Cb<RegistroToma>() {
                            @Override public void onOk(RegistroToma creado) { cb.onOk(null); }
                            @Override public void onError(String msg) { cb.onError(msg); }
                        });
                        return;
                    }
                    actualizarEstadoToma(filas.get(0).getId(), "confirmada",
                        fechaConfirmacion, cb);
                }

                @Override
                public void onFailure(Call<List<RegistroToma>> c, Throwable t) {
                    cb.onError(mensajeDeFallo(t));
                }
            });
    }

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
                    cb.onError(mensajeDeFallo(t));
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
                    cb.onError(mensajeDeFallo(t));
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

        // Por RPC: el cuidador no tiene UPDATE sobre usuarios, y no se lo
        // vamos a dar —el mismo permiso que le deja tocar el peso de su
        // paciente le dejaría cambiarle el rol—. La función escribe esas
        // dos columnas y ninguna más, y comprueba ella el vínculo.
        //
        // El 0 viaja como null: significa "borrar el dato", no "pesa cero".
        java.util.Map<String, Object> cuerpo = new java.util.HashMap<>();
        cuerpo.put("p_id_usuario", id);
        cuerpo.put("p_peso_kg", pesoKg > 0 ? (Object) pesoKg : null);
        cuerpo.put("p_anio_nacimiento", anioNacimiento > 0 ? (Object) anioNacimiento : null);

        SupabaseClient.getService().guardarDatosClinicos(cuerpo)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> c, Response<Void> r) {
                    if (r.code() == 404) {
                        cb.onError("Falta crear guardar_datos_clinicos en la base"
                            + " (supabase_datos_clinicos_compartidos.sql)");
                        return;
                    }
                    if (!r.isSuccessful()) { cb.onError(mensajeDeError(r)); return; }

                    // La caché local es SOLO del perfil propio: el peso del
                    // paciente no puede pisar el del cuidador en su sesión.
                    if (esElPropio) {
                        new SessionManager(ctx)
                            .guardarDatosClinicos(pesoKg, anioNacimiento);
                    }
                    cb.onOk(null);
                }
                @Override
                public void onFailure(Call<Void> c, Throwable t) {
                    cb.onError(mensajeDeFallo(t));
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
                "No se pudo corregir esta toma. Puede que el vínculo"
                    + " con esa persona ya no exista."));
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
                    cb.onError(mensajeDeFallo(t));
                }
            });
    }

    /**
     * Guarda los cambios de una cita editada.
     *
     * No manda la cita entera: un POST completo pisaría id_usuario y estado
     * con lo que traiga el objeto en memoria, y el estado se cambia por otro
     * camino (los botones del detalle). Solo viajan los campos del
     * formulario.
     */
    public static void actualizarCita(int idCita, CitaMedica cambios, Cb<Void> cb) {
        CitaEdicion patch = new CitaEdicion();
        patch.medico       = cambios.getMedico();
        patch.especialidad = cambios.getEspecialidad();
        patch.fechaHora    = cambios.getFechaHora();
        patch.lugar        = cambios.getLugar();
        patch.notas        = cambios.getNotas();
        patch.latitud      = cambios.getLatitud();
        patch.longitud     = cambios.getLongitud();

        SupabaseClient.getService()
            .actualizarCita("eq." + idCita, patch)
            .enqueue(VimedRepo.<CitaMedica>voidCb(cb));
    }

    /** Una cita puntual, para la pantalla de detalle. */
    public static void buscarCita(int idCita, Cb<CitaMedica> cb) {
        SupabaseClient.getService().getCitaPorId("eq." + idCita)
            .enqueue(new Callback<List<CitaMedica>>() {
                @Override
                public void onResponse(Call<List<CitaMedica>> c,
                                       Response<List<CitaMedica>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        cb.onOk(r.body().get(0));
                    } else {
                        cb.onError("No se encontró la cita");
                    }
                }
                @Override
                public void onFailure(Call<List<CitaMedica>> c, Throwable t) {
                    cb.onError("Sin conexión: " + t.getMessage());
                }
            });
    }

    /** @param estado pendiente | confirmada | asistida | cancelada */
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
                    cb.onError(mensajeDeFallo(t));
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
                    cb.onError(mensajeDeFallo(t));
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
                    cb.onError(mensajeDeFallo(t));
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
                    @Override public void onError(String msg) {
                        // Falla la SEGUNDA consulta. Devolver lo que trajo la
                        // primera mostraría "1 solicitud" cuando hay tres, y
                        // eso es peor que no mostrar nada: la persona entra,
                        // responde la que ve y se queda tranquila.
                        cb.onError(msg);
                    }
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
                    cb.onError(mensajeDeFallo(t));
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
                    cb.onError(mensajeDeFallo(t));
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
                    cb.onError(mensajeDeFallo(t));
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
    /**
     * Por qué falló una llamada, dicho de forma que se pueda actuar.
     *
     * Antes todo caía en "Sin conexión: timeout", que es confuso cuando la
     * red anda: un timeout no es lo mismo que estar sin señal, y la
     * respuesta de la persona es distinta —esperar y reintentar, o buscar
     * señal—. El texto de la excepción tampoco ayudaba: llega en inglés y
     * con el nombre del host adentro.
     */
    public static String mensajeDeFallo(Throwable t) {
        if (t instanceof java.net.SocketTimeoutException) {
            return "El servidor tardó demasiado en responder. Probá de nuevo.";
        }
        if (t instanceof java.net.UnknownHostException) {
            return "No hay conexión a internet.";
        }
        if (t instanceof javax.net.ssl.SSLException) {
            return "No se pudo establecer una conexión segura. "
                + "Revisá la fecha y hora del celular.";
        }
        if (t instanceof java.io.InterruptedIOException) {
            return "La conexión se cortó a mitad de camino. Probá de nuevo.";
        }
        return "Sin conexión. Revisá tus datos o el wifi.";
    }

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
                cb.onError(mensajeDeFallo(t));
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
                cb.onError(mensajeDeFallo(t));
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
                cb.onError(mensajeDeFallo(t));
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
