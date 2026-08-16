package com.tesis.vimed.utils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.core.content.ContextCompat;

/**
 * Servicio que HACE SONAR la alarma de medicación.
 *
 * Por qué existe: antes el tono lo ponía el canal de notificación
 * (FLAG_INSISTENT) y eso falla en demasiados escenarios reales —
 * el canal puede no existir todavía si el proceso arrancó por la alarma,
 * cualquier re-publicación de la notificación corta el tono, y varios
 * fabricantes lo silencian. Acá el audio es nuestro: un MediaPlayer en
 * loop por el stream de ALARMA, que suena aunque la pantalla completa
 * nunca llegue a abrirse y aunque el celular esté en silencio.
 *
 * Es un servicio en PRIMER PLANO porque debe seguir sonando con la app
 * cerrada; su notificación es la misma tarjeta de "Hora de tu medicamento"
 * con los dos botones, pero en un canal MUDO (el sonido ya lo ponemos acá).
 */
public class AlarmaService extends Service {

    public static final String ACTION_START  = "com.tesis.vimed.ALARMA_START";
    public static final String ACTION_UPDATE = "com.tesis.vimed.ALARMA_UPDATE";
    public static final String ACTION_STOP   = "com.tesis.vimed.ALARMA_STOP";

    /** Aviso a AlarmaActivity de que la alarma terminó, para que se cierre. */
    public static final String ACTION_TERMINADA = "com.tesis.vimed.ALARMA_TERMINADA";

    /** Cuánto suena antes de posponerse sola si nadie responde. */
    public static final long DURACION_MS = 60_000L;

    /**
     * Cuánto suena en un horario marcado como "insistir más" desde las
     * sugerencias de adherencia. El doble: si esa toma se viene perdiendo
     * seguido, un minuto de alarma ya demostró no alcanzar.
     */
    public static final long DURACION_REFUERZO_MS = 2 * DURACION_MS;

    /** Cuánto tiene que sonar esta toma, según si su horario tiene refuerzo. */
    public static long duracionPara(Context ctx, int idHorario) {
        return idHorario > 0
            && com.tesis.vimed.adherencia.AjustesAdherencia.tieneRefuerzo(ctx, idHorario)
            ? DURACION_REFUERZO_MS : DURACION_MS;
    }

    private static final long[] PATRON_VIBRACION = {0, 800, 600};

    /**
     * True mientras hay una alarma sonando. Lo consulta AlarmaReceiver para
     * no "actualizar" un servicio que nunca arrancó (eso lo crearía y
     * pondría a sonar algo que ya se respondió).
     */
    private static volatile boolean activo = false;

    public static boolean estaActivo() { return activo; }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaPlayer player;
    private Vibrator vibrador;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;

    private boolean sonando = false;

    // Datos de la toma, para reconstruir la notificación en cada update.
    private int idMedicamento = -1, idHorario = -1, idRegistro = -1, indice = 0;
    private String hora, mensaje, nombreMed, dosisTxt;

    // ═══ API estática ══════════════════════════════════════════

    /**
     * Arranca la alarma. Devuelve false si el sistema no dejó levantar el
     * servicio (pasa si la alarma se agendó inexacta y no hay exención de
     * arranque en segundo plano); en ese caso quien llama debe caer al
     * camino viejo de notificación con sonido de canal.
     */
    public static boolean iniciar(Context ctx, int idMedicamento, int idHorario,
                                  int idRegistro, String hora, int indice,
                                  String mensaje, String nombreMed, String dosisTxt) {
        Intent i = intentBase(ctx, ACTION_START, idMedicamento, idHorario,
            idRegistro, hora, indice, mensaje, nombreMed, dosisTxt);
        try {
            ContextCompat.startForegroundService(ctx, i);
            return true;
        } catch (Exception e) {
            // ForegroundServiceStartNotAllowedException y parientes.
            return false;
        }
    }

