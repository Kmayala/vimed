package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class WelcomeActivity extends AppCompatActivity {

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        sessionManager = new SessionManager(this);

        if (sessionManager.isLoggedIn() && sessionManager.hasRole()) {
            goToHome();
            return;
        }

        MaterialButton btnGoogle = findViewById(R.id.btn_google);
        MaterialButton btnCreateAccount = findViewById(R.id.btn_create_account);
        MaterialButton btnLoginLink = findViewById(R.id.btn_login_link);

        // El botón que más se toca de toda la app: es el primero que ve
        // alguien que abre Vimed por primera vez. Mostraba "próximamente"
        // aunque el login con Google ya estuviera funcionando en la
        // pantalla de iniciar sesión, dos toques más adentro.
        btnGoogle.setOnClickListener(v -> entrarConGoogle());

        btnCreateAccount.setOnClickListener(v ->
            startActivity(new Intent(this, RegisterActivity.class))
        );

        btnLoginLink.setOnClickListener(v ->
            startActivity(new Intent(this, LoginActivity.class))
        );
    }

    private void entrarConGoogle() {
        setCargandoGoogle(true);

        com.tesis.vimed.api.auth.SesionGoogle.iniciar(this,
            new com.tesis.vimed.api.auth.SesionGoogle.Callback() {
                @Override public void onEntro(boolean tieneRol) {
                    setCargandoGoogle(false);
                    // Quien entra desde acá suele ser alguien nuevo, así que
                    // lo normal es que todavía no haya elegido el rol.
                    if (tieneRol) {
                        goToHome();
                    } else {
                        startActivity(new Intent(WelcomeActivity.this,
                            RoleSelectionActivity.class));
                        finish();
                    }
                }
                @Override public void onError(String mensaje) {
                    setCargandoGoogle(false);
                    Toast.makeText(WelcomeActivity.this, mensaje, Toast.LENGTH_LONG).show();
                }
                @Override public void onCancelado() {
                    setCargandoGoogle(false);
                }
            });
    }

    private void setCargandoGoogle(boolean cargando) {
        MaterialButton btn = findViewById(R.id.btn_google);
        if (btn == null) return;
        btn.setEnabled(!cargando);
        btn.setText(cargando ? "Entrando…" : "Continuar con Google");
    }

    private void goToHome() {
        Router.irAlHome(this);
    }
}
