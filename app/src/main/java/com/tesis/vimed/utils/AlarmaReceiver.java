package com.tesis.vimed.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tesis.vimed.api.NotificacionSync;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.Notificacion;
import com.tesis.vimed.models.RegistroToma;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Único punto de entrada de eventos de alarmas de medicación:
 *   - ACTION_FIRE    → hora de tomar: registra, notifica y reagenda.
 *   - ACTION_CONFIRM → "Confirmar toma" en la notificación.
 *   - ACTION_SNOOZE  → "Posponer 15 min".
 *   - BOOT_COMPLETED → el celular arrancó: re-agendar todas las alarmas.
 *
 * Los datos viven en Supabase, así que todo el trabajo va a un hilo de
 * fondo con goAsync(): onReceive corre en el hilo principal y una llamada
 * de red ahí tiraría NetworkOnMainThreadException.
 */
public class AlarmaReceiver extends BroadcastReceiver {

    private static final SimpleDateFormat SDF_TS =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) action = NotificationHelper.ACTION_FIRE;

        final String accion = action;
        final Context appCtx = context.getApplicationContext();

        // Copiamos los extras: el Intent no sobrevive al final de onReceive.
        final int idMedicamento = intent.getIntExtra(NotificationHelper.EXTRA_ID_MED, -1);
        final int idHorario     = intent.getIntExtra(NotificationHelper.EXTRA_ID_HORARIO, -1);
        final int idRegistro    = intent.getIntExtra(NotificationHelper.EXTRA_ID_REG, -1);
        final String hora       = intent.getStringExtra(NotificationHelper.EXTRA_HORA);
        final int indice        = intent.getIntExtra(NotificationHelper.EXTRA_INDICE, 0);

        final PendingResult pending = goAsync();
        POOL.execute(() -> {
            try {
                switch (accion) {
                    case NotificationHelper.ACTION_FIRE:
                        onFire(appCtx, idMedicamento, idHorario, hora, indice);
                        break;
                    case NotificationHelper.ACTION_CONFIRM:
                        onConfirm(appCtx, idMedicamento, idHorario, idRegistro, hora, indice);
                        break;
                    case NotificationHelper.ACTION_SNOOZE:
                        onSnooze(appCtx, idMedicamento, idHorario, idRegistro, hora, indice);
                        break;
                    case Intent.ACTION_BOOT_COMPLETED:
                    case Intent.ACTION_LOCKED_BOOT_COMPLETED:
                        onBoot(appCtx);
                        break;
                }
            } finally {
                pending.finish();
            }
        });
    }

    // ═══ Disparo de alarma ═════════════════════════════════════
    private void onFire(Context ctx, int idMedicamento, int idHorario,
                        String hora, int indice) {
        if (idMedicamento == -1) return;

        // 1) REAGENDAR PRIMERO, antes que cualquier otra cosa.
        //    setExactAndAllowWhileIdle dispara una sola vez, así que la alarma
        //    de mañana nace acá. Si esto quedara al final, cualquier fallo de
        //    red más arriba se llevaba puesto el recordatorio PARA SIEMPRE.
        long enUnDia = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
        NotificationHelper.reagendarEn(ctx, idMedicamento, idHorario, hora, indice, enUnDia);

        // 2) SONAR, con lo que tengamos guardado localmente.
        //    Nada de esto toca la red: a la hora de la toma el celular puede
        //    estar sin datos o en ahorro de batería, y la alarma tiene que
        //    sonar igual.
        String nombre = MedCache.nombre(ctx, idMedicamento);
        String dosisTxt = MedCache.dosis(ctx, idMedicamento);
        if (nombre == null) nombre = "Tu medicamento";   // primera vez sin caché

        String mensaje = dosisTxt.isEmpty() ? nombre : nombre + " — " + dosisTxt;

        NotificationHelper.mostrarRecordatorioToma(ctx,
            idMedicamento, idHorario, -1, hora, indice,
            "Hora de tu medicamento", mensaje, nombre, dosisTxt);

        // 3) Recién ahora, lo que sí necesita conexión. Si falla, ya sonó.
        Medicamento med = VimedRepo.buscarMedicamentoSync(idMedicamento);
        if (med != null) {
            MedCache.guardar(ctx, med);   // refrescamos por si cambió la dosis
        }

        int idUsuario = med != null ? med.getIdUsuario()
                                    : MedCache.idUsuario(ctx, idMedicamento);

        // Registrar la toma como "omitida": si nadie confirma, así queda.
        if (idHorario > 0 && idUsuario > 0) {
            RegistroToma r = new RegistroToma(idHorario, idUsuario,
                TomaManager.fechaHoyCon(hora));
            RegistroToma creado = VimedRepo.crearTomaSync(r);
            if (creado != null) {
                // La notificación ya está en pantalla, pero sin el id de la
                // fila. La repintamos con el id para que los botones sepan
                // cuál actualizar.
                NotificationHelper.mostrarRecordatorioToma(ctx,
                    idMedicamento, idHorario, creado.getId(), hora, indice,
                    "Hora de tu medicamento", mensaje, nombre, dosisTxt, true);
            }
        }

        NotificacionSync.registrar(ctx, Notificacion.TIPO_TOMA,
            "Recordatorio enviado: " + mensaje + " (" + hora + ")", false);
    }

    // ═══ Botón "Confirmar toma" ════════════════════════════════
    private void onConfirm(Context ctx, int idMedicamento, int idHorario,
                           int idRegistro, String hora, int indice) {
        if (idMedicamento == -1) return;
        TomaManager.confirmarSync(ctx, idMedicamento, idRegistro, idHorario, hora);
        NotificationHelper.cancelarNotificacion(ctx, idMedicamento, indice);
    }

    // ═══ Botón "Posponer 15 min" ═══════════════════════════════
    private void onSnooze(Context ctx, int idMedicamento, int idHorario,
                          int idRegistro, String hora, int indice) {
        if (idMedicamento == -1) return;

        if (idRegistro > 0) {
            VimedRepo.actualizarEstadoTomaSync(idRegistro, "pospuesta", null);
        }

        long enSnoozeMs = System.currentTimeMillis()
            + NotificationHelper.SNOOZE_MINUTOS * 60L * 1000L;
        NotificationHelper.reagendarEn(ctx, idMedicamento, idHorario, hora, indice, enSnoozeMs);

        NotificationHelper.cancelarNotificacion(ctx, idMedicamento, indice);

        // Esto SÍ le importa al cuidador: la dosis quedó sin tomar.
        // Llega acá tanto si la persona tocó "Posponer" como si no respondió
        // la alarma y AlarmaActivity la pospuso sola.
        Medicamento med = VimedRepo.buscarMedicamentoSync(idMedicamento);
        String nombreMed = med != null && med.getNombre() != null
            ? med.getNombre() : "su medicamento";
        NotificacionSync.registrar(ctx, Notificacion.TIPO_TOMA,
            "No confirmó la toma de " + nombreMed
                + (hora != null ? " de las " + hora : "") + ".", true);
    }

    // ═══ Reboot — re-agendar todo ══════════════════════════════
    private void onBoot(Context ctx) {
        List<Medicamento> meds = VimedRepo.listarMedicamentosSync(ctx);
        for (Medicamento m : meds) {
            MedCache.guardar(ctx, m);   // así la alarma puede sonar sin red
            for (Horario h : VimedRepo.listarHorariosSync(m.getId())) {
                NotificationHelper.programarAlarmas(ctx,
                    m.getId(), h.getId(), h.getHoraInicio(), h.getIntervaloHoras());
            }
        }
    }
}
