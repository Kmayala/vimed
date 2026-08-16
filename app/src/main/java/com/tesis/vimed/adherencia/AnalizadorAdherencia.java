package com.tesis.vimed.adherencia;

import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.RegistroToma;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mira el historial de tomas y saca conclusiones sobre los hábitos de la
 * persona, para proponer ajustes que mejoren la adherencia.
 *
 * Qué hace, en concreto, por cada horario del tratamiento:
 *
 *   1. HORARIO REAL — junta el desfase (en minutos) entre la hora del
 *      recordatorio y la hora en que la toma se confirmó de verdad, y se
 *      queda con la MEDIANA. Si esa mediana es grande y consistente, la
 *      persona ya tiene un horario propio y el recordatorio es el que está
 *      corrido: conviene moverlo hacia el hábito real en lugar de pelearlo.
 *
 *   2. OLVIDOS — cuenta qué proporción de las tomas de ese horario quedó sin
 *      confirmar. Un horario que se pierde seguido no se arregla moviéndolo:
 *      necesita más insistencia.
 *
 *   3. FRECUENCIA — un tratamiento cada 6 horas tolera mucho menos
 *      desplazamiento que uno de una vez al día, así que el intervalo acota
 *      cuánto se puede mover un recordatorio.
 *
 * Sobre el método: se usa MEDIANA y desviación absoluta mediana (MAD), no
 * promedio y desvío estándar. Con historiales cortos —que es lo normal acá—
 * un solo día raro (una toma confirmada seis horas tarde porque la persona
 * abrió la app a la noche) mueve el promedio lo suficiente como para
 * inventar un patrón que no existe. La mediana lo ignora.
 *
 * Esta clase es PURA: no toca red, base ni Android. Todo lo que necesita
 * entra por parámetro, así se puede probar con datos de laboratorio (ver
 * AnalizadorAdherenciaTest).
 */
public final class AnalizadorAdherencia {

    /** Ventana de historial que se mira hacia atrás. */
    public static final int DIAS_HISTORIAL = 30;

    /** Menos confirmaciones que esto es anécdota, no hábito. */
    static final int MIN_CONFIRMACIONES = 5;

    /** Desfases menores no valen la pena: molestar cuesta más que los 10 minutos. */
    static final int DESFASE_MIN_MINUTOS = 20;

    /**
     * Tope de dispersión (MAD). Si la persona toma la medicación a cualquier
     * hora, no hay hábito que aprender y mover el recordatorio no arregla
     * nada — solo lo corre a otro lugar igual de arbitrario.
     *
     * Media hora: con un MAD de 30 minutos, la mitad de las tomas cae dentro
     * de esa franja alrededor de la hora típica. Más flojo que eso ya deja
     * pasar historiales sin ningún patrón (probado en
     * noSugiereSiLosHorariosEstanDesparramados).
     */
    static final int DISPERSION_MAX_MINUTOS = 30;

    /**
     * Cuánto se puede alejar el recordatorio de la hora que indicó el médico,
     * SUMANDO todos los ajustes que se hayan aceptado antes.
     *
     * Este tope es lo que evita el problema serio de un sistema que se
     * ajusta solo: veinte minutos por mes son inofensivos por separado, pero
     * en medio año la medicación terminó cuatro horas corrida de la receta
     * sin que nadie tomara nunca esa decisión.
     */
    static final int DESVIO_MAX_RECETA = 60;

    /** Desplazamiento máximo de una sola vez, pase lo que pase. */
    static final int DESPLAZAMIENTO_MAX = 120;

    /** Con menos tomas registradas no se juzga si un horario "se olvida". */
    static final int MIN_TOMAS_PARA_OLVIDOS = 6;

    /** A partir de esta proporción de omitidas, el horario necesita refuerzo. */
    static final double TASA_OLVIDO = 0.4;

    private AnalizadorAdherencia() {}

