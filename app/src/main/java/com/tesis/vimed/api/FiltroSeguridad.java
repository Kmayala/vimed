package com.tesis.vimed.api;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Lo que la app decide por sí misma, antes de preguntarle al modelo.
 *
 * POR QUÉ EXISTE. El prompt le pide al modelo que no dé indicaciones
 * médicas, y en general lo cumple. Pero un modelo tiene malos días: se
 * puede dejar convencer si la persona insiste, o interpretar mal una
 * frase. Un {@code if} no.
 *
 * Acá viven las dos únicas categorías donde una respuesta equivocada le
 * hace daño a alguien:
 *
 *   URGENCIA  — hay que mandar a llamar a emergencias, no conversar.
 *   DOSIS     — está por cambiar una dosis por su cuenta.
 *
 * Cuando una de las dos se dispara, la app contesta sola y NO llama a la
 * API. Es más rápido, más barato, y sobre todo es siempre igual: el
 * modelo puede variar su respuesta entre dos ejecuciones idénticas, esto
 * no.
 *
 * QUÉ NO ES. No es un filtro de contenido ni pretende atrapar todo. Todo
 * lo demás pasa al modelo, que para eso tiene el prompt. Ampliar esta
 * lista tiene un costo: cada patrón de más es una pregunta legítima que
 * se responde con un cartel en vez de con una explicación, y una app que
 * contesta con carteles deja de usarse.
 *
 * Java puro y sin Android, para poder probarla.
 */
public final class FiltroSeguridad {

    public enum Motivo { NINGUNO, URGENCIA, DOSIS }

    public static class Resultado {
        public final Motivo motivo;
        /** Lo que hay que responder, o null si la pregunta sigue al modelo. */
        public final String respuesta;

        Resultado(Motivo motivo, String respuesta) {
            this.motivo = motivo;
            this.respuesta = respuesta;
        }

        public boolean loRespondeLaApp() { return motivo != Motivo.NINGUNO; }
    }

    private static final Resultado PASA = new Resultado(Motivo.NINGUNO, null);

    /**
     * Síntomas que no admiten una conversación.
     *
     * Están en primer lugar porque una urgencia manda sobre cualquier otra
     * cosa: si alguien escribe que le falta el aire mientras pregunta por
     * una dosis, lo que importa es el aire.
     */
    private static final String[] URGENCIA = {
        "dolor en el pecho", "dolor de pecho", "me duele el pecho",
        "opresion en el pecho", "presion en el pecho",
        "no puedo respirar", "me falta el aire", "falta de aire",
        "me cuesta respirar", "me ahogo",
        "me desmaye", "me desmayo", "perdi el conocimiento",
        "no puedo hablar", "se me traba la lengua", "no me sale hablar",
        "no siento el brazo", "no siento la cara", "se me durmio la cara",
        "vomito sangre", "sangre en la orina", "no para de sangrar",
        "convulsion", "convulsiones"
    };

    /**
     * Frases de alguien a punto de cambiar su dosis.
     *
     * Cada patrón describe una ACCIÓN sobre la medicación, no un tema. Por
     * eso "cuanto tengo que tomar" no está: es una pregunta, y la responde
     * el modelo diciendo que eso lo indica el médico. "Voy a tomar el
     * doble" sí está: ya es una decisión tomada.
     */
    private static final String[] DOSIS = {
        "tomo el doble", "tomar el doble", "tomo doble", "me tomo dos",
        "tomo dos en vez de una", "tomo dos en lugar de una",
        "subo la dosis", "subir la dosis", "aumento la dosis",
        "aumentar la dosis", "me subo la dosis",
        "bajo la dosis", "bajar la dosis", "reduzco la dosis",
        "tomo la mitad", "tomar la mitad", "parto la pastilla",
        "dejo de tomar", "dejar de tomar", "voy a dejar",
        "suspendo el", "suspender el", "corto el tratamiento"
    };

    private FiltroSeguridad() {}

    public static Resultado revisar(String mensaje) {
        String t = normalizar(mensaje);
        if (t.isEmpty()) return PASA;

        // La urgencia gana siempre.
        if (contieneAlguno(t, URGENCIA)) {
            return new Resultado(Motivo.URGENCIA,
                "Por lo que me contás, esto no puede esperar. Llamá ya al "
                    + "servicio de emergencias o pedí que te lleven a una "
                    + "guardia. No esperes a ver si se pasa.");
        }

        if (contieneAlguno(t, DOSIS)) {
            return new Resultado(Motivo.DOSIS,
                "Esa es una decisión que tiene que tomar tu médico, no yo ni "
                    + "vos por tu cuenta. Cambiar una dosis o dejar un "
                    + "remedio puede tener efectos que no se ven enseguida. "
                    + "Llamá a tu médico o preguntale en la farmacia. Si "
                    + "querés, anotá la duda y la llevás a la próxima "
                    + "consulta.");
        }

        return PASA;
    }

    private static boolean contieneAlguno(String texto, String[] patrones) {
        for (String p : patrones) if (texto.contains(p)) return true;
        return false;
    }

    /**
     * Minúsculas, sin acentos y con la puntuación pasada a espacios.
     *
     * Lo de la puntuación importa más de lo que parece: el mensaje puede
     * venir de la transcripción de un audio, y ahí las comas caen donde el
     * transcriptor quiere. "Tomo, el doble" tiene que engancharse igual.
     */
    static String normalizar(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT),
            Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[^a-z0-9ñ]+", " ").trim();
    }
}
