package com.tesis.vimed;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

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

/**
 * Recibe el deep link vimed://reset-password#access_token=...&refresh_token=...&type=recovery
 * que abre el correo de recuperación, y permite escribir una nueva contraseña.
 *
 * Supabase envía los tokens en el FRAGMENT (#...) de la URL, no en la query.
 * Hay que parsearlo manualmente porque Uri.getQueryParameter() no los ve.
 */
public class ResetPasswordActivity extends AppCompatActivity {

    private TextInputLayout tilPassword, tilConfirm;
    private TextInputEditText etPassword, etConfirm;
    private MaterialButton btnSave;

    private String accessToken;   // token de recovery — solo sirve para cambiar la pass

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        tilPassword = findViewById(R.id.til_password);
        tilConfirm  = findViewById(R.id.til_confirm_password);
        etPassword  = findViewById(R.id.et_password);
        etConfirm   = findViewById(R.id.et_confirm_password);
        btnSave     = findViewById(R.id.btn_save);

        accessToken = extraerAccessToken(getIntent());

        if (accessToken == null) {
            new AlertDialog.Builder(this)
                .setTitle("Enlace inválido")
                .setMessage("El enlace de recuperación expiró o no es válido. "
                    + "Volvé a pedir uno nuevo desde la pantalla de inicio de sesión.")
                .setPositiveButton("Entendido", (d, w) -> volverAlInicio())
                .setCancelable(false)
                .show();
            return;
        }

        btnSave.setOnClickListener(v -> guardarPassword());
    }

    private String extraerAccessToken(Intent intent) {
        if (intent == null || intent.getData() == null) return null;
        Uri uri = intent.getData();
        String fragment = uri.getFragment();
        if (fragment == null) return null;
        // formato: access_token=xxx&refresh_token=yyy&type=recovery&...
        for (String part : fragment.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq);
            String val = part.substring(eq + 1);
            if ("access_token".equals(key)) return val;
        }
        return null;
    }

    private void guardarPassword() {
        String pass = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String conf = etConfirm.getText()  != null ? etConfirm.getText().toString()  : "";

        tilPassword.setError(null);
        tilConfirm.setError(null);

        if (pass.isEmpty()) {
            tilPassword.setError(getString(R.string.error_empty_field));
            return;
        }
        if (pass.length() < 6) {
            tilPassword.setError(getString(R.string.error_password_short));
            return;
        }
        if (!pass.equals(conf)) {
            tilConfirm.setError(getString(R.string.error_passwords_mismatch));
            return;
        }

        setLoading(true);

        SupabaseAuthClient.getService()
            .updatePassword("Bearer " + accessToken, new AuthPayloads.UpdateUserRequest(pass))
            .enqueue(new Callback<AuthPayloads.AuthUser>() {
                @Override
                public void onResponse(Call<AuthPayloads.AuthUser> c,
                                       Response<AuthPayloads.AuthUser> r) {
                    setLoading(false);
                    if (r.isSuccessful()) {
                        Toast.makeText(ResetPasswordActivity.this,
                            "Contraseña actualizada. Iniciá sesión.",
                            Toast.LENGTH_LONG).show();
                        volverAlInicio();
                    } else {
                        tilPassword.setError("No pudimos guardar. El enlace puede haber expirado.");
                    }
                }

                @Override
                public void onFailure(Call<AuthPayloads.AuthUser> c, Throwable t) {
                    setLoading(false);
                    tilPassword.setError(VimedRepo.mensajeDeFallo(t));
                }
            });
    }

    private void setLoading(boolean loading) {
        btnSave.setEnabled(!loading);
        btnSave.setText(loading ? "Guardando..." : "Guardar contraseña");
        etPassword.setEnabled(!loading);
        etConfirm.setEnabled(!loading);
    }

    private void volverAlInicio() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
