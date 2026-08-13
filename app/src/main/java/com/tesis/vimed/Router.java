package com.tesis.vimed;

import android.content.Context;
import android.content.Intent;

/**
 * Decide a qué pantalla principal entra cada usuario según su rol.
 *
 * Hasta ahora todos los puntos de entrada (Welcome, Login, Registro,
 * RoleSelection) abrían MainActivity, así que el rol "familiar" veía
 * exactamente lo mismo que el adulto mayor. Centralizamos la decisión
 * acá para que no vuelva a divergir.
 */
public final class Router {

    private Router() {}

    /** True si la sesión actual es de un cuidador/familiar. */
    public static boolean esCuidador(Context ctx) {
        return "familiar".equals(new SessionManager(ctx).getRol());
    }

    /** Home correspondiente al rol guardado en sesión. */
    public static Class<?> homeSegunRol(Context ctx) {
        return esCuidador(ctx) ? CuidadorActivity.class : MainActivity.class;
    }

    /** Abre el home del rol limpiando el back stack. */
    public static void irAlHome(Context ctx) {
        // Todo login/registro termina acá, y recién en este punto ya existe
        // el id de Supabase: es el momento correcto para atar este celular
        // a la persona para los push.
        com.tesis.vimed.api.PushManager.registrarToken(ctx);

        Intent i = new Intent(ctx, homeSegunRol(ctx));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ctx.startActivity(i);
    }
}
