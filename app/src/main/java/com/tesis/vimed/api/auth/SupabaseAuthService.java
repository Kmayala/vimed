package com.tesis.vimed.api.auth;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

/**
 * Endpoints REST de Supabase Auth (base: /auth/v1/).
 *
 * Notas:
 *   - apikey va inyectada por el interceptor en SupabaseAuthClient.
 *   - Authorization Bearer <access_token> SOLO va en endpoints
 *     que actúan sobre el usuario logueado (updateUser). Se pasa
 *     explícito vía @Header para no leakear tokens entre sesiones.
 */
public interface SupabaseAuthService {

    /** Registrar usuario nuevo. */
    @POST("signup")
    Call<AuthPayloads.AuthResponse> signUp(@Body AuthPayloads.SignUpRequest body);

    /** Login con email+password. */
    @POST("token?grant_type=password")
    Call<AuthPayloads.AuthResponse> signIn(@Body AuthPayloads.SignInRequest body);

    /** Login con Google: se canjea el id_token por una sesión de Supabase. */
    @POST("token?grant_type=id_token")
    Call<AuthPayloads.AuthResponse> signInConIdToken(@Body AuthPayloads.IdTokenRequest body);

    /** Renovar tokens cuando el access_token venció. */
    @POST("token?grant_type=refresh_token")
    Call<AuthPayloads.AuthResponse> refresh(@Body AuthPayloads.RefreshRequest body);

    /**
     * Enviar email de recuperación. La URL del botón en el email
     * se construye usando el redirect_to (debe estar en la lista
     * de Redirect URLs del Dashboard).
     */
    @POST("recover")
    Call<Void> recoverPassword(
        @Body AuthPayloads.RecoverRequest body,
        @Query("redirect_to") String redirectTo
    );

    /**
     * Cambiar la contraseña del usuario autenticado.
     * Requiere el access_token recibido al volver del deep link.
     */
    @PUT("user")
    Call<AuthPayloads.AuthUser> updatePassword(
        @Header("Authorization") String bearerAccessToken,
        @Body AuthPayloads.UpdateUserRequest body
    );
}
