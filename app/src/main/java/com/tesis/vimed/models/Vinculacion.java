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

    /**
     * Quién pidió el vínculo. Es lo que decide a quién le toca aceptar:
     * el que lo pidió ya dio su consentimiento al pedirlo.
     */
    @SerializedName("solicitado_por")
    private Integer solicitadoPor;

    public static final String PENDIENTE = "pendiente";
    public static final String ACEPTADO  = "aceptado";
    public static final String RECHAZADO = "rechazado";

    public Vinculacion() {}

    /**
     * Nace SIEMPRE pendiente. Antes nacía aceptado, y eso significaba que
     * cualquiera que supiera tu correo quedaba viendo tu medicación sin que
     * te enteraras.
     *
     * @param solicitadoPor quién lo está pidiendo; tiene que ser una de las
     *                      dos puntas.
     */
    public Vinculacion(int idAdulto, int idFamiliar, int solicitadoPor) {
        this.idAdulto = idAdulto;
        this.idFamiliar = idFamiliar;
        this.solicitadoPor = solicitadoPor;
        this.estado = PENDIENTE;
    }

    public int getIdVinculo()  { return idVinculo != null ? idVinculo : 0; }
    public int getIdAdulto()   { return idAdulto; }
    public int getIdFamiliar() { return idFamiliar; }
    public String getEstado()  { return estado != null ? estado : PENDIENTE; }
    public int getSolicitadoPor() { return solicitadoPor != null ? solicitadoPor : 0; }

    public void setIdVinculo(Integer v) { this.idVinculo = v; }
    public void setIdAdulto(int v)      { this.idAdulto = v; }
    public void setIdFamiliar(int v)    { this.idFamiliar = v; }
    public void setEstado(String v)     { this.estado = v; }
    public void setSolicitadoPor(Integer v) { this.solicitadoPor = v; }

    public boolean estaAceptado()  { return ACEPTADO.equals(getEstado()); }
    public boolean estaPendiente() { return PENDIENTE.equals(getEstado()); }

    /**
     * True si a {@code idUsuario} le toca responder esta solicitud: está
     * pendiente y no fue él quien la pidió.
     *
     * Los vínculos viejos no tienen solicitado_por (la columna se agregó
     * después). Como todos ellos quedaron en 'aceptado', nunca llegan acá.
     */
    public boolean esperaRespuestaDe(int idUsuario) {
        return estaPendiente() && getSolicitadoPor() != idUsuario;
    }

    /** La otra punta del vínculo, vista desde {@code idUsuario}. */
    public int laOtraPunta(int idUsuario) {
        return idAdulto == idUsuario ? idFamiliar : idAdulto;
    }
}
