package com.tesis.vimed.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * El cálculo de días hasta el vencimiento.
 *
 * Las fechas se arman RELATIVAS a hoy y no fijas ("2026-09-14"): un test con
 * fechas fijas pasa hoy y falla el año que viene, y el que lo vea fallar no
 * va a saber si se rompió el código o venció el test.
 */
public class MedicamentoVencimientoTest {

    private static final SimpleDateFormat SDF =
        new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private static Medicamento conVencimientoEn(int dias) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, dias);
        Medicamento m = new Medicamento();
        m.setFechaVencimiento(SDF.format(c.getTime()));
        return m;
    }

    @Test
    public void sinFecha_noAvisaNada() {
        Medicamento m = new Medicamento();
        assertEquals(Integer.MAX_VALUE, m.diasParaVencer());
        assertFalse("sin fecha no puede estar por vencer", m.venceProto());
        assertFalse(m.estaVencido());
        assertEquals("", m.vencimientoLegible());
    }

    @Test
    public void fechaIlegible_seComportaComoSinFecha() {
        Medicamento m = new Medicamento();
        m.setFechaVencimiento("el mes que viene");
        assertEquals(Integer.MAX_VALUE, m.diasParaVencer());
        assertFalse(m.venceProto());
    }

    @Test
    public void cuentaLosDiasQueFaltan() {
        assertEquals(0, conVencimientoEn(0).diasParaVencer());
        assertEquals(1, conVencimientoEn(1).diasParaVencer());
        assertEquals(30, conVencimientoEn(30).diasParaVencer());
        assertEquals(-3, conVencimientoEn(-3).diasParaVencer());
    }

    /** El pedido era avisar "por lo menos dos días antes". */
    @Test
    public void avisaDesdeDosDiasAntes() {
        assertFalse("faltando 3 días todavía no molesta",
            conVencimientoEn(3).venceProto());
        assertTrue("dos días antes es el mínimo pedido",
            conVencimientoEn(2).venceProto());
        assertTrue(conVencimientoEn(1).venceProto());
        assertTrue("el mismo día también", conVencimientoEn(0).venceProto());
    }

    /** Callarse justo cuando el problema empeora no tendría sentido. */
    @Test
    public void sigueAvisandoDespuesDeVencido() {
        Medicamento vencido = conVencimientoEn(-5);
        assertTrue(vencido.venceProto());
        assertTrue(vencido.estaVencido());
    }

    @Test
    public void noEstaVencidoElDiaDelVencimiento() {
        assertFalse("vence hoy, todavía sirve hoy", conVencimientoEn(0).estaVencido());
    }

    @Test
    public void textoLegible() {
        assertEquals("Vence hoy", conVencimientoEn(0).vencimientoLegible());
        assertEquals("Vence mañana", conVencimientoEn(1).vencimientoLegible());
        assertEquals("Vence en 4 días", conVencimientoEn(4).vencimientoLegible());
        assertEquals("Vencido ayer", conVencimientoEn(-1).vencimientoLegible());
        assertEquals("Vencido hace 6 días", conVencimientoEn(-6).vencimientoLegible());
    }

    /**
     * Con una fecha larga en el futuro hay al menos un cambio de horario de
     * verano en el medio. Restando milisegundos y dividiendo por 86.400.000
     * eso da 179,96 días y trunca a 179.
     */
    @Test
    public void noSePierdeUnDiaPorElHorarioDeVerano() {
        assertEquals(180, conVencimientoEn(180).diasParaVencer());
        assertEquals(365, conVencimientoEn(365).diasParaVencer());
    }
}
