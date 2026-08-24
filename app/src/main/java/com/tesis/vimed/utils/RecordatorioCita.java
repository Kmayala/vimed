package com.tesis.vimed.utils;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.tesis.vimed.R;
import com.tesis.vimed.models.CitaMedica;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Avisos de las citas médicas: uno el día anterior y otro dos horas antes.
 *
 * Es el mismo mecanismo que las alarmas de medicación (AlarmManager +
 * {@link AlarmaReceiver}), pero el aviso es una notificación común y no la
 * pantalla completa con tono en loop: una cita se prepara con tiempo, no se
 * atiende en el segundo, y despertar a alguien como si fuera un despertador
 * por algo que es mañana solo consigue que silencie la app.
 *
 * Todo lo que hace falta para mostrar el aviso viaja en los extras del
 * Intent: a la hora de disparar el celular puede estar sin datos, y el
 * recordatorio tiene que aparecer igual.
 */
public final class RecordatorioCita {

    public static final String ACTION_CITA = "com.tesis.vimed.CITA";

    public static final String EXTRA_ID_CITA    = "id_cita";
    public static final String EXTRA_TITULO     = "cita_titulo";
    public static final String EXTRA_TEXTO      = "cita_texto";
    /** Cuál de los dos avisos es: {@link #ANTES_UN_DIA} o {@link #ANTES_DOS_HORAS}. */
    public static final String EXTRA_ANTELACION = "cita_antelacion";

    public static final int ANTES_UN_DIA    = 0;
    public static final int ANTES_DOS_HORAS = 1;

    private static final int[] ANTELACIONES = { ANTES_UN_DIA, ANTES_DOS_HORAS };

    /** Minutos antes de la cita en que dispara cada aviso. */
    private static final int MIN_UN_DIA    = 24 * 60;
    private static final int MIN_DOS_HORAS = 2 * 60;

    private static final SimpleDateFormat SDF =
        new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private RecordatorioCita() {}

    // ═══ Agendar ═══════════════════════════════════════════════

    /**
     * Programa (o reprograma) los dos avisos de una cita.
     *
     * Volver a llamarlo con la misma cita es inofensivo: los PendingIntent
     * usan el mismo request code, así que FLAG_UPDATE_CURRENT los pisa en vez
     * de duplicarlos. Por eso se puede llamar en cada onResume sin cuidado.
     *
     * Los avisos cuyo momento ya pasó no se agendan —y se cancelan, por si
     * quedaron de una versión anterior de la cita—: si faltan menos de dos
     * horas, solo se pierde ese aviso, no el otro.
     */
    public static void programar(Context ctx, CitaMedica cita) {
        if (cita == null || cita.getId() <= 0) return;

        // Una cita cancelada no avisa nada.
        if (CitaMedica.ESTADO_CANCELADA.equals(cita.getEstado())) {
            cancelar(ctx, cita.getId());
            return;
        }

        long cuando = enMillis(cita);
        if (cuando <= 0) return;   // fecha ilegible: mejor nada que un aviso a destiempo

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        String titulo = tituloDe(cita);
        long ahora = System.currentTimeMillis();

        for (int antelacion : ANTELACIONES) {
            long disparo = cuando - minutosDe(antelacion) * 60L * 1000L;
            PendingIntent pi = intentDe(ctx, cita.getId(), antelacion,
                titulo, textoDe(cita, antelacion));

            if (disparo <= ahora) {
                am.cancel(pi);
                continue;
            }
            agendar(am, pi, disparo);
        }
    }

    /** Borra los dos avisos de una cita (se eliminó, se canceló o cambió de fecha). */
    public static void cancelar(Context ctx, int idCita) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        NotificationManager nm =
            (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        for (int antelacion : ANTELACIONES) {
            am.cancel(intentDe(ctx, idCita, antelacion, "", ""));
            // Si el aviso ya estaba en la barra, se va con la cita.
            if (nm != null) nm.cancel(idNotif(idCita, antelacion));
        }
    }

