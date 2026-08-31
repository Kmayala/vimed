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
    /**
     * La persona fue a la cita. Es distinto de CONFIRMADA: esa dice que la
     * cita está en pie, esta que ya ocurrió. Antes no existía y "ya fui" no
     * se podía anotar en ningún lado.
     */
    public static final String ESTADO_ASISTIDA   = "asistida";
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
    public boolean estaAsistida()  { return ESTADO_ASISTIDA.equals(getEstado()); }
    public boolean estaCancelada() { return ESTADO_CANCELADA.equals(getEstado()); }

    /** Etiqueta para mostrar en pantalla. */
    public String estadoLegible() {
        switch (getEstado()) {
            case ESTADO_CONFIRMADA: return "Confirmada";
            case ESTADO_ASISTIDA:   return "Ya asististe";
            case ESTADO_CANCELADA:  return "Cancelada";
            default:                return "Pendiente";
        }
    }

    /** Fecha "yyyy-MM-dd" tolerando ambos formatos (local e ISO de Postgres). */
    public String fechaYMD() {
        return fechaHora != null && fechaHora.length() >= 10
            ? fechaHora.substring(0, 10) : "";
    }

    /**
     * True si esta cita cuenta como "próxima": todavía no pasó el día y no
     * está resuelta.
     *
     * Vive acá y no en cada pantalla porque el panel del cuidador se
     * olvidaba de filtrar y listaba las cinco más viejas —ordenadas por
     * fecha ascendente— bajo el título "Próximas citas". Las de verdad
     * próximas quedaban fuera del corte.
     *
     * Se compara por DÍA y no por hora: una cita de hoy a las 8, mirada a
     * las 14, sigue siendo la de hoy y conviene tenerla a la vista para
     * marcar si se fue o no.
     *
     * @param hoyYMD la fecha de hoy en "yyyy-MM-dd". Entra por parámetro
     *               para poder probarlo sin depender del reloj.
     */
    public boolean esProxima(String hoyYMD) {
        String dia = fechaYMD();
        if (dia.isEmpty() || hoyYMD == null) return false;
        if (dia.compareTo(hoyYMD) < 0) return false;
        // Cancelada o ya asistida no es algo que esté por venir.
        return !estaCancelada() && !estaAsistida();
    }

    /** Hora "HH:mm" tolerando "yyyy-MM-dd HH:mm" e ISO "…THH:mm:ss+00". */
    public String horaHM() {
        return fechaHora != null && fechaHora.length() >= 16
            ? fechaHora.substring(11, 16) : "";
    }
}
