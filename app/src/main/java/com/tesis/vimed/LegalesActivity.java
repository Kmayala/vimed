package com.tesis.vimed;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Muestra los Términos de uso o la Política de privacidad.
 *
 * Una sola pantalla para los dos: cambia el título y el cuerpo. Son el
 * mismo tipo de documento y tenerlos en dos Activities idénticas sería
 * duplicar para nada.
 *
 * EL TEXTO VIVE EN strings_legales.xml, dentro del APK, y no en una
 * página web. Una URL se cae, cambia de dueño o queda sin pagar, y
 * entonces la app tiene un enlace muerto donde debería estar lo que la
 * persona aceptó. Además así se lee sin conexión, que para alguien que
 * instala la app en el consultorio no es un detalle menor.
 */
public class LegalesActivity extends AppCompatActivity {

    /** true para la política de privacidad, false para los términos. */
    private static final String EXTRA_PRIVACIDAD = "privacidad";

    public static Intent terminos(Context ctx) {
        return new Intent(ctx, LegalesActivity.class)
            .putExtra(EXTRA_PRIVACIDAD, false);
    }

    public static Intent privacidad(Context ctx) {
        return new Intent(ctx, LegalesActivity.class)
            .putExtra(EXTRA_PRIVACIDAD, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legales);

        boolean privacidad = getIntent().getBooleanExtra(EXTRA_PRIVACIDAD, false);

        ((TextView) findViewById(R.id.tv_titulo)).setText(
            privacidad ? R.string.privacidad_titulo : R.string.terminos_titulo);

        ((TextView) findViewById(R.id.tv_version)).setText(
            getString(R.string.legal_version_visible));

        // El texto lleva <b> para los subtítulos: sin negritas, doce
        // párrafos seguidos son un muro que nadie lee.
        String html = getString(privacidad
            ? R.string.privacidad_texto : R.string.terminos_texto);
        TextView cuerpo = findViewById(R.id.tv_cuerpo);
        cuerpo.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            ? Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            : Html.fromHtml(html));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
}