    private static void agendar(AlarmManager am, PendingIntent pi, long disparo) {
        // Mismo criterio que las alarmas de medicación: exacta si el sistema
        // la permite, inexacta como respaldo. Un recordatorio de cita que
        // llega unos minutos corrido sigue sirviendo.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pi);
        }
    }

    private static PendingIntent intentDe(Context ctx, int idCita, int antelacion,
                                          String titulo, String texto) {
        Intent i = new Intent(ctx, AlarmaReceiver.class);
        i.setAction(ACTION_CITA);
        i.putExtra(EXTRA_ID_CITA, idCita);
        i.putExtra(EXTRA_ANTELACION, antelacion);
        i.putExtra(EXTRA_TITULO, titulo);
        i.putExtra(EXTRA_TEXTO, texto);
        return PendingIntent.getBroadcast(ctx, requestCode(idCita, antelacion), i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ═══ Mostrar el aviso ══════════════════════════════════════

    /** La llama {@link AlarmaReceiver} cuando llega la hora. */
    public static void mostrar(Context ctx, int idCita, int antelacion,
                               String titulo, String texto) {
        Intent abrir = new Intent(ctx, com.tesis.vimed.AppointmentsActivity.class);
        abrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx,
            requestCode(idCita, antelacion) + 1_000, abrir,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b =
            new NotificationCompat.Builder(ctx, NotificationHelper.CITA_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm =
            (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(idNotif(idCita, antelacion), b.build());
    }

    // ═══ Textos ════════════════════════════════════════════════

    private static String tituloDe(CitaMedica cita) {
        String esp = cita.getEspecialidad();
        return esp != null && !esp.trim().isEmpty()
            ? "Cita de " + esp.trim()
            : "Cita médica";
    }

    private static String textoDe(CitaMedica cita, int antelacion) {
        StringBuilder sb = new StringBuilder();
        sb.append(antelacion == ANTES_UN_DIA
            ? "Mañana a las " : "En 2 horas, a las ");
        sb.append(cita.horaHM());

        String medico = cita.getMedico();
        if (medico != null && !medico.trim().isEmpty()) {
            sb.append(" con ").append(medico.trim());
        }
        String lugar = cita.getLugar();
        if (lugar != null && !lugar.trim().isEmpty()) {
            sb.append(" en ").append(lugar.trim());
        }
        return sb.append('.').toString();
    }

    // ═══ Helpers ═══════════════════════════════════════════════

    /**
     * La fecha de la cita en milisegundos, o 0 si no se puede leer.
     * Se arma con fechaYMD()/horaHM() para tomar tanto "yyyy-MM-dd HH:mm"
     * como el ISO que devuelve Postgres, igual que hace el resto de la app.
     */
    public static long enMillis(CitaMedica cita) {
        String ymd = cita.fechaYMD();
        String hm  = cita.horaHM();
        if (ymd.isEmpty()) return 0;
        if (hm.isEmpty()) hm = "00:00";
        try {
            Date d = SDF.parse(ymd + " " + hm);
            return d != null ? d.getTime() : 0;
        } catch (ParseException e) {
            return 0;
        }
    }

    private static int minutosDe(int antelacion) {
        return antelacion == ANTES_UN_DIA ? MIN_UN_DIA : MIN_DOS_HORAS;
    }

    // Rangos propios, lejos de los que usan las alarmas de medicación
    // (requestCodeFor: idMed*100+indice; idNotifPara: 500_000+...), para que
    // una cita no le pise el PendingIntent a un medicamento.
    private static int requestCode(int idCita, int antelacion) {
        return 800_000 + idCita * 10 + antelacion;
    }

    private static int idNotif(int idCita, int antelacion) {
        return 700_000 + idCita * 10 + antelacion;
    }

    /** Solo para el aviso: el momento de la cita ya pasó. */
    public static boolean yaPaso(CitaMedica cita) {
        long ms = enMillis(cita);
        return ms > 0 && ms <= System.currentTimeMillis();
    }

    /** Fecha tope de hoy a las 00:00, útil para validar en las pantallas. */
    public static long hoyAMedianoche() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
