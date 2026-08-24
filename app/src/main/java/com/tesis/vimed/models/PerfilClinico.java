package com.tesis.vimed.models;

import java.util.Calendar;

/**
 * Peso y edad del paciente, los dos datos que hacen falta para poder decir
 * algo sobre si una dosis se parece a la que le correspondería.
 *
 * Los dos son OPCIONALES. Un perfil vacío es un estado normal, no un error:
 * mucha gente no va a cargar el peso, y todo lo que depende de él
 * simplemente no se muestra. Por eso {@link #tienePeso()} y
 * {@link #tieneEdad()} existen y hay que preguntarlos antes de usar los
 * valores — un peso 0 no es "cero kilos", es "no lo sabemos".
 */
public class PerfilClinico {

    /** Peso en kilogramos, o 0 si no se cargó. */
    private final float pesoKg;

    /** Año de nacimiento, o 0 si no se cargó. */
    private final int anioNacimiento;

    /** Rango aceptado al cargar el peso; fuera de esto es un error de tipeo. */
    public static final float PESO_MIN = 20f;
    public static final float PESO_MAX = 300f;

    /** Desde esta edad se consideran las advertencias de adulto mayor. */
    public static final int EDAD_MAYOR = 65;

    public PerfilClinico(float pesoKg, int anioNacimiento) {
        this.pesoKg = pesoKg;
        this.anioNacimiento = anioNacimiento;
    }

    /** Perfil sin datos: todo lo que dependa del peso o la edad se calla. */
    public static PerfilClinico vacio() {
        return new PerfilClinico(0f, 0);
    }

    public float getPesoKg()        { return pesoKg; }
    public int   getAnioNacimiento(){ return anioNacimiento; }

    public boolean tienePeso() { return pesoKg >= PESO_MIN && pesoKg <= PESO_MAX; }
    public boolean tieneEdad() { return anioNacimiento > 1900 && edad() >= 0; }

    /**
     * Edad en años cumplidos, o -1 si no se conoce.
     *
     * Se calcula, no se guarda: una edad almacenada como número queda mal
     * al año siguiente y nadie la corrige. Es una aproximación al año —no
     * tenemos el día— y para lo que se usa acá alcanza de sobra.
     */
    public int edad() {
        if (anioNacimiento <= 1900) return -1;
        int anioActual = Calendar.getInstance().get(Calendar.YEAR);
        int e = anioActual - anioNacimiento;
        return (e >= 0 && e < 130) ? e : -1;
    }

    public boolean esAdultoMayor() {
        return tieneEdad() && edad() >= EDAD_MAYOR;
    }

    /** Año de nacimiento que corresponde a una edad dada. */
    public static int anioParaEdad(int edad) {
        return Calendar.getInstance().get(Calendar.YEAR) - edad;
    }
}
