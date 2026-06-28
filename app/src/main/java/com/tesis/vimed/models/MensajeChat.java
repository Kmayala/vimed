package com.tesis.vimed.models;

public class MensajeChat {
    private int id;
    private int idUsuario;
    private String rol; // usuario | bot
    private String contenido;
    private String fechaHora;

    public MensajeChat() {}

    public MensajeChat(int idUsuario, String rol, String contenido) {
        this.idUsuario = idUsuario;
        this.rol = rol;
        this.contenido = contenido;
    }

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public String getFechaHora() { return fechaHora; }
    public void setFechaHora(String fechaHora) { this.fechaHora = fechaHora; }
    public boolean esDelBot() { return "bot".equals(rol); }
}
