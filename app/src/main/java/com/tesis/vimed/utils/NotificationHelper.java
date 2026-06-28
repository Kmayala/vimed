package com.tesis.vimed.utils;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.tesis.vimed.R;

import java.util.Calendar;

public class NotificationHelper {

    public static final String CHANNEL_ID = "vimed_channel";
    public static final String CHANNEL_NAME = "Recordatorios Vimed";

    /** Crear canal de notificaciones (requerido Android 8+). */
    public static void crearCanal(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Recordatorios de medicación Vimed");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    /**
     * Programa alarmas repetitivas según hora de inicio e intervalo.
     * @param idMedicamento ID del medicamento en la BD
     * @param horaInicio    Hora en formato HH:mm
     * @param intervaloHoras Cada cuántas horas se repite (6 | 8 | 12 | 24)
     */
    public static void programarAlarmas(Context context, int idMedicamento,
                                         String horaInicio, int intervaloHoras) {
        String[] partes = horaInicio.split(":");
        int hora = Integer.parseInt(partes[0]);
        int minuto = Integer.parseInt(partes[1]);

        int cantidad = (intervaloHoras > 0 && intervaloHoras <= 24) ? 24 / intervaloHoras : 1;

        for (int i = 0; i < cantidad; i++) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hora);
            cal.set(Calendar.MINUTE, minuto);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            if (i > 0) cal.add(Calendar.HOUR_OF_DAY, intervaloHoras * i);

            // Si la hora ya pasó hoy, programar para mañana
            if (cal.before(Calendar.getInstance())) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }

            Intent intent = new Intent(context, AlarmaReceiver.class);
            intent.putExtra("id_medicamento", idMedicamento);
            intent.putExtra("numero_alarma", i);

            PendingIntent pi = PendingIntent.getBroadcast(context,
                idMedicamento * 100 + i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                    cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
            }
        }
    }

    /** Cancela todas las alarmas de un medicamento. */
    public static void cancelarAlarmas(Context context, int idMedicamento, int intervaloHoras) {
        int cantidad = (intervaloHoras > 0 && intervaloHoras <= 24) ? 24 / intervaloHoras : 1;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < cantidad; i++) {
            Intent intent = new Intent(context, AlarmaReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(context,
                idMedicamento * 100 + i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (alarmManager != null) alarmManager.cancel(pi);
        }
    }

    /** Muestra una notificación push. */
    public static void mostrarNotificacion(Context context, String titulo, String mensaje, int id) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true);

        NotificationManager manager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(id, builder.build());
    }
}