    /**
     * Repinta la notificación con el id de la fila de registro_tomas, que
     * recién se conoce después de la llamada de red. El tono NO se toca:
     * lo maneja el MediaPlayer, no la notificación.
     */
    public static void actualizarRegistro(Context ctx, int idMedicamento, int idHorario,
                                          int idRegistro, String hora, int indice,
                                          String mensaje, String nombreMed, String dosisTxt) {
        if (!activo) return;
        Intent i = intentBase(ctx, ACTION_UPDATE, idMedicamento, idHorario,
            idRegistro, hora, indice, mensaje, nombreMed, dosisTxt);
        try {
            ContextCompat.startForegroundService(ctx, i);
        } catch (Exception ignored) {
            // Si no se puede actualizar, la alarma sigue sonando igual.
        }
    }

    /** Corta el sonido. Seguro de llamar aunque no haya nada sonando. */
    public static void detener(Context ctx) {
        if (!activo) return;
        try {
            // stopService, no startService: si el servicio ya murió, esto no
            // lo resucita solo para pedirle que se apague.
            ctx.stopService(new Intent(ctx, AlarmaService.class));
        } catch (Exception ignored) { }
    }

    private static Intent intentBase(Context ctx, String accion, int idMedicamento,
                                     int idHorario, int idRegistro, String hora,
                                     int indice, String mensaje, String nombreMed,
                                     String dosisTxt) {
        Intent i = new Intent(ctx, AlarmaService.class);
        i.setAction(accion);
        i.putExtra(NotificationHelper.EXTRA_ID_MED, idMedicamento);
        i.putExtra(NotificationHelper.EXTRA_ID_HORARIO, idHorario);
        i.putExtra(NotificationHelper.EXTRA_ID_REG, idRegistro);
        i.putExtra(NotificationHelper.EXTRA_HORA, hora);
        i.putExtra(NotificationHelper.EXTRA_INDICE, indice);
        i.putExtra(NotificationHelper.EXTRA_MENSAJE, mensaje);
        i.putExtra(NotificationHelper.EXTRA_NOMBRE, nombreMed);
        i.putExtra(NotificationHelper.EXTRA_DOSIS, dosisTxt);
        return i;
    }

