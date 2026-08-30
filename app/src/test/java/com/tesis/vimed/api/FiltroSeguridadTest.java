package com.tesis.vimed.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * El filtro que corre ANTES del modelo.
 *
 * Tiene dos formas de fallar y no valen lo mismo:
 *
 *   Deja pasar algo peligroso  → el modelo es la única barrera. Grave.
 *   Frena algo inofensivo      → la app contesta con un cartel una
 *                                pregunta que podía explicar. Molesto.
 *
 * Por eso hay más pruebas de la segunda clase que de la primera: atrapar
 * de más es la tentación fácil, y una app que responde con carteles deja
 * de usarse — y entonces tampoco la usan el día que sí tienen algo grave.
 */
public class FiltroSeguridadTest {

    // ═══ Urgencias ═════════════════════════════════════════════

    @Test
    public void atrapaLosSintomasQueNoPuedenEsperar() {
        for (String frase : new String[]{
            "me duele el pecho desde anoche",
            "no puedo respirar bien",
            "me falta el aire cuando camino",
            "me desmaye en el baño",
            "no siento el brazo izquierdo",
            "vomito sangre"}) {

            FiltroSeguridad.Resultado r = FiltroSeguridad.revisar(frase);
            assertEquals("no atrapó: " + frase,
                FiltroSeguridad.Motivo.URGENCIA, r.motivo);
            assertNotNull(r.respuesta);
        }
    }

    /**
     * Si alguien menciona una urgencia mientras pregunta otra cosa, lo que
     * importa es la urgencia.
     */
    @Test
    public void laUrgenciaGanaSobreLaDosis() {
        FiltroSeguridad.Resultado r = FiltroSeguridad.revisar(
            "me falta el aire, tomo el doble de la pastilla?");
        assertEquals(FiltroSeguridad.Motivo.URGENCIA, r.motivo);
    }

    // ═══ Cambios de dosis ══════════════════════════════════════

    @Test
    public void atrapaLasDecisionesSobreLaDosis() {
        for (String frase : new String[]{
            "el medico me dijo 10 pero tomo el doble",
            "voy a subir la dosis a ver si me hace mas efecto",
            "me tomo dos en vez de una",
            "estoy pensando en dejar de tomar el enalapril",
            "bajo la dosis a la mitad"}) {

            assertEquals("no atrapó: " + frase,
                FiltroSeguridad.Motivo.DOSIS, FiltroSeguridad.revisar(frase).motivo);
        }
    }

    // ═══ Lo que TIENE que pasar al modelo ══════════════════════

    /**
     * Éstas son preguntas legítimas que el modelo sabe responder. Si el
     * filtro las atrapa, la app se vuelve inútil.
     */
    @Test
    public void dejaPasarLasPreguntasGenerales() {
        for (String frase : new String[]{
            "para que sirve el enalapril",
            "que quiere decir tomarlo en ayunas",
            "se puede combinar ibuprofeno y paracetamol",
            "me olvide una toma que hago",
            "cuanto tengo que tomar de aspirina",
            "a que hora me toca el sulfato ferroso",
            "el enalapril da sueño",
            "puedo tomarlo con leche"}) {

            assertFalse("frenó de más: " + frase,
                FiltroSeguridad.revisar(frase).loRespondeLaApp());
        }
    }

    /**
     * "Me duele la cabeza" no es una urgencia, y es de las cosas que más
     * se preguntan. Atraparla sería exactamente el error de sobrefiltrar.
     */
    @Test
    public void noConfundeUnaMolestiaComunConUnaUrgencia() {
        for (String frase : new String[]{
            "me duele la cabeza",
            "me duele el estomago desde que tomo esto",
            "me duele la espalda",
            "tengo dolor de muelas"}) {

            assertFalse("frenó de más: " + frase,
                FiltroSeguridad.revisar(frase).loRespondeLaApp());
        }
    }

    /**
     * Preguntar POR la dosis no es lo mismo que decidir cambiarla. Lo
     * primero lo contesta el modelo; lo segundo lo frena la app.
     */
    @Test
    public void distinguePreguntarDeDecidir() {
        assertFalse(FiltroSeguridad.revisar(
            "cual es la dosis habitual del ibuprofeno").loRespondeLaApp());
        assertFalse(FiltroSeguridad.revisar(
            "que pasa si me olvido una dosis").loRespondeLaApp());

        assertTrue(FiltroSeguridad.revisar(
            "voy a subir la dosis").loRespondeLaApp());
    }

    // ═══ Cómo llega el texto ═══════════════════════════════════

    /**
     * El mensaje puede venir de transcribir un audio, y ahí las comas y
     * los acentos caen donde el transcriptor quiere.
     */
    @Test
    public void noSeEscapaPorLaPuntuacionNiLosAcentos() {
        for (String frase : new String[]{
            "Tomo, el doble.",
            "TOMO EL DOBLE",
            "tomó el doble",
            "me falta... el aire",
            "¿Subo la dosis?"}) {

            assertTrue("se escapó: " + frase,
                FiltroSeguridad.revisar(frase).loRespondeLaApp());
        }
    }

    @Test
    public void unMensajeVacioNoRompeNada() {
        assertFalse(FiltroSeguridad.revisar(null).loRespondeLaApp());
        assertFalse(FiltroSeguridad.revisar("").loRespondeLaApp());
        assertFalse(FiltroSeguridad.revisar("   ").loRespondeLaApp());
    }
}
