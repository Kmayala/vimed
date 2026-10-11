package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
        btnGoogle.setOnClickListener(v -> { aceptar(); entrarConGoogle(); });

        btnCreateAccount.setOnClickListener(v -> {
            aceptar();
            startActivity(new Intent(this, RegisterActivity.class));
        });

        btnLoginLink.setOnClickListener(v -> {
            aceptar();
            startActivity(new Intent(this, LoginActivity.class));
        });

        pintarAvisoLegal();
    }

    /**
     * "Al continuar aceptás" se vuelve verdad acá: en el toque, no antes.
     * Va en las tres salidas de la pantalla porque las tres continúan.
     */
    private void aceptar() {
        sessionManager.registrarAceptacionLegal();
    }

    /**
     * El aviso de abajo, con "Términos de uso" y "Política de privacidad"
     * tocables por separado.
     *
     * Se arma en código y no en el XML porque son dos enlaces distintos
     * dentro de una misma frase, y eso en un layout no se puede: haría
     * falta partirla en tres TextViews y la frase se cortaría raro en
     * cualquier pantalla angosta.
     *
     * No hay casilla de "acepto". Para esta app el consentimiento es
     * continuar, que es lo que dice la frase, y una casilla más entre un
     * adulto mayor y su primer uso es una barrera real. Si hiciera falta
     * el consentimiento explícito, es agregar un CheckBox acá y
     * condicionar los botones.
     */
    private void pintarAvisoLegal() {
        TextView aviso = findViewById(R.id.tv_legal);

        String frase = "Al continuar aceptás los Términos de uso y la "
            + "Política de privacidad";
        SpannableString s = new SpannableString(frase);

        enlazar(s, frase, "Términos de uso",
            () -> startActivity(LegalesActivity.terminos(this)));
        enlazar(s, frase, "Política de privacidad",
            () -> startActivity(LegalesActivity.privacidad(this)));

        aviso.setText(s);
        aviso.setMovementMethod(LinkMovementMethod.getInstance());
        // Sin esto queda un fondo de selección pegado después de tocar.
        aviso.setHighlightColor(android.graphics.Color.TRANSPARENT);
    }

    private void enlazar(SpannableString s, String frase, String trozo,
                         Runnable alTocar) {
        int desde = frase.indexOf(trozo);
        if (desde < 0) return;
        int hasta = desde + trozo.length();

        s.setSpan(new ClickableSpan() {
            @Override public void onClick(View v) { alTocar.run(); }
            @Override public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(ContextCompat.getColor(
                    WelcomeActivity.this, R.color.brand_600));
                ds.setUnderlineText(true);
                ds.setFakeBoldText(true);
            }
        }, desde, hasta, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
