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
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.UsuarioDAO;
import com.tesis.vimed.database.VinculacionDAO;
import com.tesis.vimed.models.Usuario;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public class VincularFamiliarActivity extends AppCompatActivity {

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
    private VinculacionDAO vinculacionDAO;
    private UsuarioDAO usuarioDAO;
    private CountDownTimer countDownTimer;
    private String codigoActual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vincular_familiar);

        sessionManager = new SessionManager(this);
        DatabaseHelper db = DatabaseHelper.getInstance(this);
        vinculacionDAO = new VinculacionDAO(db);
        usuarioDAO = new UsuarioDAO(db);

        tvCode = findViewById(R.id.tv_code);
        tvTimer = findViewById(R.id.tv_timer);
        etEmail = findViewById(R.id.et_email);
        tilEmail = findViewById(R.id.til_email);
        familyContainer = findViewById(R.id.family_container);
        emptyFamily = findViewById(R.id.empty_family);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
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

        int idAdulto = sessionManager.getIdUsuario();
        if (idAdulto == -1) {
            Toast.makeText(this, "Inicie sesión para vincular familiares", Toast.LENGTH_SHORT).show();
            return;
        }

        // Verificar que el correo corresponde a un usuario registrado
        Usuario familiar = usuarioDAO.buscarPorCorreo(email);

        if (familiar == null) {
            new AlertDialog.Builder(this)
                .setTitle("Usuario no encontrado")
                .setMessage("No hay ninguna cuenta registrada con ese correo.\n\nComparta el código de invitación para que su familiar se registre primero.")
                .setPositiveButton("Entendido", null)
                .show();
            return;
        }

        if (familiar.getId() == idAdulto) {
            tilEmail.setError("No puede vincularse con su propia cuenta");
            return;
        }

        if (vinculacionDAO.existeVinculo(idAdulto, familiar.getId())) {
            tilEmail.setError("Este familiar ya está vinculado");
            return;
        }

        vinculacionDAO.insertar(idAdulto, familiar.getId());
        etEmail.setText("");
        cargarFamiliares();
        Toast.makeText(this,
            familiar.getNombre() + " vinculado correctamente", Toast.LENGTH_SHORT).show();
    }

    // ─── Cargar lista de familiares ─────────────────────────────────────────

    private void cargarFamiliares() {
        int idAdulto = sessionManager.getIdUsuario();
        familyContainer.removeAllViews();

        if (idAdulto == -1) {
            emptyFamily.setVisibility(View.VISIBLE);
            familyContainer.setVisibility(View.GONE);
            return;
        }

        List<int[]> vinculos = vinculacionDAO.listarFamiliares(idAdulto);

        if (vinculos.isEmpty()) {
            emptyFamily.setVisibility(View.VISIBLE);
            familyContainer.setVisibility(View.GONE);
        } else {
            emptyFamily.setVisibility(View.GONE);
            familyContainer.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(this);
            for (int[] v : vinculos) {
                int idVinculo = v[0];
                int idFamiliar = v[1];
                Usuario user = usuarioDAO.buscarPorId(idFamiliar);
                if (user == null) continue;

                View item = inflater.inflate(R.layout.item_family_member, familyContainer, false);
                bindFamilyItem(item, user, idVinculo);
                familyContainer.addView(item);
            }
        }
    }

    private void bindFamilyItem(View item, Usuario user, int idVinculo) {
        TextView tvInitial = item.findViewById(R.id.tv_family_initial);
        TextView tvName = item.findViewById(R.id.tv_family_name);
        TextView tvSub = item.findViewById(R.id.tv_family_sub);

        String nombre = user.getNombre() != null ? user.getNombre() : "?";
        tvInitial.setText(nombre.length() > 0
            ? String.valueOf(nombre.charAt(0)).toUpperCase(Locale.getDefault()) : "?");
        tvName.setText(nombre);
        tvSub.setText(user.getCorreo() != null ? user.getCorreo() : "Familiar vinculado");

        item.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Desvincular")
                .setMessage("¿Desvincular a " + nombre + "?")
                .setPositiveButton("Desvincular", (d, w) -> {
                    vinculacionDAO.eliminar(idVinculo);
                    cargarFamiliares();
                })
                .setNegativeButton("Cancelar", null)
                .show();
            return true;
        });
    }
}
