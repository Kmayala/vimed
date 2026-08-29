package com.tesis.vimed.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tesis.vimed.api.NotificacionSync;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.CitaMedica;
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
 *   - ACTION_CITA    → recordatorio de cita médica (un día / dos horas antes).
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

        // Extras propios del recordatorio de cita.
        final int idCita        = intent.getIntExtra(RecordatorioCita.EXTRA_ID_CITA, -1);
        final int antelacion    = intent.getIntExtra(RecordatorioCita.EXTRA_ANTELACION,
                                                     RecordatorioCita.ANTES_DOS_HORAS);
        final String citaTitulo = intent.getStringExtra(RecordatorioCita.EXTRA_TITULO);
        final String citaTexto  = intent.getStringExtra(RecordatorioCita.EXTRA_TEXTO);

        // El sonido se corta ACÁ, en el hilo principal y antes de tocar la
        // red: si esperara al trabajo de fondo, la alarma seguiría sonando
        // los segundos que tarde Supabase en responder.
        if (NotificationHelper.ACTION_CONFIRM.equals(accion)
                || NotificationHelper.ACTION_SNOOZE.equals(accion)) {
            AlarmaService.detener(appCtx);
        }

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
                    case RecordatorioCita.ACTION_CITA:
                        onCita(appCtx, idCita, antelacion, citaTitulo, citaTexto);
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

        // 0) ¿ESTE MEDICAMENTO TODAVÍA EXISTE?
        //
        // Se pregunta ANTES de reagendar y antes de sonar, y se responde con
        // la caché local para no depender de la red a esta hora.
        //
        // Una alarma que quedó agendada de antes —porque el borrado no llegó
        // a cancelarla, o porque la agendó una versión anterior de la app— se
        // reagenda sola cada vez que suena. Sin este corte, sonaría para
        // siempre por algo que ya no existe, y no hay forma de apagarla desde
        // la app porque el medicamento ya no aparece en ninguna lista.
        //
        // Solo corta con CERTEZA: estaBorrado() es una marca que se escribe
        // al dar de baja, no "no encontré el nombre". Un medicamento que
        // todavía no se sincronizó no está marcado, así que suena igual.
        if (MedCache.estaBorrado(ctx, idMedicamento)) {
            NotificationHelper.cancelarAlarmas(ctx, idMedicamento);
            return;
        }

        // 1) REAGENDAR, antes que cualquier otra cosa.
        //    setExactAndAllowWhileIdle dispara una sola vez, así que la alarma
        //    de mañana nace acá. Si esto quedara al final, cualquier fallo de
        //    red más arriba se llevaba puesto el recordatorio PARA SIEMPRE.
        //    La de mañana va a la MISMA hora del horario, no 24h después de
        //    este disparo: si hoy se pospuso, mañana tiene que volver a su
        //    hora, no quedarse con el corrimiento (ver proximaOcurrencia).
        NotificationHelper.reagendarEn(ctx, idMedicamento, idHorario, hora, indice,
            NotificationHelper.proximaOcurrencia(hora));

        // 2) SONAR, con lo que tengamos guardado localmente.
        //    Nada de esto toca la red: a la hora de la toma el celular puede
        //    estar sin datos o en ahorro de batería, y la alarma tiene que
        //    sonar igual.
        String nombre = MedCache.nombre(ctx, idMedicamento);
        String dosisTxt = MedCache.dosis(ctx, idMedicamento);
        if (nombre == null) nombre = "Tu medicamento";   // primera vez sin caché

        String mensaje = dosisTxt.isEmpty() ? nombre : nombre + " — " + dosisTxt;

        // Si la app CREE que se terminó, la alarma suena igual y lo dice en
        // el texto.
        //
        // El stock en cero no significa "no tiene el medicamento": significa
        // que hace rato que nadie toca ese número. La persona confirma sus
        // tomas, el contador baja solo hasta cero, compra una caja nueva y no
        // entra a la app a avisarlo —nadie que cuida a alguien de 78 años
        // piensa en eso—. Callar la alarma por ese dato sería dejar sin
        // recordatorio a alguien que sí tiene qué tomar, que es exactamente
        // el daño que esta app existe para evitar.
        //
        // Así que se avisa sin decidir por nadie: suena, y el texto dice lo
        // que la app sabe y lo que hay que hacer si está equivocada.
        boolean seTermino = MedCache.sinStock(ctx, idMedicamento);
        if (seTermino) {
            mensaje += " — Según la app se te terminó. Si todavía te queda,"
                + " tomalo igual y actualizá el stock.";
        }

        // El tono lo pone AlarmaService, no el canal de notificación: así
        // suena como despertador aunque la pantalla completa no llegue a
        // abrirse y aunque después repintemos la notificación.
        boolean conServicio = AlarmaService.iniciar(ctx,
            idMedicamento, idHorario, -1, hora, indice, mensaje, nombre, dosisTxt);

        if (!conServicio) {
            // El sistema no dejó levantar el servicio en segundo plano.
            // Respaldo: notificación con sonido de canal + FLAG_INSISTENT.
            NotificationHelper.mostrarRecordatorioToma(ctx,
                idMedicamento, idHorario, -1, hora, indice,
                "Hora de tu medicamento", mensaje, nombre, dosisTxt);
        }

        // 3) Recién ahora, lo que sí necesita conexión. Si falla, ya sonó.
        Medicamento med = VimedRepo.buscarMedicamentoSync(idMedicamento);

        // Se dio de baja desde OTRO teléfono —el del cuidador, por ejemplo—,
        // así que en este nunca se canceló nada. Recién ahora lo sabemos:
        // esta sonó, pero es la última. Solo con respuesta del servidor; sin
        // conexión, buscarMedicamentoSync devuelve null y ahí no se concluye
        // nada, porque callar una medicación por falta de red sería peor.
        if (med != null && !med.isActivo()) {
            MedCache.marcarBorrado(ctx, idMedicamento);
            NotificationHelper.cancelarAlarmas(ctx, idMedicamento);
            return;
        }

        if (med != null) {
            MedCache.guardar(ctx, med);   // refrescamos por si cambió la dosis
            // La alarma de la toma suena todos los días, así que este es el
            // único punto que garantiza el aviso de vencimiento aunque la
            // persona no abra la app en una semana.
            VencimientoChecker.revisar(ctx, med);
        }

        int idUsuario = med != null ? med.getIdUsuario()
                                    : MedCache.idUsuario(ctx, idMedicamento);

        // Registrar la toma como "omitida": si nadie confirma, así queda.
        //
        // asegurar y no crear: esta alarma puede estar sonando por segunda
        // vez —se pospuso 15 minutos y volvió— y la fila de esa dosis ya
        // existe. Insertando siempre, la misma toma de las 06:19 quedaba dos
        // veces en el historial, una "pospuesta" y otra "sin confirmar".
        if (idHorario > 0 && idUsuario > 0) {
            RegistroToma r = new RegistroToma(idHorario, idUsuario,
                TomaManager.fechaHoyCon(hora));
            // Se copia el nombre y la dosis en la fila: si mañana dan de
            // baja el medicamento, esta toma tiene que seguir diciendo qué
            // era. Salen de la caché, que es lo mismo que se acaba de
            // mostrar en la alarma.
            r.setNombreMedicamento(nombre);
            r.setDosisTexto(dosisTxt);
            RegistroToma creado = VimedRepo.asegurarTomaSync(r);
            if (creado != null) {
                // La notificación ya está en pantalla, pero sin el id de la
                // fila. La repintamos con el id para que los botones sepan
                // cuál actualizar. El tono no se entera: lo tiene el servicio.
                if (conServicio) {
                    AlarmaService.actualizarRegistro(ctx, idMedicamento, idHorario,
                        creado.getId(), hora, indice, mensaje, nombre, dosisTxt);
                } else {
                    NotificationHelper.mostrarRecordatorioToma(ctx,
                        idMedicamento, idHorario, creado.getId(), hora, indice,
                        "Hora de tu medicamento", mensaje, nombre, dosisTxt, true);
                }
            }
        }

        NotificacionSync.registrar(ctx, Notificacion.TIPO_TOMA,
            "Recordatorio enviado: " + mensaje + " (" + hora + ")", false);

        // Que el frasco figure vacío justo a la hora de la dosis es de lo
        // poco que el cuidador necesita saber el mismo día. Va acá abajo, ya
        // con el dato de la red: si estamos sin conexión vale el de la caché.
        boolean sigueVacio = med != null ? med.getStockActual() <= 0 : seTermino;
        if (sigueVacio) avisarReposicion(ctx, nombre, hora);
    }

    /**
     * Le avisa al CUIDADOR que el frasco figura vacío a la hora de la toma.
     *
     * No muestra nada en el celular del paciente: la alarma que acaba de
     * sonar ya se lo dijo, y dos avisos por lo mismo en el mismo minuto
     * enseñan a ignorar los dos.
     */
    private void avisarReposicion(Context ctx, String nombre, String hora) {
        NotificacionSync.registrar(ctx, Notificacion.TIPO_STOCK,
            "Según la app no queda " + nombre + " y tocaba la toma de las "
                + (hora != null ? hora : "hoy") + ". Conviene revisar si hay"
                + " que reponerlo.", true);
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

    // ═══ Recordatorio de cita médica ═══════════════════════════
    private void onCita(Context ctx, int idCita, int antelacion,
                        String titulo, String texto) {
        if (idCita == -1 || texto == null) return;
        if (titulo == null || titulo.isEmpty()) titulo = "Cita médica";

        // Primero mostrar, después la red: el aviso tiene que aparecer aunque
        // el celular esté sin datos.
        RecordatorioCita.mostrar(ctx, idCita, antelacion, titulo, texto);

        // Al cuidador le interesa el aviso de las 2 horas —es cuando hay que
        // salir—; el de la víspera solo queda en el historial, para no
        // mandarle dos push por la misma cita.
        boolean pushAlCuidador = antelacion == RecordatorioCita.ANTES_DOS_HORAS;
        NotificacionSync.registrar(ctx, Notificacion.TIPO_CITA,
            titulo + ": " + texto, pushAlCuidador);
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

        // Las alarmas de las citas también se pierden en el reinicio.
        for (CitaMedica c : VimedRepo.listarCitasSync(ctx)) {
            RecordatorioCita.programar(ctx, c);
        }
    }
}
