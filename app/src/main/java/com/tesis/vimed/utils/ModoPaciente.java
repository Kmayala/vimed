package com.tesis.vimed.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * "¿De quién son los datos que estoy viendo?"
 *
 * Las pantallas de medicamentos, citas y progreso nacieron para el adulto
 * mayor y consultaban siempre los datos del usuario logueado. El cuidador
 * necesita las mismas pantallas pero mirando a su familiar, así que ahora
 * pueden recibir un id por Intent y trabajar sobre ESE usuario.
 *
 * El riesgo de esto es confundirse de persona —cargarle un medicamento al
 * familiar equivocado, o creer que la lista vacía es la propia—, por eso
 * cada pantalla en modo cuidador muestra un cartel con el nombre y todos
 * los intents que salen de ahí arrastran el mismo id.
 *
 * Los nombres de los extras son los que ya usaban AgregarMedicamentoActivity
 * y AgregarCitaActivity, así que el pasamanos es directo.
 */
public final class ModoPaciente {

    public static final String EXTRA_ID_USUARIO = "para_id_usuario";
    public static final String EXTRA_NOMBRE     = "para_nombre";

    /** id_usuario del paciente, o -1 cuando la pantalla es "para mí". */
    public final int idUsuario;
    /** Nombre del paciente, vacío en modo propio. */
    public final String nombre;

    private ModoPaciente(int idUsuario, String nombre) {
        this.idUsuario = idUsuario;
        this.nombre = nombre != null ? nombre : "";
    }

    /** Lee el modo del Intent con el que se abrió la Activity. */
    public static ModoPaciente de(Activity activity) {
        Intent i = activity.getIntent();
        if (i == null) return propio();
        return new ModoPaciente(
            i.getIntExtra(EXTRA_ID_USUARIO, -1),
            i.getStringExtra(EXTRA_NOMBRE));
    }

    public static ModoPaciente propio() {
        return new ModoPaciente(-1, "");
    }

    /** Para armar el modo a mano, desde la pantalla del cuidador. */
    public static ModoPaciente de(int idUsuario, String nombre) {
        return new ModoPaciente(idUsuario, nombre);
    }

    /** True si estamos viendo a OTRA persona (rol cuidador). */
    public boolean esDeOtro() {
        return idUsuario > 0;
    }

    /** Solo el primer nombre, que es como se habla de alguien en pantalla. */
    public String primerNombre() {
        if (nombre.isEmpty()) return "";
        return nombre.split(" ")[0];
    }

    /** Cartel de contexto: "Estás viendo los medicamentos de Rosa". */
    public String cartel(String queCosa) {
        return "Estás viendo " + queCosa + " de "
            + (nombre.isEmpty() ? "tu familiar" : nombre);
    }

    // ═══ Cómo hablar en pantalla ═══════════════════════════════
    //
    // Las pantallas compartidas están escritas tuteando al adulto mayor
    // ("Tenés 3 unidades", "revisalo con tu médico"). Cuando las abre el
    // cuidador esas frases quedan hablando de él, que no es quien toma
    // nada: "tenés 3 unidades" leído por la hija dice que las unidades son
    // suyas. Estos helpers eligen la forma correcta según de quién sean los
    // datos, para no repetir el mismo ternario en cada cartel.

    /** "tenés" hablándole al paciente, "tiene" hablándole al cuidador. */
    public String tiene() {
        return esDeOtro() ? "tiene" : "tenés";
    }

    /** "tu" / "su" — como en "tu médico" o "su médico". */
    public String su() {
        return esDeOtro() ? "su" : "tu";
    }

    /** Igual que {@link #su()} pero para arrancar una oración. */
    public String Su() {
        return esDeOtro() ? "Su" : "Tu";
    }

    /** "tus citas" / "las citas de Rosa". */
    public String posesivoDe(String queCosa) {
        return esDeOtro()
            ? ("las " + queCosa + " de " + (nombre.isEmpty() ? "tu familiar" : primerNombre()))
            : ("tus " + queCosa);
    }

    /**
     * Quién es el sujeto de la frase: "Rosa" o vacío cuando se tutea.
     * Pensado para armar "Rosa tiene 3 unidades" / "Tenés 3 unidades",
     * ver {@link #frase(String)}.
     */
    public String sujeto() {
        if (!esDeOtro()) return "";
        return nombre.isEmpty() ? "Tu familiar" : primerNombre();
    }

    /**
     * Antepone el sujeto cuando hace falta y deja la primera letra en
     * mayúscula: frase("tiene 3 unidades") da "Tenés 3 unidades" para el
     * propio dueño y "Rosa tiene 3 unidades" para el cuidador.
     */
    public String frase(String resto) {
        if (resto == null || resto.isEmpty()) return "";
        if (esDeOtro()) return sujeto() + " " + resto;
        return Character.toUpperCase(resto.charAt(0)) + resto.substring(1);
    }

    /**
     * Arma un Intent que arrastra el modo. Todo lo que se abre desde una
     * pantalla en modo cuidador tiene que seguir apuntando al mismo
     * paciente; si no, el cuidador termina cargándose el medicamento a sí
     * mismo sin darse cuenta.
     */
    public Intent intent(Context ctx, Class<?> destino) {
        Intent i = new Intent(ctx, destino);
        if (esDeOtro()) {
            i.putExtra(EXTRA_ID_USUARIO, idUsuario);
            i.putExtra(EXTRA_NOMBRE, nombre);
        }
        return i;
    }
}
