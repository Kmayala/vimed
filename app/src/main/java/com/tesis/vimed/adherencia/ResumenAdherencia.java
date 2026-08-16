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
 * Cómo viene el tratamiento, resumido para el familiar.
 *
 * Es distinto de {@link AnalizadorAdherencia}: ese busca patrones para
 * proponerle ajustes a la persona que se medica. Este contesta la pregunta
 * del cuidador, que es otra — "¿está tomando la medicación?" y "¿qué se le
 * está pasando?".
 *
 * Deliberadamente NO propone nada. Un familiar que ve una sugerencia
 * automática tiende a aplicarla sobre el tratamiento de otro; acá solo
 * mostramos hechos y que decida con el médico.
 *
 * Clase PURA: sin red ni Android.
 */
public final class ResumenAdherencia {

    /** Ventana corta: cómo viene esta semana. */
    public static final int DIAS_CORTO = 7;
    /** Ventana larga: el contexto que evita alarmarse por un mal día. */
    public static final int DIAS_LARGO = 30;

    /** Con menos tomas de un horario no se lo señala como problemático. */
    static final int MIN_TOMAS_PUNTO_FLOJO = 3;

    /** Cuántos horarios problemáticos se muestran. */
    static final int MAX_PUNTOS_FLOJOS = 3;

    /** Un horario entra a la lista a partir de esta proporción de fallas. */
    static final double TASA_PARA_SENALAR = 0.25;

    private ResumenAdherencia() {}

    /** Un horario que se está perdiendo seguido. */
    public static class PuntoFlojo {
        public final String nombreMedicamento;
        public final String hora;
        public final int falladas;
        public final int total;

        PuntoFlojo(String nombreMedicamento, String hora, int falladas, int total) {
            this.nombreMedicamento = nombreMedicamento;
            this.hora = hora;
            this.falladas = falladas;
            this.total = total;
        }

        public String detalle() {
            return "Sin confirmar " + falladas + " de " + total
                + (total == 1 ? " vez" : " veces");
        }
    }

    public static class Resumen {
        public final int confirmadasCorto, totalCorto;
        public final int confirmadasLargo, totalLargo;
        public final List<PuntoFlojo> puntosFlojos;

        Resumen(int confirmadasCorto, int totalCorto,
                int confirmadasLargo, int totalLargo,
                List<PuntoFlojo> puntosFlojos) {
            this.confirmadasCorto = confirmadasCorto;
            this.totalCorto = totalCorto;
            this.confirmadasLargo = confirmadasLargo;
            this.totalLargo = totalLargo;
            this.puntosFlojos = puntosFlojos;
        }

        /** Porcentaje de la semana, o -1 si no hubo tomas registradas. */
        public int porcentajeCorto() { return porcentaje(confirmadasCorto, totalCorto); }
        public int porcentajeLargo() { return porcentaje(confirmadasLargo, totalLargo); }

        public boolean hayDatos() { return totalLargo > 0; }

        private static int porcentaje(int parte, int total) {
            return total > 0 ? Math.round(parte * 100f / total) : -1;
        }

        /** Frase corta para encabezar la tarjeta, sin porcentajes. */
        public String titular() {
            if (!hayDatos()) return "Todavía no hay tomas registradas";
            int pct = porcentajeCorto() >= 0 ? porcentajeCorto() : porcentajeLargo();
            if (pct >= 90) return "Viene cumpliendo bien el tratamiento";
            if (pct >= 70) return "Cumple casi siempre, con algunos olvidos";
            if (pct >= 40) return "Se le está pasando bastante seguido";
            return "Está tomando poco de lo indicado";
        }
    }

    /**
     * @param historial tomas de los últimos {@link #DIAS_LARGO} días.
     * @param hoyYMD fecha de hoy "yyyy-MM-dd". Entra por parámetro para que
     *        el cálculo sea reproducible en las pruebas.
     */
    public static Resumen calcular(List<Medicamento> medicamentos,
                                   List<Horario> horarios,
                                   List<RegistroToma> historial,
                                   String hoyYMD) {
        if (historial == null) historial = new ArrayList<>();

        String corteCorto = restarDias(hoyYMD, DIAS_CORTO);

        int confCorto = 0, totCorto = 0, confLargo = 0, totLargo = 0;

        // falladas/total por horario, para los puntos flojos
        Map<Integer, int[]> porHorario = new HashMap<>();

        for (RegistroToma t : historial) {
            String estado = t.getEstado();
            String prog = t.getFechaHoraProgramada();
            if (estado == null || prog == null || prog.length() < 10) continue;

            if (!estadoConocido(estado)) continue;

            boolean ok = "confirmada".equals(estado);

            totLargo++;
            if (ok) confLargo++;

            String fecha = prog.substring(0, 10);
            if (fecha.compareTo(corteCorto) >= 0) {
                totCorto++;
                if (ok) confCorto++;
            }

            int[] acum = porHorario.get(t.getIdHorario());
            if (acum == null) {
                acum = new int[2];
                porHorario.put(t.getIdHorario(), acum);
            }
            acum[1]++;
            if (!ok) acum[0]++;
        }

        return new Resumen(confCorto, totCorto, confLargo, totLargo,
            armarPuntosFlojos(medicamentos, horarios, porHorario));
    }