    // ═══ Ciclo de vida ═════════════════════════════════════════

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Reinicio del sistema sin datos: no hay alarma que sonar.
            stopSelf();
            return START_NOT_STICKY;
        }

        String accion = intent.getAction();
        if (ACTION_STOP.equals(accion)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        leerExtras(intent);

        // Obligatorio dentro de los primeros 5 s de startForegroundService,
        // en TODOS los caminos, o el sistema mata el proceso con ANR.
        irAPrimerPlano();

        if (!sonando) {
            sonando = true;
            activo = true;
            empezarSonido();
            // Si nadie responde, se pospone sola: ni suena para siempre ni
            // se come la batería.
            handler.postDelayed(this::posponerPorFaltaDeRespuesta,
                duracionPara(this, idHorario));
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        pararSonido();
        sonando = false;
        activo = false;

        // La Activity de pantalla completa puede seguir abierta (por ejemplo
        // si la persona respondió desde los botones de la notificación).
        Intent fin = new Intent(ACTION_TERMINADA);
        fin.setPackage(getPackageName());
        sendBroadcast(fin);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ═══ Notificación de primer plano ══════════════════════════

    private void irAPrimerPlano() {
        // El canal se crea acá y no solo en la Activity: cuando la alarma
        // dispara con la app cerrada, este puede ser el primer código de
        // Vimed que corre en el proceso.
        NotificationHelper.crearCanal(this);

        android.app.Notification n = NotificationHelper.construirNotificacionToma(
            this, idMedicamento, idHorario, idRegistro, hora, indice,
            "Hora de tu medicamento", mensaje, nombreMed, dosisTxt,
            NotificationHelper.ALARM_FG_CHANNEL_ID, true);

        int id = NotificationHelper.idNotifPara(idMedicamento, indice);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(id, n);
        }
    }

    private void leerExtras(Intent i) {
        idMedicamento = i.getIntExtra(NotificationHelper.EXTRA_ID_MED, idMedicamento);
        idHorario     = i.getIntExtra(NotificationHelper.EXTRA_ID_HORARIO, idHorario);
        indice        = i.getIntExtra(NotificationHelper.EXTRA_INDICE, indice);

        int reg = i.getIntExtra(NotificationHelper.EXTRA_ID_REG, -1);
        if (reg > 0) idRegistro = reg;   // el update solo agrega, nunca borra

        if (i.hasExtra(NotificationHelper.EXTRA_HORA))    hora      = i.getStringExtra(NotificationHelper.EXTRA_HORA);
        if (i.hasExtra(NotificationHelper.EXTRA_MENSAJE)) mensaje   = i.getStringExtra(NotificationHelper.EXTRA_MENSAJE);
        if (i.hasExtra(NotificationHelper.EXTRA_NOMBRE))  nombreMed = i.getStringExtra(NotificationHelper.EXTRA_NOMBRE);
        if (i.hasExtra(NotificationHelper.EXTRA_DOSIS))   dosisTxt  = i.getStringExtra(NotificationHelper.EXTRA_DOSIS);

        if (mensaje == null) mensaje = "Es hora de tu medicamento";
    }

    // ═══ Sonido y vibración ════════════════════════════════════

    private void empezarSonido() {
        pedirFocoDeAudio();

        try {
            player = new MediaPlayer();
            player.setAudioAttributes(NotificationHelper.atributosDeAlarma());
            player.setDataSource(this, NotificationHelper.sonidoDeAlarma());
            player.setLooping(true);
            // Mantiene la CPU despierta: sin esto el tono se corta cuando el
            // celular vuelve a dormirse con la pantalla apagada.
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.prepare();
            player.start();
        } catch (Exception e) {
            // Sin tono disponible al menos vibramos: la vibración va aparte.
            liberarPlayer();
        }

        empezarVibracion();
    }

    private void empezarVibracion() {
        vibrador = obtenerVibrador();
        if (vibrador == null || !vibrador.hasVibrator()) return;

        AudioAttributes attrs = NotificationHelper.atributosDeAlarma();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrador.vibrate(
                    VibrationEffect.createWaveform(PATRON_VIBRACION, 0), attrs);
            } else {
                vibrador.vibrate(PATRON_VIBRACION, 0, attrs);
            }
        } catch (Exception ignored) { }
    }

    private Vibrator obtenerVibrador() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm != null ? vm.getDefaultVibrator() : null;
        }
        return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void pararSonido() {
        liberarPlayer();
        if (vibrador != null) {
            try { vibrador.cancel(); } catch (Exception ignored) { }
            vibrador = null;
        }
        soltarFocoDeAudio();
    }

    private void liberarPlayer() {
        if (player == null) return;
        try {
            if (player.isPlaying()) player.stop();
        } catch (Exception ignored) { }
        try { player.release(); } catch (Exception ignored) { }
        player = null;
    }

    /** Pausa la música que esté sonando mientras dura la alarma. */
    private void pedirFocoDeAudio() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(NotificationHelper.atributosDeAlarma())
                    .build();
                audioManager.requestAudioFocus(focusRequest);
            } else {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
        } catch (Exception ignored) { }
    }

    private void soltarFocoDeAudio() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        } catch (Exception ignored) { }
        focusRequest = null;
        audioManager = null;
    }

    // ═══ Nadie respondió ═══════════════════════════════════════

    private void posponerPorFaltaDeRespuesta() {
        // Reusamos la lógica de datos que ya vive en AlarmaReceiver: marca la
        // toma como pospuesta, reagenda a 15 min y avisa al cuidador.
        Intent i = new Intent(this, AlarmaReceiver.class);
        i.setAction(NotificationHelper.ACTION_SNOOZE);
        i.putExtra(NotificationHelper.EXTRA_ID_MED, idMedicamento);
        i.putExtra(NotificationHelper.EXTRA_ID_HORARIO, idHorario);
        i.putExtra(NotificationHelper.EXTRA_ID_REG, idRegistro);
        i.putExtra(NotificationHelper.EXTRA_HORA, hora);
        i.putExtra(NotificationHelper.EXTRA_INDICE, indice);
        sendBroadcast(i);

        stopSelf();
    }
}
