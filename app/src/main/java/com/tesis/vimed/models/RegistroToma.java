package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

public class RegistroToma {
    /** PK en Supabase: columna id_registro. Ver nota en Medicamento. */
    @SerializedName("id_registro")
    private Integer id;

    private int idHorario;
    private int idUsuario;
    private String fechaHoraProgramada;
    private String fechaHoraConfirmacion;
    private String estado; // confirmada | pospuesta | omitida

    public RegistroToma() {}

    public RegistroToma(int idHorario, int idUsuario, String fechaHoraProgramada) {
        this.idHorario = idHorario;
        this.idUsuario = idUsuario;
        this.fechaHoraProgramada = fechaHoraProgramada;
        this.estado = "omitida";
    }

    // Getters y Setters
    public int getId() { return id != null ? id : 0; }
    public void setId(int id) { this.id = id; }
    public int getIdHorario() { return idHorario; }
    public void setIdHorario(int idHorario) { this.idHorario = idHorario; }
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    public String getFechaHoraProgramada() { return fechaHoraProgramada; }
    public void setFechaHoraProgramada(String f) { this.fechaHoraProgramada = f; }
    public String getFechaHoraConfirmacion() { return fechaHoraConfirmacion; }
    public void setFechaHoraConfirmacion(String f) { this.fechaHoraConfirmacion = f; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
