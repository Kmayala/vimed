package com.tesis.vimed.api;

import com.tesis.vimed.models.CatalogoMedicamento;

import java.util.List;
import java.util.Locale;

/**
 * Compara la dosis que cargó la persona contra la del catálogo.
 *
 * QUÉ PUEDE Y QUÉ NO PUEDE DECIR ESTO. El catálogo guarda una sola
 * `dosis_comun` por medicamento: la presentación habitual, no un rango
 * terapéutico con mínimo y máximo. Por eso este chequeo NUNCA afirma que
 * una dosis sea incorrecta o peligrosa — no tiene con qué. Lo único que
 * puede decir, y lo que dice, es "esto no se parece a lo habitual,
 * revisalo contra la receta".
 *
 * Esa distinción no es un tecnicismo: una dosis alta puede estar
 * perfectamente indicada por el médico, y presentarla como un error haría
 * que la persona desconfíe de su tratamiento o, peor, que se acostumbre a
 * saltear avisos y después ignore uno que sí importaba.
 *
 * Lo que sí atrapa bien son los errores de tipeo, que son el riesgo real
 * al cargar a mano: el cero de más (50 mg → 500 mg) y la unidad
 * equivocada (5 ml de jarabe cargados como 5 mg).
 *
 * Clase PURA: sin red ni Android, se prueba con datos de laboratorio.
 */
public final class DosisChecker {

    /** A partir de esta proporción respecto de lo habitual, se avisa. */
    static final float FACTOR_REVISAR = 2f;

    /** A partir de acá el aviso es fuerte: huele a cero de más o de menos. */
    static final float FACTOR_ALTO = 5f;

    private DosisChecker() {}

    public enum Nivel {
        /** No hay nada que decir, o no hay con qué comparar. */
        NINGUNO,
        /** Difiere de lo habitual: vale la pena mirarlo. */
        REVISAR,
        /** Difiere muchísimo: casi seguro es un error de tipeo. */
        ALTO
    }

    public static class Aviso {
        public final Nivel nivel;
        public final String texto;
        /** Dosis habitual del catálogo, para mostrarla en el mensaje. */
        public final String dosisHabitual;

        Aviso(Nivel nivel, String texto, String dosisHabitual) {
            this.nivel = nivel;
            this.texto = texto;
            this.dosisHabitual = dosisHabitual;
        }

        public boolean hayAlgoQueDecir() { return nivel != Nivel.NINGUNO; }
    }

    private static final Aviso SIN_AVISO = new Aviso(Nivel.NINGUNO, null, null);

    /** Busca el medicamento en el catálogo y revisa la dosis contra él. */
    public static Aviso revisar(String nombreTipeado, List<CatalogoMedicamento> catalogo,
                                float dosis, String unidad) {
        return revisar(CatalogoMatcher.buscar(nombreTipeado, catalogo), dosis, unidad);
    }

    /**
     * @param referencia entrada del catálogo, o null si el medicamento no
     *        está — en ese caso no se avisa nada: no sabemos qué es
     *        "habitual" para algo que no conocemos, y un aviso inventado es
     *        peor que ningún aviso.
     */
    public static Aviso revisar(CatalogoMedicamento referencia, float dosis, String unidad) {
        if (referencia == null) return SIN_AVISO;
        if (dosis <= 0) return SIN_AVISO;

        float habitual = referencia.getDosisComun();
        if (habitual <= 0) return SIN_AVISO;

        String unidadRef = referencia.getUnidad();
        String nombre = referencia.getNombreComercial() != null
            ? referencia.getNombreComercial() : "este medicamento";
        String habitualTxt = formatear(habitual) + (unidadRef != null ? " " + unidadRef : "");

        // Unidad distinta: comparar los números no significaría nada
        // (5 ml y 5 mg no son cantidades comparables), así que el aviso es
        // sobre la unidad y ahí termina.
        if (!mismaUnidad(unidad, unidadRef)) {
            return new Aviso(Nivel.REVISAR,
                nombre + " suele venir en " + unidadRef + " y cargaste "
                    + formatear(dosis) + " " + (unidad != null ? unidad : "")
                    + ". Revisá la unidad en la caja o en la receta.",
                habitualTxt);
        }

        float factor = dosis / habitual;
        boolean esMayor = factor > 1;
        // Un factor de 0,2 se lee igual de lejos que uno de 5: lo damos vuelta.
        float veces = esMayor ? factor : 1f / factor;

        if (veces >= FACTOR_ALTO) {
            return new Aviso(Nivel.ALTO,
                "Cargaste " + formatear(dosis) + " " + unidad + " de " + nombre
                    + ", que es " + formatear(veces) + " veces "
                    + (esMayor ? "más" : "menos") + " que la dosis habitual ("
                    + habitualTxt + "). Fijate que no se haya colado un cero de "
                    + (esMayor ? "más" : "menos") + ".",
                habitualTxt);
        }

        if (veces >= FACTOR_REVISAR) {
            return new Aviso(Nivel.REVISAR,
                "La dosis habitual de " + nombre + " es " + habitualTxt
                    + ". Cargaste " + formatear(dosis) + " " + unidad
                    + ". Puede estar bien si tu médico lo indicó así; si no,"
                    + " revisá la receta.",
                habitualTxt);
        }

        return SIN_AVISO;
    }

    // ═══ Helpers ═══════════════════════════════════════════════

    /** Compara unidades sin distinguir mayúsculas ni espacios. */
    static boolean mismaUnidad(String a, String b) {
        // Si falta alguna de las dos no inventamos un aviso de unidad: el
        // catálogo puede tener el campo vacío.
        if (a == null || b == null) return true;
        String na = a.trim().toLowerCase(Locale.ROOT);
        String nb = b.trim().toLowerCase(Locale.ROOT);
        if (na.isEmpty() || nb.isEmpty()) return true;
        return na.equals(nb);
    }

    /** "500" en vez de "500.0"; "2,5" con un decimal cuando hace falta. */
    static String formatear(float v) {
        if (v == Math.round(v)) return String.valueOf(Math.round(v));
        return String.format(Locale.getDefault(), "%.1f", v);
    }
}