    /**
     * @param horaOriginalPorHorario hora "HH:mm" que tenía cada horario la
     *        primera vez, antes de cualquier ajuste aceptado. Sirve para no
     *        alejarse indefinidamente de la receta. Si un horario no está en
     *        el mapa, se asume que su hora actual es la original.
     * @return sugerencias ordenadas por urgencia (primero los olvidos).
     */
    public static List<Sugerencia> analizar(List<Medicamento> medicamentos,
                                            List<Horario> horarios,
                                            List<RegistroToma> historial,
                                            Map<Integer, String> horaOriginalPorHorario) {
        List<Sugerencia> sugerencias = new ArrayList<>();
        if (horarios == null || historial == null || historial.isEmpty()) {
            return sugerencias;
        }

        Map<Integer, String> nombrePorMedicamento = new HashMap<>();
        if (medicamentos != null) {
            for (Medicamento m : medicamentos) {
                nombrePorMedicamento.put(m.getId(),
                    m.getNombre() != null ? m.getNombre() : "tu medicamento");
            }
        }

        Map<Integer, List<RegistroToma>> porHorario = agruparPorHorario(historial);

        for (Horario h : horarios) {
            List<RegistroToma> tomas = porHorario.get(h.getId());
            if (tomas == null || tomas.isEmpty()) continue;
            if (h.getHoraInicio() == null) continue;

            String nombre = nombrePorMedicamento.get(h.getIdMedicamento());
            if (nombre == null) nombre = "tu medicamento";

            // Un horario problemático se arregla de una sola forma por vez:
            // si además de olvidarse está corrido, primero hay que lograr que
            // la persona la tome, y recién después afinar la hora.
            Sugerencia olvidos = evaluarOlvidos(h, tomas, nombre);
            if (olvidos != null) {
                sugerencias.add(olvidos);
                continue;
            }

            Sugerencia desfase = evaluarDesfase(h, tomas, nombre,
                horaOriginalPorHorario != null
                    ? horaOriginalPorHorario.get(h.getId()) : null);
            if (desfase != null) sugerencias.add(desfase);
        }

        // Los olvidos van primero: son los que de verdad ponen en riesgo el
        // tratamiento. Mover una hora es una comodidad.
        Collections.sort(sugerencias, (a, b) -> {
            if (a.tipo != b.tipo) return a.tipo == Sugerencia.Tipo.REFORZAR ? -1 : 1;
            return Integer.compare(b.muestras, a.muestras);   // más evidencia primero
        });
        return sugerencias;
    }

    // ═══ Señal 1: el horario real de la persona ════════════════

    private static Sugerencia evaluarDesfase(Horario h, List<RegistroToma> tomas,
                                             String nombre, String horaOriginal) {
        List<Integer> desfases = new ArrayList<>();
        for (RegistroToma t : tomas) {
            if (!"confirmada".equals(t.getEstado())) continue;
            Integer d = desfaseEnMinutos(t);
            if (d != null) desfases.add(d);
        }
        if (desfases.size() < MIN_CONFIRMACIONES) return null;

        int mediana = (int) Math.round(mediana(desfases));
        if (Math.abs(mediana) < DESFASE_MIN_MINUTOS) return null;

        // ¿Es un hábito o es ruido? Si los desfases están desparramados, no
        // hay nada que aprender.
        List<Integer> distancias = new ArrayList<>(desfases.size());
        for (int d : desfases) distancias.add(Math.abs(d - mediana));
        if (mediana(distancias) > DISPERSION_MAX_MINUTOS) return null;

        int desplazamiento = redondearA5(mediana);

        // Tope por frecuencia: nunca invadir la mitad del camino hacia la
        // toma siguiente, o dos tomas se terminan pisando.
        int intervaloMin = h.getIntervaloHoras() > 0 ? h.getIntervaloHoras() * 60 : 24 * 60;
        int tope = Math.min(DESPLAZAMIENTO_MAX, intervaloMin / 2);
        desplazamiento = acotar(desplazamiento, tope);

        int actualMin = aMinutos(h.getHoraInicio());
        if (actualMin < 0) return null;

        // Tope respecto de la receta original, acumulando ajustes previos.
        int originalMin = horaOriginal != null ? aMinutos(horaOriginal) : actualMin;
        if (originalMin < 0) originalMin = actualMin;
        int desvioActual = diferenciaCircular(actualMin, originalMin);
        int desvioFinal = desvioActual + desplazamiento;
        if (Math.abs(desvioFinal) > DESVIO_MAX_RECETA) {
            desplazamiento = acotar(desvioFinal, DESVIO_MAX_RECETA) - desvioActual;
        }

        // Después de recortar puede no quedar nada que valga la pena mover.
        if (Math.abs(desplazamiento) < DESFASE_MIN_MINUTOS) return null;

        String nuevaHora = aHHmm(actualMin + desplazamiento);
        return Sugerencia.moverHora(h.getId(), h.getIdMedicamento(), nombre,
            h.getHoraInicio(), nuevaHora, desplazamiento, desfases.size());
    }

    // ═══ Señal 2: historial de olvidos ═════════════════════════

