package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

/** Fila de public.vinculacion_familiar en Supabase. */
public class Vinculacion {

    @SerializedName("id_vinculo")
    private Integer idVinculo;

    private int idAdulto;      // public.usuarios.id_usuario del adulto mayor
    private int idFamiliar;    // idem del cuidador

    /** pendiente | aceptado | rechazado */
    private String estado;

    public Vinculacion() {}

    public Vinculacion(int idAdulto, int idFamiliar) {
        this.idAdulto = idAdulto;
        this.idFamiliar = idFamiliar;
        this.estado = "aceptado";
    }

    public int getIdVinculo()  { return idVinculo != null ? idVinculo : 0; }
    public int getIdAdulto()   { return idAdulto; }
    public int getIdFamiliar() { return idFamiliar; }
    public String getEstado()  { return estado; }

    public void setIdVinculo(Integer v) { this.idVinculo = v; }
    public void setIdAdulto(int v)      { this.idAdulto = v; }
    public void setIdFamiliar(int v)    { this.idFamiliar = v; }
    public void setEstado(String v)     { this.estado = v; }
}
