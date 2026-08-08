package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de public.usuarios en Supabase.
 *
 * Distinto de {@link Usuario} (el modelo de SQLite local) porque
 * los ids no coinciden: acá id_usuario lo genera Postgres, y
 * auth_user_id es el UUID de auth.users.
 */
public class UsuarioSupabase {

    @SerializedName("id_usuario")
    private Integer idUsuario;      // Integer (no int) para omitirlo al insertar

    @SerializedName("auth_user_id")
    private String authUserId;      // UUID de auth.users

    private String nombre;
    private String correo;
    private String rol;

    public UsuarioSupabase() {}

    /** Constructor para INSERT: sin id_usuario (lo genera Postgres). */
    public UsuarioSupabase(String authUserId, String nombre, String correo, String rol) {
        this.authUserId = authUserId;
        this.nombre = nombre;
        this.correo = correo;
        this.rol = rol != null ? rol : "";
    }

    public Integer getIdUsuario()  { return idUsuario; }
    public String  getAuthUserId() { return authUserId; }
    public String  getNombre()     { return nombre; }
    public String  getCorreo()     { return correo; }
    public String  getRol()        { return rol; }

    public void setIdUsuario(Integer v)  { this.idUsuario = v; }
    public void setAuthUserId(String v)  { this.authUserId = v; }
    public void setNombre(String v)      { this.nombre = v; }
    public void setCorreo(String v)      { this.correo = v; }
    public void setRol(String v)         { this.rol = v; }
}
