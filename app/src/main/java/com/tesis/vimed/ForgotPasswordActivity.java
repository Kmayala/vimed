package com.tesis.vimed;

import android.os.Bundle;
import android.util.Patterns;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.api.auth.AuthPayloads;
import com.tesis.vimed.api.auth.SupabaseAuthClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends AppCompatActivity {

    /**
     * El deep link que va a abrir el correo de recuperación.
     * Tiene que estar en la lista de Redirect URLs del Dashboard de Supabase
     * (Authentication → URL Configuration).
     */
    public static final String RESET_REDIRECT = "vimed://reset-password";

    private TextInputLayout tilEmail;
    private TextInputEditText etEmail;
    private MaterialButton btnSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tilEmail = findViewById(R.id.til_email);
        etEmail = findViewById(R.id.et_email);
        btnSend = findViewById(R.id.btn_send);

        // Pre-llenar el correo si viene del Login
        String prefill = getIntent().getStringExtra("email");
        if (prefill != null) etEmail.setText(prefill);

        btnSend.setOnClickListener(v -> enviarEnlace());
    }

    private void enviarEnlace() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        tilEmail.setError(null);

        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_empty_field));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_invalid_email));
            return;
        }

        setLoading(true);

        SupabaseAuthClient.getService()
            .recoverPassword(new AuthPayloads.RecoverRequest(email), RESET_REDIRECT)
            .enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> c, Response<Void> r) {
                    setLoading(false);
                    // Supabase devuelve 200 igual si el correo no existe
                    // (evita enumeración de usuarios) — siempre mostramos éxito.
                    if (r.isSuccessful()) {
                        mostrarExito(email);
                    } else {
                        tilEmail.setError("No pudimos enviar el correo. Intentá de nuevo.");
                    }
                }

                @Override
                public void onFailure(Call<Void> c, Throwable t) {
                    setLoading(false);
                    tilEmail.setError(VimedRepo.mensajeDeFallo(t));
                }
            });
    }

    private void mostrarExito(String email) {
        new AlertDialog.Builder(this)
            .setTitle("Correo enviado")
            .setMessage("Si " + email + " está registrado, te enviamos un enlace para "
                + "crear una nueva contraseña. Revisá tu bandeja de entrada (y spam).")
            .setPositiveButton("Entendido", (d, w) -> finish())
            .setCancelable(false)
            .show();
    }

    private void setLoading(boolean loading) {
        btnSend.setEnabled(!loading);
        btnSend.setText(loading ? "Enviando..." : "Enviar enlace");
        etEmail.setEnabled(!loading);
    }
}
