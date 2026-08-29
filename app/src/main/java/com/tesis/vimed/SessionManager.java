package com.tesis.vimed;

import android.content.Context;
import android.content.SharedPreferences;

import com.tesis.vimed.models.PerfilClinico;

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

    // Datos clínicos — copia local de public.usuarios
    private static final String KEY_PESO            = "peso_kg";
    private static final String KEY_ANIO_NACIMIENTO = "anio_nacimiento";

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

    /**
     * Corrige el nombre con el que quedó guardada la sesión.
     *
     * Lo usa la sincronización del perfil: al entrar en un celular donde no
     * está la fila local, el login inventaba un nombre a partir del correo
     * —"karenayala1711"— porque era lo único que tenía a mano. El nombre de
     * verdad, el que la persona escribió al registrarse, está en Supabase;
     * en cuanto llega, pisa al inventado.
     */
    public void actualizarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) return;
        editor.putString(KEY_NOMBRE, nombre.trim());
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

    // ═══ Datos clínicos del paciente ═══════════════════════════
    //
    // Copia local de lo que hay en public.usuarios. La fuente de verdad es
    // Supabase —el cuidador tiene que poder verlos y corregirlos—, pero el
    // chequeo de dosis corre mientras la persona carga un medicamento y no
    // puede quedarse esperando una consulta de red para decidir si muestra
    // un aviso.

    public void guardarDatosClinicos(float pesoKg, int anioNacimiento) {
        editor.putFloat(KEY_PESO, pesoKg);
        editor.putInt(KEY_ANIO_NACIMIENTO, anioNacimiento);
        editor.apply();
    }

    /** Perfil clínico cacheado. Devuelve uno vacío si nunca se cargó. */
    public PerfilClinico getPerfilClinico() {
        return new PerfilClinico(
            prefs.getFloat(KEY_PESO, 0f),
            prefs.getInt(KEY_ANIO_NACIMIENTO, 0));
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}
