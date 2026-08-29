package com.tesis.vimed.api.auth;

import android.app.Activity;
import android.content.Context;

import com.tesis.vimed.SessionManager;
import com.tesis.vimed.api.PerfilSync;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.UsuarioDAO;
import com.tesis.vimed.models.Usuario;

import retrofit2.Call;
import retrofit2.Response;

/**
 * El login con Google, de punta a punta.
 *
 * POR QUÉ ES UNA CLASE Y NO UN MÉTODO DE LA ACTIVITY. Estaba escrito
 * adentro de LoginActivity, así que el botón de la pantalla de bienvenida
 * —que es el que la gente toca primero— no tenía forma de usarlo y se había
 * quedado con un aviso de "próximamente". Copiarlo hubiera sido peor: lo que
 * pasa después del token no es un par de líneas, es crear la fila local si
 * no está, guardar la sesión, guardar los tokens y resolver el perfil de
 * Supabase. Dos copias de eso se desincronizan a la primera corrección.
 *
 * Cada pantalla se ocupa solo de lo suyo: mostrar que está cargando y
 * decidir a dónde va después.
 */
public final class SesionGoogle {

    public interface Callback {
        /**
         * Entró bien.
         *
         * @param tieneRol false si todavía no eligió si es adulto mayor o
         *                 familiar; en ese caso hay que mandarlo a elegirlo
         *                 antes que al home.
         */
        void onEntro(boolean tieneRol);

        /** Algo falló y hay que decírselo. */
        void onError(String mensaje);

        /**
         * Cerró la hoja de cuentas.
         *
         * Va aparte del error a propósito: cancelar es una decisión, no una
         * falla, y anunciarla con un cartel es retarla por haber cambiado
         * de opinión.
         */
        void onCancelado();
    }

    private SesionGoogle() {}

    public static void iniciar(Activity act, Callback cb) {
        LoginGoogle.pedirToken(act, new LoginGoogle.Callback() {
            @Override
            public void onToken(String idToken, String nonce) {
                canjearToken(act, idToken, nonce, cb);
            }

            @Override
            public void onError(String mensaje, boolean cancelado) {
                if (cancelado) cb.onCancelado();
                else           cb.onError(mensaje);
            }
        });
    }

    /** El id_token de Google se cambia por una sesión de Supabase. */
    private static void canjearToken(Activity act, String idToken, String nonce,
                                     Callback cb) {
        SupabaseAuthClient.getService()
            .signInConIdToken(new AuthPayloads.IdTokenRequest(idToken, nonce))
            .enqueue(new retrofit2.Callback<AuthPayloads.AuthResponse>() {
                @Override
                public void onResponse(Call<AuthPayloads.AuthResponse> c,
                                       Response<AuthPayloads.AuthResponse> r) {
                    AuthPayloads.AuthResponse body = r.body();
                    if (!r.isSuccessful() || body == null || body.accessToken == null) {
                        cb.onError(mensajeDeError(r));
                        return;
                    }
                    String correo = body.user != null && body.user.email != null
                        ? body.user.email : "";
                    establecerSesion(act, correo, body, cb);
                }

                @Override
                public void onFailure(Call<AuthPayloads.AuthResponse> c, Throwable t) {
                    cb.onError(VimedRepo.mensajeDeFallo(t));
                }
            });
    }

    /**
     * Deja la sesión lista para usar: fila local, sesión, tokens y perfil.
     *
     * Se espera a que el perfil resuelva antes de avisar que entró. Sin eso
     * la primera pantalla se abre con el id_usuario todavía sin resolver y
     * todo lo que consulta a Supabase responde "Sesión no sincronizada".
     */
    private static void establecerSesion(Context ctx, String correo,
                                         AuthPayloads.AuthResponse body,
                                         Callback cb) {
        UsuarioDAO dao = new UsuarioDAO(DatabaseHelper.getInstance(ctx));
        Usuario usuario = dao.buscarPorCorreo(correo);

        if (usuario == null) {
            // Primera vez en este celular. El nombre sale del correo porque
            // es lo único que hay antes de hablar con el servidor; el de
            // verdad lo corrige PerfilSync unas líneas más abajo.
            String nombre = correo.contains("@") ? correo.split("@")[0] : correo;
            long id = dao.insertar(new Usuario(nombre, correo, "", ""));
            if (id == -1) {
                cb.onError("No se pudo crear la sesión en este celular.");
                return;
            }
            usuario = dao.buscarPorId((int) id);
        }

        SessionManager ses = new SessionManager(ctx);
        ses.saveSession(usuario.getId(), usuario.getNombre(),
            usuario.getCorreo(), usuario.getRol());

        String authUserId = body.user != null ? body.user.id : null;
        ses.saveAuthTokens(authUserId, body.accessToken, body.refreshToken,
            body.expiresAt > 0
                ? body.expiresAt
                : (System.currentTimeMillis() / 1000) + body.expiresIn);

        final Usuario yaCreado = usuario;
        PerfilSync.asegurarPerfil(ctx, authUserId, yaCreado.getNombre(),
            yaCreado.getCorreo(), yaCreado.getRol(),
            idUsuario -> cb.onEntro(ses.hasRole()));
    }

    /** El error que devolvió Supabase, en castellano cuando se puede. */
    private static String mensajeDeError(Response<?> r) {
        try {
            String cuerpo = r.errorBody() != null ? r.errorBody().string() : "";
            AuthPayloads.AuthError err = new com.google.gson.Gson()
                .fromJson(cuerpo, AuthPayloads.AuthError.class);
            if (err != null && err.mensajeUsuario() != null) return err.mensajeUsuario();
        } catch (Exception ignored) { }
        return "No se pudo entrar con Google (código " + r.code() + ")";
    }
}
