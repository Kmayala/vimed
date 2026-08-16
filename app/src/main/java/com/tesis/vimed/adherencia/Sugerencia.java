package com.tesis.vimed.adherencia;

/**
 * Una propuesta de ajuste que la app le hace a la persona a partir de su
 * propio historial de tomas.
 *
 * Es SIEMPRE una propuesta: nada de esto se aplica solo. La hora de una
 * medicación la indicó un médico, así que el sistema puede notar un patrón
 * y ofrecer corregirlo, pero la decisión es de la persona o de su familiar.
 */
public class Sugerencia {

    public enum Tipo {
        /** Se toma la medicación sistemáticamente corrida respecto del recordatorio. */
        MOVER_HORA,
        /** Ese horario se olvida seguido: conviene insistir más. */
        REFORZAR
    }

    public final Tipo tipo;
    public final int idHorario;
    public final int idMedicamento;
    public final String nombreMedicamento;

    /** Hora que tiene hoy el recordatorio ("HH:mm"). */
    public final String horaActual;
    /** Hora propuesta ("HH:mm"). Solo en {@link Tipo#MOVER_HORA}. */
    public final String horaSugerida;

    /**
     * Desfase típico en minutos entre el recordatorio y la toma real.
     * Positivo = se toma más tarde. Solo en {@link Tipo#MOVER_HORA}.
     */
    public final int desfaseMinutos;

    /** Cuántas tomas del historial sostienen la sugerencia. */
    public final int muestras;

    /** Tomas omitidas y total registrado. Solo en {@link Tipo#REFORZAR}. */
    public final int omitidas;
    public final int total;

    private Sugerencia(Tipo tipo, int idHorario, int idMedicamento,
                       String nombreMedicamento, String horaActual,
                       String horaSugerida, int desfaseMinutos, int muestras,
                       int omitidas, int total) {
        this.tipo = tipo;
        this.idHorario = idHorario;
        this.idMedicamento = idMedicamento;
        this.nombreMedicamento = nombreMedicamento;
        this.horaActual = horaActual;
        this.horaSugerida = horaSugerida;
        this.desfaseMinutos = desfaseMinutos;
        this.muestras = muestras;
        this.omitidas = omitidas;
        this.total = total;
    }

    static Sugerencia moverHora(int idHorario, int idMedicamento, String nombre,
                                String horaActual, String horaSugerida,
                                int desfaseMinutos, int muestras) {
        return new Sugerencia(Tipo.MOVER_HORA, idHorario, idMedicamento, nombre,
            horaActual, horaSugerida, desfaseMinutos, muestras, 0, 0);
    }

    static Sugerencia reforzar(int idHorario, int idMedicamento, String nombre,
                               String horaActual, int omitidas, int total) {
        return new Sugerencia(Tipo.REFORZAR, idHorario, idMedicamento, nombre,
            horaActual, null, 0, total, omitidas, total);
    }

    // ═══ Texto para la persona ═════════════════════════════════
    // Redactado para un adulto mayor: frases cortas, sin porcentajes ni
    // jerga, y siempre terminando en una pregunta que se pueda contestar
    // con sí o no.

    public String titulo() {
        return tipo == Tipo.MOVER_HORA
            ? "¿Movemos el recordatorio?"
            : "Este horario se te pasa seguido";
    }

    public String detalle() {
        if (tipo == Tipo.MOVER_HORA) {
            String cuando = desfaseMinutos > 0 ? "más tarde" : "más temprano";
            return "Notamos que solés tomar " + nombreMedicamento + " unos "
                + Math.abs(desfaseMinutos) + " minutos " + cuando + " de las "
                + horaActual + ". ¿Querés que el recordatorio pase a las "
                + horaSugerida + "?";
        }
        return "La toma de " + nombreMedicamento + " de las " + horaActual
            + " quedó sin confirmar " + omitidas + " de las últimas " + total
            + " veces. ¿Querés que la alarma suene el doble de tiempo en ese horario?";
    }

    public String textoAceptar() {
        return tipo == Tipo.MOVER_HORA ? "Sí, moverlo" : "Sí, insistir más";
    }
}
