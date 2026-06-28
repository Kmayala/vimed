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

    public int getIdUsuario() { return prefs.getInt(KEY_ID, -1); }
    public String getNombre() { return prefs.getString(KEY_NOMBRE, ""); }
    public String getCorreo() { return prefs.getString(KEY_CORREO, ""); }
    public String getRol() { return prefs.getString(KEY_ROL, ""); }

    public boolean esAdultoMayor() { return "adulto_mayor".equals(getRol()); }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}
