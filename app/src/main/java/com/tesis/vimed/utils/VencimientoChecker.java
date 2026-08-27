package com.tesis.vimed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.tesis.vimed.api.NotificacionSync;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.Notificacion;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Avisa cuando un medicamento está por vencer.
 *
 * POR QUÉ NO USA AlarmManager. Un vencimiento no tiene hora: da igual si el
 * aviso sale a las 9 o a las 14, mientras salga con dos días de anticipación.
 * Agendar una alarma exacta por medicamento gastaría uno de los pocos slots
 * que Android da por app, y los pelearía con las alarmas de las tomas, que sí
 * dependen del minuto. Así que se revisa cuando la app corre —al abrirla, vía
 * {@link AlarmaSync}, y cuando dispara cualquier alarma de medicación—, que en
 * esta app es al menos una vez por día porque las tomas suenan todos los días.
 *
 * SE AVISA UNA VEZ POR DÍA Y POR MEDICAMENTO. Sin ese freno, cada onResume
 * volvería a notificar lo mismo: la persona abre la app cuatro veces en una
 * mañana y recibe cuatro veces el mismo cartel, que es la forma más rápida de
 * enseñarle a ignorarlos.
 *
 * SE SIGUE AVISANDO DESPUÉS DE VENCIDO, no solo en la ventana de dos días.
 * Un medicamento vencido que sigue en el cajón es peor que uno que está por
 * vencer, y callarse justo cuando el problema empeora no tendría sentido.
 */
public final class VencimientoChecker {

    private static final String PREFS = "vimed_vencimientos";

    private static final SimpleDateFormat SDF_DIA =
        new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private VencimientoChecker() {}

    /** Revisa toda la lista. Los que no tienen fecha cargada se ignoran. */
    public static void revisar(Context ctx, List<Medicamento> meds) {
        if (meds == null) return;
        for (Medicamento m : meds) revisar(ctx, m);
    }

    public static void revisar(Context ctx, Medicamento med) {
        if (med == null || med.getId() <= 0 || !med.isActivo()) return;
        if (!med.venceProto()) return;
        if (yaAvisamosHoy(ctx, med.getId())) return;

        String nombre = med.getNombre() != null ? med.getNombre() : "Un medicamento";
        boolean vencido = med.estaVencido();

        String titulo = vencido
            ? "Vencido — " + nombre
            : "Por vencer — " + nombre;

        // El texto dice qué hacer, no solo qué pasa. "Vence en 2 días" sin
        // más deja a la persona sabiendo algo y sin nada que hacer con eso.
        String texto = vencido
            ? nombre + " venció el " + legible(med.getFechaVencimiento())
                + ". No conviene seguir tomándolo: conseguí un envase nuevo"
                + " y consultá en la farmacia qué hacer con el viejo."
            : nombre + " " + med.vencimientoLegible().toLowerCase(Locale.getDefault())
                + " (" + legible(med.getFechaVencimiento()) + ")."
                + " Aprovechá para comprar el reemplazo antes de que se te acabe.";

        NotificationHelper.mostrarNotificacion(ctx, titulo, texto, idNotif(med.getId()));

        // Al cuidador le llega también: es quien suele hacer la compra.
        NotificacionSync.registrar(ctx, Notificacion.TIPO_STOCK, texto, true);

        marcarAvisadoHoy(ctx, med.getId());
    }

    // ═══ Freno de una vez por día ══════════════════════════════

    private static boolean yaAvisamosHoy(Context ctx, int idMedicamento) {
        return hoy().equals(prefs(ctx).getString(clave(idMedicamento), null));
    }

    private static void marcarAvisadoHoy(Context ctx, int idMedicamento) {
        prefs(ctx).edit().putString(clave(idMedicamento), hoy()).apply();
    }

    /**
     * Borra el freno de un medicamento. Lo llama quien carga un envase
     * nuevo: si no, cambiar la fecha de vencimiento no volvería a avisar
     * hasta el día siguiente.
     */
    public static void olvidarAviso(Context ctx, int idMedicamento) {
        prefs(ctx).edit().remove(clave(idMedicamento)).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clave(int idMedicamento) { return "avisado_" + idMedicamento; }

    private static String hoy() { return SDF_DIA.format(new Date()); }

    // Rango propio, lejos del de las tomas (500_000) y del de las citas
    // (700_000), para que un aviso de vencimiento no pise una alarma.
    private static int idNotif(int idMedicamento) { return 900_000 + idMedicamento; }

    // ═══ Textos ════════════════════════════════════════════════

    /** "2026-09-14" → "14/09/2026". Devuelve el original si no se entiende. */
    private static String legible(String ymd) {
        if (ymd == null || ymd.length() < 10) return "";
        try {
            return ymd.substring(8, 10) + "/" + ymd.substring(5, 7) + "/" + ymd.substring(0, 4);
        } catch (Exception e) {
            return ymd;
        }
    }
}
