package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

public class Medicamento {
    /**
     * PK en Supabase: columna id_medicamento.
     * Integer (no int) para que Gson lo omita al insertar y Postgres
     * genere el valor con su secuencia.
     */
    @SerializedName("id_medicamento")
    private Integer id;

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

    /**
     * Vencimiento del envase, "yyyy-MM-dd", o null si no se cargó.
     *
     * Es opcional: mucha gente no tiene la caja a mano cuando carga el
     * medicamento. Mientras sea null la app no avisa nada — no inventa una
     * fecha ni da por buena la que no tiene.
     */
    private String fechaVencimiento;

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

    // ── Vencimiento ───────────────────────────────────────────

    /** Se avisa desde acá: dos días alcanzan para llegar a la farmacia. */
    public static final int DIAS_AVISO_VENCIMIENTO = 2;

    /**
     * Días que faltan para vencer. Negativo si ya venció, 0 si vence hoy,
     * {@link Integer#MAX_VALUE} si no hay fecha cargada.
     *
     * El MAX_VALUE no es un truco: hace que "no sé cuándo vence" nunca
     * dispare un aviso, sin obligar a preguntar por null en cada llamada.
     */
    public int diasParaVencer() {
        if (fechaVencimiento == null || fechaVencimiento.length() < 10) {
            return Integer.MAX_VALUE;
        }
        try {
            java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.util.Calendar vence = java.util.Calendar.getInstance();
            vence.setTime(f.parse(fechaVencimiento.substring(0, 10)));
            aMedianoche(vence);

            java.util.Calendar hoy = java.util.Calendar.getInstance();
            aMedianoche(hoy);

            // Se resta en días enteros y no en milisegundos: entre dos
            // fechas puede haber un cambio de horario de verano, y ahí la
            // división por 86.400.000 devuelve 1,96 días y se pierde uno.
            long ms = vence.getTimeInMillis() - hoy.getTimeInMillis();
            return (int) Math.round(ms / 86_400_000d);
        } catch (Exception e) {
            return Integer.MAX_VALUE;   // fecha ilegible: mejor callarse
        }
    }

    private static void aMedianoche(java.util.Calendar c) {
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
    }

    public boolean estaVencido() { return diasParaVencer() < 0; }

    /** True desde dos días antes, y sigue siendo true una vez vencido. */
    public boolean venceProto() {
        return diasParaVencer() <= DIAS_AVISO_VENCIMIENTO;
    }

    /** "Vence mañana", "Vencido hace 3 días"... o "" si no hay fecha. */
    public String vencimientoLegible() {
        int d = diasParaVencer();
        if (d == Integer.MAX_VALUE) return "";
        if (d < -1)  return "Vencido hace " + (-d) + " días";
        if (d == -1) return "Vencido ayer";
        if (d == 0)  return "Vence hoy";
        if (d == 1)  return "Vence mañana";
        return "Vence en " + d + " días";
    }

    // Getters y Setters
    public int getId() { return id != null ? id : 0; }
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
    public String getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(String f) { this.fechaVencimiento = f; }
}
