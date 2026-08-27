package com.tesis.vimed.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tesis.vimed.models.CatalogoMedicamento;
import com.tesis.vimed.models.PerfilClinico;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Pruebas del chequeo de dosis.
 *
 * El foco está en los falsos positivos: si la app avisa por cualquier
 * diferencia, la persona aprende a apretar "continuar" sin leer y el aviso
 * deja de servir justo cuando hace falta.
 */
public class DosisCheckerTest {

    private static final List<CatalogoMedicamento> CATALOGO = Arrays.asList(
        entrada(1, "Metformina 850", "metformina", 850f, "mg"),
        entrada(2, "Losartán 50", "losartán", 50f, "mg"),
        entrada(3, "Jarabe Ambroxol", "ambroxol", 10f, "ml"),
        entrada(4, "Complejo B", "complejo b", 0f, "mg")   // sin dosis de referencia
    );

    // ═══ Cuando NO hay que avisar ══════════════════════════════

    @Test
    public void noAvisaConLaDosisHabitual() {
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar("Metformina", CATALOGO, 850f, "mg").nivel);
    }

    @Test
    public void noAvisaPorDiferenciasChicas() {
        // La mitad justa y el doble justo son los bordes; adentro no se avisa.
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar("Losartán", CATALOGO, 75f, "mg").nivel);
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar("Losartán", CATALOGO, 30f, "mg").nivel);
    }

    @Test
    public void noAvisaSiElMedicamentoNoEstaEnElCatalogo() {
        // No sabemos qué es "habitual" para algo que no conocemos. Un aviso
        // inventado sería peor que ningún aviso.
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar("Remedio de la vecina", CATALOGO, 9999f, "mg").nivel);
    }

    @Test
    public void noAvisaSiElCatalogoNoTieneDosisDeReferencia() {
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar("Complejo B", CATALOGO, 500f, "mg").nivel);
    }

    // ═══ Cuando SÍ hay que avisar ══════════════════════════════

    @Test
    public void avisaFuerteAnteUnCeroDeMas() {
        // El error real al cargar a mano: 50 mg tipeados como 500 mg.
        DosisChecker.Aviso a = DosisChecker.revisar("Losartán", CATALOGO, 500f, "mg");
        assertEquals(DosisChecker.Nivel.ALTO, a.nivel);
        assertTrue(a.texto.contains("10 veces"));
        assertTrue(a.texto.contains("cero"));
    }

    @Test
    public void avisaFuerteAnteUnCeroDeMenos() {
        DosisChecker.Aviso a = DosisChecker.revisar("Metformina", CATALOGO, 85f, "mg");
        assertEquals(DosisChecker.Nivel.ALTO, a.nivel);
        assertTrue(a.texto.contains("menos"));
    }

    @Test
    public void avisaSuaveAnteElDoble() {
        DosisChecker.Aviso a = DosisChecker.revisar("Losartán", CATALOGO, 100f, "mg");
        assertEquals(DosisChecker.Nivel.REVISAR, a.nivel);
        // No afirma que esté mal: puede ser lo que indicó el médico.
        assertTrue(a.texto.contains("Puede estar bien"));
        assertEquals("50 mg", a.dosisHabitual);
    }

    @Test
    public void avisaSiLaUnidadNoCoincide() {
        // 10 ml de jarabe cargados como 10 mg: los números coinciden, pero
        // no son la misma cantidad.
        DosisChecker.Aviso a = DosisChecker.revisar("Ambroxol", CATALOGO, 10f, "mg");
        assertEquals(DosisChecker.Nivel.REVISAR, a.nivel);
        assertTrue(a.texto.contains("ml"));
    }

    @Test
    public void laUnidadNoDistingueMayusculas() {
        assertTrue(DosisChecker.mismaUnidad("MG", "mg"));
        assertTrue(DosisChecker.mismaUnidad(" mg ", "mg"));
        assertFalse(DosisChecker.mismaUnidad("ml", "mg"));
    }

    @Test
    public void unaUnidadVaciaNoGeneraAviso() {
        // El catálogo puede tener el campo sin cargar; no inventamos un aviso.
        assertTrue(DosisChecker.mismaUnidad(null, "mg"));
        assertTrue(DosisChecker.mismaUnidad("", "mg"));
    }

    // ═══ Match con el catálogo ═════════════════════════════════

    @Test
    public void encuentraPorPrincipioActivoAunqueElNombreTraigaLaDosis() {
        CatalogoMedicamento m = CatalogoMatcher.buscar("metformina", CATALOGO);
        assertNotNull(m);
        assertEquals(1, m.getIdCatalogo());
    }

    @Test
    public void elTextoMuestraNumerosLegibles() {
        assertEquals("500", DosisChecker.formatear(500f));
        assertEquals("2,5", DosisChecker.formatear(2.5f).replace('.', ','));
    }

    // ═══ Chequeo con peso y edad ═══════════════════════════════
    //
    // Lo que más importa acá es el silencio: casi todo el catálogo NO tiene
    // cargada la referencia por kilo, y en ese estado la app tiene que
    // comportarse exactamente como antes.

    /**
     * 10 a 20 mg/kg/día, con techo de 3000 mg.
     *
     * Va SIN dosis_comun a propósito: con una presentación de referencia
     * cargada, el chequeo de siempre saltaría primero y estas pruebas
     * pasarían sin llegar a ejercitar la cuenta por peso.
     */
    private static CatalogoMedicamento conReferenciaPorPeso() {
        CatalogoMedicamento m = entrada(9, "Ejemplina", "ejemplina", 0f, "mg");
        m.setDosisMgKgDiaMin(10f);
        m.setDosisMgKgDiaMax(20f);
        m.setDosisMaxDia(3000f);
        return m;
    }

    private static final PerfilClinico DE_70_KG = new PerfilClinico(70f, 0);

    @Test
    public void noDiceNadaDelPesoSiElCatalogoNoTieneLaReferencia() {
        // Metformina no tiene mg/kg cargado: con o sin peso, mismo resultado.
        CatalogoMedicamento sinReferencia = CatalogoMatcher.buscar("metformina", CATALOGO);
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar(sinReferencia, 850f, "mg", DE_70_KG, 3).nivel);
    }

    @Test
    public void noDiceNadaDelPesoSiElPerfilEstaVacio() {
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar(conReferenciaPorPeso(), 500f, "mg",
                PerfilClinico.vacio(), 3).nivel);
    }

    @Test
    public void noDiceNadaSinSaberLaFrecuencia() {
        // Sin cuántas veces al día, 500 mg pueden ser 500 o 2000 por día.
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar(conReferenciaPorPeso(), 500f, "mg", DE_70_KG, 0).nivel);
    }

    @Test
    public void noAvisaCuandoLaDosisDiariaCaeDentroDelRango() {
        // 70 kg → 700 a 1400 mg/día. 400 mg × 3 = 1200: adentro.
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar(conReferenciaPorPeso(), 400f, "mg", DE_70_KG, 3).nivel);
    }

    @Test
    public void avisaCuandoLaDosisDiariaPasaElRango() {
        DosisChecker.Aviso a =
            DosisChecker.revisar(conReferenciaPorPeso(), 800f, "mg", DE_70_KG, 3);
        assertEquals(DosisChecker.Nivel.REVISAR, a.nivel);
        assertTrue(a.usaPerfil);
        assertTrue(a.texto.contains("2400"));   // 800 × 3
    }

    @Test
    public void avisaFuerteCuandoLaDiariaEsMuchasVecesElTecho() {
        // 70 kg → techo 1400. 2500 × 3 = 7500, más de 5 veces.
        assertEquals(DosisChecker.Nivel.ALTO,
            DosisChecker.revisar(conReferenciaPorPeso(), 2500f, "mg", DE_70_KG, 3).nivel);
    }

    @Test
    public void elTechoDiarioRecortaElRangoEnPersonasPesadas() {
        // 200 kg × 20 mg/kg = 4000, pero el máximo del producto es 3000.
        // Con 1100 × 3 = 3300 hay que avisar; sin el techo no se avisaría.
        PerfilClinico pesado = new PerfilClinico(200f, 0);
        assertEquals(DosisChecker.Nivel.REVISAR,
            DosisChecker.revisar(conReferenciaPorPeso(), 1100f, "mg", pesado, 3).nivel);
    }

    /**
     * El techo recorta el máximo, NO el mínimo.
     *
     * Con 200 kg el rango por peso es 2000 a 4000, y el techo del producto
     * es 3000. Recortando los dos extremos el rango colapsaba a "3000 a
     * 3000", y entonces cualquier dosis por debajo del techo salía avisada
     * como si fuera POCA. Estar debajo del máximo no es estar debajo del
     * mínimo que hace efecto.
     */
    @Test
    public void elTechoNoRecortaElMinimo() {
        PerfilClinico pesado = new PerfilClinico(200f, 0);
        // 800 × 3 = 2400: por encima del mínimo real (2000) y por debajo
        // del techo (3000). No hay nada que avisar.
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar(conReferenciaPorPeso(), 800f, "mg", pesado, 3).nivel);
    }

    @Test
    public void avisaSiLaDiariaQuedaDebajoDelMinimoPorPeso() {
        // 200 kg: el mínimo por peso son 2000 mg/día. Con 500 × 3 = 1500
        // sí corresponde el aviso, y sigue funcionando después del arreglo.
        PerfilClinico pesado = new PerfilClinico(200f, 0);
        assertEquals(DosisChecker.Nivel.REVISAR,
            DosisChecker.revisar(conReferenciaPorPeso(), 500f, "mg", pesado, 3).nivel);
    }

    /**
     * Si el techo del producto queda por debajo del mínimo que le tocaría
     * por peso, el rango es incoherente y no hay nada sensato que decir.
     * Antes se mostraba "de 4400 a 4000 mg por día".
     */
    @Test
    public void seCallaSiElTechoDejaElRangoDadoVuelta() {
        CatalogoMedicamento apretado = conReferenciaPorPeso();
        apretado.setDosisMaxDia(1500f);           // por debajo de 200 × 10
        PerfilClinico pesado = new PerfilClinico(200f, 0);
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar(apretado, 400f, "mg", pesado, 3).nivel);
    }

    @Test
    public void elChequeoDeSiempreTienePrioridadSobreElDelPeso() {
        // 5000 mg contra una presentación de 500: eso es un cero de más, y
        // ese aviso es el que hay que dar. Uno solo, no dos.
        CatalogoMedicamento conAmbas = conReferenciaPorPeso();
        conAmbas.setDosisComun(500f);

        DosisChecker.Aviso a =
            DosisChecker.revisar(conAmbas, 5000f, "mg", DE_70_KG, 3);
        assertEquals(DosisChecker.Nivel.ALTO, a.nivel);
        assertFalse(a.usaPerfil);
        assertTrue(a.texto.contains("cero"));
    }

    @Test
    public void avisaPorEdadSoloSiElMedicamentoEstaMarcado() {
        CatalogoMedicamento m = entrada(10, "Mayorina", "mayorina", 50f, "mg");
        PerfilClinico mayor = new PerfilClinico(0f, PerfilClinico.anioParaEdad(80));

        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar(m, 50f, "mg", mayor, 1).nivel);

        m.setAjustarEnMayores(true);
        DosisChecker.Aviso a = DosisChecker.revisar(m, 50f, "mg", mayor, 1);
        assertEquals(DosisChecker.Nivel.REVISAR, a.nivel);
        assertTrue(a.usaPerfil);
    }

    @Test
    public void noAvisaPorEdadAAlguienQueNoEsAdultoMayor() {
        CatalogoMedicamento m = entrada(10, "Mayorina", "mayorina", 50f, "mg");
        m.setAjustarEnMayores(true);
        PerfilClinico joven = new PerfilClinico(0f, PerfilClinico.anioParaEdad(40));

        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar(m, 50f, "mg", joven, 1).nivel);
    }

    @Test
    public void noComparaPorPesoCuandoLaUnidadNoEsMg() {
        // El rango del catálogo está en mg/kg; contra ml no significa nada.
        CatalogoMedicamento jarabe = entrada(11, "Jarabe X", "jarabe x", 10f, "ml");
        jarabe.setDosisMgKgDiaMin(10f);
        jarabe.setDosisMgKgDiaMax(20f);
        assertEquals(DosisChecker.Nivel.NINGUNO,
            DosisChecker.revisar(jarabe, 10f, "ml", DE_70_KG, 3).nivel);
    }

    // ═══ Helpers ═══════════════════════════════════════════════

    private static CatalogoMedicamento entrada(int id, String comercial,
                                               String principio, float dosis,
                                               String unidad) {
        CatalogoMedicamento m = new CatalogoMedicamento();
        m.setIdCatalogo(id);
        m.setNombreComercial(comercial);
        m.setPrincipioActivo(principio);
        m.setDosisComun(dosis);
        m.setUnidad(unidad);
        m.setActivo(true);
        return m;
    }
}
