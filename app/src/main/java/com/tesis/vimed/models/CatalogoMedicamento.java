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

    // ── Referencia de dosificación por peso ───────────────────
    // Nacen en 0 / false porque la columna es NULL para casi todo el
    // catálogo: cargarlas necesita una ficha técnica. Mientras valgan 0,
    // DosisChecker no dice nada sobre el peso, que es lo correcto.

    /** Rango habitual en mg por kilo y por día. 0 = sin dato. */
    private float dosisMgKgDiaMin;
    private float dosisMgKgDiaMax;

    /** Techo diario absoluto, independiente del peso. 0 = sin dato. */
    private float dosisMaxDia;

    /** El medicamento suele indicarse a dosis menores en adultos mayores. */
    private boolean ajustarEnMayores;

    /** Frase para mostrarle al paciente cuando aplica lo anterior. */
    private String notaMayores;

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

    public float   getDosisMgKgDiaMin()  { return dosisMgKgDiaMin; }
    public float   getDosisMgKgDiaMax()  { return dosisMgKgDiaMax; }
    public float   getDosisMaxDia()      { return dosisMaxDia; }
    public boolean isAjustarEnMayores()  { return ajustarEnMayores; }
    public String  getNotaMayores()      { return notaMayores; }

    public void setDosisMgKgDiaMin(float v)  { this.dosisMgKgDiaMin = v; }
    public void setDosisMgKgDiaMax(float v)  { this.dosisMgKgDiaMax = v; }
    public void setDosisMaxDia(float v)      { this.dosisMaxDia = v; }
    public void setAjustarEnMayores(boolean v) { this.ajustarEnMayores = v; }
    public void setNotaMayores(String v)     { this.notaMayores = v; }

    /** True si esta entrada tiene con qué calcular una referencia por peso. */
    public boolean tieneReferenciaPorPeso() {
        return dosisMgKgDiaMin > 0 && dosisMgKgDiaMax > 0;
    }

    @Override
    public String toString() {
        // Para mostrar directo en un Spinner/AutoComplete
        return nombreComercial + " — " + principioActivo;
    }
}
