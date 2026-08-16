package com.tesis.vimed.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tesis.vimed.models.CatalogoMedicamento;

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
