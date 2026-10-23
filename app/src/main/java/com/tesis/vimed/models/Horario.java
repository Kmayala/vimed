package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

public class Horario {
    /** PK en Supabase: columna id_horario. Ver nota en Medicamento. */
    @SerializedName("id_horario")
    private Integer id;

    private int idMedicamento;
    private String horaInicio; // formato HH:mm
    private int intervaloHoras; // 6 | 8 | 12 | 24
    private boolean personalizado;

    /** Máscara de 7 caracteres 0/1 desde el domingo. null = todos los días. */
    @SerializedName("dias_semana")
    private String diasSemana;

    public Horario() {}

    public Horario(int idMedicamento, String horaInicio, int intervaloHoras, boolean personalizado) {
        this.idMedicamento = idMedicamento;
        this.horaInicio = horaInicio;
        this.intervaloHoras = intervaloHoras;
        this.personalizado = personalizado;
    }

    // Getters y Setters
    public int getId() { return id != null ? id : 0; }
    public void setId(int id) { this.id = id; }
    public int getIdMedicamento() { return idMedicamento; }
    public void setIdMedicamento(int idMedicamento) { this.idMedicamento = idMedicamento; }
    public String getHoraInicio() { return horaInicio; }
    public void setHoraInicio(String horaInicio) { this.horaInicio = horaInicio; }
    public int getIntervaloHoras() { return intervaloHoras; }
    public void setIntervaloHoras(int intervaloHoras) { this.intervaloHoras = intervaloHoras; }
    public boolean isPersonalizado() { return personalizado; }
    public void setPersonalizado(boolean personalizado) { this.personalizado = personalizado; }

    // ═══ Días de la semana ═════════════════════════════════════
    //
    // Siete caracteres 0/1, uno por día, EMPEZANDO POR EL DOMINGO para
    // que el índice coincida con Calendar.DAY_OF_WEEK de Android
    // (1=domingo … 7=sábado) y no haya que convertir nada en el medio.
    // Convertir índices entre dos convenciones es de los errores más
    // fáciles de cometer y más difíciles de ver.
    //
    //     "0000001"  → solo sábados
    //     "0100100"  → lunes y jueves
    //     null       → todos los días
    //
    // Hasta ahora esto no existía: intervalo_horas llega como mucho a 24,
    // así que lo más espaciado que se podía pedir era una vez por día. Un
    // tratamiento semanal no se podía cargar.

    public String getDiasSemana() { return diasSemana; }

    public void setDiasSemana(String diasSemana) {
        this.diasSemana = (diasSemana != null && diasSemana.matches("[01]{7}")
            && !diasSemana.equals("0000000")) ? diasSemana : null;
    }

    /** True si no se eligieron días: suena todos. */
    public boolean esTodosLosDias() {
        return diasSemana == null;
    }

    /**
     * @param diaDeLaSemana el de Calendar.DAY_OF_WEEK (1=domingo … 7=sábado).
     * @return true si a este horario le toca sonar ese día.
     */
    public boolean aplicaEl(int diaDeLaSemana) {
        if (esTodosLosDias()) return true;
        if (diaDeLaSemana < 1 || diaDeLaSemana > 7) return true;
        return diasSemana.charAt(diaDeLaSemana - 1) == '1';
    }

    private static final String[] NOMBRES = {
        "domingos", "lunes", "martes", "miércoles",
        "jueves", "viernes", "sábados"
    };

    /** "Todos los días", "Solo los sábados", "Lunes y jueves". */
    public String diasLegibles() {
        if (esTodosLosDias()) return "Todos los días";

        java.util.List<String> elegidos = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            if (diasSemana.charAt(i) == '1') elegidos.add(NOMBRES[i]);
        }
        if (elegidos.isEmpty()) return "Todos los días";
        if (elegidos.size() == 7) return "Todos los días";
        if (elegidos.size() == 1) {
            String uno = elegidos.get(0);
            return "Solo los " + uno;
        }
        // A mano y sin TextUtils: esta clase es un modelo, y depender de
        // Android para juntar tres strings la vuelve imposible de probar
        // en una JVM común —TextUtils llega vacío ahí—.
        String ultimo = elegidos.remove(elegidos.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < elegidos.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(elegidos.get(i));
        }
        String texto = sb + " y " + ultimo;
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }
}
