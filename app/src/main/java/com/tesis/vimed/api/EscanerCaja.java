package com.tesis.vimed.api;

import com.tesis.vimed.models.CatalogoMedicamento;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saca el nombre y la dosis del texto que se leyó de una caja.
 *
 * LO DIFÍCIL NO ES LEER. El lector devuelve TODO lo que está impreso: la
 * marca, el principio activo, "500 mg", "20 comprimidos recubiertos", el
 * laboratorio, el lote, el vencimiento, el código de barras en números.
 * Nada de eso viene etiquetado. Elegir cuál de esas líneas es el
 * medicamento es el trabajo, y lo resuelve el catálogo: se prueba línea
 * por línea contra las entradas conocidas y la que engancha es el nombre.
 *
 * Lo que no está en el catálogo no se adivina. Se devuelve lo que se pudo
 * y el resto queda para que la persona lo complete: inventar el nombre de
 * un medicamento a partir de un texto borroso es exactamente el tipo de
 * error que esta app no se puede permitir.
 *
 * Esta clase es Java puro y no toca Android, para poder probarla.
 */
public final class EscanerCaja {

    /**
     * Un número seguido de una unidad de dosis.
     *
     * La unidad es OBLIGATORIA, y ahí está la gracia: una caja dice también
     * "20 comprimidos", "30 unidades", "x 10". Sin exigir la unidad, el
     * primer número de la caja se colaba como dosis, y la cantidad de
     * comprimidos es el número que más grande viene impreso.
     *
     * Se aceptan coma y punto decimal ("0,4 mg" y "0.4 mg" conviven en las
     * cajas según el laboratorio).
     */
    private static final Pattern DOSIS = Pattern.compile(
        "(?<![\\w.,])(\\d{1,5}(?:[.,]\\d{1,3})?)\\s*(mg|mcg|ml|g|ui)(?![a-záéíóúñ])",
        Pattern.CASE_INSENSITIVE);

    /** Lo que se pudo sacar de la caja. Cualquier campo puede faltar. */
    public static class Lectura {
        /** Entrada del catálogo que enganchó, o null. */
        public final CatalogoMedicamento delCatalogo;
        /** Nombre para mostrar, o null si no se reconoció ninguno. */
        public final String nombre;
        /** 0 si no se encontró una dosis con unidad. */
        public final float dosis;
        /** "mg", "ml"… o null. */
        public final String unidad;

        Lectura(CatalogoMedicamento delCatalogo, String nombre, float dosis, String unidad) {
            this.delCatalogo = delCatalogo;
            this.nombre = nombre;
            this.dosis = dosis;
            this.unidad = unidad;
        }

        public boolean tieneNombre() { return nombre != null && !nombre.isEmpty(); }
        public boolean tieneDosis()  { return dosis > 0 && unidad != null; }
        public boolean vacia()       { return !tieneNombre() && !tieneDosis(); }
    }

    private static final Lectura NADA = new Lectura(null, null, 0f, null);

    private EscanerCaja() {}

    /**
     * @param lineas lo que leyó el lector, una entrada por renglón.
     * @param catalogo el catálogo de medicamentos; sin él no hay con qué
     *                 reconocer el nombre y solo se devuelve la dosis.
     */
    public static Lectura leer(List<String> lineas, List<CatalogoMedicamento> catalogo) {
        if (lineas == null || lineas.isEmpty()) return NADA;

        CatalogoMedicamento encontrado = buscarEnCatalogo(lineas, catalogo);
        float[] dosis = buscarDosis(lineas);

        String nombre = encontrado != null ? encontrado.getNombreComercial() : null;
        String unidad = dosis[1] > 0 ? UNIDADES[(int) dosis[1] - 1] : null;

        if (nombre == null && unidad == null) return NADA;
        return new Lectura(encontrado, nombre, dosis[0], unidad);
    }

    /** Índice → texto, para poder devolver la unidad desde un float[]. */
    private static final String[] UNIDADES = {"mg", "mcg", "ml", "g", "UI"};

    /**
     * La primera línea que corresponda a una entrada del catálogo.
     *
     * Se recorre en orden y no se busca "la mejor": las cajas ponen el
     * nombre arriba y los datos del laboratorio abajo, así que la primera
     * coincidencia es casi siempre la buena. Buscar la mejor obligaría a
     * puntuar, y una puntuación equivocada acá cambia el medicamento.
     */
    private static CatalogoMedicamento buscarEnCatalogo(
            List<String> lineas, List<CatalogoMedicamento> catalogo) {
        if (catalogo == null || catalogo.isEmpty()) return null;

        for (String linea : lineas) {
            if (linea == null) continue;
            String limpia = limpiar(linea);
            // Menos de cuatro letras no alcanza para reconocer nada y sí
            // para enganchar cualquier cosa por casualidad.
            if (limpia.length() < 4) continue;

            CatalogoMedicamento m = CatalogoMatcher.buscar(limpia, catalogo);
            if (m != null) return m;
        }
        return null;
    }

    /**
     * La dosis, buscada en TODAS las líneas.
     *
     * @return {valor, índiceDeUnidad+1}; {0,0} si no hay ninguna.
     */
    private static float[] buscarDosis(List<String> lineas) {
        for (String linea : lineas) {
            if (linea == null) continue;
            Matcher m = DOSIS.matcher(linea);
            if (!m.find()) continue;

            float valor;
            try {
                valor = Float.parseFloat(m.group(1).replace(',', '.'));
            } catch (NumberFormatException e) {
                continue;
            }
            if (valor <= 0) continue;

            String u = m.group(2).toLowerCase(Locale.ROOT);
            for (int i = 0; i < UNIDADES.length; i++) {
                if (UNIDADES[i].toLowerCase(Locale.ROOT).equals(u)) {
                    return new float[]{valor, i + 1};
                }
            }
        }
        return new float[]{0f, 0f};
    }

    /**
     * Deja la línea en algo que el catálogo pueda reconocer.
     *
     * Se le sacan la dosis y la presentación porque el nombre del catálogo
     * no las lleva: "ENALAPRIL 10 mg comprimidos" tiene que poder
     * engancharse con la entrada "Enalapril".
     */
    static String limpiar(String linea) {
        String s = linea.trim();
        s = DOSIS.matcher(s).replaceAll(" ");
        s = s.replaceAll("(?i)\\b(comprimidos?|capsulas?|cápsulas?|tabletas?"
            + "|recubiertos?|ranurados?|x\\s*\\d+|\\d+\\s*u(nidades)?)\\b", " ");
        return s.replaceAll("\\s+", " ").trim();
    }
}
