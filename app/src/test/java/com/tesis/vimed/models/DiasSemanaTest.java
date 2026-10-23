package com.tesis.vimed.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.junit.Test;

/**
 * Qué días suena un horario.
 *
 * La máscara va de 7 caracteres EMPEZANDO POR EL DOMINGO, para que el
 * índice coincida con Calendar.DAY_OF_WEEK de Android (1=domingo …
 * 7=sábado). Convertir entre dos convenciones de índices es de los
 * errores más fáciles de cometer y más difíciles de ver a ojo, así que
 * estas pruebas lo fijan contra las constantes reales de Calendar y no
 * contra números escritos a mano.
 */
public class DiasSemanaTest {

    private static Horario con(String mascara) {
        Horario h = new Horario();
        h.setDiasSemana(mascara);
        return h;
    }

    // ── Sin días elegidos: suena todos ─────────────────────────

    @Test
    public void sinMascaraSuenaTodosLosDias() {
        Horario h = con(null);
        assertTrue(h.esTodosLosDias());
        assertTrue(h.aplicaEl(Calendar.MONDAY));
        assertTrue(h.aplicaEl(Calendar.SUNDAY));
        assertEquals("Todos los días", h.diasLegibles());
    }

    @Test
    public void losSieteDiasSeGuardanIgualQueNinguno() {
        // "Todos marcados" y "sin elegir" son lo mismo para la alarma.
        assertEquals("Todos los días", con("1111111").diasLegibles());
    }

    // ── Solo sábados, el caso que motivó todo ──────────────────

    @Test
    public void soloSabadosSuenaElSabado() {
        assertTrue(con("0000001").aplicaEl(Calendar.SATURDAY));
    }

    @Test
    public void soloSabadosNoSuenaElResto() {
        Horario h = con("0000001");
        assertFalse(h.aplicaEl(Calendar.SUNDAY));
        assertFalse(h.aplicaEl(Calendar.MONDAY));
        assertFalse(h.aplicaEl(Calendar.TUESDAY));
        assertFalse(h.aplicaEl(Calendar.WEDNESDAY));
        assertFalse(h.aplicaEl(Calendar.THURSDAY));
        assertFalse(h.aplicaEl(Calendar.FRIDAY));
    }

    @Test
    public void soloSabadosSeLeeEnPalabras() {
        assertEquals("Solo los sábados", con("0000001").diasLegibles());
    }

    // ── El domingo es el primero, no el último ─────────────────

    @Test
    public void elPrimerCaracterEsElDomingo() {
        // Si alguien reordenara la máscara empezando por el lunes, esta
        // prueba es la que lo atrapa.
        Horario h = con("1000000");
        assertTrue(h.aplicaEl(Calendar.SUNDAY));
        assertFalse(h.aplicaEl(Calendar.MONDAY));
    }

    @Test
    public void variosDiasSeLeenConY() {
        assertEquals("Lunes y jueves", con("0100100").diasLegibles());
    }

    // ── Entradas que no sirven ─────────────────────────────────

    @Test
    public void unaMascaraDeCerosSeDescarta() {
        // Un horario que no suena ningún día no es un horario.
        Horario h = con("0000000");
        assertNull(h.getDiasSemana());
        assertTrue(h.esTodosLosDias());
    }

    @Test
    public void unaMascaraMalFormadaSeDescarta() {
        assertNull(con("lunes").getDiasSemana());
        assertNull(con("011").getDiasSemana());
        assertNull(con("01234567").getDiasSemana());
    }

    @Test
    public void unDiaFueraDeRangoNoApagaLaAlarma() {
        // Ante un dato raro, que suene: perder una toma es peor que
        // avisar de más.
        assertTrue(con("0000001").aplicaEl(0));
        assertTrue(con("0000001").aplicaEl(9));
    }
}
