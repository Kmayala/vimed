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

    /**
     * Quién la marcó como confirmada, cuando NO fue el propio paciente.
     * null = la confirmó él mismo (y también los registros viejos).
     *
     * Existe para que una dosis que el cuidador da por tomada no se
     * confunda con una que la persona confirmó apretando el botón: la
     * primera es lo que alguien CREE que pasó, la segunda es lo que la
     * persona dijo que hizo. Mezclarlas vacía de sentido el porcentaje de
     * adherencia sin que nadie se entere.
     */
    private Integer confirmadoPor;

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
    public Integer getConfirmadoPor() { return confirmadoPor; }
    public void setConfirmadoPor(Integer v) { this.confirmadoPor = v; }

    public boolean estaConfirmada() { return "confirmada".equals(estado); }
    public boolean estaOlvidada()   { return "omitida".equals(estado); }

    /** True si la confirmó otra persona (el cuidador), no el paciente. */
    public boolean laCorrigioOtro() {
        return confirmadoPor != null && confirmadoPor > 0;
    }
}
