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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pruebas del motor de sugerencias.
 *
 * Lo que se prueba acá no es que el código corra, sino que NO sugiera de
 * más: un sistema que le mueve la hora de la medicación a un adulto mayor
 * porque vio tres datos sueltos es peor que uno que no sugiere nada.
 */
public class AnalizadorAdherenciaTest {

    private static final int ID_MED = 7;
    private static final int ID_HORARIO = 42;

    // ═══ Casos donde SÍ hay que sugerir ════════════════════════

    @Test
    public void sugiereMoverCuandoElHabitoEsConsistente() {
        // Recordatorio a las 08:00, pero la toma real ronda las 08:25.
        List<RegistroToma> historial = confirmadas("08:00",
            new int[]{25, 22, 28, 24, 26, 23});

        List<Sugerencia> s = analizar("08:00", 24, historial, null);

        assertEquals(1, s.size());
        assertEquals(Sugerencia.Tipo.MOVER_HORA, s.get(0).tipo);
        assertEquals("08:25", s.get(0).horaSugerida);
        assertEquals(6, s.get(0).muestras);
    }

    /**
     * Un medicamento de una sola toma diaria habla de UNA hora.
     */
    @Test
    public void unaTomaAlDiaNombraSuHoraYNadaMas() {
        List<Sugerencia> s = analizar("08:00", 24,
            confirmadas("08:00", new int[]{25, 22, 28, 24, 26, 23}), null);

        Sugerencia sug = s.get(0);
        assertFalse(sug.afectaVariasTomas());
        assertEquals(1, sug.tomasPorDia());
        assertEquals("¿Movemos el recordatorio?", sug.titulo());
        assertTrue(sug.detalle().contains("pase a las 08:25"));
    }

    /**
     * El caso que estaba mal.
     *
     * La app guarda UNA hora de inicio por horario, así que un medicamento
     * cada 6 horas tiene sus cuatro tomas derivadas de ese único valor:
     * mover el recordatorio las mueve todas. El texto hablaba de una sola
     * hora, y quien aceptaba se encontraba con las otras tres cambiadas.
     */
    @Test
    public void variasTomasAlDiaLasNombraATodas() {
        List<Sugerencia> s = analizar("00:20", 6,
            confirmadas("00:20", new int[]{25, 22, 28, 24, 26, 23}), null);

        Sugerencia sug = s.get(0);
        assertTrue(sug.afectaVariasTomas());
        assertEquals(4, sug.tomasPorDia());
        assertEquals("¿Movemos los recordatorios?", sug.titulo());
        assertEquals("Sí, moverlos", sug.textoAceptar());

        // Las cuatro horas viejas y las cuatro nuevas, dichas en el cartel.
        String detalle = sug.detalle();
        for (String hora : new String[]{"00:20", "06:20", "12:20", "18:20"}) {
            assertTrue("falta la hora vieja " + hora, detalle.contains(hora));
        }
        for (String hora : new String[]{"00:45", "06:45", "12:45", "18:45"}) {
            assertTrue("falta la hora nueva " + hora, detalle.contains(hora));
        }
        assertTrue(detalle.contains("4 veces al día"));
    }

    /** Las horas del día se enumeran con "y" antes de la última. */
    @Test
    public void laUltimaHoraVaConY() {
        List<Sugerencia> s = analizar("08:00", 8,
            confirmadas("08:00", new int[]{25, 22, 28, 24, 26, 23}), null);
        assertTrue(s.get(0).detalle().contains("y 00:25"));
    }

    @Test
    public void sugiereReforzarCuandoElHorarioSeOlvida() {
        List<RegistroToma> historial = new ArrayList<>();
        historial.addAll(omitidas(5));
        historial.addAll(confirmadas("22:00", new int[]{2, 3, 1}));

        List<Sugerencia> s = analizar("22:00", 24, historial, null);

        assertEquals(1, s.size());
        assertEquals(Sugerencia.Tipo.REFORZAR, s.get(0).tipo);
        assertEquals(5, s.get(0).omitidas);
        assertEquals(8, s.get(0).total);
    }

