package com.tesis.vimed.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.tesis.vimed.models.CatalogoMedicamento;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reconocer lo que NO está en el catálogo.
 *
 * El catálogo tiene unas decenas de entradas y las farmacias venden miles
 * de productos: los antigripales, los genéricos de marca local y casi todo
 * lo de venta libre quedan afuera. Que el escáner solo sirviera para lo
 * conocido lo dejaba inútil justo en los casos más comunes.
 *
 * La señal es el TAMAÑO del texto: en una caja, el nombre del producto es
 * lo que está impreso más grande, porque es lo que la marca quiere que se
 * vea desde el mostrador. El lector ya devuelve el rectángulo de cada
 * línea.
 */
public class EscanerCajaDesconocidosTest {

    private static CatalogoMedicamento entrada(String comercial, String principio) {
        CatalogoMedicamento m = new CatalogoMedicamento();
        m.setNombreComercial(comercial);
        m.setPrincipioActivo(principio);
        return m;
    }

    private static final List<CatalogoMedicamento> CATALOGO =
        Arrays.asList(entrada("Enalapril", "enalapril"));

    private static EscanerCaja.Linea l(String texto, int alto) {
        return new EscanerCaja.Linea(texto, alto);
    }

    /** Una caja de antigripal, con el nombre grande y el resto chico. */
    private static List<EscanerCaja.Linea> cajaDeAntigripal() {
        return Arrays.asList(
            l("LABORATORIO GUARANI", 18),
            l("TAPSIN NOCHE", 62),
            l("Paracetamol 500 mg", 24),
            l("20 comprimidos recubiertos", 16),
            l("Venta libre", 14),
            l("Industria paraguaya", 12));
    }

    // ═══ Lo desconocido, por el tamaño ═════════════════════════

    @Test
    public void reconoceElNombreMasGrandeAunqueNoEsteEnElCatalogo() {
        EscanerCaja.Lectura r = EscanerCaja.leer(cajaDeAntigripal(), CATALOGO, null);

        assertEquals("TAPSIN NOCHE", r.nombre);
        assertEquals(500f, r.dosis, 0.001);
        assertEquals("mg", r.unidad);
    }

    /**
     * Y avisa que es una suposición. "Enalapril" reconocido del catálogo y
     * "TAPSIN NOCHE" adivinado por ser lo más grande no merecen la misma
     * confianza, y quien confirma tiene que saber cuál le están mostrando.
     */
    @Test
    public void loAdivinadoSeMarcaComoApuesta() {
        assertTrue(EscanerCaja.leer(cajaDeAntigripal(), CATALOGO, null).nombreEsUnaApuesta);
    }

    @Test
    public void loDelCatalogoNoSeMarcaComoApuesta() {
        EscanerCaja.Lectura r = EscanerCaja.leer(Arrays.asList(
            l("LABORATORIO CATEDRAL", 40),
            l("ENALAPRIL", 30),
            l("10 mg", 20)), CATALOGO, null);

        assertEquals("Enalapril", r.nombre);
        assertFalse(r.nombreEsUnaApuesta);
    }

    /**
     * El catálogo gana aunque esté impreso más chico: es un dato, no una
     * suposición, y además trae dosis habitual y presentación.
     */
    @Test
    public void elCatalogoGanaAlTamano() {
        EscanerCaja.Lectura r = EscanerCaja.leer(Arrays.asList(
            l("FARMACIA DEL PUEBLO", 70),
            l("ENALAPRIL", 22)), CATALOGO, null);

        assertEquals("Enalapril", r.nombre);
        assertFalse(r.nombreEsUnaApuesta);
    }

    // ═══ Lo que no puede ser un nombre ═════════════════════════

    @Test
    public void noEligeUnaLineaDeEnvaseAunqueSeaLaMasGrande() {
        EscanerCaja.Lectura r = EscanerCaja.leer(Arrays.asList(
            l("30 COMPRIMIDOS", 80),
            l("BRONCOFLEM", 40)), CATALOGO, null);

        assertEquals("BRONCOFLEM", r.nombre);
    }

    @Test
    public void noEligeUnCodigoNiUnLote() {
        EscanerCaja.Lectura r = EscanerCaja.leer(Arrays.asList(
            l("7840123456789", 90),
            l("LOTE 24531", 70),
            l("VITAFLU", 30)), CATALOGO, null);

        assertEquals("VITAFLU", r.nombre);
    }

    @Test
    public void noEligeUnaFraseDelProspecto() {
        EscanerCaja.Lectura r = EscanerCaja.leer(Arrays.asList(
            l("Mantener fuera del alcance de los ninos y en lugar seco", 50),
            l("DOLOFIN", 30)), CATALOGO, null);

        assertEquals("DOLOFIN", r.nombre);
    }

    /**
     * Sin los altos no se adivina: elegir "la primera línea larga"
     * acertaría a veces y pondría el laboratorio otras, y no habría forma
     * de saber cuál de las dos pasó.
     */
    @Test
    public void sinTamanosNoAdivina() {
        EscanerCaja.Lectura r = EscanerCaja.leer(
            Arrays.asList("LABORATORIO GUARANI", "TAPSIN NOCHE", "500 mg"),
            CATALOGO);

        assertFalse(r.tieneNombre());
        assertNull(r.nombre);
        assertEquals(500f, r.dosis, 0.001);   // la dosis sí sale igual
    }

    // ═══ Lo que la persona ya tiene cargado ════════════════════

    /**
     * A partir del segundo escaneo ya no es una apuesta: la primera vez se
     * adivinó y se guardó como medicamento suyo.
     */
    @Test
    public void loQueYaTieneCargadoDejaDeSerUnaApuesta() {
        EscanerCaja.Lectura r = EscanerCaja.leer(cajaDeAntigripal(), CATALOGO,
            Arrays.asList("Tapsin Noche"));

        assertEquals("Tapsin Noche", r.nombre);
        assertFalse(r.nombreEsUnaApuesta);
    }

    /** Y respeta cómo lo escribió la persona, no cómo salió del lector. */
    @Test
    public void conservaLaEscrituraDeLaPersona() {
        EscanerCaja.Lectura r = EscanerCaja.leer(Arrays.asList(
            l("VITAFLU DIA", 60)), CATALOGO,
            Arrays.asList("Vitaflú Día"));

        assertEquals("Vitaflú Día", r.nombre);
    }

    @Test
    public void loSuyoGanaAlTamanoPeroNoAlCatalogo() {
        EscanerCaja.Lectura r = EscanerCaja.leer(Arrays.asList(
            l("MARCA GRANDE INVENTADA", 90),
            l("ENALAPRIL", 20)), CATALOGO,
            Arrays.asList("Marca Grande Inventada"));

        assertEquals("Enalapril", r.nombre);
    }

    // ═══ Nada legible ══════════════════════════════════════════

    @Test
    public void unaCajaIlegibleSigueSinDevolverNada() {
        EscanerCaja.Lectura r = EscanerCaja.leer(Arrays.asList(
            l("|||", 40), l("...", 30), l("7840123456789", 60)),
            Collections.emptyList(), null);

        assertTrue(r.vacia());
    }
}
