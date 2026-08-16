package com.tesis.vimed.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.tesis.vimed.R;

import java.util.Calendar;

public class NotificationHelper {

    public static final String CHANNEL_ID   = "vimed_channel";
    public static final String CHANNEL_NAME = "Recordatorios Vimed";

    // Canal aparte para la alarma a pantalla completa. Es SILENCIOSO a nivel
    // de canal porque el sonido en loop lo pone AlarmaActivity; así no suena
    // dos veces. Importancia HIGH para que el full-screen intent funcione.
    // OJO con el sufijo _v2: un canal es INMUTABLE después de crearse. El
    // canal viejo quedó registrado sin sonido en los celulares que ya tienen
    // la app, y ningún cambio de código lo despierta. Para que el sonido
    // nuevo tenga efecto hay que estrenar un id.
    public static final String ALARM_CHANNEL_ID   = "vimed_alarma_channel_v2";
    public static final String ALARM_CHANNEL_NAME = "Alarma de medicación";

    /** Id del canal mudo original, solo para poder borrarlo. */
    private static final String ALARM_CHANNEL_ID_VIEJO = "vimed_alarma_channel";

    // Canal de la notificación que sostiene a AlarmaService. Este SÍ es mudo
    // a propósito: mientras el servicio vive, el tono lo pone su MediaPlayer.
    // Si sonara también el canal, se escucharían dos alarmas encimadas.
    public static final String ALARM_FG_CHANNEL_ID   = "vimed_alarma_activa_v1";
    public static final String ALARM_FG_CHANNEL_NAME = "Alarma sonando";

    // Acciones que reconoce AlarmaReceiver
    public static final String ACTION_FIRE    = "com.tesis.vimed.FIRE";      // dispara la alarma
    public static final String ACTION_CONFIRM = "com.tesis.vimed.CONFIRM";   // botón "Confirmar toma"
    public static final String ACTION_SNOOZE  = "com.tesis.vimed.SNOOZE";    // botón "Posponer 15 min"

    // Extras
    public static final String EXTRA_ID_MED    = "id_medicamento";
    public static final String EXTRA_ID_HORARIO= "id_horario";
    public static final String EXTRA_ID_REG    = "id_registro";
    public static final String EXTRA_HORA      = "hora";           // "HH:mm"
    public static final String EXTRA_INDICE    = "indice_alarma";  // 0..N
    public static final String EXTRA_MENSAJE   = "mensaje";        // texto del medicamento
    // La pantalla de alarma muestra el nombre y la dosis en renglones
    // distintos, así que viajan separados en vez de un solo texto pegado.
    public static final String EXTRA_NOMBRE    = "nombre_med";     // "Diclofenac"
    public static final String EXTRA_DOSIS     = "dosis_med";      // "50 mg"

    public static final int SNOOZE_MINUTOS = 15;

    private static final int REQ_POST_NOTIFICATIONS = 4711;

    // ═══ Canal ═════════════════════════════════════════════════
    public static void crearCanal(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Canal general (avisos, stock bajo, etc.)
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Recordatorios de medicación Vimed");
            manager.createNotificationChannel(channel);

            // Canal de la alarma. El sonido lo pone EL CANAL, no la Activity:
            // con el celular bloqueado el sistema puede no dejar abrir la
            // pantalla completa (pasa seguido en Xiaomi/Samsung, o si el
            // permiso está denegado). Cuando el canal era mudo, en esos casos
            // no sonaba nada. Ahora suena igual aunque la pantalla no abra.
            NotificationChannel alarma = new NotificationChannel(
                ALARM_CHANNEL_ID, ALARM_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            alarma.setDescription("Alarma a pantalla completa para tomar la medicación");
            alarma.setSound(sonidoDeAlarma(), atributosDeAlarma());
            alarma.enableVibration(true);
            alarma.setVibrationPattern(new long[]{0, 800, 600});
            alarma.setBypassDnd(true);   // suena aunque esté en "No molestar"
            alarma.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(alarma);

            // Canal de la alarma EN CURSO (la que sostiene AlarmaService).
            // Importancia HIGH para que el full-screen intent siga
            // funcionando, pero sin sonido ni vibración propios.
            NotificationChannel alarmaActiva = new NotificationChannel(
                ALARM_FG_CHANNEL_ID, ALARM_FG_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH);
            alarmaActiva.setDescription("Notificación de la alarma mientras está sonando");
            alarmaActiva.setSound(null, null);
            alarmaActiva.enableVibration(false);
            alarmaActiva.setBypassDnd(true);
            alarmaActiva.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(alarmaActiva);

            // Sacamos de los ajustes el canal mudo viejo, para que la persona
            // no vea dos "Alarma de medicación" y silencie el que sí anda.
            manager.deleteNotificationChannel(ALARM_CHANNEL_ID_VIEJO);
        }
    }

