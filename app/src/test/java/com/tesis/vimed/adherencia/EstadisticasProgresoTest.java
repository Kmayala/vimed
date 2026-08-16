package com.tesis.vimed.adherencia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.RegistroToma;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EstadisticasProgresoTest {

    private static final String HOY = "2026-06-30";
    private static final Medicamento MED_A = med(1, "Losartán");
    private static final Medicamento MED_B = med(2, "Metformina");
    private static final Horario HOR_A = horario(10, 1, "08:00");
    private static final Horario HOR_B = horario(20, 2, "22:00");

    // ═══ La serie ══════════════════════════════════════════════

    @Test
    public void laSerieTieneUnDiaPorCadaDiaDelRango() {
        // Aunque solo haya registros de un día: si la serie se armara solo
        // con los días que tienen datos, el gráfico se comprimiría y
        // parecería que hubo tomas todos los días.
        List<RegistroToma> h = tomas(HOR_A.getId(), "2026-06-28", "confirmada", 2);

        EstadisticasProgreso.Progreso p = calcular(h, 7);

        assertEquals(7, p.serie.size());
        assertEquals("2026-06-24", p.serie.get(0).fecha);
        assertEquals(HOY, p.serie.get(6).fecha);
    }

    @Test
    public void unDiaSinTomasNoEsCeroPorCiento() {
        // "No había nada que tomar" es distinto de "no tomó nada".
        EstadisticasProgreso.Progreso p = calcular(new ArrayList<>(), 7);
        assertTrue(p.serie.get(0).vacio());
        assertEquals(-1, p.serie.get(0).porcentaje());
    }

    @Test
    public void ignoraLoQueQuedaFueraDeLaVentana() {
        List<RegistroToma> h = new ArrayList<>();
        h.addAll(tomas(HOR_A.getId(), "2026-06-29", "confirmada", 1));
        h.addAll(tomas(HOR_A.getId(), "2026-05-01", "confirmada", 9));

        assertEquals(1, calcular(h, 7).total);
    }

    // ═══ Rachas ════════════════════════════════════════════════

    @Test
    public void cuentaDiasSeguidosCompletos() {
        List<RegistroToma> h = new ArrayList<>();
        for (String d : new String[]{"2026-06-28","2026-06-29","2026-06-30"}) {
            h.addAll(tomas(HOR_A.getId(), d, "confirmada", 2));
        }
        assertEquals(3, calcular(h, 7).racha);
    }

    @Test
    public void unDiaSinNadaAgendadoNoCortaLaRacha() {
        // Un tratamiento día por medio no incumple los días libres.
        List<RegistroToma> h = new ArrayList<>();
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "confirmada", 1));
        h.addAll(tomas(HOR_A.getId(), "2026-06-30", "confirmada", 1));
        // el 29 no tiene ninguna toma agendada

        assertEquals(2, calcular(h, 7).racha);
    }

    @Test
    public void elDiaDeHoyAMediasNoCortaLaRacha() {
        // A las 9 de la mañana, con la toma de la noche pendiente, decirle
        // a alguien que perdió una racha de doce días es sencillamente
        // falso: el día todavía no terminó.
        List<RegistroToma> h = new ArrayList<>();
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "confirmada", 2));
        h.addAll(tomas(HOR_A.getId(), "2026-06-29", "confirmada", 2));
        h.addAll(tomas(HOR_A.getId(), HOY, "confirmada", 1));
        h.addAll(tomas(HOR_A.getId(), HOY, "omitida", 1));

        assertEquals(2, calcular(h, 7).racha);
    }

    @Test
    public void unaOmisionCortaLaRacha() {
        List<RegistroToma> h = new ArrayList<>();
        h.addAll(tomas(HOR_A.getId(), "2026-06-27", "confirmada", 1));
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "omitida", 1));
        h.addAll(tomas(HOR_A.getId(), "2026-06-29", "confirmada", 1));

        EstadisticasProgreso.Progreso p = calcular(h, 7);
        assertEquals(1, p.racha);        // solo el 29 (el 30 esta vacio)
        assertEquals(1, p.mejorRacha);
    }

    @Test
    public void laMejorRachaRecuerdaElTramoMasLargo() {
        List<RegistroToma> h = new ArrayList<>();
        for (String d : new String[]{"2026-06-24","2026-06-25","2026-06-26"}) {
            h.addAll(tomas(HOR_A.getId(), d, "confirmada", 1));
        }
        h.addAll(tomas(HOR_A.getId(), "2026-06-27", "omitida", 1));
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "confirmada", 1));

        EstadisticasProgreso.Progreso p = calcular(h, 7);
        assertEquals(3, p.mejorRacha);
    }

    // ═══ Desglose por medicamento ══════════════════════════════

    @Test
    public void ordenaLosMedicamentosDelPeorAlMejor() {
        List<RegistroToma> h = new ArrayList<>();
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "confirmada", 9));
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "omitida", 1));
        h.addAll(tomas(HOR_B.getId(), "2026-06-28", "confirmada", 2));
        h.addAll(tomas(HOR_B.getId(), "2026-06-28", "omitida", 8));

        List<EstadisticasProgreso.PorMedicamento> lista = calcular(h, 7).porMedicamento;

        assertEquals(2, lista.size());
        assertEquals("Metformina", lista.get(0).nombre);   // 20%, primero
        assertEquals(20, lista.get(0).porcentaje());
        assertEquals(90, lista.get(1).porcentaje());
    }

    @Test
    public void unMedicamentoSinTomasEnElPeriodoNoAparece() {
        List<RegistroToma> h = tomas(HOR_A.getId(), "2026-06-28", "confirmada", 3);
        List<EstadisticasProgreso.PorMedicamento> lista = calcular(h, 7).porMedicamento;
        assertEquals(1, lista.size());
        assertEquals("Losartán", lista.get(0).nombre);
    }

    // ═══ Helpers ═══════════════════════════════════════════════

    private EstadisticasProgreso.Progreso calcular(List<RegistroToma> historial, int dias) {
        return EstadisticasProgreso.calcular(
            Arrays.asList(MED_A, MED_B), Arrays.asList(HOR_A, HOR_B), historial, dias, HOY);
    }

    private static List<RegistroToma> tomas(int idHorario, String fecha,
                                            String estado, int cuantas) {
        List<RegistroToma> lista = new ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            RegistroToma t = new RegistroToma();
            t.setIdHorario(idHorario);
            t.setEstado(estado);
            t.setFechaHoraProgramada(fecha + " 08:00:00");
            lista.add(t);
        }
        return lista;
    }

    private static Medicamento med(int id, String nombre) {
        Medicamento m = new Medicamento();
        m.setId(id); m.setNombre(nombre);
        return m;
    }

    private static Horario horario(int id, int idMed, String hora) {
        Horario h = new Horario(idMed, hora, 24, false);
        h.setId(id);
        return h;
    }
}
