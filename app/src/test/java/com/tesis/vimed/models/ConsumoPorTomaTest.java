package com.tesis.vimed.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Cuánto gasta una toma, según cómo esté medido el medicamento.
 *
 * El bug que fijan estas pruebas: cada toma descontaba 1 del stock sin
 * mirar nada. Un frasco de jarabe de 120 ml con una dosis de 5 ml daba
 * para 120 tomas en la cuenta de la app y para 24 en la realidad. La
 * persona se quedaba sin jarabe mientras la pantalla le decía que le
 * sobraba, que es la peor forma de equivocarse para esta app.
 */
public class ConsumoPorTomaTest {

    private static Medicamento med(float dosis, String unidad, int stock) {
        Medicamento m = new Medicamento();
        m.setDosis(dosis);
        m.setUnidad(unidad);
        m.setStockActual(stock);
        return m;
    }

    // ── Sólidos: se cuentan de a uno ───────────────────────────

    @Test
    public void unComprimidoGastaUnaUnidad() {
        assertEquals(1, med(500, "mg", 30).consumoPorToma());
    }

    @Test
    public void laDosisEnMiligramosNoCambiaElDescuento() {
        // 850 mg de metformina no son 850 comprimidos.
        assertEquals(1, med(850, "mg", 60).consumoPorToma());
    }

    @Test
    public void elTextoDeUnSolidoHablaDeUnidades() {
        assertEquals("30 unidades", med(500, "mg", 30).stockLegible());
        assertEquals("1 unidad", med(500, "mg", 1).stockLegible());
    }

    // ── Líquidos: se cuentan en mililitros ─────────────────────

    @Test
    public void unJarabeGastaLosMililitrosDeLaDosis() {
        assertEquals(5, med(5, "ml", 120).consumoPorToma());
    }

    @Test
    public void unaDosisConDecimalesSeRedondea() {
        assertEquals(3, med(2.5f, "ml", 100).consumoPorToma());
    }

    @Test
    public void nuncaDescuentaMenosDeUno() {
        // Una dosis de 0,4 ml redondea a 0; descontar cero dejaría el
        // frasco lleno para siempre.
        assertEquals(1, med(0.4f, "ml", 20).consumoPorToma());
    }

    @Test
    public void elTextoDeUnLiquidoHablaDeMililitros() {
        assertEquals("120 ml", med(5, "ml", 120).stockLegible());
    }

    // ── Lo que queda alcanza, o no ─────────────────────────────

    @Test
    public void tresMililitrosNoAlcanzanParaUnaDosisDeCinco() {
        // Tiene stock y aun así no hay toma posible: por eso la pregunta
        // no puede ser "¿es cero?".
        assertTrue(med(5, "ml", 3).noAlcanzaParaUnaToma());
    }

    @Test
    public void cincoMililitrosAlcanzanJustoParaUnaDosisDeCinco() {
        assertFalse(med(5, "ml", 5).noAlcanzaParaUnaToma());
    }

    @Test
    public void unComprimidoConStockUnoTodaviaAlcanza() {
        assertFalse(med(500, "mg", 1).noAlcanzaParaUnaToma());
    }

    @Test
    public void sinStockNoAlcanzaParaNada() {
        assertTrue(med(500, "mg", 0).noAlcanzaParaUnaToma());
    }

    // ── Casos sueltos ──────────────────────────────────────────

    @Test
    public void sinUnidadSeCuentaDeAUno() {
        assertEquals(1, med(0, null, 10).consumoPorToma());
    }

    @Test
    public void laUnidadEnMayusculaTambienCuenta() {
        // La unidad llega de la base o del lector de la caja, no siempre
        // del botón de la app.
        assertEquals(5, med(5, "ML", 120).consumoPorToma());
    }
}
