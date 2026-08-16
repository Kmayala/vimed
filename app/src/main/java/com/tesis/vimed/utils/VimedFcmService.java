package com.tesis.vimed.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.tesis.vimed.CuidadorActivity;
import com.tesis.vimed.R;
import com.tesis.vimed.api.PushManager;

import java.util.Map;

/**
 * Recibe los push de Firebase en el celular del CUIDADOR.
 *
 * La Edge Function manda solo el bloque "data" (sin "notification"), así
 * el push nos llega también con la app en primer plano y somos nosotros
 * los que armamos la notificación — si viniera como "notification", Android
 * la dibujaría solo con la app cerrada y en primer plano no se vería nada.
 */
public class VimedFcmService extends FirebaseMessagingService {

    /** Notificaciones del panel de cuidador (distintas de la alarma del adulto). */
    private static final int ID_BASE = 900_000;

    /**
     * Firebase rota el token (reinstalación, limpieza de datos, restore).
     * Cuando pasa, hay que volver a subirlo o los push dejan de llegar.
     */
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        PushManager.guardarToken(this, token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);

        Map<String, String> datos = message.getData();
        String titulo  = datos.get("titulo");
        String mensaje = datos.get("mensaje");

        // Si llegara como notification (por ejemplo, una prueba desde la
        // consola de Firebase), igual lo mostramos.
        if (titulo == null && message.getNotification() != null) {
            titulo  = message.getNotification().getTitle();
            mensaje = message.getNotification().getBody();
        }
        if (titulo == null && mensaje == null) return;

        mostrar(titulo != null ? titulo : "Vimed",
                mensaje != null ? mensaje : "");
    }

    private void mostrar(String titulo, String mensaje) {
        // El canal ya existe (VimedApp lo crea al arrancar), pero por las
        // dudas: si el proceso arrancó por el push, puede no haber pasado.
        NotificationHelper.crearCanal(this);

        Intent abrir = new Intent(this, CuidadorActivity.class);
        abrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, abrir,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b =
            new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // id distinto por aviso: si llegan dos seguidos, no se pisan.
        if (nm != null) nm.notify(ID_BASE + (int) (System.currentTimeMillis() % 10_000), b.build());
    }
}
