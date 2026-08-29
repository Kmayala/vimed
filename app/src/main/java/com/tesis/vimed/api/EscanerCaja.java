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
 * Nada de eso viene etiquetado, y elegir cuál de esas líneas es el
 * medicamento es el trabajo.
 *
 * Se resuelve en tres intentos, de más confiable a menos:
 *
 *   1. El CATÁLOGO. Si alguna línea corresponde a una entrada conocida, esa
 *      es. Además trae dosis habitual, presentación e instrucciones.
 *   2. LO QUE LA PERSONA YA TIENE CARGADO. Un antigripal escaneado una vez
 *      queda guardado como medicamento suyo, así que la próxima vez que
 *      fotografíe la misma caja se reconoce solo.
 *   3. EL TEXTO MÁS GRANDE. En una caja, el nombre del producto es lo que
 *      está impreso más grande. Es una apuesta, no un dato, y se marca como
 *      tal para que la pantalla lo diga.
 *
 * Esta clase es Java puro y no toca Android, para poder probarla.
 */
public final class EscanerCaja {

    /**
     * Una línea leída, con el alto que ocupa en la foto.
     *
     * El alto es la señal que permite reconocer lo que no está en ningún
     * lado: el nombre del producto es lo más grande impreso en la caja. El
     * lector ya lo devuelve —cada línea trae su rectángulo— y no se estaba
     * usando.
     */
    public static class Linea {
        public final String texto;
        /** Alto del rectángulo en píxeles. 0 si no se conoce. */
        public final int alto;

        public Linea(String texto, int alto) {
            this.texto = texto;
            this.alto = alto;
        }
    }

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

    /**
     * Palabras que aparecen en toda caja y NUNCA son el nombre.
     *
     * Una línea que es solo esto se descarta antes de considerarla. La
     * lista está en minúsculas y sin acentos: se compara contra el texto ya
     * normalizado.
     */
    private static final String[] RUIDO = {
        "laboratorio", "laboratorios", "industria", "paraguaya", "argentina",
        "brasilera", "nacional", "elaborado", "fabricado", "distribuido",
        "venta", "receta", "bajo", "libre", "archivada", "profesional",
        "comprimido", "comprimidos", "capsula", "capsulas", "tableta",
        "tabletas", "recubierto", "recubiertos", "ranurado", "ranurados",
        "jarabe", "gotas", "suspension", "solucion", "inyectable", "ampolla",
        "uso", "oral", "topico", "externo", "adulto", "adultos", "nino",
        "ninos", "pediatrico", "contenido", "neto", "envase", "conservar",
        "mantener", "alcance", "temperatura", "ambiente", "seco", "fresco",
        "lote", "vence", "vencimiento", "elab", "exp", "ven", "vto",
        "registro", "sanitario", "reg", "sanit", "codigo", "barras",
        "formula", "excipientes", "cada", "contiene", "leer", "prospecto",
        "consulte", "medico", "farmaceutico", "www", "com", "net"
    };

    /** Lo que se pudo sacar de la caja. Cualquier campo puede faltar. */
    public static class Lectura {
        /** Entrada del catálogo que enganchó, o null si no es una conocida. */
        public final CatalogoMedicamento delCatalogo;
        /** Nombre para mostrar, o null si no se pudo sacar ninguno. */
        public final String nombre;
        /** 0 si no se encontró una dosis con unidad. */
        public final float dosis;
        /** "mg", "ml"… o null. */
        public final String unidad;
        /**
         * True si el nombre salió de mirar el tamaño del texto y no de una
         * lista conocida.
         *
         * Importa que la pantalla lo diga: "Enalapril" reconocido del
         * catálogo y "TAPSIN" adivinado por ser lo más grande de la caja no
         * merecen la misma confianza, y quien confirma tiene que saber cuál
         * de las dos le están mostrando.
         */
        public final boolean nombreEsUnaApuesta;

        Lectura(CatalogoMedicamento delCatalogo, String nombre, float dosis,
                String unidad, boolean nombreEsUnaApuesta) {
            this.delCatalogo = delCatalogo;
            this.nombre = nombre;
            this.dosis = dosis;
            this.unidad = unidad;
            this.nombreEsUnaApuesta = nombreEsUnaApuesta;
        }

