package com.tesis.vimed;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.UsuarioSupabase;
import com.tesis.vimed.models.Vinculacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VincularFamiliarActivity extends AppCompatActivity {

    /**
     * True cuando se llega acá desde el alta de cuenta (RoleSelectionActivity).
     * En ese caso no hay ninguna pantalla detrás a la cual volver, así que al
     * salir entramos al home en vez de simplemente cerrar.
     */
    public static final String EXTRA_IR_AL_HOME = "ir_al_home_al_salir";

    private TextInputEditText etEmail;
    private TextInputLayout tilEmail;
    private LinearLayout familyContainer;
    private View emptyFamily;

    private SessionManager sessionManager;
    private boolean irAlHomeAlSalir;

    /**
     * De qué lado del vínculo está quien abrió la pantalla.
     *
     * La tabla vinculacion_familiar tiene dos columnas —id_adulto e
     * id_familiar— y quién va en cuál lo decide el ROL, no la pantalla.
     * Antes esto se daba por sentado: se guardaba siempre
     * (id_adulto = yo, id_familiar = el del correo). Para el adulto mayor
     * estaba bien, pero el cuidador entra a la MISMA pantalla desde su menú
     * de perfil, así que al cargar a su paciente guardaba la fila dada
     * vuelta: quedaba figurando como el adulto y su paciente como su
     * cuidador. Después CuidadorActivity buscaba filas con
     * id_familiar = él, no encontraba ninguna, y la app se veía vacía.
     */
    private boolean soyElCuidador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vincular_familiar);

        sessionManager = new SessionManager(this);
        irAlHomeAlSalir = getIntent().getBooleanExtra(EXTRA_IR_AL_HOME, false);
        soyElCuidador = !sessionManager.esAdultoMayor();

        etEmail = findViewById(R.id.et_email);
        tilEmail = findViewById(R.id.til_email);
        familyContainer = findViewById(R.id.family_container);
        emptyFamily = findViewById(R.id.empty_family);

        findViewById(R.id.btn_back).setOnClickListener(v -> salir());
        findViewById(R.id.btn_add_by_email).setOnClickListener(v -> agregarPorCorreo());

        adaptarTextosAlRol();
        cargarFamiliares();
    }

    /**
     * La pantalla es la misma para los dos roles, pero todo lo que dice
     * cambia de sentido: el adulto invita a alguien a mirarlo, el cuidador
     * agrega a alguien para mirar.
     */
    private void adaptarTextosAlRol() {
        if (!soyElCuidador) return;   // los textos del XML ya son los del adulto

        texto(R.id.tv_toolbar_titulo, "Vincular paciente");
        texto(R.id.tv_titulo,         "Agregá a quien cuidás");
        texto(R.id.tv_subtitulo,      "Vas a ver sus tomas del día, sus citas y"
            + " los avisos de olvidos o de stock bajo. Necesita tener una"
            + " cuenta en Vimed.");
        texto(R.id.tv_seccion_lista,  "PACIENTES A TU CARGO");
        texto(R.id.tv_vacio_titulo,   "Todavía no cuidás a nadie");
        texto(R.id.tv_vacio_detalle,  "Agregá a tu paciente con el correo de su cuenta");
        ((com.google.android.material.button.MaterialButton)
            findViewById(R.id.btn_add_by_email)).setText("Agregar paciente");
        tilEmail.setHint("Correo de tu paciente");
    }

    private void texto(int id, String valor) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(valor);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarFamiliares();
    }

    /** El botón físico "atrás" tiene que hacer lo mismo que la flecha. */
    @Override
    public void onBackPressed() {
        salir();
    }

    /**
     * Si venimos del alta de cuenta no hay nada detrás: cerrar sin más dejaría
     * a la persona fuera de la app. En ese caso entramos al home.
     */
    private void salir() {
        if (irAlHomeAlSalir) Router.irAlHome(this);
        else finish();
    }

    // ─── Agregar por correo ─────────────────────────────────────────────────

    private void agregarPorCorreo() {
        tilEmail.setError(null);
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Ingrese un correo válido");
            return;
        }

        if (sessionManager.getSupabaseIdUsuario() == -1) {
            Toast.makeText(this, "Inicie sesión para vincular familiares", Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.equalsIgnoreCase(sessionManager.getCorreo())) {
            tilEmail.setError("No puede vincularse con su propia cuenta");
            return;
        }

        // La otra persona tiene que tener cuenta en Supabase (no en el SQLite
        // de este celular)
        VimedRepo.buscarPerfilPorCorreo(email, new VimedRepo.Cb<UsuarioSupabase>() {
            @Override
            public void onOk(UsuarioSupabase otro) {
                if (otro == null) {
                    new AlertDialog.Builder(VincularFamiliarActivity.this)
                        .setTitle("Usuario no encontrado")
                        .setMessage(soyElCuidador
                            ? "No hay ninguna cuenta registrada con ese correo.\n\n"
                                + "Tu paciente tiene que instalar Vimed y crear su"
                                + " cuenta antes de que puedas agregarlo."
                            : "No hay ninguna cuenta registrada con ese correo.\n\n"
                                + "Compartí el código de invitación para que tu"
                                + " familiar se registre primero.")
                        .setPositiveButton("Entendido", null)
                        .show();
                    return;
                }

                VimedRepo.Cb<Void> alVincular = new VimedRepo.Cb<Void>() {
                    @Override public void onOk(Void v) {
                        etEmail.setText("");
                        cargarFamiliares();
                        // Ya no dice "vinculado": todavía no lo está. El vínculo
                    // nace pendiente y no da acceso a nada hasta que el otro
                    // acepte. Prometer lo contrario haría que el cuidador
                    // crea que algo se rompió cuando no ve datos.
                    new AlertDialog.Builder(VincularFamiliarActivity.this)
                        .setTitle("Solicitud enviada")
                        .setMessage(soyElCuidador
                            ? "Le mandamos la solicitud a " + otro.getNombre()
                                + ". Vas a poder ver su medicación cuando la"
                                + " acepte desde su app."
                            : otro.getNombre() + " va a recibir la solicitud."
                                + " Cuando la acepte, va a poder seguir tu"
                                + " medicación.")
                        .setPositiveButton("Entendido", null)
                        .show();
                    }
                    @Override public void onError(String msg) {
                        // El UNIQUE (id_adulto, id_familiar) rebota los
                        // duplicados. Ya no se puede afirmar "está
                        // vinculado": la fila existente puede estar
                        // pendiente o rechazada, y decirle a alguien que ya
                        // está vinculado cuando en realidad falta que el
                        // otro acepte lo deja esperando datos que no van a
                        // llegar. La lista de abajo muestra el estado real.
                        boolean duplicado = msg.contains("409") || msg.contains("duplicate");
                        tilEmail.setError(duplicado
                            ? "Ya hay una solicitud con esta persona — mirá la lista de abajo"
                            : msg);
                        if (duplicado) cargarFamiliares();
                    }
                };

                // Acá es donde importa el rol: la fila es la misma tabla con
                // las columnas invertidas según quién cuida a quién.
                if (soyElCuidador) {
                    VimedRepo.crearVinculoComoCuidador(VincularFamiliarActivity.this,
                        otro.getIdUsuario(), alVincular);
                } else {
                    VimedRepo.crearVinculo(VincularFamiliarActivity.this,
                        otro.getIdUsuario(), alVincular);
                }
            }

            @Override
            public void onError(String msg) {
                tilEmail.setError(msg);
            }
        });
    }

    // ─── Cargar lista de familiares ─────────────────────────────────────────

    /**
     * Trae los vínculos de LOS DOS LADOS y los muestra juntos.
     *
     * Antes traía uno solo, según el rol: el cuidador veía la lista de
     * id_familiar y el adulto la de id_adulto. El problema es que una
     * solicitud puede llegar del lado que no te corresponde por rol — a un
     * adulto mayor puede escribirle alguien pidiéndole que lo cuide— y esa
     * quedaba invisible. El aviso del inicio la contaba (mira los dos
     * lados) pero esta pantalla no la mostraba: tocabas el aviso, entrabas,
     * no había nada que aceptar, y el aviso se quedaba pegado para siempre.
     *
     * Se piden las dos listas y se juntan. Cada fila ya sabe sola quién es
     * la otra punta, así que da igual de qué consulta salió.
     */
    private void cargarFamiliares() {
        familyContainer.removeAllViews();

        final List<Vinculacion> todos = new ArrayList<>();

        VimedRepo.listarMisCuidadores(this, new VimedRepo.Cb<List<Vinculacion>>() {
            @Override public void onOk(List<Vinculacion> unos) {
                if (unos != null) todos.addAll(unos);
                VimedRepo.listarMisPacientes(VincularFamiliarActivity.this,
                    new VimedRepo.Cb<List<Vinculacion>>() {
                        @Override public void onOk(List<Vinculacion> otros) {
                            if (otros != null) todos.addAll(otros);
                            pintarVinculos(todos);
                        }
                        @Override public void onError(String msg) { pintarVinculos(todos); }
                    });
            }
            @Override public void onError(String msg) {
                // Si la primera falla se intenta la otra igual: puede que
                // los vínculos estén todos del otro lado.
                VimedRepo.listarMisPacientes(VincularFamiliarActivity.this,
                    new VimedRepo.Cb<List<Vinculacion>>() {
                        @Override public void onOk(List<Vinculacion> otros) {
                            if (otros != null) todos.addAll(otros);
                            pintarVinculos(todos);
                        }
                        @Override public void onError(String m2) { pintarVinculos(todos); }
                    });
            }
        });
    }

    private void pintarVinculos(List<Vinculacion> vinculos) {
        familyContainer.removeAllViews();

        if (vinculos.isEmpty()) {
            emptyFamily.setVisibility(View.VISIBLE);
            familyContainer.setVisibility(View.GONE);
            return;
        }
        emptyFamily.setVisibility(View.GONE);
        familyContainer.setVisibility(View.VISIBLE);

        // Lo que espera respuesta va PRIMERO: es lo único de esta lista que
        // pide una acción, y abajo de cinco vínculos aceptados no se ve.
        List<Vinculacion> ordenados = new ArrayList<>();
        for (Vinculacion v : vinculos) if (v.esperaRespuestaDe(miId())) ordenados.add(v);
        for (Vinculacion v : vinculos) if (!v.esperaRespuestaDe(miId())) ordenados.add(v);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Vinculacion v : ordenados) {
            View item = inflater.inflate(R.layout.item_family_member,
                familyContainer, false);
            familyContainer.addView(item);

            // Para reencontrar esta fila cuando vuelva la consulta de los
            // avisos, que llega después y puede llegar reordenada.
            item.setTag(v.getIdVinculo());
            item.setTag(R.id.tv_sin_avisos, v);

            // El nombre de la otra persona llega en una segunda consulta
            bindFamilyItem(item, v);
        }

        avisarSiNoLleganLosAvisos();
    }

    /**
     * Marca los vínculos donde el cuidador todavía no va a recibir nada.
     *
     * "Vinculado" no quiere decir que los avisos lleguen: hace falta que
     * el cuidador haya abierto Vimed en su celular al menos una vez con
     * la sesión iniciada, que es cuando se guarda el token de push. Hasta
     * entonces el paciente marca sus tomas creyendo que alguien las ve
     * del otro lado, y no las ve nadie.
     *
     * Es una consulta aparte y no viene con el vínculo porque la tabla de
     * dispositivos tiene RLS —nadie lee tokens ajenos, y está bien que
     * sea así—. La función del servidor contesta un sí o un no.
     *
     * Si falla, no se muestra nada. Un aviso de "quizás no le lleguen los
     * avisos" porque se cayó una consulta asusta sin motivo.
     */
    private void avisarSiNoLleganLosAvisos() {
        VimedRepo.vinculosRecibenAvisos(
            new VimedRepo.Cb<List<com.tesis.vimed.models.AvisoVinculo>>() {
                @Override
                public void onOk(List<com.tesis.vimed.models.AvisoVinculo> estados) {
                    if (estados == null) return;
                    for (com.tesis.vimed.models.AvisoVinculo e : estados) {
                        if (e.recibeAvisos()) continue;
                        pintarFaltaCelular(e.getIdVinculo());
                    }
                }
                @Override public void onError(String msg) { }
            });
    }

    /**
     * El renglón ámbar en la fila de ese vínculo.
     *
     * La fila se busca por el id del vínculo guardado en el tag: la lista
     * se pudo haber vuelto a pintar mientras la consulta viajaba, y el
     * orden de las filas no es estable —lo pendiente sube arriba—, así
     * que acordarse de la posición no serviría.
     */
    private void pintarFaltaCelular(int idVinculo) {
        for (int i = 0; i < familyContainer.getChildCount(); i++) {
            View fila = familyContainer.getChildAt(i);
            Object tag = fila.getTag();
            if (!(tag instanceof Integer) || (Integer) tag != idVinculo) continue;

            TextView aviso = fila.findViewById(R.id.tv_sin_avisos);
            if (aviso == null) return;

            // El texto depende de quién mira: a uno le falta avisarle al
            // otro, y el otro tiene que abrir la app en su celular.
            Vinculacion v = vinculoDe(fila);
            boolean soyYoElQueNoRecibe = v != null && v.getIdFamiliar() == miId();

            aviso.setText(soyYoElQueNoRecibe
                ? "No vas a recibir avisos en este celular. Entrá a Vimed con"
                    + " tu cuenta desde el teléfono donde los querés recibir."
                : "Todavía no va a recibir tus avisos: tiene que abrir Vimed"
                    + " en su celular con su cuenta al menos una vez.");
            aviso.setVisibility(View.VISIBLE);
            return;
        }
    }

    private Vinculacion vinculoDe(View fila) {
        Object v = fila.getTag(R.id.tv_sin_avisos);
        return v instanceof Vinculacion ? (Vinculacion) v : null;
    }

    private void bindFamilyItem(View item, Vinculacion vinculo) {
        TextView tvInitial = item.findViewById(R.id.tv_family_initial);
        TextView tvName = item.findViewById(R.id.tv_family_name);
        TextView tvSub = item.findViewById(R.id.tv_family_sub);

        tvName.setText("Cargando…");
        tvSub.setText("");
        tvInitial.setText("?");

        pintarEstadoDelVinculo(item, vinculo);

        // La otra punta se calcula desde MI id y no desde el rol: ahora la
        // lista mezcla vínculos de los dos lados, así que suponer "el
        // cuidador siempre mira id_adulto" haría que en la mitad de las
        // filas la persona se viera a sí misma.
        int idDelOtro = vinculo.laOtraPunta(miId());

        VimedRepo.buscarPerfilPorId(idDelOtro, new VimedRepo.Cb<UsuarioSupabase>() {
            @Override
            public void onOk(UsuarioSupabase user) {
                String nombre = user != null && user.getNombre() != null
                    ? user.getNombre()
                    : (soyElCuidador ? "Paciente" : "Familiar");
                tvInitial.setText(String.valueOf(nombre.charAt(0)).toUpperCase(Locale.getDefault()));
                tvName.setText(nombre);
                tvSub.setText(user != null && user.getCorreo() != null
                    ? user.getCorreo()
                    : (soyElCuidador ? "Paciente a tu cargo" : "Familiar vinculado"));

                // El texto de los botones necesita el nombre, así que la
                // solicitud se cablea recién cuando el perfil llegó.
                cablearRespuesta(item, vinculo, nombre);

                // La flecha de la derecha prometía llevar a algún lado y no
                // hacía nada: desvincular estaba solo en el mantener
                // apretado, que nadie descubre. Ahora el toque abre eso
                // mismo, y la flecha dice la verdad.
                if (vinculo.estaAceptado()) {
                    item.setOnClickListener(v -> ofrecerDesvincular(vinculo, nombre));
                    item.setOnLongClickListener(v -> {
                        ofrecerDesvincular(vinculo, nombre);
                        return true;
                    });
                }
            }
        });
    }

    // ─── Estado del vínculo y respuesta a la solicitud ──────────────────────

    /**
     * Muestra en qué estado está el vínculo. Un vínculo aceptado no lleva
     * cartel: si cada fila dijera "aceptado", el aviso de las que SÍ
     * necesitan atención se perdería entre el ruido.
     */
    private void pintarEstadoDelVinculo(View item, Vinculacion vinculo) {
        TextView tvEstado = item.findViewById(R.id.tv_family_estado);
        View acciones     = item.findViewById(R.id.acciones_solicitud);
        View chevron      = item.findViewById(R.id.iv_family_chevron);

        boolean meToca = vinculo.esperaRespuestaDe(miId());

        acciones.setVisibility(meToca ? View.VISIBLE : View.GONE);
        chevron.setVisibility(vinculo.estaAceptado() ? View.VISIBLE : View.INVISIBLE);

        if (vinculo.estaAceptado()) {
            tvEstado.setVisibility(View.GONE);
            return;
        }

        tvEstado.setVisibility(View.VISIBLE);
        if (meToca) {
            tvEstado.setText(soyElCuidador
                ? "TE PROPUSO QUE LO CUIDES"
                : "QUIERE SEGUIR TU MEDICACIÓN");
        } else if (vinculo.estaPendiente()) {
            tvEstado.setText("ESPERANDO SU RESPUESTA");
        } else {
            tvEstado.setText("SOLICITUD RECHAZADA");
        }
    }

    /** Cablea Aceptar/Rechazar. No hace nada si esta solicitud no me toca. */
    private void cablearRespuesta(View item, Vinculacion vinculo, String nombre) {
        if (!vinculo.esperaRespuestaDe(miId())) {
            // Una solicitud que mandé yo, o una rechazada, queda trabada: el
            // UNIQUE (id_adulto, id_familiar) impide volver a pedirla
            // mientras la fila exista. Borrarla estaba solo en el
            // mantener-apretado, que nadie descubre. Con un toque se ofrece.
            if (!vinculo.estaAceptado()) {
                item.setOnClickListener(v -> ofrecerBorrar(vinculo, nombre));
            }
            return;
        }

        item.findViewById(R.id.btn_aceptar).setOnClickListener(v ->
            responder(vinculo, true, nombre));

        // Rechazar sí pregunta: es la opción que corta algo, y en una fila
        // con dos botones grandes el dedo se equivoca.
        item.findViewById(R.id.btn_rechazar).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Rechazar solicitud")
                .setMessage(soyElCuidador
                    ? "¿Rechazar cuidar a " + nombre + "?"
                    : "¿Rechazar que " + nombre + " siga tu medicación?"
                        + " No va a poder ver nada.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Rechazar", (d, w) -> responder(vinculo, false, nombre))
                .show());
    }

    private void responder(Vinculacion vinculo, boolean acepta, String nombre) {
        VimedRepo.Cb<Void> cb = new VimedRepo.Cb<Void>() {
            @Override public void onOk(Void v) {
                cargarFamiliares();
                Toast.makeText(VincularFamiliarActivity.this,
                    acepta
                        ? (soyElCuidador
                            ? "Listo, ya podés ver la medicación de " + nombre
                            : nombre + " ya puede seguir tu medicación")
                        : "Solicitud rechazada",
                    Toast.LENGTH_LONG).show();
            }
            @Override public void onError(String msg) {
                Toast.makeText(VincularFamiliarActivity.this,
                    "No se pudo responder: " + msg, Toast.LENGTH_LONG).show();
            }
        };

        if (acepta) VimedRepo.aceptarVinculo(vinculo.getIdVinculo(), cb);
        else        VimedRepo.rechazarVinculo(vinculo.getIdVinculo(), cb);
    }

    /** Borra una solicitud pendiente o rechazada, para poder volver a pedirla. */
    private void ofrecerBorrar(Vinculacion vinculo, String nombre) {
        boolean rechazada = !vinculo.estaPendiente();

        new AlertDialog.Builder(this)
            .setTitle(rechazada ? "Solicitud rechazada" : "Solicitud enviada")
            .setMessage(rechazada
                ? nombre + " rechazó esta solicitud. Podés borrarla para"
                    + " volver a intentarlo."
                : "Todavía no respondió. Podés cancelar la solicitud.")
            .setNegativeButton("Dejarla así", null)
            .setPositiveButton(rechazada ? "Borrar" : "Cancelar solicitud", (d, w) ->
                VimedRepo.eliminarVinculo(vinculo.getIdVinculo(), new VimedRepo.Cb<Void>() {
                    @Override public void onOk(Void x) { cargarFamiliares(); }
                    @Override public void onError(String msg) {
                        Toast.makeText(VincularFamiliarActivity.this,
                            msg, Toast.LENGTH_LONG).show();
                    }
                }))
            .show();
    }

    /**
     * Lo que se puede hacer con un vínculo ya aceptado: cortarlo.
     *
     * El texto dice qué se pierde de cada lado, porque no es lo mismo:
     * el adulto deja de ser mirado, el cuidador deja de poder mirar.
     */
    private void ofrecerDesvincular(Vinculacion vinculo, String nombre) {
        boolean esMiCuidador = vinculo.getIdAdulto() == miId();

        new AlertDialog.Builder(this)
            .setTitle(esMiCuidador ? "Desvincular a " + nombre : "Dejar de cuidar")
            .setMessage(esMiCuidador
                ? nombre + " va a dejar de ver tu medicación y de recibir"
                    + " avisos si te olvidás de una toma."
                : "Vas a dejar de ver la medicación de " + nombre
                    + " y de recibir sus avisos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(esMiCuidador ? "Desvincular" : "Dejar de cuidar", (d, w) ->
                VimedRepo.eliminarVinculo(vinculo.getIdVinculo(), new VimedRepo.Cb<Void>() {
                    @Override public void onOk(Void x) { cargarFamiliares(); }
                    @Override public void onError(String msg) {
                        Toast.makeText(VincularFamiliarActivity.this,
                            msg, Toast.LENGTH_LONG).show();
                    }
                }))
            .show();
    }

    private int miId() {
        return sessionManager.getSupabaseIdUsuario();
    }
}
