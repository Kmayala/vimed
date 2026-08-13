package com.tesis.vimed;

import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
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
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
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

    // Vistas y animadores de la campana
    private ImageView vCampana;
    private View vCirculo;
    private ObjectAnimator animCampana;
    private ObjectAnimator animCirculo;

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

        vCampana = findViewById(R.id.alarma_campana);
        vCirculo = findViewById(R.id.alarma_circulo);

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

        // Movimiento visual: la campana se balancea mientras suena.
        empezarAnimacionCampana();
    }

    private void pararAlarma() {
        pararAnimacionCampana();
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

    // ═══ Animación de la campana ═══════════════════════════════

    /**
     * Un ciclo completo de repique. Mismo PERÍODO que el patrón de vibración
     * ({0,800,600} = 1400 ms). Ojo: mismo período no es misma fase — arrancan
     * en momentos distintos y con relojes distintos, así que no van a quedar
     * clavadas una con la otra. 1400 ms se elige porque es buen ritmo, no
     * porque sincronice.
     */
    private static final long CICLO_CAMPANA_MS = 1400L;

    /** Ángulo máximo del balanceo. 10-12° suave, 14° recomendado, 18°+ marea. */
    private static final float ANGULO_MAX = 14f;

    /** Si el círculo de fondo también late suavemente. */
    private static final boolean PULSAR_CIRCULO = true;

    /** Alto/ancho del ImageView en el layout. Solo red de seguridad del pivote. */
    private static final float CAMPANA_DP = 72f;

    /**
     * Fracción de la altura donde está la corona de la campana: ic_bell tiene
     * viewport 24x24 y el domo arranca en y=2 → 2/24 ≈ 0.08. Rotar ahí es lo
     * que la hace parecer colgada, en vez de girar sobre su propio centro.
     */
    private static final float PIVOTE_Y_FRAC = 0.08f;

    private final Runnable arrancarBalanceo = () -> {
        if (yaCerrada || isFinishing() || isDestroyed()) return;
        if (vCampana == null) return;

        int w = vCampana.getWidth();
        int h = vCampana.getHeight();
        if (w == 0 || h == 0) {
            // Red de seguridad: el ImageView mide 72dp fijos, no hace falta
            // esperar al layout para saber dónde va el pivote.
            int px = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                CAMPANA_DP, getResources().getDisplayMetrics()));
            w = px;
            h = px;
        }
        vCampana.setPivotX(w / 2f);
        vCampana.setPivotY(h * PIVOTE_Y_FRAC);

        animCampana = ObjectAnimator.ofPropertyValuesHolder(vCampana, balanceoPendular());
        animCampana.setDuration(CICLO_CAMPANA_MS);
        animCampana.setRepeatCount(ValueAnimator.INFINITE);
        animCampana.setRepeatMode(ValueAnimator.RESTART);
        // Lineal a nivel global: el suavizado vive dentro de cada keyframe.
        animCampana.setInterpolator(new LinearInterpolator());
        animCampana.start();

        if (PULSAR_CIRCULO && vCirculo != null) {
            animCirculo = ObjectAnimator.ofPropertyValuesHolder(vCirculo,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f, 1f));
            animCirculo.setDuration(CICLO_CAMPANA_MS);
            animCirculo.setRepeatCount(ValueAnimator.INFINITE);
            animCirculo.setInterpolator(new AccelerateDecelerateInterpolator());
            animCirculo.start();   // mismo frame que la campana → sí quedan en fase
        }
    };

    /** Empieza el movimiento de la campana. Idempotente. */
    private void empezarAnimacionCampana() {
        if (vCampana == null) return;
        pararAnimacionCampana();   // nunca dejar un animador infinito huérfano

        // Si la persona desactivó las animaciones del sistema (accesibilidad, o
        // el ahorro de batería de Xiaomi/Samsung, que es lo más común), dejamos
        // la campana quieta: el sonido y la vibración ya comunican la urgencia.
        if (!animacionesHabilitadas()) {
            vCampana.setRotation(0f);
            return;
        }
        // Diferido: el pivote sale mejor con la vista ya medida.
        vCampana.post(arrancarBalanceo);
    }

    /** Detiene y libera la animación. Idempotente. */
    private void pararAnimacionCampana() {
        if (vCampana != null) {
            // Por si se respondió antes de que el post() llegara a correr.
            vCampana.removeCallbacks(arrancarBalanceo);
        }
        if (animCampana != null) { animCampana.cancel(); animCampana = null; }
        if (animCirculo != null) { animCirculo.cancel(); animCirculo = null; }
        // Que no quede la campana torcida ni el círculo agrandado.
        if (vCampana != null) vCampana.setRotation(0f);
        if (vCirculo != null) { vCirculo.setScaleX(1f); vCirculo.setScaleY(1f); }
    }

    /**
     * Oscilación amortiguada + pausa final: 14° → 10° → 6° → 0 y descansa.
     * Esa pausa (último 18% del ciclo) es lo que hace que se lea como campana
     * repicando y no como un péndulo hipnótico.
     */
    private static PropertyValuesHolder balanceoPendular() {
        return PropertyValuesHolder.ofKeyframe(View.ROTATION,
            Keyframe.ofFloat(0.00f, 0f),   // el interpolador del kf 0 no se usa
            kf(0.09f, -ANGULO_MAX),
            kf(0.24f,  ANGULO_MAX),
            kf(0.39f, -ANGULO_MAX * 0.70f),
            kf(0.52f,  ANGULO_MAX * 0.70f),
            kf(0.64f, -ANGULO_MAX * 0.40f),
            kf(0.74f,  ANGULO_MAX * 0.40f),
            kf(0.82f,  0f),
            kf(1.00f,  0f));               // pausa antes del próximo repique
    }

    /** Keyframe con suavizado propio: el interpolador rige el tramo QUE LLEGA a él. */
    private static Keyframe kf(float fraccion, float grados) {
        Keyframe k = Keyframe.ofFloat(fraccion, grados);
        k.setInterpolator(new AccelerateDecelerateInterpolator());
        return k;
    }

    /**
     * ¿El sistema tiene las animaciones activadas? Con la escala en 0 el
     * animador salta directo al último valor pero igual pide un frame cada
     * 16 ms para nada, así que ni lo arrancamos.
     */
    private boolean animacionesHabilitadas() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return ValueAnimator.areAnimatorsEnabled();
        }
        return Settings.Global.getFloat(getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
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

    // La alarma sigue sonando si la persona toca HOME (a propósito), pero la
    // pantalla queda invisible: sin esto el animador seguiría pidiendo un frame
    // cada 16 ms para dibujar algo que nadie ve, justo cuando el teléfono ya
    // está gastando batería con el audio y el vibrador.
    @Override
    protected void onStart() {
        super.onStart();
        if (animCampana != null && animCampana.isPaused()) animCampana.resume();
        if (animCirculo != null && animCirculo.isPaused()) animCirculo.resume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (animCampana != null && animCampana.isStarted()) animCampana.pause();
        if (animCirculo != null && animCirculo.isStarted()) animCirculo.pause();
    }

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