    private static Sugerencia evaluarOlvidos(Horario h, List<RegistroToma> tomas,
                                             String nombre) {
        int omitidas = 0, total = 0;
        for (RegistroToma t : tomas) {
            String estado = t.getEstado();
            if (estado == null) continue;
            total++;
            // "pospuesta" cuenta como olvido: la alarma sonó completa y nadie
            // la respondió, así fue como quedó marcada.
            if ("omitida".equals(estado) || "pospuesta".equals(estado)) omitidas++;
        }
        if (total < MIN_TOMAS_PARA_OLVIDOS) return null;
        if ((double) omitidas / total < TASA_OLVIDO) return null;

        return Sugerencia.reforzar(h.getId(), h.getIdMedicamento(), nombre,
            h.getHoraInicio(), omitidas, total);
    }

    // ═══ Cálculo ═══════════════════════════════════════════════

    /**
     * Minutos entre la hora del recordatorio y la confirmación real.
     * null si falta algún dato o si la diferencia es absurda (más de 12
     * horas), que suele ser una confirmación cargada a mano días después.
     */
    static Integer desfaseEnMinutos(RegistroToma t) {
        long programada = aEpochMinutos(t.getFechaHoraProgramada());
        long confirmada = aEpochMinutos(t.getFechaHoraConfirmacion());
        if (programada < 0 || confirmada < 0) return null;

        long diff = confirmada - programada;
        if (Math.abs(diff) > 12 * 60) return null;
        return (int) diff;
    }

    /**
     * Convierte "yyyy-MM-dd HH:mm:ss" (o el ISO "yyyy-MM-ddTHH:mm:ss…" que
     * devuelve Postgres) a minutos absolutos, sin pasar por Calendar: solo
     * necesitamos restar dos fechas cercanas entre sí.
     */
    private static long aEpochMinutos(String ts) {
        if (ts == null || ts.length() < 16) return -1;
        try {
            int anio = Integer.parseInt(ts.substring(0, 4));
            int mes  = Integer.parseInt(ts.substring(5, 7));
            int dia  = Integer.parseInt(ts.substring(8, 10));
            int hora = Integer.parseInt(ts.substring(11, 13));
            int min  = Integer.parseInt(ts.substring(14, 16));
            // Días desde una fecha base cualquiera; solo importan las restas.
            long dias = diasDesdeEpoca(anio, mes, dia);
            return dias * 1440L + hora * 60L + min;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Días julianos simplificados (algoritmo de Howard Hinnant). */
    private static long diasDesdeEpoca(int anio, int mes, int dia) {
        long y = anio;
        y -= mes <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (mes + (mes > 2 ? -3 : 9)) + 2) / 5 + dia - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    static double mediana(List<Integer> valores) {
        List<Integer> copia = new ArrayList<>(valores);
        Collections.sort(copia);
        int n = copia.size();
        if (n == 0) return 0;
        return n % 2 == 1
            ? copia.get(n / 2)
            : (copia.get(n / 2 - 1) + copia.get(n / 2)) / 2.0;
    }

    private static Map<Integer, List<RegistroToma>> agruparPorHorario(List<RegistroToma> historial) {
        Map<Integer, List<RegistroToma>> mapa = new HashMap<>();
        for (RegistroToma t : historial) {
            List<RegistroToma> lista = mapa.get(t.getIdHorario());
            if (lista == null) {
                lista = new ArrayList<>();
                mapa.put(t.getIdHorario(), lista);
            }
            lista.add(t);
        }
        return mapa;
    }

    // ═══ Helpers de hora ═══════════════════════════════════════

    /** "HH:mm" → minutos desde medianoche, o -1 si no se entiende. */
    static int aMinutos(String hhmm) {
        if (hhmm == null || hhmm.length() < 4) return -1;
        try {
            String[] p = hhmm.split(":");
            int h = Integer.parseInt(p[0].trim());
            int m = Integer.parseInt(p[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    static String aHHmm(int minutos) {
        int m = ((minutos % 1440) + 1440) % 1440;   // envuelve en el día
        return String.format(java.util.Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    /**
     * Diferencia con signo entre dos horas del día tomando el camino corto:
     * de las 23:50 a las 00:10 son 20 minutos, no 1420.
     */
    static int diferenciaCircular(int minutosA, int minutosB) {
        int d = (minutosA - minutosB) % 1440;
        if (d > 720) d -= 1440;
        if (d < -720) d += 1440;
        return d;
    }

    /** Redondea a múltiplos de 5: "8:23" como recordatorio no le dice nada a nadie. */
    static int redondearA5(int minutos) {
        return Math.round(minutos / 5f) * 5;
    }

    private static int acotar(int valor, int tope) {
        return Math.max(-tope, Math.min(tope, valor));
    }
}