    @Test
    public void losOlvidosGananAlDesfase() {
        // El horario está corrido Y se olvida. Primero hay que lograr que la
        // tome; afinar la hora es secundario.
        List<RegistroToma> historial = new ArrayList<>();
        historial.addAll(omitidas(6));
        historial.addAll(confirmadas("08:00", new int[]{40, 45, 38, 42, 41}));

        List<Sugerencia> s = analizar("08:00", 24, historial, null);

        assertEquals(1, s.size());
        assertEquals(Sugerencia.Tipo.REFORZAR, s.get(0).tipo);
    }

    // ═══ Casos donde NO hay que sugerir ════════════════════════

    @Test
    public void noSugiereConPocasMuestras() {
        List<RegistroToma> historial = confirmadas("08:00", new int[]{30, 32, 28});
        assertTrue(analizar("08:00", 24, historial, null).isEmpty());
    }

    @Test
    public void noSugiereSiElDesfaseEsChico() {
        // Diez minutos no justifican molestar a nadie.
        List<RegistroToma> historial = confirmadas("08:00",
            new int[]{10, 8, 12, 9, 11, 10});
        assertTrue(analizar("08:00", 24, historial, null).isEmpty());
    }

    @Test
    public void noSugiereSiLosHorariosEstanDesparramados() {
        // Misma mediana alta que el caso bueno, pero sin ningún hábito:
        // mover el recordatorio no arreglaría nada.
        List<RegistroToma> historial = confirmadas("08:00",
            new int[]{5, 90, 30, 120, 15, 75});
        assertTrue(analizar("08:00", 24, historial, null).isEmpty());
    }

    @Test
    public void unDiaRaroNoInventaUnPatron() {
        // Cinco tomas puntuales y una confirmada seis horas tarde. Con
        // promedio el desfase daría ~60 min y sugeriría mover; con mediana,
        // el dato raro no pesa.
        List<RegistroToma> historial = confirmadas("08:00",
            new int[]{2, 0, 5, 3, 1, 360});
        assertTrue(analizar("08:00", 24, historial, null).isEmpty());
    }

    // ═══ Topes de seguridad ════════════════════════════════════

    @Test
    public void nuncaSeAlejaMasDeUnaHoraDeLaReceta() {
        // La persona toma la medicación más de tres horas tarde. Igual el
        // recordatorio solo puede moverse una hora: más que eso ya no es el
        // tratamiento que indicó el médico.
        List<RegistroToma> historial = confirmadas("06:00",
            new int[]{200, 205, 195, 198, 202, 201});

        List<Sugerencia> s = analizar("06:00", 24, historial, null);

        assertEquals(1, s.size());
        assertEquals("07:00", s.get(0).horaSugerida);
    }

    @Test
    public void noSePasaDeLaMitadDelIntervalo() {
        // Cada hora: correr el recordatorio más de 30 minutos lo dejaría
        // encima de la toma siguiente.
        List<RegistroToma> historial = confirmadas("06:00",
            new int[]{200, 205, 195, 198, 202, 201});

        List<Sugerencia> s = analizar("06:00", 1, historial, null);

        assertEquals(1, s.size());
        assertEquals("06:30", s.get(0).horaSugerida);
    }

    @Test
    public void cercaDelTopeNoValeLaPenaSugerir() {
        // Receta 08:00, ya ajustado a 08:50: quedan 10 minutos de margen.
        // Sugerir un cambio de 10 minutos es molestar por nada.
        Map<Integer, String> originales = new HashMap<>();
        originales.put(ID_HORARIO, "08:00");

        List<RegistroToma> historial = confirmadas("08:50",
            new int[]{40, 38, 42, 41, 39, 40});

        assertTrue(analizar("08:50", 24, historial, originales).isEmpty());
    }

    @Test
    public void noMueveMasDeDosHorasDeUnaSolaVez() {
        // Receta 08:00, ya ajustado a 07:00 (más temprano). Contra la receta
        // quedan 2 horas de margen hacia adelante, pero un solo ajuste nunca
        // puede mover más de 2 horas.
        Map<Integer, String> originales = new HashMap<>();
        originales.put(ID_HORARIO, "08:00");

        List<RegistroToma> historial = confirmadas("07:00",
            new int[]{200, 205, 195, 198, 202, 201});

        List<Sugerencia> s = analizar("07:00", 24, historial, originales);

        assertEquals(1, s.size());
        assertEquals("09:00", s.get(0).horaSugerida);   // 07:00 + 120 min
    }

