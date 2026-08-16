package com.tesis.vimed;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tesis.vimed.utils.TemaManager;

/**
 * Configuración de la app. Por ahora, la apariencia.
 *
 * Tres opciones y no un interruptor de dos posiciones: con un switch no
 * hay forma de decir "seguí a mi celular", que es lo que la mayoría de
 * la gente quiere y además es el estado inicial.
 */
public class ConfiguracionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuracion);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.opt_sistema).setOnClickListener(v -> elegir(TemaManager.SISTEMA));
        findViewById(R.id.opt_claro).setOnClickListener(v   -> elegir(TemaManager.CLARO));
        findViewById(R.id.opt_oscuro).setOnClickListener(v  -> elegir(TemaManager.OSCURO));

        pintarElegido(TemaManager.modoGuardado(this));
    }

    private void elegir(int modo) {
        if (modo == TemaManager.modoGuardado(this)) return;

        pintarElegido(modo);
        // Esto recrea la Activity para aplicar el tema nuevo: el tilde ya
        // quedó pintado antes para que el estado sobreviva a la recreación
        // sin que se vea saltar.
        TemaManager.guardarYAplicar(this, modo);
    }

    private void pintarElegido(int modo) {
        marcar(R.id.check_sistema, modo == TemaManager.SISTEMA);
        marcar(R.id.check_claro,   modo == TemaManager.CLARO);
        marcar(R.id.check_oscuro,  modo == TemaManager.OSCURO);
    }

    /**
     * El tilde de la opción elegida. Las otras dos se dejan invisibles en
     * vez de quitarlas: así las tres filas miden lo mismo y el texto no
     * se corre al cambiar de opción.
     */
    private void marcar(int idCheck, boolean elegido) {
        ImageView iv = findViewById(idCheck);
        if (iv == null) return;
        iv.setVisibility(elegido ? View.VISIBLE : View.INVISIBLE);
        iv.setColorFilter(ContextCompat.getColor(this, R.color.brand_600));
    }
}
