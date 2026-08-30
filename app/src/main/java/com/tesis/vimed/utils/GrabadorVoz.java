package com.tesis.vimed.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;

import java.io.File;

/**
 * Graba lo que la persona dice, para mandarlo a transcribir.
 *
 * TOCAR PARA EMPEZAR Y TOCAR PARA TERMINAR, no mantener apretado.
 * Sostener un botón mientras se habla exige coordinar dos cosas a la vez,
 * y basta que el dedo se corra un milímetro para perder la grabación
 * entera. Dos toques separados perdonan el pulso.
 *
 * El formato es m4a con AAC: lo entiende el transcriptor, pesa poco —lo
 * que importa cuando se sube por datos móviles— y lo genera el propio
 * Android sin librerías.
 */
public class GrabadorVoz {

    /** Más de esto no es una consulta, es un monólogo. Y se paga por minuto. */
    public static final int MAX_SEGUNDOS = 60;

    private MediaRecorder grabador;
    private File archivo;
    private boolean grabando = false;

    public boolean estaGrabando() { return grabando; }

    /**
     * @return false si el micrófono no se pudo abrir.
     */
    public boolean empezar(Context ctx) {
        if (grabando) return true;
        try {
            File dir = new File(ctx.getCacheDir(), "voz");
            if (!dir.exists() && !dir.mkdirs()) return false;
            archivo = new File(dir, "consulta.m4a");

            grabador = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new MediaRecorder(ctx)
                : new MediaRecorder();

            grabador.setAudioSource(MediaRecorder.AudioSource.MIC);
            grabador.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            grabador.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            // 16 kHz mono: es lo que usan los transcriptores. Grabar en
            // calidad de música multiplica el peso del archivo sin que se
            // entienda una palabra más.
            grabador.setAudioSamplingRate(16000);
            grabador.setAudioChannels(1);
            grabador.setAudioEncodingBitRate(32000);
            grabador.setMaxDuration(MAX_SEGUNDOS * 1000);
            grabador.setOutputFile(archivo.getAbsolutePath());

            grabador.prepare();
            grabador.start();
            grabando = true;
            return true;

        } catch (Exception e) {
            soltar();
            return false;
        }
    }

    /**
     * Corta la grabación.
     *
     * @return el archivo grabado, o null si no sirve. Devuelve null también
     *         cuando el audio es demasiado corto: un toque accidental
     *         genera un archivo de medio segundo que igual se cobraría y
     *         que solo puede transcribirse como ruido.
     */
    public File terminar() {
        if (!grabando) return null;
        grabando = false;

        try {
            grabador.stop();
        } catch (Exception e) {
            // stop() tira si no llegó a grabar nada. El archivo no sirve.
            soltar();
            borrar();
            return null;
        } finally {
            soltar();
        }

        if (archivo == null || !archivo.exists() || archivo.length() < 2000) {
            borrar();
            return null;
        }
        return archivo;
    }

    /** Cancela sin devolver nada. Para cuando se cierra la pantalla. */
    public void cancelar() {
        if (grabando) {
            grabando = false;
            try { grabador.stop(); } catch (Exception ignored) { }
        }
        soltar();
        borrar();
    }

    /** El audio se borra apenas se transcribió: es la voz de alguien. */
    public void borrar() {
        if (archivo != null && archivo.exists()) archivo.delete();
    }

    private void soltar() {
        if (grabador != null) {
            try { grabador.release(); } catch (Exception ignored) { }
            grabador = null;
        }
    }
}
