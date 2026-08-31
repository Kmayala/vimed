package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

public class CitaMedica {
    /** PK en Supabase: columna id_cita. Ver nota en Medicamento. */
    @SerializedName("id_cita")
    private Integer id;

    private int idUsuario;
    private String medico;
    private String especialidad;
    private String fechaHora;
    private String lugar;
    private String notas;
    private boolean recordatorioEnviado;

    /**
     * Coordenadas del lugar, si se eligió en el mapa. 0 = no se eligió.
     *
     * Double y no double: Gson omite los null al serializar, así que una
     * cita sin ubicación no escribe un 0,0 en la base — que además cae en
     * el Golfo de Guinea y abriría el mapa ahí.
     */
    private Double latitud;
    private Double longitud;

    /** pendiente | confirmada | cancelada */
    private String estado;

    public static final String ESTADO_PENDIENTE  = "pendiente";
    public static final String ESTADO_CONFIRMADA = "confirmada";
    public static final String ESTADO_CANCELADA  = "cancelada";

    public CitaMedica() {}

    public CitaMedica(int idUsuario, String medico, String especialidad,
                      String fechaHora, String lugar, String notas) {
        this.idUsuario = idUsuario;
        this.medico = medico;
        this.especialidad = especialidad;
        this.fechaHora = fechaHora;
        this.lugar = lugar;
        this.notas = notas;
        this.recordatorioEnviado = false;
    }

    // Getters y Setters
    public int getId() { return id != null ? id : 0; }
    public void setId(int id) { this.id = id; }
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    public String getMedico() { return medico; }
    public void setMedico(String medico) { this.medico = medico; }
    public String getEspecialidad() { return especialidad; }
    public void setEspecialidad(String especialidad) { this.especialidad = especialidad; }
    public String getFechaHora() { return fechaHora; }
    public void setFechaHora(String fechaHora) { this.fechaHora = fechaHora; }
    public String getLugar() { return lugar; }
    public void setLugar(String lugar) { this.lugar = lugar; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public boolean isRecordatorioEnviado() { return recordatorioEnviado; }
    public void setRecordatorioEnviado(boolean r) { this.recordatorioEnviado = r; }
    public String getEstado() { return estado != null ? estado : ESTADO_PENDIENTE; }
    public void setEstado(String estado) { this.estado = estado; }
    public Double getLatitud()  { return latitud; }
    public Double getLongitud() { return longitud; }
    public void setLatitud(Double v)  { this.latitud = v; }
    public void setLongitud(Double v) { this.longitud = v; }

    /** True si la cita tiene un punto elegido en el mapa. */
    public boolean tieneUbicacion() {
        return latitud != null && longitud != null
            && (latitud != 0 || longitud != 0);
    }

    public boolean estaConfirmada() { return ESTADO_CONFIRMADA.equals(getEstado()); }

    /** Fecha "yyyy-MM-dd" tolerando ambos formatos (local e ISO de Postgres). */
    public String fechaYMD() {
        return fechaHora != null && fechaHora.length() >= 10
            ? fechaHora.substring(0, 10) : "";
    }

    /** Hora "HH:mm" tolerando "yyyy-MM-dd HH:mm" e ISO "…THH:mm:ss+00". */
    public String horaHM() {
        return fechaHora != null && fechaHora.length() >= 16
            ? fechaHora.substring(11, 16) : "";
    }
}
