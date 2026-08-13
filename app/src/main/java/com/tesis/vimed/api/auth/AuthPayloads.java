package com.tesis.vimed.api.auth;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * DTOs para los endpoints de Supabase Auth (/auth/v1).
 * Agrupados en una sola clase para no inflar el package.
 */
public final class AuthPayloads {

    private AuthPayloads() {}

    // ── Requests ─────────────────────────────────────────────

    public static class SignUpRequest {
        public String email;
        public String password;
        /** Metadata extra que va a raw_user_meta_data en auth.users. */
        public Map<String, Object> data;

        public SignUpRequest(String email, String password, Map<String, Object> data) {
            this.email = email;
            this.password = password;
            this.data = data;
        }
    }

    public static class SignInRequest {
        public String email;
        public String password;

        public SignInRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    public static class RefreshRequest {
        @SerializedName("refresh_token")
        public String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class RecoverRequest {
        public String email;

        public RecoverRequest(String email) {
            this.email = email;
        }
    }

    /** PUT /user — cambiar contraseña del usuario autenticado. */
    public static class UpdateUserRequest {
        public String password;

        public UpdateUserRequest(String password) {
            this.password = password;
        }
    }

    // ── Responses ────────────────────────────────────────────

    /**
     * Respuesta de /token (signin/refresh) y de /signup cuando la
     * sesión arranca al momento (confirm email desactivado).
     */
    public static class AuthResponse {
        @SerializedName("access_token")  public String accessToken;
        @SerializedName("refresh_token") public String refreshToken;
        @SerializedName("token_type")    public String tokenType;
        @SerializedName("expires_in")    public long   expiresIn;     // segundos
        @SerializedName("expires_at")    public long   expiresAt;     // epoch seconds
        public AuthUser user;
    }

    /** El subobjeto user que viene en AuthResponse y otros endpoints. */
    public static class AuthUser {
        public String id;          // UUID
        public String email;
        public String role;
        @SerializedName("created_at") public String createdAt;
    }

    /** Error de Supabase Auth — varios formatos según endpoint y versión. */
    public static class AuthError {
        public String error;
        @SerializedName("error_description") public String errorDescription;
        public String msg;
        /** Formato nuevo del gateway (por ejemplo "Invalid API key"). */
        public String message;
        public String hint;
        public Integer code;

        public String mensajeUsuario() {
            if (errorDescription != null) return errorDescription;
            if (msg   != null) return msg;
            // Antes faltaba "message" y cualquier error del gateway caía al
            // texto genérico de abajo: un anon key mal cargado se veía como
            // "Error de autenticación" y parecía un problema de contraseña.
            if (message != null) {
                return hint != null ? message + " — " + hint : message;
            }
            if (error != null) return error;
            return "Error de autenticación";
        }
    }
}
