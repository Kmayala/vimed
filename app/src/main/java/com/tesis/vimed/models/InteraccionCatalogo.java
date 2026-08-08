package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

public class InteraccionCatalogo {

    @SerializedName("id_interaccion_cat")
    private int idInteraccionCat;

    private int    idCatalogoA;
    private int    idCatalogoB;
    private String nivelRiesgo;   // "bajo" | "medio" | "alto"
    private String descripcion;

    public InteraccionCatalogo() {}

    public int    getIdInteraccionCat() { return idInteraccionCat; }
    public int    getIdCatalogoA()      { return idCatalogoA; }
    public int    getIdCatalogoB()      { return idCatalogoB; }
    public String getNivelRiesgo()      { return nivelRiesgo; }
    public String getDescripcion()      { return descripcion; }

    public void setIdInteraccionCat(int v) { this.idInteraccionCat = v; }
    public void setIdCatalogoA(int v)      { this.idCatalogoA = v; }
    public void setIdCatalogoB(int v)      { this.idCatalogoB = v; }
    public void setNivelRiesgo(String v)   { this.nivelRiesgo = v; }
    public void setDescripcion(String v)   { this.descripcion = v; }

    public boolean esAlto()  { return "alto".equalsIgnoreCase(nivelRiesgo); }
    public boolean esMedio() { return "medio".equalsIgnoreCase(nivelRiesgo); }
}
