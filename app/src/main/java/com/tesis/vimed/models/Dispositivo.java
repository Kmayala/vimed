package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

/**
 * Token de push (FCM) de un celular concreto.
 *
 * Una persona puede tener varios: el mismo cuidador puede entrar desde el
 * teléfono y una tablet, y el aviso tiene que llegar a los dos. Por eso la
 * clave única en Supabase es el token, no el id_usuario.
 */
public class Dispositivo {

    /** PK en Supabase: columna id_dispositivo. Ver nota en Medicamento. */
    @SerializedName("id_dispositivo")
    private Integer id;

    private int idUsuario;
    private String token;
    private String plataforma;   // "android"

    public Dispositivo() {}

    public Dispositivo(int idUsuario, String token) {
        this.idUsuario = idUsuario;
        this.token = token;
        this.plataforma = "android";
    }

    public int getId() { return id != null ? id : 0; }
    public void setId(int id) { this.id = id; }
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPlataforma() { return plataforma; }
    public void setPlataforma(String plataforma) { this.plataforma = plataforma; }
}
