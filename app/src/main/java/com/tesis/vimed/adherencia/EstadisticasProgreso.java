package com.tesis.vimed.adherencia;

import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.RegistroToma;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Los números de la pantalla de Progreso.
 *
 * Es la tercera clase de la carpeta y conviene decir para qué es cada una:
 * {@link AnalizadorAdherencia} busca patrones para PROPONER ajustes,
 * {@link ResumenAdherencia} contesta "¿está tomando la medicación?" en dos
 * líneas para el familiar, y esta arma el detalle navegable — día por día,
 * medicamento por medicamento— para quien quiere mirar de verdad.
 *
 * Clase PURA: sin red ni Android, se prueba con datos de laboratorio.
 */
public final class EstadisticasProgreso {

    private EstadisticasProgreso() {}

    /** Un día de la serie. */
    public static class Dia {
        public final String fecha;      // "yyyy-MM-dd"
        public final int confirmadas, total;

        Dia(String fecha, int confirmadas, int total) {
            this.fecha = fecha; this.confirmadas = confirmadas; this.total = total;
        }
        /** -1 cuando no había nada agendado: no es lo mismo que 0%. */
        public int porcentaje() {
            return total > 0 ? Math.round(confirmadas * 100f / total) : -1;
        }
        public boolean completo() { return total > 0 && confirmadas == total; }
        public boolean vacio()    { return total == 0; }
        /** Día de la semana como número 1..31 para la etiqueta. */
        public String diaDelMes() {
            return fecha.length() >= 10 ? String.valueOf(Integer.parseInt(fecha.substring(8,10))) : "";
        }
    }

    /** Cumplimiento de un medicamento en el período. */
    public static class PorMedicamento {
        public final int idMedicamento;
        public final String nombre;
        public final String colorIcono;
        public final int confirmadas, total;

        PorMedicamento(int idMedicamento, String nombre, String colorIcono,
                       int confirmadas, int total) {
            this.idMedicamento = idMedicamento; this.nombre = nombre;
            this.colorIcono = colorIcono;
            this.confirmadas = confirmadas; this.total = total;
        }
        public int porcentaje() {
            return total > 0 ? Math.round(confirmadas * 100f / total) : -1;
        }
    }

    public static class Progreso {
        public final int confirmadas, total;
        public final int racha;                 // días seguidos completos hasta hoy
        public final int mejorRacha;
        public final List<Dia> serie;           // del más viejo al más nuevo
        public final List<PorMedicamento> porMedicamento;
        public final List<ResumenAdherencia.PuntoFlojo> puntosFlojos;

        Progreso(int confirmadas, int total, int racha, int mejorRacha, List<Dia> serie,
                 List<PorMedicamento> porMedicamento,
                 List<ResumenAdherencia.PuntoFlojo> puntosFlojos) {
            this.confirmadas = confirmadas; this.total = total;
            this.racha = racha; this.mejorRacha = mejorRacha;
            this.serie = serie; this.porMedicamento = porMedicamento;
            this.puntosFlojos = puntosFlojos;
        }

        public int porcentaje() {
            return total > 0 ? Math.round(confirmadas * 100f / total) : -1;
        }
        public boolean hayDatos() { return total > 0; }

        /** Días con al menos una toma agendada, para no dividir por días vacíos. */
        public int diasConTomas() {
            int n = 0;
            for (Dia d : serie) if (!d.vacio()) n++;
            return n;
        }
        public int diasCompletos() {
            int n = 0;
            for (Dia d : serie) if (d.completo()) n++;
            return n;
        }
    }