        public boolean tieneNombre() { return nombre != null && !nombre.isEmpty(); }
        public boolean tieneDosis()  { return dosis > 0 && unidad != null; }
        public boolean vacia()       { return !tieneNombre() && !tieneDosis(); }
    }

    private static final Lectura NADA = new Lectura(null, null, 0f, null, false);

    /** Índice → texto, para poder devolver la unidad desde un float[]. */
    private static final String[] UNIDADES = {"mg", "mcg", "ml", "g", "UI"};

    private EscanerCaja() {}

    /** Versión sin tamaños: no puede adivinar nombres desconocidos. */
    public static Lectura leer(List<String> textos, List<CatalogoMedicamento> catalogo) {
        List<Linea> lineas = new ArrayList<>();
        if (textos != null) {
            for (String t : textos) lineas.add(new Linea(t, 0));
        }
        return leer(lineas, catalogo, null);
    }

    /**
     * @param lineas   lo que leyó el lector, con el alto de cada renglón.
     * @param catalogo el catálogo de medicamentos conocidos.
     * @param yaCargados nombres de los medicamentos que la persona ya tiene;
     *                   sirve para reconocer lo que escaneó alguna vez.
     */
    public static Lectura leer(List<Linea> lineas, List<CatalogoMedicamento> catalogo,
                               List<String> yaCargados) {
        if (lineas == null || lineas.isEmpty()) return NADA;

        float[] dosis = buscarDosis(lineas);
        String unidad = dosis[1] > 0 ? UNIDADES[(int) dosis[1] - 1] : null;

        // 1) El catálogo: lo más confiable, y trae datos además del nombre.
        CatalogoMedicamento delCatalogo = buscarEnCatalogo(lineas, catalogo);
        if (delCatalogo != null) {
            return new Lectura(delCatalogo, delCatalogo.getNombreComercial(),
                dosis[0], unidad, false);
        }

        // 2) Lo que la persona ya tiene cargado.
        String conocido = buscarEntreLosSuyos(lineas, yaCargados);
        if (conocido != null) {
            return new Lectura(null, conocido, dosis[0], unidad, false);
        }

        // 3) El texto más grande de la caja. Es una apuesta.
        String apuesta = elTextoMasGrande(lineas);
        if (apuesta != null) {
            return new Lectura(null, apuesta, dosis[0], unidad, true);
        }

        if (unidad == null) return NADA;
        return new Lectura(null, null, dosis[0], unidad, false);
    }

    // ═══ 1. El catálogo ════════════════════════════════════════

    /**
     * La primera línea que corresponda a una entrada del catálogo.
     *
     * Se recorre en orden y no se busca "la mejor": las cajas ponen el
     * nombre arriba y los datos del laboratorio abajo, así que la primera
     * coincidencia es casi siempre la buena. Buscar la mejor obligaría a
     * puntuar, y una puntuación equivocada acá cambia el medicamento.
     */
    private static CatalogoMedicamento buscarEnCatalogo(
            List<Linea> lineas, List<CatalogoMedicamento> catalogo) {
        if (catalogo == null || catalogo.isEmpty()) return null;

        for (Linea l : lineas) {
            String limpia = limpiar(l.texto);
            if (limpia.length() < 4) continue;

            CatalogoMedicamento m = CatalogoMatcher.buscar(limpia, catalogo);
            if (m != null) return m;
        }
        return null;
    }

    // ═══ 2. Lo que ya tiene cargado ════════════════════════════

    /**
     * Reconoce un medicamento que la persona ya cargó alguna vez.
     *
     * Es lo que hace que un antigripal —o cualquier cosa que no esté en el
     * catálogo— se reconozca a partir del segundo escaneo: la primera vez
     * se adivina y se guarda como medicamento suyo; de ahí en más ya es un
     * nombre conocido.
     */
    private static String buscarEntreLosSuyos(List<Linea> lineas, List<String> yaCargados) {
        if (yaCargados == null || yaCargados.isEmpty()) return null;

        for (Linea l : lineas) {
            String limpia = normalizar(limpiar(l.texto));
            if (limpia.length() < 4) continue;

            for (String suyo : yaCargados) {
                if (suyo == null || suyo.trim().length() < 4) continue;
                String n = normalizar(suyo);
                if (limpia.contains(n) || n.contains(limpia)) return suyo.trim();
            }
        }
        return null;
    }

