package com.tesis.vimed;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
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

import java.util.List;
import java.util.Locale;
import java.util.Random;

public class VincularFamiliarActivity extends AppCompatActivity {

    /**
     * True cuando se llega acá desde el alta de cuenta (RoleSelectionActivity).
     * En ese caso no hay ninguna pantalla detrás a la cual volver, así que al
     * salir entramos al home en vez de simplemente cerrar.
     */
    public static final String EXTRA_IR_AL_HOME = "ir_al_home_al_salir";

    private static final String PREF_CODIGO = "VinculoCodigo";
    private static final String KEY_CODIGO = "codigo";
    private static final String KEY_EXPIRY = "expiry_ms";
    private static final long DURACION_MS = 30 * 60 * 1000L; // 30 minutos

    private TextView tvCode, tvTimer;
    private TextInputEditText etEmail;
    private TextInputLayout tilEmail;
    private LinearLayout familyContainer;
    private View emptyFamily;

    private SessionManager sessionManager;
    private CountDownTimer countDownTimer;
    private String codigoActual;
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

        tvCode = findViewById(R.id.tv_code);
        tvTimer = findViewById(R.id.tv_timer);
        etEmail = findViewById(R.id.et_email);
        tilEmail = findViewById(R.id.til_email);
        familyContainer = findViewById(R.id.family_container);
        emptyFamily = findViewById(R.id.empty_family);

        findViewById(R.id.btn_back).setOnClickListener(v -> salir());
        findViewById(R.id.btn_share_email).setOnClickListener(v -> compartirPorCorreo());
        findViewById(R.id.btn_add_by_email).setOnClickListener(v -> agregarPorCorreo());

        adaptarTextosAlRol();
        if (!soyElCuidador) mostrarCodigo();
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
        texto(R.id.tv_seccion_correo, "AGREGAR POR CORREO");
        texto(R.id.tv_seccion_lista,  "PACIENTES A TU CARGO");
        texto(R.id.tv_vacio_titulo,   "Todavía no cuidás a nadie");
        texto(R.id.tv_vacio_detalle,  "Agregá a tu paciente con el correo de su cuenta");
        ((com.google.android.material.button.MaterialButton)
            findViewById(R.id.btn_add_by_email)).setText("Agregar paciente");
        tilEmail.setHint("Correo de tu paciente");

        // El código de invitación dice "invitame a ser tu cuidador": del lado
        // del cuidador es exactamente al revés, así que no se muestra.
        findViewById(R.id.card_codigo).setVisibility(View.GONE);
        findViewById(R.id.btn_share_email).setVisibility(View.GONE);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
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

    // ─── Código de invitación ───────────────────────────────────────────────

    private void mostrarCodigo() {
        SharedPreferences prefs = getSharedPreferences(PREF_CODIGO, MODE_PRIVATE);
        long expiry = prefs.getLong(KEY_EXPIRY, 0);
        long ahora = System.currentTimeMillis();

        if (expiry == 0 || ahora >= expiry) {
            // Generar nuevo código
            codigoActual = generarCodigo();
            long nuevaExpiry = ahora + DURACION_MS;
            prefs.edit()
                .putString(KEY_CODIGO, codigoActual)
                .putLong(KEY_EXPIRY, nuevaExpiry)
                .apply();
            iniciarContador(nuevaExpiry - ahora);
        } else {
            codigoActual = prefs.getString(KEY_CODIGO, generarCodigo());
            iniciarContador(expiry - ahora);
        }

        tvCode.setText(codigoActual);
    }

    private String generarCodigo() {
        int num = new Random().nextInt(900000) + 100000; // 100000–999999
        String s = String.valueOf(num);
        return s.substring(0, 3) + "-" + s.substring(3);
    }

    private void iniciarContador(long msRestantes) {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(msRestantes, 60_000) {
            @Override
            public void onTick(long ms) {
                long mins = ms / 60_000;
                tvTimer.setText(mins <= 1
                    ? "Válido por 1 minuto"
                    : "Válido por " + mins + " minutos");
            }

            @Override
            public void onFinish() {
                tvTimer.setText("Código expirado — generando nuevo...");
                mostrarCodigo();
            }
        }.start();
    }

