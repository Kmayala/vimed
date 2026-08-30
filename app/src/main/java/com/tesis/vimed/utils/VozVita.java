package com.tesis.vimed.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * La voz de Vita, con el lector que ya trae el celular.
 *
 * POR QUÉ NO SE PAGA POR ESTO. Hay APIs que generan voz y suenan algo
 * mejor, pero cuestan por carácter, necesitan conexión y agregan un
 * servicio más que puede fallar. TextToSpeech es parte de Android:
 * gratis, sin red, y ya está instalado. En una app que se usa a las siete
 * de la mañana en la cocina, que funcione sin señal vale más que el timbre
 * de la voz.
 *
 * MÁS LENTO QUE LO NORMAL. La velocidad va a 0.92 a propósito. El ritmo
 * por defecto está pensado para alguien que escucha una notificación al
 * pasar, no para una persona de 78 años que está tratando de entender qué
 * hacer con su medicación.
 */
public class VozVita {

    /** Nada de leer respuestas larguísimas: TTS corta a los 4000 caracteres. */
    private static final int MAX_CARACTERES = 3500;

    private TextToSpeech tts;
    private boolean listo = false;
    private boolean habilitada = true;

    /** Lo último que se pidió leer antes de que el motor estuviera listo. */
    private String pendiente;

    public interface AlCambiarEstado {
        void enEstado(boolean hablando);
    }

    private AlCambiarEstado observador;

    public VozVita(Context ctx, AlCambiarEstado observador) {
        this.observador = observador;

        tts = new TextToSpeech(ctx.getApplicationContext(), estado -> {
            if (estado != TextToSpeech.SUCCESS) return;

            // Español de la región si está; si no, cualquier español. Un
            // celular sin español instalado leería el texto en inglés, que
            // es peor que no leer nada.
            int r = tts.setLanguage(new Locale("es", "PY"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(new Locale("es", "AR"));
            }
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(new Locale("es"));
            }
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                return;   // sin español: mejor callarse que leer en inglés
            }

            tts.setSpeechRate(0.92f);
            listo = true;

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id)  { avisar(true); }
                @Override public void onDone(String id)   { avisar(false); }
                @Override public void onError(String id)  { avisar(false); }
            });

            if (pendiente != null) {
                String p = pendiente;
                pendiente = null;
                leer(p);
            }
        });
    }

    /** Lee la respuesta en voz alta. Si ya estaba hablando, la pisa. */
    public void leer(String texto) {
        if (!habilitada || texto == null || texto.trim().isEmpty()) return;

        if (!listo) {
            // El motor tarda en arrancar la primera vez. Se guarda para
            // leerlo apenas esté, en vez de perder la respuesta.
            pendiente = texto;
            return;
        }

        String limpio = paraLeer(texto);
        tts.speak(limpio, TextToSpeech.QUEUE_FLUSH, null, "vita");
    }

    public void callar() {
        pendiente = null;
        if (tts != null && listo) tts.stop();
        avisar(false);
    }

    /** Para el botón de silenciar. */
    public void habilitar(boolean si) {
        habilitada = si;
        if (!si) callar();
    }

    public boolean estaHabilitada() { return habilitada; }

    public void liberar() {
        observador = null;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        listo = false;
    }

    /**
     * Saca lo que el lector pronunciaría mal.
     *
     * El prompt ya le pide al modelo que escriba texto corrido sin
     * símbolos, pero un modelo se olvida. Sin esta limpieza, un asterisco
     * suelto se escucha como "asterisco" en el medio de una frase sobre
     * una dosis.
     */
    static String paraLeer(String texto) {
        String t = texto.replaceAll("[*_#`>]", " ")   // marcas de markdown
                        .replaceAll("^\\s*[-•]\\s*", " ")
                        .replaceAll("\\n\\s*[-•]\\s*", ". ")
                        .replaceAll("\\s+", " ")
                        .trim();
        return t.length() > MAX_CARACTERES ? t.substring(0, MAX_CARACTERES) : t;
    }

    private void avisar(boolean hablando) {
        if (observador != null) observador.enEstado(hablando);
    }
}
