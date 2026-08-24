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
            .putInt(clave(med.getId(), "stock"), med.getStockActual())
            .apply();
    }

    /**
     * Deja el stock al día sin tocar el resto. Lo llama la confirmación de
     * toma después de descontar la unidad: la alarma de mañana decide si
     * suena mirando ESTE número, y si la caché quedara vieja seguiría
     * despertando a alguien por un frasco vacío.
     */
    public static void guardarStock(Context ctx, int idMedicamento, int stock) {
        if (idMedicamento <= 0) return;
        prefs(ctx).edit().putInt(clave(idMedicamento, "stock"), stock).apply();
    }

    /**
     * Stock conocido, o -1 si nunca se cacheó.
     *
     * El -1 no es "cero": significa "no sé". Ante la duda la alarma tiene
     * que sonar — dejar de avisar una medicación por un dato que no
     * tenemos es mucho peor que avisar de más.
     */
    public static int stock(Context ctx, int idMedicamento) {
        return prefs(ctx).getInt(clave(idMedicamento, "stock"), -1);
    }

    /** True solo si sabemos con certeza que no queda nada. */
    public static boolean sinStock(Context ctx, int idMedicamento) {
        return stock(ctx, idMedicamento) == 0;
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
            .remove(clave(idMedicamento, "stock"))
            .apply();
    }

    private static String clave(int idMedicamento, String campo) {
        return "med_" + idMedicamento + "_" + campo;
    }
}
