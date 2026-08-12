package com.tesis.vimed;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.tesis.vimed.utils.AlarmaReceiver;
import com.tesis.vimed.utils.NotificationHelper;

import java.util.Locale;

/**
 * Pantalla de alarma a PANTALLA COMPLETA para el recordatorio de medicación.
 *
 * Se lanza mediante un "full-screen intent" desde NotificationHelper: cuando
 * llega la hora, el sistema abre esta Activity encima de la pantalla de bloqueo
 * (como un despertador), suena en loop y vibra HASTA que la persona toca un botón.
 *
 * Botones:
 *   - "Ya tomé mi medicación" → confirma la toma (reusa AlarmaReceiver.ACTION_CONFIRM)
 *   - "Posponer 15 minutos"   → reprograma (reusa AlarmaReceiver.ACTION_SNOOZE)
 *
 * Si nadie responde en {@link #AUTO_POSPONER_MS}, se pospone sola para no
 * sonar indefinidamente ni agotar la batería.
 */
public class AlarmaActivity extends AppCompatActivity {

    /** Tiempo que suena antes de posponerse sola si nadie responde (60 s). */
    private static final long AUTO_POSPONER_MS = 60_000L;

    private MediaPlayer player;
    private Vibrator vibrator;
    private final Handler autoHandler = new Handler(Looper.getMainLooper());

    // Datos de la toma (llegan por extras)
    private int idMedicamento, idHorario, idRegistro, indice;
    private String hora;

    private boolean yaCerrada = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Mostrar sobre el bloqueo y prender la pantalla ──────────
        mostrarSobreBloqueo();
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.activity_alarma);

        leerExtras(getIntent());
        pintarDatos();

        Button btnConfirmar = findViewById(R.id.btn_ya_tome);
        Button btnPosponer  = findViewById(R.id.btn_posponer);
        btnConfirmar.setOnClickListener(v -> responder(NotificationHelper.ACTION_CONFIRM));
        btnPosponer.setOnClickListener(v -> responder(NotificationHelper.ACTION_SNOOZE));

        empezarAlarma();

        // Si nadie responde, se pospone sola
        autoHandler.postDelayed(() -> responder(NotificationHelper.ACTION_SNOOZE),
            AUTO_POSPONER_MS);
    }

    /** Si llega otra alarma mientras esta ya está abierta, actualizamos los datos. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        leerExtras(intent);
        pintarDatos();
    }

    private void leerExtras(Intent intent) {
        idMedicamento = intent.getIntExtra(NotificationHelper.EXTRA_ID_MED, -1);
        idHorario     = intent.getIntExtra(NotificationHelper.EXTRA_ID_HORARIO, -1);
        idRegistro    = intent.getIntExtra(NotificationHelper.EXTRA_ID_REG, -1);
        indice        = intent.getIntExtra(NotificationHelper.EXTRA_INDICE, 0);
        hora          = intent.getStringExtra(NotificationHelper.EXTRA_HORA);
    }

    private void pintarDatos() {
        TextView tvHora = findViewById(R.id.alarma_hora);
        TextView tvMed  = findViewById(R.id.alarma_medicamento);

        tvHora.setText(hora != null ? hora : horaAhora());

        String mensaje = getIntent().getStringExtra(NotificationHelper.EXTRA_MENSAJE);
        if (mensaje != null && !mensaje.isEmpty()) tvMed.setText(mensaje);
    }

    // ═══ Sonido + vibración ════════════════════════════════════

    private void empezarAlarma() {
        // Sonido de alarma en loop, con volumen tipo alarma (no depende del
        // volumen de multimedia, usa el canal de alarma del sistema).
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            player = new MediaPlayer();
            player.setDataSource(this, uri);
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception ignored) {
            // Si falla el audio, al menos queda la vibración y la pantalla.
        }

        // Vibración en patrón que se repite hasta cerrar.
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] patron = {0, 800, 600};   // espera, vibra, pausa…
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(patron, 0));
            } else {
                vibrator.vibrate(patron, 0);
            }
        }
    }

    private void pararAlarma() {
        autoHandler.removeCallbacksAndMessages(null);
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    // ═══ Responder (confirmar / posponer) ══════════════════════

    /** Para el sonido, reenvía la acción al AlarmaReceiver y cierra la pantalla. */
    private void responder(String accion) {
        if (yaCerrada) return;
        yaCerrada = true;
        pararAlarma();

        // Reusamos toda la lógica de datos que ya vive en AlarmaReceiver.
        Intent i = new Intent(this, AlarmaReceiver.class);
        i.setAction(accion);
        i.putExtra(NotificationHelper.EXTRA_ID_MED, idMedicamento);
        i.putExtra(NotificationHelper.EXTRA_ID_HORARIO, idHorario);
        i.putExtra(NotificationHelper.EXTRA_ID_REG, idRegistro);
        i.putExtra(NotificationHelper.EXTRA_HORA, hora);
        i.putExtra(NotificationHelper.EXTRA_INDICE, indice);
        sendBroadcast(i);

        // Por las dudas, retiramos también la notificación asociada.
        NotificationHelper.cancelarNotificacion(this, idMedicamento, indice);

        finish();
    }

    // ═══ Ciclo de vida ═════════════════════════════════════════

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pararAlarma();
    }

    // No dejamos que el botón "atrás" apague la alarma sin decidir:
    // se ignora, la persona debe elegir un botón.
    @Override
    public void onBackPressed() {
        // intencionalmente vacío
    }

    // ═══ Helpers ═══════════════════════════════════════════════

    private void mostrarSobreBloqueo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        }
    }

    private String horaAhora() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format(Locale.getDefault(), "%02d:%02d",
            c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
    }
}
