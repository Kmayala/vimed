package com.tesis.vimed.utils;

import android.content.Context;

import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Re-programa en este celular las alarmas de TODOS los medicamentos del
 * usuario logueado, leyéndolos de Supabase.
 *
 * Hace falta porque AlarmManager es local: si el CUIDADOR carga un
 * medicamento desde su teléfono, el del adulto mayor no se entera solo.
 * Sin esto, la alarma recién aparecería después de un reinicio.
 *
 * MainActivity lo llama en onResume. Reprogramar una alarma ya existente
 * es inofensivo: los PendingIntent usan el mismo request code, así que
 * FLAG_UPDATE_CURRENT la pisa en vez de duplicarla.
 */
public final class AlarmaSync {

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private AlarmaSync() {}

    /** Corre en segundo plano; no bloquea la pantalla. */
    public static void sincronizar(Context context) {
        final Context appCtx = context.getApplicationContext();
        POOL.execute(() -> {
            try {
                List<Medicamento> meds = VimedRepo.listarMedicamentosSync(appCtx);
                for (Medicamento m : meds) {
                    for (Horario h : VimedRepo.listarHorariosSync(m.getId())) {
                        NotificationHelper.programarAlarmas(appCtx,
                            m.getId(), h.getId(), h.getHoraInicio(), h.getIntervaloHoras());
                    }
                }
            } catch (Exception ignored) {
                // Sin red o sesión vencida: lo reintenta el próximo onResume.
            }
        });
    }
}
