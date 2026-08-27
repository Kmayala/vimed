package com.tesis.vimed.utils;

import android.content.Context;

import com.tesis.vimed.api.NotificacionSync;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.Notificacion;
import com.tesis.vimed.models.RegistroToma;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Confirmar / deshacer una toma. Los datos viven en Supabase.
 *
 * Dos caminos llegan acá:
 *   - El botón "Confirmar toma" de la notificación (AlarmaReceiver) → *Sync
 *   - Tocar la toma en el dashboard (MainActivity)                  → async
 *
 * Los métodos *Sync son BLOQUEANTES: solo llamarlos desde un hilo
 * de fondo (AlarmaReceiver usa goAsync + executor).
 */
public final class TomaManager {

    private static final SimpleDateFormat SDF_TS =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private TomaManager() {}

    // ═══ Camino asíncrono (dashboard) ══════════════════════════

    /**
     * Marca la toma como confirmada, descuenta stock y sincroniza.
     *
     * @param idRegistro fila existente en registro_tomas, o -1 si la alarma
     *                   todavía no disparó (la persona se adelanta).
     * @param idHorario  necesario si idRegistro es -1, para crear la fila.
     * @param horaHHMM   hora programada "HH:mm".
     */
    public static void confirmar(Context ctx, Medicamento med, int idRegistro,
                                 int idHorario, String horaHHMM,
                                 VimedRepo.Cb<Void> cb) {
        if (med == null) { cb.onError("Medicamento no encontrado"); return; }
        String ahora = SDF_TS.format(new Date());

        VimedRepo.Cb<Void> trasRegistrar = new VimedRepo.Cb<Void>() {
            @Override public void onOk(Void v) {
                descontarStock(ctx, med);
                cb.onOk(null);
            }
            @Override public void onError(String msg) { cb.onError(msg); }
        };

        if (idRegistro > 0) {
            VimedRepo.actualizarEstadoToma(idRegistro, "confirmada", ahora, trasRegistrar);
        } else if (idHorario > 0) {
            // Sin id de fila no significa que no exista: el panel arma su
            // lista al abrirse, y la alarma pudo haber creado la fila
            // después. Se busca antes de insertar.
            RegistroToma r = new RegistroToma(idHorario, med.getIdUsuario(),
                fechaHoyCon(horaHHMM));
            r.setEstado("confirmada");
            r.setFechaHoraConfirmacion(ahora);
            VimedRepo.confirmarTomaDelSlot(r, ahora, new VimedRepo.Cb<Void>() {
                @Override public void onOk(Void v) { trasRegistrar.onOk(null); }
                @Override public void onError(String msg) { cb.onError(msg); }
            });
        } else {
            cb.onError("No se pudo identificar la toma");
        }
    }

    /** Deshace una confirmación y devuelve la unidad al stock. */
    public static void deshacer(Context ctx, Medicamento med, int idRegistro,
                                VimedRepo.Cb<Void> cb) {
        if (idRegistro <= 0 || med == null) { cb.onError("Toma no encontrada"); return; }

        VimedRepo.actualizarEstadoToma(idRegistro, "omitida", null,
            new VimedRepo.Cb<Void>() {
                @Override public void onOk(Void v) {
                    VimedRepo.actualizarStock(med.getId(), med.getStockActual() + 1,
                        new VimedRepo.Cb<Void>() {
                            @Override public void onOk(Void x) {
                                MedCache.guardarStock(ctx, med.getId(),
                                    med.getStockActual() + 1);
                                cb.onOk(null);
                            }
                            @Override public void onError(String msg) { cb.onError(msg); }
                        });
                }
                @Override public void onError(String msg) { cb.onError(msg); }
            });
    }

