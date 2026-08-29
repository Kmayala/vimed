package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.api.auth.AuthPayloads;
import com.tesis.vimed.api.auth.SupabaseAuthClient;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.UsuarioDAO;
import com.tesis.vimed.models.Usuario;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirmPassword;
    private TextInputEditText etName, etEmail, etPassword, etConfirmPassword;
    private MaterialButton btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        sessionManager = new SessionManager(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tilName = findViewById(R.id.til_name);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);
        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);

        btnRegister = findViewById(R.id.btn_register);
        MaterialButton tvLogin = findViewById(R.id.tv_login);

        btnRegister.setOnClickListener(v -> attemptRegister());

        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

        tilName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);

        boolean hasError = false;

        if (name.isEmpty()) { tilName.setError(getString(R.string.error_empty_field)); hasError = true; }
        if (email.isEmpty()) { tilEmail.setError(getString(R.string.error_empty_field)); hasError = true; }
        else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { tilEmail.setError(getString(R.string.error_invalid_email)); hasError = true; }
        if (password.isEmpty()) { tilPassword.setError(getString(R.string.error_empty_field)); hasError = true; }
        else if (password.length() < 6) { tilPassword.setError(getString(R.string.error_password_short)); hasError = true; }
        if (!password.equals(confirmPassword)) { tilConfirmPassword.setError(getString(R.string.error_passwords_mismatch)); hasError = true; }

        if (hasError) return;

        setLoading(true);

        // Metadata extra que va a auth.users.raw_user_meta_data
        Map<String, Object> meta = new HashMap<>();
        meta.put("nombre", name);

        AuthPayloads.SignUpRequest req = new AuthPayloads.SignUpRequest(email, password, meta);

        SupabaseAuthClient.getService().signUp(req).enqueue(new Callback<AuthPayloads.AuthResponse>() {
            @Override
            public void onResponse(Call<AuthPayloads.AuthResponse> call,
                                   Response<AuthPayloads.AuthResponse> r) {
                if (r.isSuccessful() && r.body() != null) {
                    onSignUpExitoso(name, email, password, r.body());
                } else {
                    setLoading(false);
                    mostrarErrorRespuesta(r);
                }
            }

            @Override
            public void onFailure(Call<AuthPayloads.AuthResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this,
                    VimedRepo.mensajeDeFallo(t), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onSignUpExitoso(String name, String email, String password,
                                 AuthPayloads.AuthResponse body) {
        // 1) Insertar (o reusar) el usuario local — id_usuario sigue siendo PK
        //    para que los DAOs de medicamentos/horarios/citas funcionen sin cambios.
        UsuarioDAO usuarioDAO = new UsuarioDAO(DatabaseHelper.getInstance(this));
        Usuario existente = usuarioDAO.buscarPorCorreo(email);
        long idLocal;
        if (existente != null) {
            idLocal = existente.getId();
        } else {
            // Guardamos el password localmente también para que validarLogin()
            // siga funcionando si en algún momento el dispositivo está offline.
            // En el futuro convendría guardar solo un placeholder.
            Usuario nuevo = new Usuario(name, email, password, "");
            idLocal = usuarioDAO.insertar(nuevo);
        }

        if (idLocal == -1) {
            setLoading(false);
            tilEmail.setError("Error guardando datos locales. Intentá de nuevo.");
            return;
        }

        // 2) Persistir sesión local + tokens
        sessionManager.saveSession((int) idLocal, name, email, "");
        String authUserId = body.user != null ? body.user.id : null;
        if (body.accessToken != null) {
            sessionManager.saveAuthTokens(
                authUserId,
                body.accessToken,
                body.refreshToken,
                body.expiresAt > 0
                    ? body.expiresAt
                    : (System.currentTimeMillis() / 1000) + body.expiresIn
            );
            // 3) Crear la fila espejo en public.usuarios y ESPERARLA: la
            //    pantalla siguiente ya consulta con ese id, y sin él
            //    responde "Sesión no sincronizada". Ver la nota en
            //    LoginActivity.onLoginExitoso.
            com.tesis.vimed.api.PerfilSync.asegurarPerfil(
                this, authUserId, name, email, "",
                idUsuario -> irADespuesDelRegistro(body));
            return;
        }

        irADespuesDelRegistro(body);
    }

    /** A dónde va la persona una vez creada la cuenta. */
    private void irADespuesDelRegistro(AuthPayloads.AuthResponse body) {
        setLoading(false);
        // Si el dashboard tiene "Confirm email" activado, accessToken viene null
        // y el usuario tiene que confirmar antes de loguearse. Le avisamos.
        if (body.accessToken == null) {
            Toast.makeText(this,
                "Revisá tu correo para confirmar la cuenta antes de iniciar sesión.",
                Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
        } else {
            startActivity(new Intent(this, RoleSelectionActivity.class));
        }
        finish();
    }

    private void mostrarErrorRespuesta(Response<?> r) {
        String msg = "Error " + r.code();
        try {
            String body = r.errorBody() != null ? r.errorBody().string() : "";
            AuthPayloads.AuthError err = new com.google.gson.Gson()
                .fromJson(body, AuthPayloads.AuthError.class);
            if (err != null) msg = err.mensajeUsuario();
        } catch (Exception ignored) {}

        // Heurística para asignar el error al campo correcto
        String lower = msg.toLowerCase();
        if (lower.contains("already") || lower.contains("registered") || lower.contains("exists")) {
            tilEmail.setError(getString(R.string.error_email_exists));
        } else if (lower.contains("password")) {
            tilPassword.setError(msg);
        } else {
            tilEmail.setError(msg);
        }
    }

    private void setLoading(boolean loading) {
        btnRegister.setEnabled(!loading);
        btnRegister.setText(loading ? "Creando cuenta..." : getString(R.string.btn_register));
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
        etConfirmPassword.setEnabled(!loading);
        etName.setEnabled(!loading);
    }
}
