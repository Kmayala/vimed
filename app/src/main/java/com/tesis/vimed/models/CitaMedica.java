package com.tesis.vimed.models;

public class CitaMedica {
    private int id;
    private int idUsuario;
    private String medico;
    private String especialidad;
    private String fechaHora;
    private String lugar;
    private String notas;
    private boolean recordatorioEnviado;

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
    public int getId() { return id; }
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
}
