package com.tesis.vimed;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.PerfilClinico;
import com.tesis.vimed.utils.ModoPaciente;
import com.tesis.vimed.utils.PermisosAlarma;
import com.tesis.vimed.utils.TemaManager;

import java.util.List;

/**
 * Configuración de la app. Por ahora, la apariencia.
 *
 * Tres opciones y no un interruptor de dos posiciones: con un switch no
 * hay forma de decir "seguí a mi celular", que es lo que la mayoría de
 * la gente quiere y además es el estado inicial.
 */
public class ConfiguracionActivity extends AppCompatActivity {

    /**
     * De quién son los datos clínicos de esta pantalla.
     *
     * El tema es una preferencia del aparato y siempre es de quien está
     * usándolo. El peso y la edad NO: son de quien toma la medicación. Al
     * cuidador se le preguntaba "¿cuánto pesás?" y se guardaban sus kilos
     * en su propia fila, que no lee nadie —el chequeo de dosis usa los del
     * paciente—, así que lo que cargaba uno no aparecía nunca del otro
     * lado. Ahora los dos editan la MISMA fila.
     */
    private ModoPaciente modo = ModoPaciente.propio();

    /** Perfil clínico de quien se está mirando, cuando no es el propio. */
    private PerfilClinico delPaciente = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuracion);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        modo = ModoPaciente.de(this);

        findViewById(R.id.opt_peso).setOnClickListener(v -> pedirPeso());
        findViewById(R.id.opt_edad).setOnClickListener(v -> pedirEdad());
        adaptarSeccionClinica();
        pintarDatosClinicos();
        cargarDatosClinicos();

        findViewById(R.id.opt_sistema).setOnClickListener(v -> elegir(TemaManager.SISTEMA));
        findViewById(R.id.opt_claro).setOnClickListener(v   -> elegir(TemaManager.CLARO));
        findViewById(R.id.opt_oscuro).setOnClickListener(v  -> elegir(TemaManager.OSCURO));

        pintarElegido(TemaManager.modoGuardado(this));

        ((TextView) findViewById(R.id.tv_version))
            .setText("Vimed " + BuildConfig.VERSION_NAME);
    }

    /**
     * Al volver de los ajustes del sistema hay que repintar: la persona
     * acaba de conceder (o no) el permiso, y la lista tiene que reflejarlo
     * sin que tenga que salir y entrar de nuevo.
     */
    @Override
    protected void onResume() {
        super.onResume();
        pintarRevisionDeAlarma();
    }

    // ═══════════════════════════════════════════════════════════
    //  Datos del paciente (peso y edad)
    // ═══════════════════════════════════════════════════════════

    /**
     * Ajusta la sección a de quién son los datos.
     *
     * Sin paciente vinculado la sección desaparece: los datos clínicos del
     * cuidador no los usa nadie, y pedirle el peso a alguien para no
     * hacer nada con él es juntar información de salud porque sí.
     */
    private void adaptarSeccionClinica() {
        View seccion = findViewById(R.id.seccion_clinicos);

        if (!modo.esDeOtro()) {
            if (esCuidador()) seccion.setVisibility(View.GONE);
            return;
        }

        seccion.setVisibility(View.VISIBLE);
        String quien = modo.primerNombre();

        ((TextView) findViewById(R.id.tv_clinicos_titulo))
            .setText("Datos de " + quien);
        ((TextView) findViewById(R.id.tv_clinicos_subtitulo)).setText(
            "Con el peso y la edad de " + quien + ", la app puede avisarte"
                + " si una dosis que cargás se aleja de la habitual para"
                + " alguien como " + quien + ".");
        ((TextView) findViewById(R.id.tv_clinicos_nota)).setText(
            "Vimed nunca indica ni cambia una dosis. Solo compara lo que"
                + " cargaste contra la referencia del medicamento y te dice"
                + " si conviene revisarlo con su médico.");
    }

    private boolean esCuidador() {
        return !new SessionManager(this).esAdultoMayor();
    }

    /** El perfil que hay que mostrar y editar en esta pantalla. */
    private PerfilClinico perfilEnPantalla() {
        if (modo.esDeOtro()) {
            return delPaciente != null ? delPaciente : PerfilClinico.vacio();
        }
        return new SessionManager(this).getPerfilClinico();
    }

    /**
     * Pinta lo que hay guardado localmente. Corre antes de la consulta de
     * red para que la pantalla nunca aparezca en blanco: si el celular está
     * sin datos, se muestra lo último que sabemos en vez de nada.
     */
    private void pintarDatosClinicos() {
        PerfilClinico p = perfilEnPantalla();

        ((TextView) findViewById(R.id.tv_peso_valor)).setText(
            p.tienePeso() ? formatearPeso(p.getPesoKg()) + " kg" : "Tocá para cargarlo");

        ((TextView) findViewById(R.id.tv_edad_valor)).setText(
            p.tieneEdad() ? p.edad() + " años" : "Tocá para cargarla");
    }

    private void cargarDatosClinicos() {
        VimedRepo.cargarDatosClinicos(this, modo.idUsuario,
            new VimedRepo.Cb<com.tesis.vimed.models.UsuarioSupabase>() {
                @Override public void onOk(com.tesis.vimed.models.UsuarioSupabase perfil) {
                    // El propio ya lo dejó en la sesión el repositorio; el
                    // del paciente NO se cachea —pisaría el del cuidador—,
                    // así que se guarda solo mientras dure la pantalla.
                    if (modo.esDeOtro() && perfil != null) {
                        delPaciente = perfil.perfilClinico();
                    }
                    pintarDatosClinicos();
                }
                @Override public void onError(String msg) {
                    // Sin red nos quedamos con lo local; no vale un cartel.
                }
            });
    }

    private void pedirPeso() {
        PerfilClinico actual = perfilEnPantalla();

        final EditText input = campoNumerico(
            actual.tienePeso() ? formatearPeso(actual.getPesoKg()) : "");
        // decimal: hay pesos como 62,5 y redondear a mano es una fricción
        // innecesaria en un dato que después se multiplica.
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Kilos");

        new AlertDialog.Builder(this)
            .setTitle(modo.esDeOtro()
                ? "¿Cuánto pesa " + modo.primerNombre() + "?"
                : "¿Cuánto pesás?")
            .setMessage(modo.esDeOtro()
                ? "Sirve para revisar si las dosis que le cargás son las"
                    + " habituales para su peso. Podés dejarlo vacío."
                : "Sirve para revisar si las dosis que cargás son las"
                    + " habituales para tu peso. Podés dejarlo vacío.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (d, w) -> {
                String txt = input.getText().toString().trim().replace(',', '.');
                if (txt.isEmpty()) { guardar(0f, actual.getAnioNacimiento()); return; }

                float peso;
                try {
                    peso = Float.parseFloat(txt);
                } catch (NumberFormatException e) {
                    avisar("Ese peso no se entiende. Escribí solo el número.");
                    return;
                }
                // El rango ataja el error de tipeo más común —el punto
                // decimal de más, "625" en vez de "62,5"— antes de que ese
                // número entre en una cuenta de dosis.
                if (peso < PerfilClinico.PESO_MIN || peso > PerfilClinico.PESO_MAX) {
                    avisar("El peso tiene que estar entre "
                        + (int) PerfilClinico.PESO_MIN + " y "
                        + (int) PerfilClinico.PESO_MAX + " kg. Revisá lo que escribiste.");
                    return;
                }
                guardar(peso, actual.getAnioNacimiento());
            })
            .show();
    }

    private void pedirEdad() {
        PerfilClinico actual = perfilEnPantalla();

        final EditText input = campoNumerico(
            actual.tieneEdad() ? String.valueOf(actual.edad()) : "");
        input.setHint("Años");

        new AlertDialog.Builder(this)
            .setTitle(modo.esDeOtro()
                ? "¿Cuántos años tiene " + modo.primerNombre() + "?"
                : "¿Cuántos años tenés?")
            .setMessage("Algunos medicamentos se indican en dosis más bajas"
                + " después de los " + PerfilClinico.EDAD_MAYOR
                + ". Podés dejarlo vacío.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (d, w) -> {
                String txt = input.getText().toString().trim();
                if (txt.isEmpty()) { guardar(actual.getPesoKg(), 0); return; }

                int edad;
                try {
                    edad = Integer.parseInt(txt);
                } catch (NumberFormatException e) {
                    avisar("Esa edad no se entiende. Escribí solo el número.");
                    return;
                }
                if (edad < 0 || edad > 120) {
                    avisar("Revisá la edad: tiene que estar entre 0 y 120.");
                    return;
                }
                // Se guarda el AÑO DE NACIMIENTO, no la edad: una edad
                // guardada como número queda mal al año siguiente y nadie
                // la corrige. Escribir la edad es más fácil; convertirla es
                // trabajo nuestro.
                guardar(actual.getPesoKg(), PerfilClinico.anioParaEdad(edad));
            })
            .show();
    }

    private void guardar(float pesoKg, int anioNacimiento) {
        VimedRepo.guardarDatosClinicos(this, modo.idUsuario, pesoKg, anioNacimiento,
            new VimedRepo.Cb<Void>() {
                @Override public void onOk(Void v) {
                    // El propio lo refresca el repositorio en la sesión; el
                    // del paciente vive solo en esta pantalla, así que se
                    // actualiza a mano para que el cambio se vea al toque.
                    if (modo.esDeOtro()) {
                        delPaciente = new PerfilClinico(pesoKg, anioNacimiento);
                    }
                    pintarDatosClinicos();
                    Toast.makeText(ConfiguracionActivity.this,
                        "Guardado ✓", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String msg) {
                    // No se pinta nada: la sesión local solo se actualiza
                    // cuando el servidor confirma, así que mostrar el valor
                    // nuevo acá sería mentirle a la persona.
                    Toast.makeText(ConfiguracionActivity.this,
                        "No se pudo guardar: " + msg, Toast.LENGTH_LONG).show();
                }
            });
    }

    private EditText campoNumerico(String valorInicial) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(valorInicial);
        input.setSelection(valorInicial.length());
        input.setTextSize(19);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        return input;
    }

    private void avisar(String mensaje) {
        new AlertDialog.Builder(this)
            .setMessage(mensaje)
            .setPositiveButton("Entendido", null)
            .show();
    }

    /** "62" en vez de "62.0"; "62,5" cuando el decimal importa. */
    private String formatearPeso(float kg) {
        if (kg == Math.round(kg)) return String.valueOf(Math.round(kg));
        return String.format(java.util.Locale.getDefault(), "%.1f", kg);
    }

    private void pintarRevisionDeAlarma() {
        LinearLayout cont = findViewById(R.id.permisos_container);
        TextView resumen = findViewById(R.id.tv_alarma_resumen);
        if (cont == null) return;
        cont.removeAllViews();

        List<PermisosAlarma.Requisito> requisitos = PermisosAlarma.revisar(this);
        int faltan = PermisosAlarma.faltantesComprobables(this);

        resumen.setText(faltan == 0
            ? "Todo lo que podemos comprobar está en orden."
            : (faltan == 1
                ? "Falta 1 permiso para que la alarma suene siempre."
                : "Faltan " + faltan + " permisos para que la alarma suene siempre."));

        LayoutInflater inflater = LayoutInflater.from(this);
        for (PermisosAlarma.Requisito r : requisitos) {
            View fila = inflater.inflate(R.layout.item_permiso_alarma, cont, false);

            ((TextView) fila.findViewById(R.id.tv_titulo)).setText(r.titulo);
            ((TextView) fila.findViewById(R.id.tv_detalle)).setText(r.detalle);

            ImageView estado = fila.findViewById(R.id.iv_estado);
            estado.setImageResource(r.cumplido ? R.drawable.ic_check : R.drawable.ic_warn);
            estado.setColorFilter(ContextCompat.getColor(this,
                r.cumplido ? R.color.success : R.color.warn));

            // Los cumplidos siguen siendo tocables: sirve para revisar o
            // revertir, y una fila que deja de responder desconcierta.
            if (r.abrirAjuste != null) {
                fila.setOnClickListener(v -> r.abrirAjuste.run());
            } else {
                fila.findViewById(R.id.iv_flecha).setVisibility(View.INVISIBLE);
            }

            cont.addView(fila);
        }
    }

    private void elegir(int modo) {
        if (modo == TemaManager.modoGuardado(this)) return;

        pintarElegido(modo);
        // Esto recrea la Activity para aplicar el tema nuevo: el tilde ya
        // quedó pintado antes para que el estado sobreviva a la recreación
        // sin que se vea saltar.
        TemaManager.guardarYAplicar(this, modo);
    }

    private void pintarElegido(int modo) {
        marcar(R.id.check_sistema, modo == TemaManager.SISTEMA);
        marcar(R.id.check_claro,   modo == TemaManager.CLARO);
        marcar(R.id.check_oscuro,  modo == TemaManager.OSCURO);
    }

    /**
     * El tilde de la opción elegida. Las otras dos se dejan invisibles en
     * vez de quitarlas: así las tres filas miden lo mismo y el texto no
     * se corre al cambiar de opción.
     */
    private void marcar(int idCheck, boolean elegido) {
        ImageView iv = findViewById(idCheck);
        if (iv == null) return;
        iv.setVisibility(elegido ? View.VISIBLE : View.INVISIBLE);
        iv.setColorFilter(ContextCompat.getColor(this, R.color.brand_600));
    }
}
