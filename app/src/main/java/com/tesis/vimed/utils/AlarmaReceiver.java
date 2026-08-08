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

        Medicamento med = VimedRepo.buscarMedicamentoSync(idMedicamento);
        if (med == null) return;

        // 1) Registrar la toma como "omitida": si nadie confirma, así queda.
        int idRegistro = -1;
        if (idHorario > 0) {
            RegistroToma r = new RegistroToma(idHorario, med.getIdUsuario(),
                TomaManager.fechaHoyCon(hora));
            RegistroToma creado = VimedRepo.crearTomaSync(r);
            if (creado != null) idRegistro = creado.getId();
        }

        // 2) Notificación con botones
        String dosis = med.getDosis() == (int) med.getDosis()
            ? String.valueOf((int) med.getDosis())
            : String.valueOf(med.getDosis());
        String mensaje = med.getNombre() + " — " + dosis
            + " " + (med.getUnidad() != null ? med.getUnidad() : "");

        NotificationHelper.mostrarRecordatorioToma(ctx,
            idMedicamento, idHorario, idRegistro, hora, indice,
            "Hora de tu medicamento", mensaje);

        NotificacionSync.registrar(ctx, Notificacion.TIPO_TOMA,
            "Recordatorio enviado: " + mensaje + " (" + hora + ")");

        // 3) Reagendar para dentro de 24 h (setExactAndAllowWhileIdle dispara una vez)
        long enUnDia = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
        NotificationHelper.reagendarEn(ctx, idMedicamento, idHorario, hora, indice, enUnDia);
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
    }

    // ═══ Reboot — re-agendar todo ══════════════════════════════
    private void onBoot(Context ctx) {
        List<Medicamento> meds = VimedRepo.listarMedicamentosSync(ctx);
        for (Medicamento m : meds) {
            for (Horario h : VimedRepo.listarHorariosSync(m.getId())) {
                NotificationHelper.programarAlarmas(ctx,
                    m.getId(), h.getId(), h.getHoraInicio(), h.getIntervaloHoras());
            }
        }
    }
}
