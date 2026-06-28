package com.tesis.vimed.models;

public class Medicamento {
    private int id;
    private int idUsuario;
    private String nombre;
    private String presentacion; // comprimido | capsula | jarabe | inyectable | gotas | parche | inhalador
    private float dosis;
    private String unidad; // mg | ml | mcg
    private String instrucciones; // antes_comer | despues_comer | ayunas | con_agua | con_leche | antes_dormir | al_despertar | sin_restriccion
    private String colorIcono;
    private int stockActual;
    private int stockMinimo;
    private boolean activo;

    public Medicamento() {}

    public Medicamento(int idUsuario, String nombre, String presentacion,
                       float dosis, String unidad, String instrucciones,
                       String colorIcono, int stockActual, int stockMinimo) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.presentacion = presentacion;
        this.dosis = dosis;
        this.unidad = unidad;
        this.instrucciones = instrucciones;
        this.colorIcono = colorIcono;
        this.stockActual = stockActual;
        this.stockMinimo = stockMinimo;
        this.activo = true;
    }

    public boolean isStockBajo() { return stockActual <= stockMinimo; }

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getPresentacion() { return presentacion; }
    public void setPresentacion(String presentacion) { this.presentacion = presentacion; }
    public float getDosis() { return dosis; }
    public void setDosis(float dosis) { this.dosis = dosis; }
    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
    public String getInstrucciones() { return instrucciones; }
    public void setInstrucciones(String instrucciones) { this.instrucciones = instrucciones; }
    public String getColorIcono() { return colorIcono; }
    public void setColorIcono(String colorIcono) { this.colorIcono = colorIcono; }
    public int getStockActual() { return stockActual; }
    public void setStockActual(int stockActual) { this.stockActual = stockActual; }
    public int getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(int stockMinimo) { this.stockMinimo = stockMinimo; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
