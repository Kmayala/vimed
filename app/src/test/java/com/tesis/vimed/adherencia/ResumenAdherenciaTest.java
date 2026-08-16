package com.tesis.vimed.adherencia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.RegistroToma;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pruebas del resumen que ve el familiar. */
public class ResumenAdherenciaTest {

    private static final String HOY = "2026-06-30";

    private static final Medicamento MED_A = med(1, "Losartán");
    private static final Medicamento MED_B = med(2, "Metformina");
    private static final Horario HOR_A = horario(10, 1, "08:00");
    private static final Horario HOR_B = horario(20, 2, "22:00");

    // ═══ Porcentajes ═══════════════════════════════════════════

    @Test
    public void cuentaConfirmadasSobreElTotal() {
        List<RegistroToma> h = new ArrayList<>();
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "confirmada", 3));
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "omitida", 1));

        ResumenAdherencia.Resumen r = calcular(h);

        assertEquals(3, r.confirmadasCorto);
        assertEquals(4, r.totalCorto);
        assertEquals(75, r.porcentajeCorto());
    }

    @Test
    public void laVentanaCortaSoloMiraLaUltimaSemana() {
        List<RegistroToma> h = new ArrayList<>();
        // Hace tres semanas: mal. Esta semana: perfecto.
        h.addAll(tomas(HOR_A.getId(), "2026-06-08", "omitida", 10));
        h.addAll(tomas(HOR_A.getId(), "2026-06-29", "confirmada", 5));

        ResumenAdherencia.Resumen r = calcular(h);

        assertEquals(100, r.porcentajeCorto());   // la semana viene bien
        assertEquals(33, r.porcentajeLargo());    // el mes arrastra lo viejo
    }

    @Test
    public void laPospuestaCuentaComoNoTomada() {
        // La alarma sonó completa y nadie la respondió: para el familiar
        // eso no es una toma.
        List<RegistroToma> h = new ArrayList<>();
        h.addAll(tomas(HOR_A.getId(), "2026-06-29", "confirmada", 1));
        h.addAll(tomas(HOR_A.getId(), "2026-06-29", "pospuesta", 1));

        assertEquals(50, calcular(h).porcentajeCorto());
    }

    @Test
    public void sinTomasEnLaSemanaNoInventaUnCero() {
        // Cero por ciento diría "no tomó nada", que es muy distinto de
        // "no había nada para tomar".
        List<RegistroToma> h = new ArrayList<>(
            tomas(HOR_A.getId(), "2026-06-10", "confirmada", 4));

        ResumenAdherencia.Resumen r = calcular(h);

        assertEquals(-1, r.porcentajeCorto());
        assertEquals(100, r.porcentajeLargo());
    }

    @Test
    public void sinHistorialNoHayDatos() {
        ResumenAdherencia.Resumen r = calcular(new ArrayList<>());
        assertFalse(r.hayDatos());
        assertEquals("Todavía no hay tomas registradas", r.titular());
    }

    @Test
    public void unEstadoDesconocidoNoSeCuentaComoOlvido() {
        List<RegistroToma> h = new ArrayList<>();
        h.addAll(tomas(HOR_A.getId(), "2026-06-29", "confirmada", 2));
        h.addAll(tomas(HOR_A.getId(), "2026-06-29", "en_revision", 5));

        assertEquals(100, calcular(h).porcentajeCorto());
    }

    // ═══ Titular ═══════════════════════════════════════════════

    @Test
    public void elTitularAcompanaAlNumero() {
        assertEquals("Viene cumpliendo bien el tratamiento",
            calcular(tomas(HOR_A.getId(), "2026-06-29", "confirmada", 10)).titular());

        List<RegistroToma> flojo = new ArrayList<>();
        flojo.addAll(tomas(HOR_A.getId(), "2026-06-29", "confirmada", 1));
        flojo.addAll(tomas(HOR_A.getId(), "2026-06-29", "omitida", 9));
        assertEquals("Está tomando poco de lo indicado", calcular(flojo).titular());
    }

    // ═══ Horarios que se pierden ═══════════════════════════════

    @Test
    public void senalaElHorarioQueMasSePierde() {
        List<RegistroToma> h = new ArrayList<>();
        // El de las 22:00 se pierde casi siempre; el de las 08:00 va bien.
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "confirmada", 10));
        h.addAll(tomas(HOR_B.getId(), "2026-06-28", "omitida", 7));
        h.addAll(tomas(HOR_B.getId(), "2026-06-28", "confirmada", 1));

        ResumenAdherencia.Resumen r = calcular(h);

        assertEquals(1, r.puntosFlojos.size());
        ResumenAdherencia.PuntoFlojo p = r.puntosFlojos.get(0);
        assertEquals("Metformina", p.nombreMedicamento);
        assertEquals("22:00", p.hora);
        assertEquals(7, p.falladas);
        assertEquals(8, p.total);
        assertTrue(p.detalle().contains("7 de 8"));
    }

    @Test
    public void noSenalaUnHorarioConMuyPocasTomas() {
        // Dos olvidos de dos tomas no alcanzan para hablar de un patrón.
        List<RegistroToma> h = new ArrayList<>(
            tomas(HOR_B.getId(), "2026-06-29", "omitida", 2));

        assertTrue(calcular(h).puntosFlojos.isEmpty());
    }

    @Test
    public void unHorarioQueVaBienNoAparece() {
        List<RegistroToma> h = new ArrayList<>();
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "confirmada", 19));
        h.addAll(tomas(HOR_A.getId(), "2026-06-28", "omitida", 1));

        assertTrue(calcular(h).puntosFlojos.isEmpty());
    }

    // ═══ Fechas ════════════════════════════════════════════════

    @Test
    public void restarDiasCruzaMesesYAnios() {
        assertEquals("2026-06-23", ResumenAdherencia.restarDias("2026-06-30", 7));
        assertEquals("2026-02-28", ResumenAdherencia.restarDias("2026-03-01", 1));
        assertEquals("2025-12-31", ResumenAdherencia.restarDias("2026-01-01", 1));
        // 2024 fue bisiesto
        assertEquals("2024-02-29", ResumenAdherencia.restarDias("2024-03-01", 1));
    }

    // ═══ Helpers ═══════════════════════════════════════════════

    private ResumenAdherencia.Resumen calcular(List<RegistroToma> historial) {
        return ResumenAdherencia.calcular(
            Arrays.asList(MED_A, MED_B), Arrays.asList(HOR_A, HOR_B), historial, HOY);
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
        m.setId(id);
        m.setNombre(nombre);
        return m;
    }

    private static Horario horario(int id, int idMed, String hora) {
        Horario h = new Horario(idMed, hora, 24, false);
        h.setId(id);
        return h;
    }
}