    @Test
    public void yaEnElTopeNoVuelveASugerir() {
        // Receta 08:00, ya ajustado a 09:00: no queda margen, y sugerir un
        // ajuste de cero minutos sería solo ruido.
        Map<Integer, String> originales = new HashMap<>();
        originales.put(ID_HORARIO, "08:00");

        List<RegistroToma> historial = confirmadas("09:00",
            new int[]{30, 28, 32, 31, 29, 30});

        assertTrue(analizar("09:00", 24, historial, originales).isEmpty());
    }

    // ═══ Helpers de hora ═══════════════════════════════════════

    @Test
    public void laDiferenciaDeHoraTomaElCaminoCorto() {
        // De 23:50 a 00:10 son 20 minutos, no 1420.
        assertEquals(20, AnalizadorAdherencia.diferenciaCircular(
            AnalizadorAdherencia.aMinutos("00:10"),
            AnalizadorAdherencia.aMinutos("23:50")));
    }

    @Test
    public void laHoraSugeridaEnvuelveEnMedianoche() {
        assertEquals("00:10", AnalizadorAdherencia.aHHmm(24 * 60 + 10));
        assertEquals("23:50", AnalizadorAdherencia.aHHmm(-10));
    }

    @Test
    public void elDesfaseCruzaLaMedianoche() {
        // Programada 23:50 del día 1, confirmada 00:15 del día 2.
        RegistroToma t = new RegistroToma();
        t.setFechaHoraProgramada("2026-08-16 23:50:00");
        t.setFechaHoraConfirmacion("2026-08-17 00:15:00");
        assertEquals(Integer.valueOf(25), AnalizadorAdherencia.desfaseEnMinutos(t));
    }

    @Test
    public void aceptaElFormatoISOdePostgres() {
        RegistroToma t = new RegistroToma();
        t.setFechaHoraProgramada("2026-08-16T08:00:00");
        t.setFechaHoraConfirmacion("2026-08-16T08:20:00");
        assertEquals(Integer.valueOf(20), AnalizadorAdherencia.desfaseEnMinutos(t));
    }

    // ═══ Armado de datos de prueba ═════════════════════════════

    private List<Sugerencia> analizar(String horaInicio, int intervalo,
                                      List<RegistroToma> historial,
                                      Map<Integer, String> originales) {
        Medicamento med = new Medicamento();
        med.setId(ID_MED);
        med.setNombre("Losartán");

        Horario h = new Horario(ID_MED, horaInicio, intervalo, false);
        h.setId(ID_HORARIO);

        return AnalizadorAdherencia.analizar(
            Arrays.asList(med), Arrays.asList(h), historial, originales);
    }

    /** Tomas confirmadas con el desfase en minutos que se le indique. */
    private List<RegistroToma> confirmadas(String horaProgramada, int[] desfases) {
        List<RegistroToma> lista = new ArrayList<>();
        for (int i = 0; i < desfases.length; i++) {
            int dia = 1 + i;
            RegistroToma t = new RegistroToma();
            t.setIdHorario(ID_HORARIO);
            t.setEstado("confirmada");
            t.setFechaHoraProgramada(fecha(dia, horaProgramada));
            t.setFechaHoraConfirmacion(fecha(dia, sumar(horaProgramada, desfases[i])));
            lista.add(t);
        }
        return lista;
    }

    private List<RegistroToma> omitidas(int cuantas) {
        List<RegistroToma> lista = new ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            RegistroToma t = new RegistroToma();
            t.setIdHorario(ID_HORARIO);
            t.setEstado("omitida");
            t.setFechaHoraProgramada(fecha(20 + i, "22:00"));
            lista.add(t);
        }
        return lista;
    }

    private String fecha(int dia, String hhmm) {
        return String.format("2026-06-%02d %s:00", dia, hhmm);
    }

    /** Suma minutos a un "HH:mm", envolviendo al día siguiente si hace falta. */
    private String sumar(String hhmm, int minutos) {
        return AnalizadorAdherencia.aHHmm(
            AnalizadorAdherencia.aMinutos(hhmm) + minutos);
    }
}
