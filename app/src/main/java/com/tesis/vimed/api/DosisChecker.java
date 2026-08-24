package com.tesis.vimed.api;

import com.tesis.vimed.models.CatalogoMedicamento;
import com.tesis.vimed.models.PerfilClinico;

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

        /**
         * True si este aviso salió de mirar el peso o la edad.
         *
         * Lo necesita la pantalla de carga: el aviso común ya se mostró en
         * el paso de la dosis, pero el del peso recién se puede calcular al
         * final —cuando ya se eligió la frecuencia—, así que es la primera
         * vez que la persona lo ve y hay que mostrarlo aunque no sea ALTO.
         */
        public final boolean usaPerfil;

        Aviso(Nivel nivel, String texto, String dosisHabitual) {
            this(nivel, texto, dosisHabitual, false);
        }

        Aviso(Nivel nivel, String texto, String dosisHabitual, boolean usaPerfil) {
            this.nivel = nivel;
            this.texto = texto;
            this.dosisHabitual = dosisHabitual;
            this.usaPerfil = usaPerfil;
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
     * Igual que {@link #revisar(CatalogoMedicamento, float, String)} pero
     * teniendo en cuenta el peso y la edad del paciente.
     *
     * QUÉ AGREGA EL PESO, Y QUÉ NO. Cuando la entrada del catálogo tiene
     * cargado el rango en mg por kilo y por día, se puede calcular cuánto le
     * correspondería a ESTA persona y comparar contra lo que cargó. Eso es
     * bastante más específico que la comparación contra la presentación
     * habitual, pero sigue siendo lo mismo en el fondo: una comparación
     * contra una referencia, no una indicación. La app no receta.
     *
     * Cuando el catálogo NO tiene esos números —que hoy es el caso de casi
     * todo el catálogo— este método se comporta exactamente igual que el de
     * siempre. Es a propósito: sin fuente clínica no hay nada que calcular,
     * y una cuenta inventada sobre un dato médico es peor que no decir nada.
     *
     * @param perfil      peso y edad; puede venir vacío.
     * @param tomasPorDia cuántas veces al día se toma. Con 0 o menos no se
     *                    revisa la dosis diaria: sin saber la frecuencia,
     *                    50 mg pueden ser 50 o 200 por día.
     */
    public static Aviso revisar(CatalogoMedicamento referencia, float dosis,
                                String unidad, PerfilClinico perfil, int tomasPorDia) {
        // El chequeo de siempre manda: un cero de más o una unidad
        // equivocada son errores más graves y más frecuentes que una dosis
        // diaria fuera de rango, y hay que mostrar UN aviso, no dos.
        Aviso base = revisar(referencia, dosis, unidad);
        if (base.hayAlgoQueDecir()) return base;

        if (referencia == null || perfil == null || dosis <= 0) return SIN_AVISO;

        Aviso porPeso = revisarDosisDiaria(referencia, dosis, unidad, perfil, tomasPorDia);
        if (porPeso.hayAlgoQueDecir()) return porPeso;

        return revisarEdad(referencia, perfil);
    }

    /**
     * Compara la dosis diaria contra el rango por kilo del catálogo.
     * Devuelve SIN_AVISO si falta cualquiera de los datos que necesita.
     */
    private static Aviso revisarDosisDiaria(CatalogoMedicamento ref, float dosis,
                                            String unidad, PerfilClinico perfil,
                                            int tomasPorDia) {
        if (!ref.tieneReferenciaPorPeso()) return SIN_AVISO;
        if (!perfil.tienePeso()) return SIN_AVISO;
        if (tomasPorDia <= 0) return SIN_AVISO;

        // El rango del catálogo está en mg/kg. Comparar contra ml o UI no
        // significaría nada.
        if (!mismaUnidad(unidad, "mg") || !mismaUnidad(ref.getUnidad(), "mg")) {
            return SIN_AVISO;
        }

        float peso = perfil.getPesoKg();
        float diariaCargada = dosis * tomasPorDia;

        float min = ref.getDosisMgKgDiaMin() * peso;
        float max = ref.getDosisMgKgDiaMax() * peso;

        // El techo absoluto recorta el rango: muchos medicamentos se
        // dosifican por kilo HASTA un tope, y sin esto una persona de 110 kg
        // recibiría una referencia por encima de la dosis máxima real.
        float tope = ref.getDosisMaxDia();
        if (tope > 0) {
            if (max > tope) max = tope;
            if (min > tope) min = tope;
        }

        String nombre = ref.getNombreComercial() != null
            ? ref.getNombreComercial() : "este medicamento";
        String rango = formatear(min) + " a " + formatear(max) + " mg por día";
        String cargado = "Con " + formatear(dosis) + " mg " + vecesAlDia(tomasPorDia)
            + " estarías tomando " + formatear(diariaCargada) + " mg por día. ";

        if (diariaCargada > max) {
            // Muy por encima del techo huele a error de carga; apenas por
            // encima puede ser perfectamente lo que indicó el médico.
            Nivel nivel = (max > 0 && diariaCargada / max >= FACTOR_ALTO)
                ? Nivel.ALTO : Nivel.REVISAR;
            return new Aviso(nivel,
                cargado + "Para " + formatear(peso) + " kg, lo habitual de "
                    + nombre + " es " + rango + ". Revisalo con tu médico"
                    + (nivel == Nivel.ALTO ? " antes de tomarlo." : "."),
                rango, true);
        }

        if (diariaCargada < min) {
            return new Aviso(Nivel.REVISAR,
                cargado + "Para " + formatear(peso) + " kg, lo habitual de "
                    + nombre + " es " + rango + ". Puede estar bien si tu"
                    + " médico lo indicó así; si no, revisá la receta.",
                rango, true);
        }

        return SIN_AVISO;
    }

    /**
     * Aviso de adulto mayor. No propone una dosis nueva: cuánto se reduce
     * depende de la función renal de cada persona, y la app no la conoce.
     * Lo único que puede hacer es marcar que este medicamento es de los que
     * conviene conversar.
     */
    private static Aviso revisarEdad(CatalogoMedicamento ref, PerfilClinico perfil) {
        if (!ref.isAjustarEnMayores() || !perfil.esAdultoMayor()) return SIN_AVISO;

        String nota = ref.getNotaMayores();
        String nombre = ref.getNombreComercial() != null
            ? ref.getNombreComercial() : "Este medicamento";

        return new Aviso(Nivel.REVISAR,
            (nota != null && !nota.trim().isEmpty())
                ? nota.trim()
                : nombre + " suele indicarse en dosis más bajas después de los "
                    + PerfilClinico.EDAD_MAYOR + " años. Preguntale a tu médico"
                    + " si la tuya es la que te corresponde.",
            null, true);
    }

    /** "una vez al día", "dos veces al día", "3 veces al día". */
    private static String vecesAlDia(int tomas) {
        if (tomas == 1) return "una vez al día";
        if (tomas == 2) return "dos veces al día";
        return tomas + " veces al día";
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
