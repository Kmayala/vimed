package com.tesis.vimed.api;

import android.content.Context;

import com.tesis.vimed.SessionManager;
import com.tesis.vimed.models.UsuarioSupabase;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Mantiene sincronizada la fila de public.usuarios en Supabase con
 * el usuario de auth.users.
 *
 * Por qué hace falta: Supabase Auth crea el usuario en auth.users,
 * pero las tablas de la app (medicamentos, notificaciones, etc.)
 * apuntan a public.usuarios.id_usuario. Este helper hace el puente.
 *
 * Todas las operaciones son "best effort": si fallan, la app sigue
 * funcionando con SQLite local — solo se pierde la sync a la nube.
 */
public final class PerfilSync {

    private PerfilSync() {}

    public interface Callback0 {
        /** @param idUsuarioSupabase id en public.usuarios, o -1 si no se pudo. */
        void onListo(int idUsuarioSupabase);
    }

    /**
     * Busca el perfil por auth_user_id. Si no existe, lo crea.
     * Guarda el id resultante en SessionManager.
     */
    public static void asegurarPerfil(Context ctx, String authUserId,
                                      String nombre, String correo,
                                      String rol, Callback0 cb) {
        if (authUserId == null || authUserId.isEmpty()) {
            if (cb != null) cb.onListo(-1);
            return;
        }

        SupabaseClient.getService()
            .getPerfilPorAuthId("eq." + authUserId)
            .enqueue(new Callback<List<UsuarioSupabase>>() {
                @Override
                public void onResponse(Call<List<UsuarioSupabase>> c,
                                       Response<List<UsuarioSupabase>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        // Ya existe
                        UsuarioSupabase perfil = r.body().get(0);
                        guardarId(ctx, perfil.getIdUsuario(), cb);
                    } else {
                        // No existe → crearlo
                        crearPerfil(ctx, authUserId, nombre, correo, rol, cb);
                    }
                }

                @Override
                public void onFailure(Call<List<UsuarioSupabase>> c, Throwable t) {
                    if (cb != null) cb.onListo(-1);
                }
            });
    }

    private static void crearPerfil(Context ctx, String authUserId,
                                    String nombre, String correo,
                                    String rol, Callback0 cb) {
        UsuarioSupabase nuevo = new UsuarioSupabase(authUserId, nombre, correo, rol);

        SupabaseClient.getService()
            .crearPerfil(nuevo)
            .enqueue(new Callback<List<UsuarioSupabase>>() {
                @Override
                public void onResponse(Call<List<UsuarioSupabase>> c,
                                       Response<List<UsuarioSupabase>> r) {
                    if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                        guardarId(ctx, r.body().get(0).getIdUsuario(), cb);
                    } else {
                        if (cb != null) cb.onListo(-1);
                    }
                }

                @Override
                public void onFailure(Call<List<UsuarioSupabase>> c, Throwable t) {
                    if (cb != null) cb.onListo(-1);
                }
            });
    }

    /** Actualiza el rol en la nube cuando la persona lo elige. */
    public static void actualizarRol(Context ctx, String rol) {
        SessionManager ses = new SessionManager(ctx);
        int idSupabase = ses.getSupabaseIdUsuario();
        if (idSupabase == -1) return;

        UsuarioSupabase cambios = new UsuarioSupabase();
        cambios.setRol(rol);

        SupabaseClient.getService()
            .actualizarPerfil("eq." + idSupabase, cambios)
            .enqueue(new Callback<List<UsuarioSupabase>>() {
                @Override public void onResponse(Call<List<UsuarioSupabase>> c,
                                                 Response<List<UsuarioSupabase>> r) {}
                @Override public void onFailure(Call<List<UsuarioSupabase>> c, Throwable t) {}
            });
    }

    private static void guardarId(Context ctx, Integer id, Callback0 cb) {
        int resuelto = id != null ? id : -1;
        if (resuelto != -1) {
            new SessionManager(ctx).saveSupabaseIdUsuario(resuelto);
        }
        if (cb != null) cb.onListo(resuelto);
    }
}
