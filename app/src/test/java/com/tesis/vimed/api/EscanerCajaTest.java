package com.tesis.vimed.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.tesis.vimed.models.CatalogoMedicamento;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Lo que se saca del texto de una caja.
 *
 * Las listas de estas pruebas están escritas como devuelve el lector: un
 * renglón por línea impresa, en el orden en que aparecen, con el ruido que
 * traen las cajas de verdad —el laboratorio, el lote, la cantidad de
 * comprimidos—. Probar con "Enalapril 10 mg" solo no demostraría nada:
 * justamente lo difícil es el resto.
 */
public class EscanerCajaTest {

    private static CatalogoMedicamento entrada(String comercial, String principio) {
        CatalogoMedicamento m = new CatalogoMedicamento();
        m.setNombreComercial(comercial);
        m.setPrincipioActivo(principio);
        return m;
    }

    private static final List<CatalogoMedicamento> CATALOGO = Arrays.asList(
        entrada("Enalapril", "enalapril"),
        entrada("Metformina", "metformina"),
        entrada("Tamsulosina", "tamsulosina"));

    // ═══ El caso normal ════════════════════════════════════════

    @Test
    public void sacaNombreYDosisDeUnaCajaTipica() {
        EscanerCaja.Lectura l = EscanerCaja.leer(Arrays.asList(
            "LABORATORIO CATEDRAL",
            "ENALAPRIL",
            "10 mg",
            "30 comprimidos ranurados",
            "Venta bajo receta"), CATALOGO);

        assertEquals("Enalapril", l.nombre);
        assertEquals(10f, l.dosis, 0.001);
        assertEquals("mg", l.unidad);
    }

    /**
     * El número más grande de una caja suele ser la cantidad de
     * comprimidos, no la dosis. Por eso la unidad es obligatoria.
     */
    @Test
    public void noConfundeLaCantidadDeComprimidosConLaDosis() {
        EscanerCaja.Lectura l = EscanerCaja.leer(Arrays.asList(
            "METFORMINA",
            "60 comprimidos",
            "850 mg"), CATALOGO);

        assertEquals(850f, l.dosis, 0.001);
        assertEquals("mg", l.unidad);
    }

    @Test
    public void unaCajaSinDosisNoInventaNinguna() {
        EscanerCaja.Lectura l = EscanerCaja.leer(Arrays.asList(
            "ENALAPRIL", "30 comprimidos", "Industria paraguaya"), CATALOGO);

        assertTrue(l.tieneNombre());
        assertFalse(l.tieneDosis());
        assertEquals(0f, l.dosis, 0.001);
        assertNull(l.unidad);
    }

    // ═══ Cómo vienen escritos los números ══════════════════════

    @Test
    public void aceptaLaComaDecimal() {
        // "0,4 mg" y "0.4 mg" conviven según el laboratorio.
        EscanerCaja.Lectura coma = EscanerCaja.leer(
            Arrays.asList("TAMSULOSINA", "0,4 mg"), CATALOGO);
        EscanerCaja.Lectura punto = EscanerCaja.leer(
            Arrays.asList("TAMSULOSINA", "0.4 mg"), CATALOGO);

        assertEquals(0.4f, coma.dosis, 0.001);
        assertEquals(0.4f, punto.dosis, 0.001);
    }

    @Test
    public void aceptaLaUnidadPegadaAlNumero() {
        EscanerCaja.Lectura l = EscanerCaja.leer(
            Arrays.asList("ENALAPRIL 10mg"), CATALOGO);
        assertEquals(10f, l.dosis, 0.001);
        assertEquals("mg", l.unidad);
    }

    @Test
    public void reconoceLasDistintasUnidades() {
        assertEquals("mcg", EscanerCaja.leer(
            Arrays.asList("LEVOTIROXINA 50 mcg"), CATALOGO).unidad);
        assertEquals("ml", EscanerCaja.leer(
            Arrays.asList("JARABE 120 ml"), CATALOGO).unidad);
        assertEquals("UI", EscanerCaja.leer(
            Arrays.asList("INSULINA 100 UI"), CATALOGO).unidad);
    }

    // ═══ El ruido de las cajas de verdad ═══════════════════════

    /**
     * Un lote y un vencimiento son números, pero no llevan unidad de dosis.
     * Si alguno se colara, la app cargaría una dosis inventada.
     */
    @Test
    public void ignoraLoteYVencimiento() {
        EscanerCaja.Lectura l = EscanerCaja.leer(Arrays.asList(
            "ENALAPRIL",
            "LOTE 24531",
            "VEN 03/2027",
            "EAN 7840123456789"), CATALOGO);

        assertFalse(l.tieneDosis());
    }

    /** "500 gotas" no es una dosis de 500 g. */
    @Test
    public void noCortaUnaPalabraParaSacarUnaUnidad() {
        EscanerCaja.Lectura l = EscanerCaja.leer(
            Arrays.asList("ENVASE POR 500 gotas"), CATALOGO);
        assertFalse(l.tieneDosis());
    }

    // ═══ El nombre ═════════════════════════════════════════════

    @Test
    public void encuentraElNombreAunqueLaLineaTraigaLaDosis() {
        EscanerCaja.Lectura l = EscanerCaja.leer(
            Arrays.asList("METFORMINA 850 mg comprimidos recubiertos"), CATALOGO);
        assertEquals("Metformina", l.nombre);
        assertEquals(850f, l.dosis, 0.001);
    }

    /**
     * Lo que no está en el catálogo no se adivina: se devuelve la dosis y
     * el nombre queda para que lo complete la persona. Inventarlo a partir
     * de un texto borroso es el error que esta app no se puede permitir.
     */
    @Test
    public void loQueNoEstaEnElCatalogoNoSeInventa() {
        EscanerCaja.Lectura l = EscanerCaja.leer(Arrays.asList(
            "RIVAROXABAN", "20 mg", "28 comprimidos"), CATALOGO);

        assertFalse(l.tieneNombre());
        assertNull(l.nombre);
        assertTrue(l.tieneDosis());
        assertEquals(20f, l.dosis, 0.001);
    }

    @Test
    public void sinCatalogoIgualDevuelveLaDosis() {
        EscanerCaja.Lectura l = EscanerCaja.leer(
            Arrays.asList("ENALAPRIL", "10 mg"), Collections.emptyList());
        assertFalse(l.tieneNombre());
        assertEquals(10f, l.dosis, 0.001);
    }

    /** Dos o tres letras enganchan cualquier cosa por casualidad. */
    @Test
    public void ignoraLasLineasDemasiadoCortas() {
        EscanerCaja.Lectura l = EscanerCaja.leer(
            Arrays.asList("EN", "AL", "MG"), CATALOGO);
        assertFalse(l.tieneNombre());
    }

    // ═══ Nada que leer ═════════════════════════════════════════

    @Test
    public void unaFotoIlegibleNoDevuelveNada() {
        assertTrue(EscanerCaja.leer(null, CATALOGO).vacia());
        assertTrue(EscanerCaja.leer(new ArrayList<>(), CATALOGO).vacia());
        assertTrue(EscanerCaja.leer(
            Arrays.asList("", "   ", "|||"), CATALOGO).vacia());
    }
}