    // ─── Compartir por correo ───────────────────────────────────────────────

    private void compartirPorCorreo() {
        String asunto = "Te invito a seguir mi medicación en Vimed";
        String cuerpo = "Hola,\n\nTe invito a ser mi cuidador en la app Vimed.\n"
            + "Mi código de vinculación es: " + codigoActual + "\n\n"
            + "Válido por 30 minutos. Descarga Vimed e ingresa este código para vincularnos.\n\n"
            + "Saludos,\n" + sessionManager.getNombre();

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_SUBJECT, asunto);
        intent.putExtra(Intent.EXTRA_TEXT, cuerpo);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Fallback: share sheet
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, asunto);
            share.putExtra(Intent.EXTRA_TEXT, cuerpo);
            startActivity(Intent.createChooser(share, "Compartir código"));
        }
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

    private void cargarFamiliares() {
        familyContainer.removeAllViews();

        VimedRepo.Cb<List<Vinculacion>> cb = new VimedRepo.Cb<List<Vinculacion>>() {
            @Override
            public void onOk(List<Vinculacion> vinculos) {
                if (vinculos.isEmpty()) {
                    emptyFamily.setVisibility(View.VISIBLE);
                    familyContainer.setVisibility(View.GONE);
                    return;
                }
                emptyFamily.setVisibility(View.GONE);
                familyContainer.setVisibility(View.VISIBLE);

                LayoutInflater inflater = LayoutInflater.from(VincularFamiliarActivity.this);
                for (Vinculacion v : vinculos) {
                    View item = inflater.inflate(R.layout.item_family_member,
                        familyContainer, false);
                    familyContainer.addView(item);
                    // El nombre del familiar llega en una segunda consulta
                    bindFamilyItem(item, v);
                }
            }

            @Override
            public void onError(String msg) {
                emptyFamily.setVisibility(View.VISIBLE);
                familyContainer.setVisibility(View.GONE);
            }
        };

        // El cuidador ve a sus pacientes; el adulto mayor, a sus cuidadores.
        if (soyElCuidador) VimedRepo.listarMisPacientes(this, cb);
        else               VimedRepo.listarMisCuidadores(this, cb);
    }

    private void bindFamilyItem(View item, Vinculacion vinculo) {
        TextView tvInitial = item.findViewById(R.id.tv_family_initial);
        TextView tvName = item.findViewById(R.id.tv_family_name);
        TextView tvSub = item.findViewById(R.id.tv_family_sub);

        tvName.setText("Cargando…");
        tvSub.setText("");
        tvInitial.setText("?");

        pintarEstadoDelVinculo(item, vinculo);

        // La otra punta del vínculo: para el cuidador es el adulto, para el
        // adulto es el familiar. Antes se leía siempre id_familiar, así que
        // el cuidador se veía a sí mismo repetido en su propia lista.
        int idDelOtro = soyElCuidador ? vinculo.getIdAdulto() : vinculo.getIdFamiliar();

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

                item.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(VincularFamiliarActivity.this)
                        .setTitle(soyElCuidador ? "Dejar de cuidar" : "Desvincular")
                        .setMessage(soyElCuidador
                            ? "¿Dejar de seguir la medicación de " + nombre + "?"
                            : "¿Desvincular a " + nombre + "?")
                        .setPositiveButton(soyElCuidador ? "Dejar de cuidar" : "Desvincular",
                            (d, w) ->
                            VimedRepo.eliminarVinculo(vinculo.getIdVinculo(),
                                new VimedRepo.Cb<Void>() {
                                    @Override public void onOk(Void x) { cargarFamiliares(); }
                                    @Override public void onError(String msg) {
                                        Toast.makeText(VincularFamiliarActivity.this,
                                            msg, Toast.LENGTH_LONG).show();
                                    }
                                }))
                        .setNegativeButton("Cancelar", null)
                        .show();
                    return true;
                });
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

    private int miId() {
        return sessionManager.getSupabaseIdUsuario();
    }
}
