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

    // ═══ Cuánto gasta una toma ═════════════════════════════════
    //
    // UN JARABE NO SE GASTA DE A UNO. Antes cada toma descontaba 1 del
    // stock, viniera de donde viniera: un frasco de 120 ml con una dosis
    // de 5 ml duraba 120 tomas en la cuenta de la app, cuando en la
    // realidad dura 24. La persona se quedaba sin jarabe con la pantalla
    // diciéndole que le sobraba.
    //
    // La regla sale de la UNIDAD de la dosis y no de la presentación: si
    // la dosis está en mililitros, el stock está en mililitros y la toma
    // gasta esos mililitros. Comprimidos, cápsulas y parches se cuentan
    // de a uno. Y si alguien carga un jarabe diciendo la dosis en mg
    // —"250 mg cada 5 ml"— no hay forma de saber los ml, así que se
    // cuenta de a uno: es lo mismo que hacíamos antes, y al menos no
    // inventa una conversión.

    /** Lo que se descuenta del stock al confirmar una toma. Nunca menos de 1. */
    public int consumoPorToma() {
        if (!seMideEnMl()) return 1;
        return Math.max(1, Math.round(dosis));
    }

    /** True si el stock de este medicamento se cuenta en mililitros. */
    public boolean seMideEnMl() {
        return "ml".equalsIgnoreCase(unidad) && dosis > 0;
    }

    /** "ml" o "unidades", para no decirle unidades a un jarabe. */
    public String unidadDeStock() {
        return seMideEnMl() ? "ml" : "unidades";
    }

    /** El stock con su unidad: "120 ml", "1 unidad", "30 unidades". */
    public String stockLegible() {
        if (seMideEnMl()) return stockActual + " ml";
        return stockActual + (stockActual == 1 ? " unidad" : " unidades");
    }

    /**
     * True si lo que queda no alcanza para la próxima toma.
     *
     * No es lo mismo que estar en cero: con 3 ml en el frasco y una dosis
     * de 5 ml ya no hay toma posible, y la alarma tiene que decirlo.
     */
    public boolean noAlcanzaParaUnaToma() {
        return stockActual < consumoPorToma();
    }
    public int getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(int stockMinimo) { this.stockMinimo = stockMinimo; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(String f) { this.fechaVencimiento = f; }
}
