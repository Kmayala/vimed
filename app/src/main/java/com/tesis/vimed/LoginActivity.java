package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.UsuarioDAO;
import com.tesis.vimed.models.Usuario;

public class LoginActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;

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

        MaterialButton btnLogin = findViewById(R.id.btn_login);
        MaterialButton tvRegister = findViewById(R.id.tv_register);

        btnLogin.setOnClickListener(v -> attemptLogin());

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
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

        UsuarioDAO usuarioDAO = new UsuarioDAO(DatabaseHelper.getInstance(this));

        if (!usuarioDAO.validarLogin(email, password)) {
            tilPassword.setError(getString(R.string.error_invalid_credentials));
            return;
        }

        Usuario usuario = usuarioDAO.buscarPorCorreo(email);
        if (usuario != null) {
            sessionManager.saveSession(usuario.getId(), usuario.getNombre(),
                usuario.getCorreo(), usuario.getRol());
        }

        if (sessionManager.hasRole()) {
            goToHome();
        } else {
            startActivity(new Intent(this, RoleSelectionActivity.class));
            finish();
        }
    }

    private void goToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
