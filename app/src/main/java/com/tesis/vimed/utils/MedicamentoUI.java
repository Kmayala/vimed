package com.tesis.vimed.utils;

import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Cómo se escribe un medicamento en pantalla.
 *
 * Vive acá y no en cada Activity porque lo mismo se muestra en la lista, en
 * el detalle y en el panel del cuidador. Cuando estaba duplicado, corregir
 * el texto de los horarios en un lado dejaba los otros dos mostrando la
 * versión vieja, y nadie se enteraba hasta ver las tres pantallas juntas.
 */
public final class MedicamentoUI {

    private MedicamentoUI() {}

    /** "50 mg". Sin decimales cuando es entero. */
    public static String dosis(Medicamento m) {
        if (m == null) return "";
        float d = m.getDosis();
        String num = d == (int) d ? String.valueOf((int) d) : String.valueOf(d);
        String unidad = m.getUnidad() != null ? m.getUnidad() : "";
        return (num + " " + unidad).trim();
    }

    /** El tag que guarda la base pasado a algo que se pueda leer. */
    public static String instruccion(String tag) {
        if (tag == null) return "";
        switch (tag) {
            case "despues_comer":   return "Después de comer";
            case "antes_comer":     return "Antes de comer";
            case "ayunas":          return "En ayunas";
            case "con_agua":        return "Con agua";
            case "con_leche":       return "Con leche";
            case "antes_dormir":    return "Antes de dormir";
            case "al_despertar":    return "Al despertar";
            case "sin_restriccion": return "Sin restricción";
            default:                return tag;
        }
    }

    /**
     * Todas las horas del día en que toca, no solo la de inicio.
     *
     * Antes se mostraba únicamente la primera: de un tratamiento cada 8
     * horas se veía "17:00 · Cada 8h" y las otras dos tomas quedaban
     * invisibles.
     */
    public static String horarios(List<Horario> horarios) {
        if (horarios == null || horarios.isEmpty()) return "Sin horario configurado";

        List<String> horas = new ArrayList<>();
        int intervaloMasCorto = 24;

        for (Horario h : horarios) {
            int intervalo = h.getIntervaloHoras();
            if (intervalo > 0 && intervalo < intervaloMasCorto) intervaloMasCorto = intervalo;

            for (String hora : horasDelDia(h)) {
                if (!horas.contains(hora)) horas.add(hora);
            }
        }

        if (horas.isEmpty()) return "Sin horario configurado";
        Collections.sort(horas);   // "HH:mm" ordena bien como texto

        String intervaloTxt = intervaloMasCorto == 24
            ? "Una vez al día" : "Cada " + intervaloMasCorto + " h";

        // Dos separadores distintos a propósito. Las horas son entre sí lo
        // mismo, así que van con un punto medio que las deja parejas; la
        // frecuencia NO es una hora más, y con coma o con el mismo punto se
        // leía como si lo fuera: "00:19, 06:19, 12:19, 18:19 · Cada 6h"
        // parecía una lista de cinco cosas. La raya la separa del grupo.
        return android.text.TextUtils.join("  •  ", horas) + "  —  " + intervaloTxt;
    }

    /**
     * Expande un horario en las horas concretas del día, con la misma cuenta
     * que usa {@link NotificationHelper#programarAlarmas} — así lo que se lee
     * en pantalla es exactamente cuándo va a sonar.
     */
    public static List<String> horasDelDia(Horario h) {
        List<String> out = new ArrayList<>();
        String inicio = h.getHoraInicio();
        if (inicio == null || inicio.length() < 5) return out;

        int intervalo = h.getIntervaloHoras();
        int cantidad = (intervalo > 0 && intervalo <= 24) ? 24 / intervalo : 1;

        int hora, minuto;
        try {
            hora   = Integer.parseInt(inicio.substring(0, 2));
            minuto = Integer.parseInt(inicio.substring(3, 5));
        } catch (NumberFormatException e) {
            out.add(inicio);   // dato raro: se muestra tal cual antes que nada
            return out;
        }

        for (int i = 0; i < cantidad; i++) {
            int hh = (hora + intervalo * i) % 24;
            out.add(String.format(Locale.getDefault(), "%02d:%02d", hh, minuto));
        }
        return out;
    }

    /** Cuántas veces al día se toma, según el intervalo. */
    public static int tomasPorDia(int intervaloHoras) {
        return (intervaloHoras > 0 && intervaloHoras <= 24) ? 24 / intervaloHoras : 1;
    }

    /** "Cada 8 horas" / "Una vez al día". */
    public static String frecuencia(int intervaloHoras) {
        if (intervaloHoras <= 0 || intervaloHoras >= 24) return "Una vez al día";
        return "Cada " + intervaloHoras + " horas";
    }

    /** "14/09/2026" a partir de "2026-09-14". Vacío si no se entiende. */
    public static String fechaLegible(String ymd) {
        if (ymd == null || ymd.length() < 10) return "";
        return ymd.substring(8, 10) + "/" + ymd.substring(5, 7) + "/" + ymd.substring(0, 4);
    }
}