    private static void descontarStock(Context ctx, Medicamento med) {
        int nuevoStock = Math.max(0, med.getStockActual() - 1);
        VimedRepo.actualizarStock(med.getId(), nuevoStock, new VimedRepo.Cb<Void>() {
            @Override public void onOk(Void v) {
                // La alarma de mañana mira la caché para decidir si suena.
                MedCache.guardarStock(ctx, med.getId(), nuevoStock);

                if (nuevoStock <= med.getStockMinimo()) {
                    String msgStock = "Quedan " + nuevoStock + " unidades de "
                        + med.getNombre() + ". Acordate de comprar más.";
                    NotificationHelper.mostrarNotificacion(ctx,
                        "Stock bajo — " + med.getNombre(), msgStock,
                        med.getId() + 10_000);
                    NotificacionSync.registrar(ctx, Notificacion.TIPO_STOCK, msgStock);
                }
                NotificacionSync.registrar(ctx, Notificacion.TIPO_TOMA,
                    "Toma confirmada: " + med.getNombre());
            }
        });
    }

    // ═══ Camino bloqueante (AlarmaReceiver) ════════════════════

    /** Igual que confirmar(), pero bloqueante. Solo desde hilo de fondo. */
    public static void confirmarSync(Context ctx, int idMedicamento, int idRegistro,
                                     int idHorario, String horaHHMM) {
        String ahora = SDF_TS.format(new Date());

        // LO PRIMERO es dejar registrada la toma. Antes esto empezaba
        // pidiendo el medicamento a la red y hacía `return` si no venía:
        // a la hora de una toma el celular puede estar sin datos, y en ese
        // caso la persona tocaba "Ya tomé", la alarma se callaba y no se
        // guardaba nada. El dato de que se tomó no puede depender de una
        // consulta que solo hace falta para descontar el stock.
        if (idRegistro > 0) {
            VimedRepo.actualizarEstadoTomaSync(idRegistro, "confirmada", ahora);
        } else if (idHorario > 0) {
            // Sin fila previa hay que crearla, y para eso se necesita el
            // id_usuario. Se toma de la caché local, que existe justamente
            // para que la alarma funcione sin conexión.
            int idUsuario = MedCache.idUsuario(ctx, idMedicamento);
            if (idUsuario <= 0) {
                Medicamento m = VimedRepo.buscarMedicamentoSync(idMedicamento);
                if (m != null) idUsuario = m.getIdUsuario();
            }
            if (idUsuario > 0) {
                // La alarma ya pudo haber creado la fila de esta dosis. Si
                // existe se ACTUALIZA; insertar otra dejaba la misma toma
                // dos veces, una sin confirmar y otra confirmada.
                RegistroToma r = new RegistroToma(idHorario, idUsuario,
                    fechaHoyCon(horaHHMM));
                r.setEstado("confirmada");
                r.setFechaHoraConfirmacion(ahora);

                RegistroToma fila = VimedRepo.asegurarTomaSync(r);
                if (fila != null && !fila.estaConfirmada()) {
                    VimedRepo.actualizarEstadoTomaSync(fila.getId(), "confirmada", ahora);
                }
            }
        }

        // De acá para abajo es todo secundario: descontar stock y avisar.
        // Si falla, la toma ya quedó registrada igual.
        Medicamento med = VimedRepo.buscarMedicamentoSync(idMedicamento);
        if (med == null) return;

        int nuevoStock = Math.max(0, med.getStockActual() - 1);
        VimedRepo.actualizarStockSync(med.getId(), nuevoStock);
        MedCache.guardarStock(ctx, med.getId(), nuevoStock);

        if (nuevoStock <= med.getStockMinimo()) {
            String msgStock = "Quedan " + nuevoStock + " unidades de "
                + med.getNombre() + ". Acordate de comprar más.";
            NotificationHelper.mostrarNotificacion(ctx,
                "Stock bajo — " + med.getNombre(), msgStock, idMedicamento + 10_000);
            NotificacionSync.registrar(ctx, Notificacion.TIPO_STOCK, msgStock);
        }
        NotificacionSync.registrar(ctx, Notificacion.TIPO_TOMA,
            "Toma confirmada: " + med.getNombre());
    }

    /** Construye "yyyy-MM-dd HH:mm:00" para el día de hoy con la hora dada. */
    public static String fechaHoyCon(String horaHHMM) {
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(new Date());
        String hora = (horaHHMM != null && horaHHMM.length() >= 5)
            ? horaHHMM.substring(0, 5) : "00:00";
        return hoy + " " + hora + ":00";
    }
}
