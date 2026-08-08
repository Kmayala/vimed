package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

/**
 * Entrada del catálogo de medicamentos (tabla pública de referencia).
 *
 * NO confundir con {@link Medicamento}, que es el medicamento que un
 * usuario específico tiene cargado en su tratamiento.
 */
public class CatalogoMedicamento {

    @SerializedName("id_catalogo")
    private int idCatalogo;

    private String nombreComercial;
    private String principioActivo;
    private String presentacion;
    private float  dosisComun;
    private String unidad;
    private String categoria;
    private String instrucciones;
    private boolean requiereReceta;
    private boolean activo;

    public CatalogoMedicamento() {}

    public int     getIdCatalogo()       { return idCatalogo; }
    public String  getNombreComercial()  { return nombreComercial; }
    public String  getPrincipioActivo()  { return principioActivo; }
    public String  getPresentacion()     { return presentacion; }
    public float   getDosisComun()       { return dosisComun; }
    public String  getUnidad()           { return unidad; }
    public String  getCategoria()        { return categoria; }
    public String  getInstrucciones()    { return instrucciones; }
    public boolean isRequiereReceta()    { return requiereReceta; }
    public boolean isActivo()            { return activo; }

    public void setIdCatalogo(int v)      { this.idCatalogo = v; }
    public void setNombreComercial(String v) { this.nombreComercial = v; }
    public void setPrincipioActivo(String v) { this.principioActivo = v; }
    public void setPresentacion(String v) { this.presentacion = v; }
    public void setDosisComun(float v)    { this.dosisComun = v; }
    public void setUnidad(String v)       { this.unidad = v; }
    public void setCategoria(String v)    { this.categoria = v; }
    public void setInstrucciones(String v){ this.instrucciones = v; }
    public void setRequiereReceta(boolean v) { this.requiereReceta = v; }
    public void setActivo(boolean v)      { this.activo = v; }

    @Override
    public String toString() {
        // Para mostrar directo en un Spinner/AutoComplete
        return nombreComercial + " — " + principioActivo;
    }
}
