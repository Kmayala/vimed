package com.tesis.vimed.models;

public class Horario {
    private int id;
    private int idMedicamento;
    private String horaInicio; // formato HH:mm
    private int intervaloHoras; // 6 | 8 | 12 | 24
    private boolean personalizado;

    public Horario() {}

    public Horario(int idMedicamento, String horaInicio, int intervaloHoras, boolean personalizado) {
        this.idMedicamento = idMedicamento;
        this.horaInicio = horaInicio;
        this.intervaloHoras = intervaloHoras;
        this.personalizado = personalizado;
    }

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getIdMedicamento() { return idMedicamento; }
    public void setIdMedicamento(int idMedicamento) { this.idMedicamento = idMedicamento; }
    public String getHoraInicio() { return horaInicio; }
    public void setHoraInicio(String horaInicio) { this.horaInicio = horaInicio; }
    public int getIntervaloHoras() { return intervaloHoras; }
    public void setIntervaloHoras(int intervaloHoras) { this.intervaloHoras = intervaloHoras; }
    public boolean isPersonalizado() { return personalizado; }
    public void setPersonalizado(boolean personalizado) { this.personalizado = personalizado; }
}
