package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de public.notificaciones en Supabase.
 * Sirve para que el familiar vinculado vea el historial de avisos
 * del adulto mayor desde la nube.
 */
public class Notificacion {

    // Valores válidos del CHECK en la tabla
    public static final String TIPO_TOMA        = "toma";
    public static final String TIPO_STOCK       = "stock";
    public static final String TIPO_INTERACCION = "interaccion";
    public static final String TIPO_CITA        = "cita";

    @SerializedName("id_notificacion")
    private Integer idNotificacion;   // Integer para omitirlo al insertar

    @SerializedName("id_destinatario")
    private Integer idDestinatario;   // FK a public.usuarios.id_usuario

    @SerializedName("id_registro")
    private Integer idRegistro;       // FK opcional a registro_tomas

    private String  tipo;
    private String  mensaje;
    private Boolean enviada;

    @SerializedName("fecha_envio")
    private String fechaEnvio;

    public Notificacion() {}

    /** Constructor para INSERT. */
    public Notificacion(int idDestinatario, String tipo, String mensaje) {
        this.idDestinatario = idDestinatario;
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.enviada = true;
    }

    public Integer getIdNotificacion() { return idNotificacion; }
    public Integer getIdDestinatario() { return idDestinatario; }
    public Integer getIdRegistro()     { return idRegistro; }
    public String  getTipo()           { return tipo; }
    public String  getMensaje()        { return mensaje; }
    public Boolean getEnviada()        { return enviada; }
    public String  getFechaEnvio()     { return fechaEnvio; }

    public void setIdNotificacion(Integer v) { this.idNotificacion = v; }
    public void setIdDestinatario(Integer v) { this.idDestinatario = v; }
    public void setIdRegistro(Integer v)     { this.idRegistro = v; }
    public void setTipo(String v)            { this.tipo = v; }
    public void setMensaje(String v)         { this.mensaje = v; }
    public void setEnviada(Boolean v)        { this.enviada = v; }
    public void setFechaEnvio(String v)      { this.fechaEnvio = v; }
}
