package com.tesis.vimed;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.tesis.vimed.api.NominatimClient;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Elegir un lugar en el mapa y devolverlo a quien la abrió.
 *
 * CÓMO ESTÁ HECHA. El mapa es OpenStreetMap dibujado con Leaflet dentro de
 * un WebView; los archivos de Leaflet están empaquetados en assets, así que
 * no se descarga una librería de un CDN en cada apertura. La búsqueda por
 * nombre va a Nominatim (ver {@link NominatimClient}).
 *
 * POR QUÉ NO GOOGLE MAPS. El SDK de Maps y la API de Places piden una clave
 * con facturación activa y cobran por consulta. OpenStreetMap no pide clave
 * ni tarjeta, y para encontrar un hospital o una calle alcanza de sobra.
 *
 * EL PIN NO SE TOCA, SE MUEVE EL MAPA. El pin está fijo al centro de la
 * pantalla y lo que se arrastra es el mapa por debajo. Es el gesto que la
 * gente ya conoce de pedir un viaje, y evita tener que acertarle con el
 * dedo a un punto chiquito sobre una superficie que además se desplaza.
 */
public class SeleccionarUbicacionActivity extends AppCompatActivity {

    /** Extras de entrada: dónde abrir el mapa, si ya se sabe. */
    public static final String EXTRA_LAT    = "lat";
    public static final String EXTRA_LNG    = "lng";
    public static final String EXTRA_TEXTO  = "texto";

    /** Extras de salida, en el Intent del RESULT_OK. */
    public static final String RESULT_LAT       = "lat";
    public static final String RESULT_LNG       = "lng";
    public static final String RESULT_DIRECCION = "direccion";

    private WebView mapa;
    private TextView tvDireccion;
    private TextInputEditText etBuscar;
    private ProgressBar cargando;
    private View panelResultados;
    private LinearLayout resultadosContainer;

    /** Centro actual del mapa. Es lo que se devuelve al confirmar. */
    private double lat, lng;

    /** Dirección del centro, resuelta por Nominatim. Puede quedar vacía. */
    private String direccion = "";

    /** El mapa recién acepta órdenes cuando el HTML terminó de cargar. */
    private boolean mapaListo = false;

    private final Handler enPantalla = new Handler(Looper.getMainLooper());

