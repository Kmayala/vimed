package com.tesis.vimed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tesis.vimed.utils.PermisosAlarma;
import com.tesis.vimed.utils.TemaManager;

import java.util.List;

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

        ((TextView) findViewById(R.id.tv_version))
            .setText("Vimed " + BuildConfig.VERSION_NAME);
    }

    /**
     * Al volver de los ajustes del sistema hay que repintar: la persona
     * acaba de conceder (o no) el permiso, y la lista tiene que reflejarlo
     * sin que tenga que salir y entrar de nuevo.
     */
    @Override
    protected void onResume() {
        super.onResume();
        pintarRevisionDeAlarma();
    }

    private void pintarRevisionDeAlarma() {
        LinearLayout cont = findViewById(R.id.permisos_container);
        TextView resumen = findViewById(R.id.tv_alarma_resumen);
        if (cont == null) return;
        cont.removeAllViews();

        List<PermisosAlarma.Requisito> requisitos = PermisosAlarma.revisar(this);
        int faltan = PermisosAlarma.faltantesComprobables(this);

        resumen.setText(faltan == 0
            ? "Todo lo que podemos comprobar está en orden."
            : (faltan == 1
                ? "Falta 1 permiso para que la alarma suene siempre."
                : "Faltan " + faltan + " permisos para que la alarma suene siempre."));

        LayoutInflater inflater = LayoutInflater.from(this);
        for (PermisosAlarma.Requisito r : requisitos) {
            View fila = inflater.inflate(R.layout.item_permiso_alarma, cont, false);

            ((TextView) fila.findViewById(R.id.tv_titulo)).setText(r.titulo);
            ((TextView) fila.findViewById(R.id.tv_detalle)).setText(r.detalle);

            ImageView estado = fila.findViewById(R.id.iv_estado);
            estado.setImageResource(r.cumplido ? R.drawable.ic_check : R.drawable.ic_warn);
            estado.setColorFilter(ContextCompat.getColor(this,
                r.cumplido ? R.color.success : R.color.warn));

            // Los cumplidos siguen siendo tocables: sirve para revisar o
            // revertir, y una fila que deja de responder desconcierta.
            if (r.abrirAjuste != null) {
                fila.setOnClickListener(v -> r.abrirAjuste.run());
            } else {
                fila.findViewById(R.id.iv_flecha).setVisibility(View.INVISIBLE);
            }

            cont.addView(fila);
        }
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
