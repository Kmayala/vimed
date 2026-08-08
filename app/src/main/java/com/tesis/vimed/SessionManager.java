package com.tesis.vimed;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME = "VimedsSession";
    private static final String KEY_ID = "id_usuario";
    private static final String KEY_NOMBRE = "nombre";
    private static final String KEY_CORREO = "correo";
    private static final String KEY_ROL = "rol";
    private static final String KEY_LOGGED = "is_logged_in";

    // Supabase Auth
    private static final String KEY_AUTH_USER_ID    = "auth_user_id";   // UUID
    private static final String KEY_ACCESS_TOKEN    = "access_token";   // JWT
    private static final String KEY_REFRESH_TOKEN   = "refresh_token";
    private static final String KEY_TOKEN_EXPIRES   = "token_expires_at"; // epoch seconds
    /** id_usuario de public.usuarios en Supabase — NO es el mismo que KEY_ID (SQLite local). */
    private static final String KEY_SUPABASE_ID     = "supabase_id_usuario";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    /** Guarda sesión completa luego de login o registro. */
    public void saveSession(int id, String nombre, String correo, String rol) {
        editor.putInt(KEY_ID, id);
        editor.putString(KEY_NOMBRE, nombre);
        editor.putString(KEY_CORREO, correo);
        editor.putString(KEY_ROL, rol);
        editor.putBoolean(KEY_LOGGED, true);
        editor.apply();
    }

    /** Guarda los tokens devueltos por Supabase Auth. */
    public void saveAuthTokens(String authUserId, String accessToken,
                               String refreshToken, long expiresAtEpochSec) {
        editor.putString(KEY_AUTH_USER_ID,  authUserId);
        editor.putString(KEY_ACCESS_TOKEN,  accessToken);
        editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        editor.putLong  (KEY_TOKEN_EXPIRES, expiresAtEpochSec);
        editor.apply();
    }

    /** Guarda el id_usuario de public.usuarios (Supabase). */
    public void saveSupabaseIdUsuario(int id) {
        editor.putInt(KEY_SUPABASE_ID, id);
        editor.apply();
    }

    /** Guarda sesión de usuario Google (sin id de BD aún). */
    public void saveGoogleUser(String nombre, String correo) {
        editor.putString(KEY_NOMBRE, nombre);
        editor.putString(KEY_CORREO, correo);
        editor.putBoolean(KEY_LOGGED, true);
        editor.apply();
    }

    /** Guarda el rol elegido. */
    public void saveRol(String rol) {
        editor.putString(KEY_ROL, rol);
        editor.apply();
    }

    public boolean isLoggedIn() { return prefs.getBoolean(KEY_LOGGED, false); }
    public boolean hasRole() { return !getRol().isEmpty(); }

    public int    getIdUsuario()    { return prefs.getInt(KEY_ID, -1); }
    /** id_usuario en public.usuarios de Supabase (-1 si aún no se sincronizó). */
    public int    getSupabaseIdUsuario() { return prefs.getInt(KEY_SUPABASE_ID, -1); }
    public String getNombre()       { return prefs.getString(KEY_NOMBRE, ""); }
    public String getCorreo()       { return prefs.getString(KEY_CORREO, ""); }
    public String getRol()          { return prefs.getString(KEY_ROL, ""); }
    public String getAuthUserId()   { return prefs.getString(KEY_AUTH_USER_ID, null); }
    public String getAccessToken()  { return prefs.getString(KEY_ACCESS_TOKEN, null); }
    public String getRefreshToken() { return prefs.getString(KEY_REFRESH_TOKEN, null); }
    public long   getTokenExpiresAt() { return prefs.getLong(KEY_TOKEN_EXPIRES, 0); }

    /** True si tenemos un access token que aún no vence (margen de 30s). */
    public boolean hasValidAccessToken() {
        String t = getAccessToken();
        return t != null && getTokenExpiresAt() > (System.currentTimeMillis() / 1000) + 30;
    }

    public boolean esAdultoMayor() { return "adulto_mayor".equals(getRol()); }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}