    // ═══ 3. El texto más grande ════════════════════════════════

    /**
     * El renglón más grande que pueda ser un nombre.
     *
     * En una caja, el nombre del producto es lo que está impreso más
     * grande: es lo que la marca quiere que se vea desde el mostrador. Se
     * descartan antes las líneas que seguro no son un nombre —las que son
     * puro número, las que solo dicen palabras de envase, las muy cortas—
     * y de las que quedan gana la más alta.
     *
     * A igualdad de alto gana la primera, que está más arriba en la caja.
     *
     * Sin altos —cuando el lector no los da— no se adivina nada: elegir
     * "la primera línea larga" acertaría a veces y pondría el nombre del
     * laboratorio otras, y no habría forma de saber cuál de las dos pasó.
     */
    private static String elTextoMasGrande(List<Linea> lineas) {
        String mejor = null;
        int altoMejor = 0;

        for (Linea l : lineas) {
            if (l.alto <= 0) continue;
            String candidato = limpiar(l.texto);
            if (!puedeSerNombre(candidato)) continue;

            if (l.alto > altoMejor) {
                altoMejor = l.alto;
                mejor = candidato;
            }
        }
        return mejor;
    }

    /** Filtra lo que seguro no es el nombre de un medicamento. */
    static boolean puedeSerNombre(String texto) {
        if (texto == null) return false;
        String t = texto.trim();
        // Un nombre de menos de cuatro letras no existe, y uno larguísimo
        // es una frase del prospecto.
        if (t.length() < 4 || t.length() > 40) return false;

        // Tiene que tener letras de verdad, no ser un código ni un lote.
        int letras = 0;
        for (char c : t.toCharArray()) if (Character.isLetter(c)) letras++;
        if (letras < 4) return false;
        if (letras < t.length() / 2) return false;

        // Si TODAS sus palabras son de envase o de trámite, no es el nombre.
        String norm = normalizar(t);
        boolean algoUtil = false;
        for (String palabra : norm.split("[^a-z0-9]+")) {
            if (palabra.length() < 3) continue;
            if (!esRuido(palabra)) { algoUtil = true; break; }
        }
        return algoUtil;
    }

    private static boolean esRuido(String palabra) {
        for (String r : RUIDO) if (r.equals(palabra)) return true;
        return false;
    }

    // ═══ La dosis ══════════════════════════════════════════════

    /**
     * La dosis, buscada en TODAS las líneas.
     *
     * @return {valor, índiceDeUnidad+1}; {0,0} si no hay ninguna.
     */
    private static float[] buscarDosis(List<Linea> lineas) {
        for (Linea l : lineas) {
            if (l.texto == null) continue;
            Matcher m = DOSIS.matcher(l.texto);
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

    // ═══ Limpieza ══════════════════════════════════════════════

    /**
     * Deja la línea en algo que se pueda comparar con un nombre.
     *
     * Se le sacan la dosis y la presentación porque el nombre no las lleva:
     * "ENALAPRIL 10 mg comprimidos" tiene que poder engancharse con la
     * entrada "Enalapril".
     */
    static String limpiar(String linea) {
        if (linea == null) return "";
        String s = linea.trim();
        s = DOSIS.matcher(s).replaceAll(" ");
        s = s.replaceAll("(?i)\\b(comprimidos?|capsulas?|cápsulas?|tabletas?"
            + "|recubiertos?|ranurados?|x\\s*\\d+|\\d+\\s*u(nidades)?)\\b", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Minúsculas y sin acentos, para comparar. */
    static String normalizar(String s) {
        if (s == null) return "";
        return java.text.Normalizer
            .normalize(s.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }
}