    /**
     * Consultar la dirección en CADA movimiento del mapa dispararía una
     * llamada por cada píxel arrastrado, y Nominatim pide explícitamente no
     * hacer eso. Se espera a que la mano se quede quieta.
     */
    private static final long ESPERA_ANTES_DE_CONSULTAR_MS = 700;
    private Runnable consultaPendiente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seleccionar_ubicacion);

        mapa                = findViewById(R.id.mapa);
        tvDireccion         = findViewById(R.id.tv_direccion);
        etBuscar            = findViewById(R.id.et_buscar);
        cargando            = findViewById(R.id.cargando);
        panelResultados     = findViewById(R.id.panel_resultados);
        resultadosContainer = findViewById(R.id.resultados_container);

        lat = getIntent().getDoubleExtra(EXTRA_LAT, 0);
        lng = getIntent().getDoubleExtra(EXTRA_LNG, 0);

        findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btn_confirmar).setOnClickListener(v -> confirmar());
        findViewById(R.id.btn_buscar).setOnClickListener(v -> buscar());

        etBuscar.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { buscar(); return true; }
            return false;
        });

        // Si la cita ya tenía un lugar escrito pero sin coordenadas, se usa
        // como primera búsqueda: es lo que la persona ya había tipeado.
        String textoPrevio = getIntent().getStringExtra(EXTRA_TEXTO);
        if (textoPrevio != null && !textoPrevio.trim().isEmpty()) {
            etBuscar.setText(textoPrevio.trim());
        }

        prepararMapa();
    }

    @Override
    public void onBackPressed() {
        // Con la lista de resultados abierta, "atrás" tiene que cerrarla y
        // volver al mapa, no salir de la pantalla.
        if (panelResultados.getVisibility() == View.VISIBLE) {
            cerrarResultados();
            return;
        }
        super.onBackPressed();
    }

    // ═══ El mapa ═══════════════════════════════════════════════

    @SuppressLint("SetJavaScriptEnabled")
    private void prepararMapa() {
        // JavaScript es la única forma de que Leaflet funcione. El WebView
        // solo carga un archivo nuestro de assets y los tiles de OSM; no
        // navega a ningún lado que escriba la persona.
        mapa.getSettings().setJavaScriptEnabled(true);
        mapa.getSettings().setDomStorageEnabled(true);
        mapa.addJavascriptInterface(new PuenteMapa(), "Android");

        mapa.setWebViewClient(new WebViewClient());
        mapa.loadUrl("file:///android_asset/mapa/mapa.html");
    }

    /** Lo que el mapa puede contarle a Android. */
    private class PuenteMapa {

        @JavascriptInterface
        public void mapaListo() {
            enPantalla.post(() -> {
                mapaListo = true;
                if (lat != 0 || lng != 0) {
                    moverMapaA(lat, lng);
                } else if (etBuscar.getText() != null
                        && etBuscar.getText().length() > 0) {
                    // Sin coordenadas pero con texto: se busca solo, para
                    // que el mapa no abra en un lugar cualquiera.
                    buscar();
                }
            });
        }

        @JavascriptInterface
        public void centroCambio(double nuevaLat, double nuevaLng) {
            enPantalla.post(() -> {
                lat = nuevaLat;
                lng = nuevaLng;
                programarConsultaDeDireccion();
            });
        }
    }

    private void moverMapaA(double aLat, double aLng) {
        if (!mapaListo) return;
        mapa.evaluateJavascript(
            String.format(Locale.US, "window.irA(%f, %f, 17);", aLat, aLng), null);
    }

    // ═══ De coordenadas a dirección ════════════════════════════

    private void programarConsultaDeDireccion() {
        if (consultaPendiente != null) enPantalla.removeCallbacks(consultaPendiente);

        tvDireccion.setText("Buscando la dirección…");

        consultaPendiente = this::consultarDireccion;
        enPantalla.postDelayed(consultaPendiente, ESPERA_ANTES_DE_CONSULTAR_MS);
    }

    private void consultarDireccion() {
        NominatimClient.get().direccionDe(lat, lng, "es")
            .enqueue(new Callback<NominatimClient.Lugar>() {
                @Override
                public void onResponse(Call<NominatimClient.Lugar> c,
                                       Response<NominatimClient.Lugar> r) {
                    if (r.isSuccessful() && r.body() != null
                            && r.body().nombreCompleto != null) {
                        direccion = r.body().nombreCompleto;
                        tvDireccion.setText(direccion);
                    } else {
                        sinDireccion();
                    }
                }

                @Override
                public void onFailure(Call<NominatimClient.Lugar> c, Throwable t) {
                    sinDireccion();
                }
            });
    }

    /**
     * Sin conexión o sin resultado, el punto igual sirve: se muestran las
     * coordenadas y se puede confirmar. Bloquear el botón por no haber
     * conseguido un nombre sería perder una ubicación que ya está elegida.
     */
    private void sinDireccion() {
        direccion = "";
        tvDireccion.setText(String.format(Locale.getDefault(),
            "Punto en el mapa (%.5f, %.5f)", lat, lng));
    }

    // ═══ Búsqueda por nombre ═══════════════════════════════════

    private void buscar() {
        String q = etBuscar.getText() != null ? etBuscar.getText().toString().trim() : "";
        if (q.isEmpty()) return;

        esconderTeclado();
        cargando.setVisibility(View.VISIBLE);

        // Sesgado a Paraguay, sin excluir al resto: es donde está el 99% de
        // lo que va a buscar quien usa esto.
        NominatimClient.get().buscar(q, "es", "py")
            .enqueue(new Callback<List<NominatimClient.Lugar>>() {
                @Override
                public void onResponse(Call<List<NominatimClient.Lugar>> c,
                                       Response<List<NominatimClient.Lugar>> r) {
                    cargando.setVisibility(View.GONE);
                    List<NominatimClient.Lugar> lugares =
                        r.isSuccessful() && r.body() != null ? r.body() : null;

                    if (lugares == null || lugares.isEmpty()) {
                        Toast.makeText(SeleccionarUbicacionActivity.this,
                            "No encontramos ese lugar. Probá con menos palabras,"
                                + " o movés el mapa a mano.",
                            Toast.LENGTH_LONG).show();
                        return;
                    }
                    // Un solo resultado no merece una lista de una fila.
                    if (lugares.size() == 1) { elegir(lugares.get(0)); return; }
                    mostrarResultados(lugares);
                }

                @Override
                public void onFailure(Call<List<NominatimClient.Lugar>> c, Throwable t) {
                    cargando.setVisibility(View.GONE);
                    Toast.makeText(SeleccionarUbicacionActivity.this,
                        "No se pudo buscar: revisá la conexión.",
                        Toast.LENGTH_LONG).show();
                }
            });
    }

    private void mostrarResultados(List<NominatimClient.Lugar> lugares) {
        resultadosContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (NominatimClient.Lugar lugar : lugares) {
            View fila = inflater.inflate(R.layout.item_resultado_lugar,
                resultadosContainer, false);
            ((TextView) fila.findViewById(R.id.tv_lugar_titulo)).setText(lugar.titulo());
            ((TextView) fila.findViewById(R.id.tv_lugar_detalle)).setText(lugar.detalle());
            fila.setOnClickListener(v -> elegir(lugar));
            resultadosContainer.addView(fila);
        }
        panelResultados.setVisibility(View.VISIBLE);
    }

    private void elegir(NominatimClient.Lugar lugar) {
        cerrarResultados();
        lat = lugar.latitud();
        lng = lugar.longitud();
        direccion = lugar.nombreCompleto;
        tvDireccion.setText(direccion);
        moverMapaA(lat, lng);
    }

    private void cerrarResultados() {
        panelResultados.setVisibility(View.GONE);
    }

    private void esconderTeclado() {
        InputMethodManager imm =
            (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && etBuscar.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(etBuscar.getWindowToken(), 0);
        }
    }

    // ═══ Devolver el resultado ═════════════════════════════════

    private void confirmar() {
        if (lat == 0 && lng == 0) {
            Toast.makeText(this, "Todavía no elegiste un punto en el mapa.",
                Toast.LENGTH_SHORT).show();
            return;
        }

        Intent datos = new Intent();
        datos.putExtra(RESULT_LAT, lat);
        datos.putExtra(RESULT_LNG, lng);
        // Sin dirección resuelta se devuelven las coordenadas como texto: el
        // campo de la cita nunca queda vacío después de elegir algo.
        datos.putExtra(RESULT_DIRECCION,
            direccion != null && !direccion.isEmpty()
                ? direccion
                : String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng));

        setResult(RESULT_OK, datos);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (consultaPendiente != null) enPantalla.removeCallbacks(consultaPendiente);
        if (mapa != null) mapa.destroy();
        super.onDestroy();
    }
}
