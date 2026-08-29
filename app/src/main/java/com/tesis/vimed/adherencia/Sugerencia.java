package com.tesis.vimed.adherencia;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Todas las horas del día, antes y después del cambio.
     *
     * Existen porque un medicamento cada 6 horas guarda UNA sola hora de
     * inicio y las otras tres tomas se derivan de ella: mover el
     * recordatorio mueve las cuatro. La propuesta tiene que decirlo, si no
     * la persona acepta mover una toma y le cambian todas.
     */
    public final List<String> horasActuales;
    public final List<String> horasNuevas;

    private Sugerencia(Tipo tipo, int idHorario, int idMedicamento,
                       String nombreMedicamento, String horaActual,
                       String horaSugerida, int desfaseMinutos, int muestras,
                       int omitidas, int total,
                       List<String> horasActuales, List<String> horasNuevas) {
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
        this.horasActuales = horasActuales != null ? horasActuales : new ArrayList<>();
        this.horasNuevas   = horasNuevas   != null ? horasNuevas   : new ArrayList<>();
    }

    /** Cuántas tomas del día se ven afectadas. */
    public int tomasPorDia() {
        return Math.max(1, horasNuevas.size());
    }

    /** True si mover el recordatorio arrastra más de una toma. */
    public boolean afectaVariasTomas() {
        return horasNuevas.size() > 1;
    }

    static Sugerencia moverHora(int idHorario, int idMedicamento, String nombre,
                                String horaActual, String horaSugerida,
                                int desfaseMinutos, int muestras,
                                List<String> horasActuales, List<String> horasNuevas) {
        return new Sugerencia(Tipo.MOVER_HORA, idHorario, idMedicamento, nombre,
            horaActual, horaSugerida, desfaseMinutos, muestras, 0, 0,
            horasActuales, horasNuevas);
    }

    static Sugerencia reforzar(int idHorario, int idMedicamento, String nombre,
                               String horaActual, int omitidas, int total) {
        return new Sugerencia(Tipo.REFORZAR, idHorario, idMedicamento, nombre,
            horaActual, null, 0, total, omitidas, total, null, null);
    }

    // ═══ Texto para la persona ═════════════════════════════════
    // Redactado para un adulto mayor: frases cortas, sin porcentajes ni
    // jerga, y siempre terminando en una pregunta que se pueda contestar
    // con sí o no.

    public String titulo() {
        if (tipo != Tipo.MOVER_HORA) return "Este horario se te pasa seguido";
        return afectaVariasTomas()
            ? "¿Movemos los recordatorios?"
            : "¿Movemos el recordatorio?";
    }

    public String detalle() {
        if (tipo == Tipo.MOVER_HORA) {
            String cuando = desfaseMinutos > 0 ? "más tarde" : "más temprano";
            String observacion = "Notamos que solés tomar " + nombreMedicamento
                + " unos " + Math.abs(desfaseMinutos) + " minutos " + cuando;

            // Una sola toma al día: se nombra la hora y listo.
            if (!afectaVariasTomas()) {
                return observacion + " de las " + horaActual
                    + ". ¿Querés que el recordatorio pase a las "
                    + horaSugerida + "?";
            }

            // Varias tomas: se dicen TODAS las horas que van a cambiar. La
            // app guarda una sola hora de inicio, así que no se puede mover
            // una toma sin mover el resto, y aceptar a ciegas un cambio en
            // el horario de una medicación es justo lo que hay que evitar.
            return observacion + ". Se toma " + tomasPorDia() + " veces al día,"
                + " así que se moverían todas: de las " + enumerar(horasActuales)
                + " pasarían a las " + enumerar(horasNuevas) + ". ¿Lo hacemos?";
        }
        return "La toma de " + nombreMedicamento + " de las " + horaActual
            + " quedó sin confirmar " + omitidas + " de las últimas " + total
            + " veces. ¿Querés que la alarma suene el doble de tiempo en ese horario?";
    }

    /** "08:00, 14:00 y 20:00" — con "y" antes de la última, como se habla. */
    private static String enumerar(List<String> horas) {
        if (horas.isEmpty()) return "";
        if (horas.size() == 1) return horas.get(0);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < horas.size(); i++) {
            if (i > 0) sb.append(i == horas.size() - 1 ? " y " : ", ");
            sb.append(horas.get(i));
        }
        return sb.toString();
    }

    public String textoAceptar() {
        if (tipo != Tipo.MOVER_HORA) return "Sí, insistir más";
        return afectaVariasTomas() ? "Sí, moverlos" : "Sí, moverlo";
    }
}