    /**
     * @param dias tamaño de la ventana (7, 30, 90…).
     * @param hoyYMD fecha de hoy "yyyy-MM-dd". Entra por parámetro para que
     *        el cálculo sea reproducible en las pruebas.
     */
    public static Progreso calcular(List<Medicamento> medicamentos,
                                    List<Horario> horarios,
                                    List<RegistroToma> historial,
                                    int dias, String hoyYMD) {
        if (historial == null) historial = new ArrayList<>();

        // Esqueleto de la serie: TODOS los días del rango, aunque no haya
        // registros. Sin esto el gráfico se comprime y miente — parecería
        // que hubo tomas todos los días.
        LinkedHashMap<String, int[]> porDia = new LinkedHashMap<>();
        for (int i = dias - 1; i >= 0; i--) {
            porDia.put(ResumenAdherencia.restarDias(hoyYMD, i), new int[2]);
        }

        Map<Integer, int[]> porHorario = new HashMap<>();
        Map<Integer, int[]> porMed = new HashMap<>();

        Map<Integer, Integer> medDeHorario = new HashMap<>();
        if (horarios != null) {
            for (Horario h : horarios) medDeHorario.put(h.getId(), h.getIdMedicamento());
        }

        int confirmadas = 0, total = 0;

        for (RegistroToma t : historial) {
            String estado = t.getEstado();
            String prog = t.getFechaHoraProgramada();
            if (estado == null || prog == null || prog.length() < 10) continue;
            if (!esEstadoConocido(estado)) continue;

            String fecha = prog.substring(0, 10);
            int[] dia = porDia.get(fecha);
            if (dia == null) continue;   // fuera de la ventana

            boolean ok = "confirmada".equals(estado);
            dia[1]++; if (ok) dia[0]++;
            total++;   if (ok) confirmadas++;

            acumular(porHorario, t.getIdHorario(), ok);
            Integer idMed = medDeHorario.get(t.getIdHorario());
            if (idMed != null) acumular(porMed, idMed, ok);
        }

        List<Dia> serie = new ArrayList<>();
        for (Map.Entry<String, int[]> e : porDia.entrySet()) {
            serie.add(new Dia(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }

        return new Progreso(confirmadas, total,
            rachaActual(serie), mejorRacha(serie), serie,
            armarPorMedicamento(medicamentos, porMed),
            puntosFlojos(medicamentos, horarios, porHorario));
    }

    // ═══ Rachas ════════════════════════════════════════════════

    /**
     * Días seguidos con TODAS las tomas confirmadas, contando hacia atrás.
     *
     * Los días sin nada agendado no cortan la racha ni la suman: si el
     * tratamiento es día por medio, un día libre no es un incumplimiento.
     * El día de hoy tampoco corta si todavía tiene tomas pendientes — no
     * tiene sentido decirle a alguien a las 9 de la mañana que perdió una
     * racha de doce días.
     */
    static int rachaActual(List<Dia> serie) {
        int racha = 0;
        for (int i = serie.size() - 1; i >= 0; i--) {
            Dia d = serie.get(i);
            if (d.vacio()) continue;
            if (d.completo()) { racha++; continue; }
            // El último día de la serie es hoy: si está a medias, se ignora.
            if (i == serie.size() - 1 && d.confirmadas > 0) continue;
            break;
        }
        return racha;
    }

    static int mejorRacha(List<Dia> serie) {
        int mejor = 0, actual = 0;
        for (Dia d : serie) {
            if (d.vacio()) continue;
            if (d.completo()) { actual++; mejor = Math.max(mejor, actual); }
            else actual = 0;
        }
        return mejor;
    }

    // ═══ Desgloses ═════════════════════════════════════════════

    private static List<PorMedicamento> armarPorMedicamento(List<Medicamento> medicamentos,
                                                            Map<Integer, int[]> acum) {
        List<PorMedicamento> out = new ArrayList<>();
        if (medicamentos == null) return out;

        for (Medicamento m : medicamentos) {
            int[] a = acum.get(m.getId());
            if (a == null || a[1] == 0) continue;
            out.add(new PorMedicamento(m.getId(),
                m.getNombre() != null ? m.getNombre() : "Medicamento",
                m.getColorIcono(), a[0], a[1]));
        }
        // Lo peor arriba: es lo que hay que mirar.
        Collections.sort(out, (a, b) -> Integer.compare(a.porcentaje(), b.porcentaje()));
        return out;
    }

    private static List<ResumenAdherencia.PuntoFlojo> puntosFlojos(
            List<Medicamento> medicamentos, List<Horario> horarios,
            Map<Integer, int[]> porHorario) {
        // Se reusa el mismo criterio del resumen del cuidador para que las
        // dos pantallas no señalen horarios distintos con los mismos datos.
        Map<Integer, int[]> falladas = new HashMap<>();
        for (Map.Entry<Integer, int[]> e : porHorario.entrySet()) {
            int ok = e.getValue()[0], tot = e.getValue()[1];
            falladas.put(e.getKey(), new int[]{tot - ok, tot});
        }
        return ResumenAdherencia.puntosFlojosDesde(medicamentos, horarios, falladas);
    }

    // ═══ Helpers ═══════════════════════════════════════════════

    private static void acumular(Map<Integer, int[]> mapa, int clave, boolean ok) {
        int[] a = mapa.get(clave);
        if (a == null) { a = new int[2]; mapa.put(clave, a); }
        a[1]++; if (ok) a[0]++;
    }

    private static boolean esEstadoConocido(String estado) {
        return "confirmada".equals(estado) || "omitida".equals(estado)
            || "pospuesta".equals(estado);
    }
}
