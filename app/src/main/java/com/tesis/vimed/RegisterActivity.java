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

public class RegisterActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirmPassword;
    private TextInputEditText etName, etEmail, etPassword, etConfirmPassword;

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

        MaterialButton btnRegister = findViewById(R.id.btn_register);
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

        UsuarioDAO usuarioDAO = new UsuarioDAO(DatabaseHelper.getInstance(this));

        if (usuarioDAO.buscarPorCorreo(email) != null) {
            tilEmail.setError(getString(R.string.error_email_exists));
            return;
        }

        Usuario nuevoUsuario = new Usuario(name, email, password, "");
        long id = usuarioDAO.insertar(nuevoUsuario);

        if (id == -1) {
            tilEmail.setError("Error al registrar. Intentá de nuevo.");
            return;
        }

        sessionManager.saveSession((int) id, name, email, "");
        startActivity(new Intent(this, RoleSelectionActivity.class));
        finish();
    }
}
