package com.tesis.vimed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pruebas de cómo habla cada pantalla según de quién sean los datos.
 *
 * Equivocarse acá no rompe nada visible: la app funciona igual y el texto
 * se lee bien. Pero le dice a la hija que los medicamentos son suyos, y
 * eso es exactamente la confusión que el modo cuidador tiene que evitar.
 *
 * Se usa el constructor de(id, nombre), que no toca Android.
 */
public class ModoPacienteTest {

    private static ModoPaciente propio()   { return ModoPaciente.propio(); }
    private static ModoPaciente deRosa()   { return ModoPaciente.de(7, "Rosa Giménez"); }
    private static ModoPaciente sinNombre(){ return ModoPaciente.de(7, null); }

    @Test
    public void enModoPropioTutea() {
        ModoPaciente m = propio();
        assertFalse(m.esDeOtro());
        assertEquals("tenés", m.tiene());
        assertEquals("tu", m.su());
        assertEquals("tus citas", m.posesivoDe("citas"));
    }

    @Test
    public void enModoCuidadorHablaDelPaciente() {
        ModoPaciente m = deRosa();
        assertTrue(m.esDeOtro());
        assertEquals("tiene", m.tiene());
        assertEquals("su", m.su());
        assertEquals("las citas de Rosa", m.posesivoDe("citas"));
    }

    @Test
    public void laFraseAntepomeElNombreSoloEnModoCuidador() {
        assertEquals("Tenés 3 unidades", propio().frase("tenés 3 unidades"));
        assertEquals("Rosa tiene 3 unidades", deRosa().frase("tiene 3 unidades"));
    }

    @Test
    public void laFrasePropiaArrancaEnMayuscula() {
        // El llamador escribe el verbo en minúscula porque no sabe si va a
        // quedar al principio de la oración o después del nombre.
        assertEquals("No tiene citas", propio().frase("no tiene citas"));
    }

    @Test
    public void sinNombreCaeEnUnaFormaGenericaYNoEnVacio() {
        // El nombre llega por Intent y puede faltar. Sin este respaldo la
        // frase quedaría empezando con un espacio: " tiene 3 unidades".
        ModoPaciente m = sinNombre();
        assertEquals("Tu familiar", m.sujeto());
        assertEquals("Tu familiar tiene 3 unidades", m.frase("tiene 3 unidades"));
        assertEquals("las citas de tu familiar", m.posesivoDe("citas"));
    }

    @Test
    public void usaSoloElPrimerNombre() {
        // "Rosa Giménez tiene 3 unidades" es como habla un formulario, no
        // como se habla de la madre de uno.
        assertEquals("Rosa", deRosa().primerNombre());
        assertEquals("Rosa", deRosa().sujeto());
    }

    @Test
    public void unaFraseVaciaNoRompe() {
        assertEquals("", propio().frase(""));
        assertEquals("", deRosa().frase(null));
    }
}
