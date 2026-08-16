package com.tesis.vimed.adherencia;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lo que la persona decidió sobre las sugerencias, guardado en el celular.
 *
 * Por qué local y no en Supabase: la hora ajustada sí viaja al servidor
 * (es la columna hora_inicio de horarios, la comparte el cuidador), pero
 * el resto son cosas de este aparato — cuánto insiste la alarma, cuándo se
 * preguntó por última vez— y no justifican tocar el esquema.
 *
 * La hora original SÍ importa que sobreviva: es la referencia contra la que
 * se mide cuánto se alejó el recordatorio de lo que indicó el médico.
 */
public final class AjustesAdherencia {

    private static final String PREFS = "vimed_adherencia";

    private static final String K_HORA_ORIGINAL = "hora_original_";   // + idHorario
    private static final String K_REFUERZO      = "refuerzo_";        // + idHorario
    private static final String K_SILENCIADA    = "silenciada_";      // + idHorario + tipo

    /**
     * Cuánto se espera antes de volver a ofrecer una sugerencia que la
     * persona rechazó. Dos semanas: lo suficiente para que el historial
     * cambie de verdad, y para no convertir el dashboard en una insistencia.
     */
    private static final long ESPERA_TRAS_RECHAZO_MS = 14L * 24 * 60 * 60 * 1000;

    private AjustesAdherencia() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ═══ Hora recetada originalmente ═══════════════════════════

    /**
     * Guarda la hora que tenía el recordatorio ANTES del primer ajuste.
     * No pisa un valor ya existente: la referencia es la receta, no el
     * último ajuste aceptado.
     */
    public static void recordarHoraOriginal(Context ctx, int idHorario, String horaHHMM) {
        if (horaHHMM == null) return;
        SharedPreferences p = prefs(ctx);
        String clave = K_HORA_ORIGINAL + idHorario;
        if (p.contains(clave)) return;
        p.edit().putString(clave, horaHHMM).apply();
    }

    /** Hora original, o null si este horario nunca se ajustó. */
    public static String horaOriginal(Context ctx, int idHorario) {
        return prefs(ctx).getString(K_HORA_ORIGINAL + idHorario, null);
    }

    // ═══ Refuerzo de un horario que se olvida ══════════════════

    /**
     * Marca un horario como "insistir más": la alarma suena el doble de
     * tiempo antes de posponerse sola y el aviso al familiar sale igual
     * aunque la toma se recupere después.
     */
    public static void activarRefuerzo(Context ctx, int idHorario) {
        prefs(ctx).edit().putBoolean(K_REFUERZO + idHorario, true).apply();
    }

    public static boolean tieneRefuerzo(Context ctx, int idHorario) {
        return prefs(ctx).getBoolean(K_REFUERZO + idHorario, false);
    }

    // ═══ Sugerencias rechazadas ════════════════════════════════

    /** La persona dijo "ahora no": no volver a ofrecer esto por un tiempo. */
    public static void silenciar(Context ctx, Sugerencia s) {
        prefs(ctx).edit()
            .putLong(claveSilencio(s), System.currentTimeMillis())
            .apply();
    }

    public static boolean estaSilenciada(Context ctx, Sugerencia s) {
        long cuando = prefs(ctx).getLong(claveSilencio(s), 0L);
        if (cuando == 0L) return false;
        return System.currentTimeMillis() - cuando < ESPERA_TRAS_RECHAZO_MS;
    }

    private static String claveSilencio(Sugerencia s) {
        return K_SILENCIADA + s.idHorario + "_" + s.tipo.name();
    }
}
