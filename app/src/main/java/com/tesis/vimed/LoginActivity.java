package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tesis.vimed.api.auth.AuthPayloads;
import com.tesis.vimed.api.auth.SupabaseAuthClient;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.UsuarioDAO;
import com.tesis.vimed.models.Usuario;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);

        btnLogin = findViewById(R.id.btn_login);
        MaterialButton tvRegister = findViewById(R.id.tv_register);
        MaterialButton tvForgot   = findViewById(R.id.tv_forgot_password);

        btnLogin.setOnClickListener(v -> attemptLogin());

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });

        tvForgot.setOnClickListener(v -> {
            Intent i = new Intent(this, ForgotPasswordActivity.class);
            // Pre-llenamos el correo si la usuaria ya lo tipeó
            String currentEmail = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            if (!currentEmail.isEmpty()) i.putExtra("email", currentEmail);
            startActivity(i);
        });
    }

    private void attemptLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        tilEmail.setError(null);
        tilPassword.setError(null);

        boolean hasError = false;

        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_empty_field));
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_invalid_email));
            hasError = true;
        }
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_empty_field));
            hasError = true;
        }
        if (hasError) return;

        setLoading(true);

        SupabaseAuthClient.getService()
            .signIn(new AuthPayloads.SignInRequest(email, password))
            .enqueue(new Callback<AuthPayloads.AuthResponse>() {
                @Override
                public void onResponse(Call<AuthPayloads.AuthResponse> c,
                                       Response<AuthPayloads.AuthResponse> r) {
                    if (r.isSuccessful() && r.body() != null && r.body().accessToken != null) {
                        onLoginExitoso(email, password, r.body());
                    } else {
                        setLoading(false);
                        mostrarErrorRespuesta(r);
                    }
                }

                @Override
                public void onFailure(Call<AuthPayloads.AuthResponse> c, Throwable t) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this,
                        "Sin conexión: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
    }

    private void onLoginExitoso(String email, String password,
                                AuthPayloads.AuthResponse body) {
        // Buscar (o crear lazy) la fila local con id_usuario
        UsuarioDAO usuarioDAO = new UsuarioDAO(DatabaseHelper.getInstance(this));
        Usuario usuario = usuarioDAO.buscarPorCorreo(email);

        if (usuario == null) {
            // Bootstrap: el usuario existe en Supabase Auth pero no en SQLite local
            // (probablemente migración, reinstalación o cambio de dispositivo).
            // Lo creamos sin password (la fuente de verdad ya es Auth).
            String nombre = body.user != null && body.user.email != null
                ? body.user.email.split("@")[0]
                : email.split("@")[0];
            Usuario nuevo = new Usuario(nombre, email, password, "");
            long id = usuarioDAO.insertar(nuevo);
            if (id == -1) {
                setLoading(false);
                tilEmail.setError("Error al crear sesión local. Intentá de nuevo.");
                return;
            }
            usuario = usuarioDAO.buscarPorId((int) id);
        }

        sessionManager.saveSession(usuario.getId(), usuario.getNombre(),
            usuario.getCorreo(), usuario.getRol());

        String authUserId = body.user != null ? body.user.id : null;
        sessionManager.saveAuthTokens(
            authUserId,
            body.accessToken,
            body.refreshToken,
            body.expiresAt > 0
                ? body.expiresAt
                : (System.currentTimeMillis() / 1000) + body.expiresIn
        );

        // Resolver el id_usuario de public.usuarios (lo crea si no existe).
        // Best effort — si falla, la app sigue andando con SQLite local.
        com.tesis.vimed.api.PerfilSync.asegurarPerfil(
            this, authUserId, usuario.getNombre(), usuario.getCorreo(),
            usuario.getRol(), null);

        setLoading(false);

        if (sessionManager.hasRole()) {
            goToHome();
        } else {
            startActivity(new Intent(this, RoleSelectionActivity.class));
            finish();
        }
    }

    private void mostrarErrorRespuesta(Response<?> r) {
        String msg = getString(R.string.error_invalid_credentials);
        try {
            String body = r.errorBody() != null ? r.errorBody().string() : "";
            AuthPayloads.AuthError err = new com.google.gson.Gson()
                .fromJson(body, AuthPayloads.AuthError.class);
            if (err != null && err.mensajeUsuario() != null) {
                String m = err.mensajeUsuario().toLowerCase();
                if (m.contains("invalid") || m.contains("credentials")) {
                    msg = getString(R.string.error_invalid_credentials);
                } else if (m.contains("confirm")) {
                    msg = "Confirmá tu correo antes de iniciar sesión.";
                } else {
                    msg = err.mensajeUsuario();
                }
            }
        } catch (Exception ignored) {}
        tilPassword.setError(msg);
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Ingresando..." : getString(R.string.btn_login));
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }

    private void goToHome() {
        Router.irAlHome(this);
    }
}
