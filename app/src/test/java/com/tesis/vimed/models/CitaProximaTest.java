package com.tesis.vimed.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Qué cuenta como "próxima cita".
 *
 * El panel del cuidador listaba las cinco más viejas bajo ese título:
 * el endpoint las trae de la más antigua a la más nueva, y ahí se cortaban
 * las primeras cinco sin filtrar. Estas pruebas fijan la regla para que no
 * vuelva a depender de que cada pantalla se acuerde de aplicarla.
 */
public class CitaProximaTest {

    private static final String HOY = "2026-08-31";

    private static CitaMedica cita(String fechaHora, String estado) {
        CitaMedica c = new CitaMedica(1, "Dr. Pérez", "Cardiología",
            fechaHora, "Hospital", "");
        c.setEstado(estado);
        return c;
    }

    @Test
    public void unaCitaDeManianaEsProxima() {
        assertTrue(cita("2026-09-01 10:00", CitaMedica.ESTADO_PENDIENTE).esProxima(HOY));
    }

    @Test
    public void unaCitaDeAyerNoEsProxima() {
        assertFalse(cita("2026-08-30 10:00", CitaMedica.ESTADO_PENDIENTE).esProxima(HOY));
    }

    @Test
    public void unaCitaDeHoySiguePorVenirAunqueLaHoraYaHayaPasado() {
        // Se compara por día: a las 14, la cita de hoy a las 8 sigue siendo
        // la de hoy, y conviene tenerla a la vista para marcar si se fue.
        assertTrue(cita("2026-08-31 08:00", CitaMedica.ESTADO_PENDIENTE).esProxima(HOY));
    }

    @Test
    public void unaCitaCanceladaNoEsProximaAunqueSeaManiana() {
        assertFalse(cita("2026-09-01 10:00", CitaMedica.ESTADO_CANCELADA).esProxima(HOY));
    }

    @Test
    public void unaCitaYaAsistidaNoEsProxima() {
        assertFalse(cita("2026-08-31 08:00", CitaMedica.ESTADO_ASISTIDA).esProxima(HOY));
    }

    @Test
    public void unaCitaConfirmadaSiEsProxima() {
        // Confirmada quiere decir que sigue en pie, no que ya ocurrió.
        assertTrue(cita("2026-09-05 09:00", CitaMedica.ESTADO_CONFIRMADA).esProxima(HOY));
    }

    @Test
    public void elFormatoISODePostgresTambienFunciona() {
        // El servidor puede devolver "2026-09-01T10:00:00+00:00".
        assertTrue(cita("2026-09-01T10:00:00+00:00",
            CitaMedica.ESTADO_PENDIENTE).esProxima(HOY));
    }

    @Test
    public void unaFechaIlegibleNoSeCuelaComoProxima() {
        // Ante un dato roto, mejor no mostrarla que mostrarla mal: en la
        // tarjeta quedaría ocupando el lugar de una cita real.
        assertFalse(cita("", CitaMedica.ESTADO_PENDIENTE).esProxima(HOY));
        assertFalse(cita("2026-09-01 10:00", CitaMedica.ESTADO_PENDIENTE).esProxima(null));
    }
}
