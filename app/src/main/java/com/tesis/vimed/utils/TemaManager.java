package com.tesis.vimed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Claro, oscuro o lo que diga el sistema.
 *
 * El modo se guarda como un número propio y no como la constante de
 * AppCompat: esas constantes son detalles de la librería y ya cambiaron
 * de valor entre versiones. Guardando lo nuestro, una actualización de
 * AndroidX no puede dejar a alguien con la app en un modo que no eligió.
 *
 * Se aplica en dos momentos: al arrancar el proceso (VimedApp) y cuando
 * la persona toca una opción en Configuración.
 */
public final class TemaManager {

    public static final int SISTEMA = 0;
    public static final int CLARO   = 1;
    public static final int OSCURO  = 2;

    private static final String PREFS = "vimed_tema";
    private static final String K_MODO = "modo";

    private TemaManager() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Modo elegido. Por defecto sigue al sistema. */
    public static int modoGuardado(Context ctx) {
        return prefs(ctx).getInt(K_MODO, SISTEMA);
    }

    /**
     * Guarda la elección y la aplica al instante.
     *
     * setDefaultNightMode recrea las Activity que estén abiertas, así que
     * el cambio se ve sin reiniciar la app ni volver atrás.
     */
    public static void guardarYAplicar(Context ctx, int modo) {
        prefs(ctx).edit().putInt(K_MODO, modo).apply();
        aplicar(modo);
    }

    /** Aplica el modo ya guardado. Lo llama VimedApp al arrancar. */
    public static void aplicarGuardado(Context ctx) {
        aplicar(modoGuardado(ctx));
    }

    private static void aplicar(int modo) {
        int nightMode;
        switch (modo) {
            case CLARO:  nightMode = AppCompatDelegate.MODE_NIGHT_NO;  break;
            case OSCURO: nightMode = AppCompatDelegate.MODE_NIGHT_YES; break;
            default:     nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    /** Etiqueta para mostrar en el menú de perfil. */
    public static String nombre(int modo) {
        switch (modo) {
            case CLARO:  return "Claro";
            case OSCURO: return "Oscuro";
            default:     return "Según el sistema";
        }
    }
}
