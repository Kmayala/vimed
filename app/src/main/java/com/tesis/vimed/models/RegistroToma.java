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

    /**
     * Copia del nombre y de la dosis EN EL MOMENTO de la toma.
     *
     * El historial no puede depender de la ficha del medicamento: darlo de
     * baja lo saca de todas las consultas (que filtran activo = true) y el
     * registro entero pasaba a decir "Medicamento". Y aunque no se diera de
     * baja, renombrarlo reescribiría el pasado — lo que se tomó el martes
     * no cambia porque hoy cambie la ficha.
     *
     * Es duplicar un dato a propósito. En un registro clínico lo correcto
     * es lo que era cierto cuando pasó, no lo que es cierto ahora.
     */
    private String nombreMedicamento;

    /** Dosis ya formateada ("50 mg"). Ver la nota de arriba. */
    private String dosisTexto;

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

    public String getNombreMedicamento() { return nombreMedicamento; }
    public void setNombreMedicamento(String v) { this.nombreMedicamento = v; }
    public String getDosisTexto() { return dosisTexto; }
    public void setDosisTexto(String v) { this.dosisTexto = v; }

    /**
     * Nombre para mostrar, o null si esta fila no lo tiene guardado.
     *
     * Devuelve null en vez de "Medicamento" a propósito: así quien la
     * llama puede intentar el camino viejo —buscarlo por el horario— antes
     * de rendirse. Solo las filas anteriores a esta columna que el backfill
     * no pudo resolver llegan sin nombre.
     */
    public String nombreParaMostrar() {
        return nombreMedicamento != null && !nombreMedicamento.trim().isEmpty()
            ? nombreMedicamento.trim() : null;
    }

    public boolean estaConfirmada() { return "confirmada".equals(estado); }
    public boolean estaOlvidada()   { return "omitida".equals(estado); }

    /** True si la confirmó otra persona (el cuidador), no el paciente. */
    public boolean laCorrigioOtro() {
        return confirmadoPor != null && confirmadoPor > 0;
    }
}