    /**
     * Una fila de registro_tomas solo se escribe cuando la alarma dispara,
     * así que todas cuentan. Igual filtramos estados desconocidos: si
     * alguna vez se agrega uno nuevo, mejor dejarlo afuera del porcentaje
     * que contarlo como olvido y asustar al familiar por un cambio de
     * esquema.
     */
    private static boolean estadoConocido(String estado) {
        return "confirmada".equals(estado)
            || "omitida".equals(estado)
            || "pospuesta".equals(estado);
    }

    /**
     * Mismo criterio, expuesto para que la pantalla de Progreso señale los
     * mismos horarios que el resumen del cuidador. Dos pantallas marcando
     * horarios distintos con los mismos datos es peor que no marcar nada.
     *
     * @param porHorario id_horario → [falladas, total]
     */
    public static List<PuntoFlojo> puntosFlojosDesde(List<Medicamento> medicamentos,
                                                     List<Horario> horarios,
                                                     Map<Integer, int[]> porHorario) {
        return armarPuntosFlojos(medicamentos, horarios, porHorario);
    }

    private static List<PuntoFlojo> armarPuntosFlojos(List<Medicamento> medicamentos,
                                                      List<Horario> horarios,
                                                      Map<Integer, int[]> porHorario) {
        List<PuntoFlojo> puntos = new ArrayList<>();
        if (horarios == null) return puntos;

        Map<Integer, String> nombrePorMed = new HashMap<>();
        if (medicamentos != null) {
            for (Medicamento m : medicamentos) {
                nombrePorMed.put(m.getId(),
                    m.getNombre() != null ? m.getNombre() : "Medicamento");
            }
        }

        for (Horario h : horarios) {
            int[] acum = porHorario.get(h.getId());
            if (acum == null) continue;

            int falladas = acum[0], total = acum[1];
            if (total < MIN_TOMAS_PUNTO_FLOJO) continue;
            if ((double) falladas / total < TASA_PARA_SENALAR) continue;

            String nombre = nombrePorMed.get(h.getIdMedicamento());
            if (nombre == null) nombre = "Medicamento";

            puntos.add(new PuntoFlojo(nombre,
                h.getHoraInicio() != null ? h.getHoraInicio() : "", falladas, total));
        }

        // Lo más perdido primero; a igual proporción, lo que más veces pasó.
        Collections.sort(puntos, (a, b) -> {
            double ra = (double) a.falladas / a.total;
            double rb = (double) b.falladas / b.total;
            if (ra != rb) return Double.compare(rb, ra);
            return Integer.compare(b.falladas, a.falladas);
        });

        return puntos.size() > MAX_PUNTOS_FLOJOS
            ? new ArrayList<>(puntos.subList(0, MAX_PUNTOS_FLOJOS))
            : puntos;
    }

    /** "2026-08-16" menos N días, sin pasar por Calendar. */
    public static String restarDias(String ymd, int dias) {
        try {
            int anio = Integer.parseInt(ymd.substring(0, 4));
            int mes  = Integer.parseInt(ymd.substring(5, 7));
            int dia  = Integer.parseInt(ymd.substring(8, 10));

            long jd = aDiaJuliano(anio, mes, dia) - dias;
            return deDiaJuliano(jd);
        } catch (Exception e) {
            return ymd;
        }
    }

    // Algoritmo de Howard Hinnant, igual que en AnalizadorAdherencia.
    private static long aDiaJuliano(int anio, int mes, int dia) {
        long y = anio - (mes <= 2 ? 1 : 0);
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (mes + (mes > 2 ? -3 : 9)) + 2) / 5 + dia - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    private static String deDiaJuliano(long z) {
        z += 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp + (mp < 10 ? 3 : -9);
        y += (m <= 2 ? 1 : 0);
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m, d);
    }
}