    /** Tono de alarma del sistema; si el celular no tiene, el de notificación. */
    public static Uri sonidoDeAlarma() {
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        return uri;
    }

    /**
     * USAGE_ALARM: suena por el volumen de ALARMA, no por el de notificaciones.
     * Es lo que hace que se escuche aunque el celular esté en silencio, que es
     * justo el escenario de la noche.
     */
    public static AudioAttributes atributosDeAlarma() {
        return new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    }

    // ═══ Permisos ══════════════════════════════════════════════

    /** True si tenemos permiso para mostrar notificaciones (Android 13+ lo pide en runtime). */
    public static boolean tienePermisoNotificaciones(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED;
    }

    /** Pide POST_NOTIFICATIONS. Resultado llega a Activity.onRequestPermissionsResult. */
    public static void pedirPermisoNotificaciones(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !tienePermisoNotificaciones(activity)) {
            ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQ_POST_NOTIFICATIONS);
        }
    }

    /** True si el sistema permite programar alarmas exactas (Android 12+ lo restringe). */
    public static boolean puedeAlarmasExactas(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    /** Manda al usuario a la pantalla de Settings para habilitar alarmas exactas. */
    public static void pedirPermisoAlarmasExactas(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !puedeAlarmasExactas(activity)) {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(i);
        }
    }

    /** True si el sistema permite abrir la alarma a pantalla completa (Android 14+ lo restringe). */
    public static boolean puedeFullScreenIntent(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        return nm != null && nm.canUseFullScreenIntent();
    }

    /** Manda al usuario a Settings para habilitar la alarma a pantalla completa. */
    public static void pedirPermisoFullScreenIntent(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && !puedeFullScreenIntent(activity)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(i);
        }
    }

    // ═══ Agendar alarmas ═══════════════════════════════════════

    /**
     * Programa las tomas del día para un medicamento.
     * Usa setExactAndAllowWhileIdle — cada alarma se dispara UNA vez.
     * El receiver reagenda la misma para 24h después.
     */
    public static void programarAlarmas(Context context, int idMedicamento,
                                        int idHorario, String horaInicio,
                                        int intervaloHoras) {
        String[] partes = horaInicio.split(":");
        int hora = Integer.parseInt(partes[0]);
        int minuto = Integer.parseInt(partes[1]);
        int cantidad = (intervaloHoras > 0 && intervaloHoras <= 24) ? 24 / intervaloHoras : 1;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        for (int i = 0; i < cantidad; i++) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hora);
            cal.set(Calendar.MINUTE, minuto);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (i > 0) cal.add(Calendar.HOUR_OF_DAY, intervaloHoras * i);
            if (cal.before(Calendar.getInstance())) cal.add(Calendar.DAY_OF_YEAR, 1);

            agendarUna(context, am, idMedicamento, idHorario,
                horaFormato(cal), i, cal.getTimeInMillis());
        }
    }

    /** Reagenda una alarma específica para X ms desde ahora (usado por SNOOZE). */
    public static void reagendarEn(Context context, int idMedicamento, int idHorario,
                                    String horaOriginal, int indice, long triggerAtMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        agendarUna(context, am, idMedicamento, idHorario, horaOriginal, indice, triggerAtMillis);
    }

    private static void agendarUna(Context context, AlarmManager am,
                                    int idMedicamento, int idHorario,
                                    String hora, int indice, long triggerAtMillis) {
        Intent intent = new Intent(context, com.tesis.vimed.utils.AlarmaReceiver.class);
        intent.setAction(ACTION_FIRE);
        intent.putExtra(EXTRA_ID_MED, idMedicamento);
        intent.putExtra(EXTRA_ID_HORARIO, idHorario);
        intent.putExtra(EXTRA_HORA, hora);
        intent.putExtra(EXTRA_INDICE, indice);

        PendingIntent pi = PendingIntent.getBroadcast(context,
            requestCodeFor(idMedicamento, indice), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Si tenemos permiso de exactas, la usamos. Si no, fallback inexacto
        // (mejor tarde que nunca).
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    // ═══ Cancelar alarmas ══════════════════════════════════════

    public static void cancelarAlarmas(Context context, int idMedicamento, int intervaloHoras) {
        int cantidad = (intervaloHoras > 0 && intervaloHoras <= 24) ? 24 / intervaloHoras : 1;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < cantidad; i++) {
            Intent intent = new Intent(context, com.tesis.vimed.utils.AlarmaReceiver.class);
            intent.setAction(ACTION_FIRE);
            PendingIntent pi = PendingIntent.getBroadcast(context,
                requestCodeFor(idMedicamento, i), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        }
    }

    // ═══ Notificación con acciones ═════════════════════════════

    /**
     * Muestra la notificación de "hora de tu medicamento" con los dos botones.
     * @param idRegistro id de la fila que se acaba de insertar en registro_tomas.
     *                   Se usa para saber cuál actualizar cuando la persona toca
     *                   "Confirmar" o "Posponer".
     */
    public static void mostrarRecordatorioToma(Context ctx, int idMedicamento,
                                                int idHorario, int idRegistro,
                                                String hora, int indice,
                                                String titulo, String mensaje,
                                                String nombreMed, String dosisTxt) {
        mostrarRecordatorioToma(ctx, idMedicamento, idHorario, idRegistro, hora,
            indice, titulo, mensaje, nombreMed, dosisTxt, false);
    }

    /**
     * @param esActualizacion true cuando solo estamos repintando una alarma que
     *        YA está sonando (por ejemplo para agregarle el id de la fila que
     *        recién creó la red). Marca la notificación como "no vuelvas a
     *        alertar": sin esto el tono se reiniciaría desde cero cada vez.
     */
    public static void mostrarRecordatorioToma(Context ctx, int idMedicamento,
                                                int idHorario, int idRegistro,
                                                String hora, int indice,
                                                String titulo, String mensaje,
                                                String nombreMed, String dosisTxt,
                                                boolean esActualizacion) {
        android.app.Notification n = construirNotificacionToma(ctx, idMedicamento,
            idHorario, idRegistro, hora, indice, titulo, mensaje, nombreMed,
            dosisTxt, ALARM_CHANNEL_ID, esActualizacion);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(idNotifPara(idMedicamento, indice), n);
    }

    /**
     * Arma la tarjeta de "Hora de tu medicamento" con sus dos botones y el
     * full-screen intent, sin publicarla.
     *
     * @param canalId {@link #ALARM_CHANNEL_ID} cuando el tono lo tiene que
     *        poner el canal (camino de respaldo, sin servicio), o
     *        {@link #ALARM_FG_CHANNEL_ID} cuando la notificación es la que
     *        sostiene a {@link AlarmaService} y el audio ya corre por su
     *        MediaPlayer.
     */
    public static android.app.Notification construirNotificacionToma(
            Context ctx, int idMedicamento, int idHorario, int idRegistro,
            String hora, int indice, String titulo, String mensaje,
            String nombreMed, String dosisTxt, String canalId,
            boolean esActualizacion) {
        // Intent del botón "Confirmar toma"
        Intent confirmIntent = new Intent(ctx, com.tesis.vimed.utils.AlarmaReceiver.class);
        confirmIntent.setAction(ACTION_CONFIRM);
        confirmIntent.putExtra(EXTRA_ID_MED, idMedicamento);
        confirmIntent.putExtra(EXTRA_ID_HORARIO, idHorario);
        confirmIntent.putExtra(EXTRA_ID_REG, idRegistro);
        confirmIntent.putExtra(EXTRA_HORA, hora);
        confirmIntent.putExtra(EXTRA_INDICE, indice);
        PendingIntent piConfirm = PendingIntent.getBroadcast(ctx,
            requestCodeFor(idMedicamento, indice) + 1_000_000, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent del botón "Posponer"
        Intent snoozeIntent = new Intent(ctx, com.tesis.vimed.utils.AlarmaReceiver.class);
        snoozeIntent.setAction(ACTION_SNOOZE);
        snoozeIntent.putExtra(EXTRA_ID_MED, idMedicamento);
        snoozeIntent.putExtra(EXTRA_ID_HORARIO, idHorario);
        snoozeIntent.putExtra(EXTRA_ID_REG, idRegistro);
        snoozeIntent.putExtra(EXTRA_HORA, hora);
        snoozeIntent.putExtra(EXTRA_INDICE, indice);
        PendingIntent piSnooze = PendingIntent.getBroadcast(ctx,
            requestCodeFor(idMedicamento, indice) + 2_000_000, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // El "full-screen intent" abre AlarmaActivity a pantalla completa
        // (como un despertador), incluso con el celular bloqueado. Ahí suena
        // en loop y vibra hasta que la persona responde.
        Intent alarmaIntent = new Intent(ctx, com.tesis.vimed.AlarmaActivity.class);
        alarmaIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        alarmaIntent.putExtra(EXTRA_ID_MED, idMedicamento);
        alarmaIntent.putExtra(EXTRA_ID_HORARIO, idHorario);
        alarmaIntent.putExtra(EXTRA_ID_REG, idRegistro);
        alarmaIntent.putExtra(EXTRA_HORA, hora);
        alarmaIntent.putExtra(EXTRA_INDICE, indice);
        alarmaIntent.putExtra(EXTRA_MENSAJE, mensaje);
        alarmaIntent.putExtra(EXTRA_NOMBRE, nombreMed);
        alarmaIntent.putExtra(EXTRA_DOSIS, dosisTxt);
        PendingIntent piAlarma = PendingIntent.getActivity(ctx,
            requestCodeFor(idMedicamento, indice) + 3_000_000, alarmaIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, canalId)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(true)                      // no se puede deslizar para descartar
            .setContentIntent(piAlarma)
            .setFullScreenIntent(piAlarma, true)   // ← abre la pantalla completa
            .addAction(android.R.drawable.checkbox_on_background, "Confirmar toma", piConfirm)
            .addAction(android.R.drawable.ic_menu_recent_history,
                "Posponer " + SNOOZE_MINUTOS + " min", piSnooze);

        // En Android 7 y anteriores no hay canales: el sonido y la prioridad
        // se declaran en la notificación misma.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && !esActualizacion) {
            b.setSound(sonidoDeAlarma(), AudioManager.STREAM_ALARM);
            b.setVibrate(new long[]{0, 800, 600});
        }
        if (esActualizacion) b.setOnlyAlertOnce(true);

        android.app.Notification n = b.build();

        // FLAG_INSISTENT: el sonido se REPITE hasta que se responde, en vez de
        // sonar una vez y callarse. Es lo que hace un despertador, y es lo que
        // salva el caso de que la pantalla completa no llegue a abrirse.
        // En una actualización no va: el tono ya está sonando y volver a
        // pedirlo lo reiniciaría.
        if (!esActualizacion) n.flags |= android.app.Notification.FLAG_INSISTENT;

        return n;
    }

    /** Notificación simple sin acciones (para stock bajo, avisos generales). */
    public static void mostrarNotificacion(Context context, String titulo, String mensaje, int id) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true);

        NotificationManager manager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(id, builder.build());
    }

    /** Cancela una notificación específica. Se usa cuando la persona confirma la toma. */
    public static void cancelarNotificacion(Context ctx, int idMedicamento, int indice) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(idNotifPara(idMedicamento, indice));
    }

    // ═══ Helpers ═══════════════════════════════════════════════

    private static int requestCodeFor(int idMedicamento, int indice) {
        return idMedicamento * 100 + indice;
    }

    public static int idNotifPara(int idMedicamento, int indice) {
        return 500_000 + idMedicamento * 100 + indice;
    }

    private static String horaFormato(Calendar cal) {
        return String.format(java.util.Locale.getDefault(),
            "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }
}
