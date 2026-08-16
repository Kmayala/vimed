package com.tesis.vimed.models;

import com.tesis.vimed.R;

/**
 * Especialidades médicas precargadas, con su ícono y su color.
 *
 * Antes la especialidad era un campo de texto libre: cada persona escribía
 * "cardiologo", "Cardiología", "corazón" o lo dejaba vacío, así que la
 * misma consulta quedaba guardada de cuatro formas distintas y no había
 * manera de agrupar ni de mostrar nada por especialidad.
 *
 * El texto que se guarda en la base es {@link #nombre}, en el mismo
 * formato para todos. El ícono y el color son solo presentación: si
 * mañana se agrega una especialidad, las citas viejas se siguen viendo.
 *
 * La lista es la de un centro de salud típico en Paraguay, ordenada por
 * lo que más consulta un adulto mayor. "Otra" queda al final y abre el
 * campo de texto: no se puede pretender cubrir todas las especialidades
 * con una grilla de once.
 */
public enum Especialidad {

    GENERAL      ("Clínica general", R.drawable.ic_esp_cruz,    R.color.brand_500),
    CARDIOLOGIA  ("Cardiología",     R.drawable.ic_esp_corazon, R.color.rosa_500),
    TRAUMATOLOGIA("Traumatología",   R.drawable.ic_esp_hueso,   R.color.azul_500),
    OFTALMOLOGIA ("Oftalmología",    R.drawable.ic_esp_ojo,     R.color.verde_500),
    ODONTOLOGIA  ("Odontología",     R.drawable.ic_esp_diente,  R.color.azul_500),
    NEUROLOGIA   ("Neurología",      R.drawable.ic_esp_cabeza,  R.color.naranja_500),
    NEUMOLOGIA   ("Neumología",      R.drawable.ic_esp_pulmon,  R.color.azul_500),
    ENDOCRINO    ("Endocrinología",  R.drawable.ic_esp_gota,    R.color.rosa_500),
    UROLOGIA     ("Urología",        R.drawable.ic_esp_rinon,   R.color.naranja_500),
    NUTRICION    ("Nutrición",       R.drawable.ic_esp_manzana, R.color.verde_500),
    OTRA         ("Otra",            R.drawable.ic_esp_cruz,    R.color.brand_500);

    public final String nombre;
    public final int icono;
    public final int color;

    Especialidad(String nombre, int icono, int color) {
        this.nombre = nombre;
        this.icono = icono;
        this.color = color;
    }

    public boolean esOtra() { return this == OTRA; }

    /**
     * Busca la especialidad de una cita ya guardada.
     *
     * Tolerante a propósito: hay citas cargadas antes de que existiera
     * esta lista, con el texto escrito a mano y sin tildes. Si no se
     * reconoce se devuelve null y quien llama usa el ícono genérico —
     * nunca se pierde la cita por no poder clasificarla.
     */
    public static Especialidad desdeNombre(String texto) {
        if (texto == null) return null;
        String n = normalizar(texto);
        if (n.isEmpty()) return null;

        for (Especialidad e : values()) {
            if (e == OTRA) continue;
            String propio = normalizar(e.nombre);
            if (n.equals(propio) || n.contains(propio) || propio.contains(n)) return e;
        }
        // "cardiologo", "traumatologo": la raíz sin la terminación.
        for (Especialidad e : values()) {
            if (e == OTRA) continue;
            String raiz = normalizar(e.nombre);
            raiz = raiz.length() > 6 ? raiz.substring(0, raiz.length() - 4) : raiz;
            if (n.startsWith(raiz)) return e;
        }
        return null;
    }

    /** Minúsculas y sin tildes, para que "Cardiología" y "cardiologia" empaten. */
    private static String normalizar(String s) {
        String n = s.trim().toLowerCase(java.util.Locale.ROOT);
        n = java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n;
    }
}
