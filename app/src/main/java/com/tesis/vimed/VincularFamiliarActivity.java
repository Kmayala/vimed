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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vincular_familiar);

        sessionManager = new SessionManager(this);
        irAlHomeAlSalir = getIntent().getBooleanExtra(EXTRA_IR_AL_HOME, false);

        tvCode = findViewById(R.id.tv_code);
        tvTimer = findViewById(R.id.tv_timer);
        etEmail = findViewById(R.id.et_email);
        tilEmail = findViewById(R.id.til_email);
        familyContainer = findViewById(R.id.family_container);
        emptyFamily = findViewById(R.id.empty_family);

        findViewById(R.id.btn_back).setOnClickListener(v -> salir());
        findViewById(R.id.btn_share_email).setOnClickListener(v -> compartirPorCorreo());
        findViewById(R.id.btn_add_by_email).setOnClickListener(v -> agregarPorCorreo());

        mostrarCodigo();
        cargarFamiliares();
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

        int idAdulto = sessionManager.getSupabaseIdUsuario();
        if (idAdulto == -1) {
            Toast.makeText(this, "Inicie sesión para vincular familiares", Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.equalsIgnoreCase(sessionManager.getCorreo())) {
            tilEmail.setError("No puede vincularse con su propia cuenta");
            return;
        }

        // El familiar tiene que tener cuenta en Supabase (no en el SQLite de este celular)
        VimedRepo.buscarPerfilPorCorreo(email, new VimedRepo.Cb<UsuarioSupabase>() {
            @Override
            public void onOk(UsuarioSupabase familiar) {
                if (familiar == null) {
                    new AlertDialog.Builder(VincularFamiliarActivity.this)
                        .setTitle("Usuario no encontrado")
                        .setMessage("No hay ninguna cuenta registrada con ese correo.\n\n"
                            + "Compartí el código de invitación para que tu familiar se registre primero.")
                        .setPositiveButton("Entendido", null)
                        .show();
                    return;
                }

                VimedRepo.crearVinculo(VincularFamiliarActivity.this, familiar.getIdUsuario(),
                    new VimedRepo.Cb<Void>() {
                        @Override public void onOk(Void v) {
                            etEmail.setText("");
                            cargarFamiliares();
                            Toast.makeText(VincularFamiliarActivity.this,
                                familiar.getNombre() + " vinculado correctamente",
                                Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(String msg) {
                            // El UNIQUE (id_adulto, id_familiar) rebota los duplicados
                            tilEmail.setError(msg.contains("409") || msg.contains("duplicate")
                                ? "Este familiar ya está vinculado" : msg);
                        }
                    });
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

        VimedRepo.listarMisCuidadores(this, new VimedRepo.Cb<List<Vinculacion>>() {
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
        });
    }

    private void bindFamilyItem(View item, Vinculacion vinculo) {
        TextView tvInitial = item.findViewById(R.id.tv_family_initial);
        TextView tvName = item.findViewById(R.id.tv_family_name);
        TextView tvSub = item.findViewById(R.id.tv_family_sub);

        tvName.setText("Cargando…");
        tvSub.setText("");
        tvInitial.setText("?");

        VimedRepo.buscarPerfilPorId(vinculo.getIdFamiliar(), new VimedRepo.Cb<UsuarioSupabase>() {
            @Override
            public void onOk(UsuarioSupabase user) {
                String nombre = user != null && user.getNombre() != null
                    ? user.getNombre() : "Familiar";
                tvInitial.setText(String.valueOf(nombre.charAt(0)).toUpperCase(Locale.getDefault()));
                tvName.setText(nombre);
                tvSub.setText(user != null && user.getCorreo() != null
                    ? user.getCorreo() : "Familiar vinculado");

                item.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(VincularFamiliarActivity.this)
                        .setTitle("Desvincular")
                        .setMessage("¿Desvincular a " + nombre + "?")
                        .setPositiveButton("Desvincular", (d, w) ->
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
}
