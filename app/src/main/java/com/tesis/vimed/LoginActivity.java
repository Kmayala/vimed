package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.api.auth.AuthPayloads;
import com.tesis.vimed.api.auth.LoginGoogle;
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
        findViewById(R.id.btn_google).setOnClickListener(v -> entrarConGoogle());
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
                        VimedRepo.mensajeDeFallo(t), Toast.LENGTH_LONG).show();
                }
            });
    }

    // ═══ Entrar con Google ═════════════════════════════════════

    /**
     * Pide el token a Google y lo canjea por una sesión de Supabase.
     *
     * Al final reusa {@link #onLoginExitoso}: ese método solo usa la
     * contraseña para crear la fila local si no existe, y desde que la
     * fuente de verdad es Auth ese campo ya no se consulta para nada. Se
     * le pasa vacía en vez de duplicar todo el manejo de sesión.
     */
    private void entrarConGoogle() {
        setCargandoGoogle(true);

        LoginGoogle.pedirToken(this, new LoginGoogle.Callback() {
            @Override
            public void onToken(String idToken, String nonce) {
                SupabaseAuthClient.getService()
                    .signInConIdToken(new AuthPayloads.IdTokenRequest(idToken, nonce))
                    .enqueue(new Callback<AuthPayloads.AuthResponse>() {
                        @Override
                        public void onResponse(Call<AuthPayloads.AuthResponse> c,
                                               Response<AuthPayloads.AuthResponse> r) {
                            setCargandoGoogle(false);
                            AuthPayloads.AuthResponse body = r.body();
                            if (r.isSuccessful() && body != null && body.accessToken != null) {
                                String correo = body.user != null && body.user.email != null
                                    ? body.user.email : "";
                                onLoginExitoso(correo, "", body);
                            } else {
                                mostrarErrorRespuesta(r);
                            }
                        }

                        @Override
                        public void onFailure(Call<AuthPayloads.AuthResponse> c, Throwable t) {
                            setCargandoGoogle(false);
                            Toast.makeText(LoginActivity.this,
                                VimedRepo.mensajeDeFallo(t), Toast.LENGTH_LONG).show();
                        }
                    });
            }

            @Override
            public void onError(String mensaje, boolean cancelado) {
                setCargandoGoogle(false);
                // Si cerró la hoja de cuentas a propósito, no es un error
                // que haya que anunciarle.
                if (!cancelado) {
                    Toast.makeText(LoginActivity.this, mensaje, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void setCargandoGoogle(boolean cargando) {
        com.google.android.material.button.MaterialButton btn = findViewById(R.id.btn_google);
        if (btn == null) return;
        btn.setEnabled(!cargando);
        btn.setText(cargando ? "Entrando…" : "Continuar con Google");
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

        // Resolver el id_usuario de public.usuarios (lo crea si no existe)
        // ANTES de entrar a la app.
        //
        // Se esperaba a que terminara: se llamaba con callback null y se
        // navegaba de una. La primera pantalla se abría con el id todavía
        // sin resolver y todo lo que consulta a Supabase respondía "Sesión
        // no sincronizada", hasta que la respuesta llegaba sola y el
        // siguiente onResume ya andaba. Parecía un error de red al azar.
        //
        // La espera es corta —una consulta— y ocurre con el botón ya en
        // "Entrando…", así que no hay pantalla en blanco.
        com.tesis.vimed.api.PerfilSync.asegurarPerfil(
            this, authUserId, usuario.getNombre(), usuario.getCorreo(),
            usuario.getRol(), idUsuario -> {
                setLoading(false);
                // Se entra igual si no se pudo resolver: quedarse en el
                // login por un fallo de red sería peor, y la app reintenta
                // sola en cada pantalla.
                if (sessionManager.hasRole()) {
                    goToHome();
                } else {
                    startActivity(new Intent(this, RoleSelectionActivity.class));
                    finish();
                }
            });
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
