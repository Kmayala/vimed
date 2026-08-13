package com.tesis.vimed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.tesis.vimed.models.Medicamento;

/**
 * Copia local del nombre y la dosis de cada medicamento.
 *
 * Existe por una sola razón: la alarma NO puede depender de internet.
 * Los datos viven en Supabase, pero a la hora de la toma el celular puede
 * estar sin datos, sin wifi o en modo de ahorro. Antes, si esa consulta
 * fallaba, la alarma no sonaba y encima no se reagendaba: el recordatorio
 * se perdía para siempre.
 *
 * Guardamos lo mínimo para poder mostrar la alarma sin red. El resto del
 * trabajo (registrar la toma, descontar stock) sí necesita conexión, pero
 * ocurre después y su fallo ya no impide que suene.
 */
public final class MedCache {

    private static final String PREFS = "VimedMedCache";

    private MedCache() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Texto de dosis listo para mostrar: "50 mg". */
    public static String dosisLegible(Medicamento med) {
        if (med == null) return "";
        float d = med.getDosis();
        String num = d == (int) d ? String.valueOf((int) d) : String.valueOf(d);
        String unidad = med.getUnidad() != null ? med.getUnidad() : "";
        return (num + " " + unidad).trim();
    }

    public static void guardar(Context ctx, Medicamento med) {
        if (med == null || med.getId() <= 0) return;
        prefs(ctx).edit()
            .putString(clave(med.getId(), "nombre"), med.getNombre())
            .putString(clave(med.getId(), "dosis"), dosisLegible(med))
            .putInt(clave(med.getId(), "usuario"), med.getIdUsuario())
            .apply();
    }

    /** @return nombre guardado, o null si nunca se cacheó. */
    public static String nombre(Context ctx, int idMedicamento) {
        return prefs(ctx).getString(clave(idMedicamento, "nombre"), null);
    }

    public static String dosis(Context ctx, int idMedicamento) {
        return prefs(ctx).getString(clave(idMedicamento, "dosis"), "");
    }

    /** @return id del dueño, o -1 si no se conoce. */
    public static int idUsuario(Context ctx, int idMedicamento) {
        return prefs(ctx).getInt(clave(idMedicamento, "usuario"), -1);
    }

    public static void borrar(Context ctx, int idMedicamento) {
        prefs(ctx).edit()
            .remove(clave(idMedicamento, "nombre"))
            .remove(clave(idMedicamento, "dosis"))
            .remove(clave(idMedicamento, "usuario"))
            .apply();
    }

    private static String clave(int idMedicamento, String campo) {
        return "med_" + idMedicamento + "_" + campo;
    }
}
