package com.tesis.vimed.utils;

import static org.junit.Assert.assertEquals;

import com.tesis.vimed.R;

import org.junit.Test;

/**
 * Qué dibujo le toca a cada presentación.
 *
 * Solo se ejercita {@link MedicamentoUI#iconoDePresentacion}: el resto de la
 * clase toca APIs de Android que en una prueba de JVM son stubs. Los
 * R.drawable son constantes enteras de compilación, así que comparar ids no
 * necesita ningún recurso cargado.
 */
public class IconoPresentacionTest {

    @Test
    public void cadaPresentacionTieneSuDibujo() {
        assertEquals(R.drawable.ic_pres_comprimido,
            MedicamentoUI.iconoDePresentacion("Comprimido"));
        assertEquals(R.drawable.ic_pres_capsula,
            MedicamentoUI.iconoDePresentacion("Cápsula"));
        assertEquals(R.drawable.ic_pres_jarabe,
            MedicamentoUI.iconoDePresentacion("Jarabe"));
        assertEquals(R.drawable.ic_pres_inyectable,
            MedicamentoUI.iconoDePresentacion("Inyectable"));
        assertEquals(R.drawable.ic_pres_gotas,
            MedicamentoUI.iconoDePresentacion("Gotas"));
        assertEquals(R.drawable.ic_pres_parche,
            MedicamentoUI.iconoDePresentacion("Parche"));
        assertEquals(R.drawable.ic_pres_inhalador,
            MedicamentoUI.iconoDePresentacion("Inhalador"));
        assertEquals(R.drawable.ic_pres_otro,
            MedicamentoUI.iconoDePresentacion("Otro"));
    }

    /**
     * El valor viene de la base y pudo haberse guardado de cualquier forma:
     * en minúscula, sin acento, con espacios de más.
     */
    @Test
    public void noDependeDeAcentosNiMayusculas() {
        int capsula = R.drawable.ic_pres_capsula;
        assertEquals(capsula, MedicamentoUI.iconoDePresentacion("capsula"));
        assertEquals(capsula, MedicamentoUI.iconoDePresentacion("CÁPSULA"));
        assertEquals(capsula, MedicamentoUI.iconoDePresentacion("  Cápsula  "));
    }

    /** Sinónimos que puede traer el catálogo o escribir la persona. */
    @Test
    public void reconoceLosSinonimosHabituales() {
        assertEquals(R.drawable.ic_pres_comprimido,
            MedicamentoUI.iconoDePresentacion("Tableta"));
        assertEquals(R.drawable.ic_pres_comprimido,
            MedicamentoUI.iconoDePresentacion("Pastilla"));
        assertEquals(R.drawable.ic_pres_jarabe,
            MedicamentoUI.iconoDePresentacion("Suspensión oral"));
        assertEquals(R.drawable.ic_pres_inyectable,
            MedicamentoUI.iconoDePresentacion("Ampolla"));
        assertEquals(R.drawable.ic_pres_inhalador,
            MedicamentoUI.iconoDePresentacion("Aerosol"));
    }

    /**
     * Lo desconocido cae en el sobrecito genérico y NUNCA en un dibujo
     * concreto: mostrar una jeringa donde hay un comprimido es peor que
     * mostrar algo neutro.
     */
    @Test
    public void loDesconocidoCaeEnElGenerico() {
        int otro = R.drawable.ic_pres_otro;
        assertEquals(otro, MedicamentoUI.iconoDePresentacion(null));
        assertEquals(otro, MedicamentoUI.iconoDePresentacion(""));
        assertEquals(otro, MedicamentoUI.iconoDePresentacion("supositorio"));
        assertEquals(otro, MedicamentoUI.iconoDePresentacion("lo que sea"));
    }
}
