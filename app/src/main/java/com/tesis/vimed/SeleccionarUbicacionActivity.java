package com.tesis.vimed;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Elegir un lugar en el mapa y devolverlo a quien la abrió.
 *
 * CÓMO ESTÁ HECHA. Mapa con el SDK de Google Maps para Android y búsqueda
 * con el {@link Geocoder} del sistema.
 *
 * QUÉ SE FACTURA DE ESTO: nada. El Maps SDK no cobra por carga de mapa en
 * Android, y el Geocoder lo resuelve el sistema operativo, no una API web
 * con cuota. Lo que sí cuesta es Places, y esta pantalla no lo usa. Google
 * igual exige una cuenta de facturación para emitir la clave.
 *
 * SIN CLAVE LA APP NO SE ROMPE. El mapa queda gris y aparece un cartel que
 * lo explica; el campo de texto sigue sirviendo para escribir la dirección
 * a mano. Que alguien clone el repo sin la clave no tiene por qué dejarlo
 * con una pantalla muda.
 *
 * EL PIN NO SE TOCA, SE MUEVE EL MAPA. El pin está fijo al centro de la
 * pantalla y lo que se arrastra es el mapa por debajo. Es el gesto que la
 * gente ya conoce de pedir un viaje, y evita tener que acertarle con el
 * dedo a un punto chiquito sobre una superficie que además se desplaza.
 */
public class SeleccionarUbicacionActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    /** Extras de entrada: dónde abrir el mapa, si ya se sabe. */
    public static final String EXTRA_LAT    = "lat";
    public static final String EXTRA_LNG    = "lng";
    public static final String EXTRA_TEXTO  = "texto";

    /** Extras de salida, en el Intent del RESULT_OK. */
    public static final String RESULT_LAT       = "lat";
    public static final String RESULT_LNG       = "lng";
    public static final String RESULT_DIRECCION = "direccion";

    /** Asunción, mientras no haya nada mejor a dónde apuntar. */
    private static final LatLng ASUNCION = new LatLng(-25.2637, -57.5759);
    private static final float ZOOM_LUGAR = 16.5f;

    private GoogleMap mapa;
    private TextView tvDireccion;
    private TextInputEditText etBuscar;
    private ProgressBar cargando;
    private View panelResultados;
    private LinearLayout resultadosContainer;

    /** Centro actual del mapa. Es lo que se devuelve al confirmar. */
    private double lat, lng;

    /** Dirección del centro. Puede quedar vacía y no pasa nada. */
    private String direccion = "";

    private final Handler enPantalla = new Handler(Looper.getMainLooper());

    /** El Geocoder bloquea, así que nunca se lo llama en el hilo principal. */
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();

    /**
     * Geocodificar en CADA movimiento del mapa dispararía una consulta por
     * píxel arrastrado. Se espera a que la mano se quede quieta.
     */
    private static final long ESPERA_ANTES_DE_CONSULTAR_MS = 600;
    private Runnable consultaPendiente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seleccionar_ubicacion);

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

        // Si la cita ya tenía un lugar escrito, se usa como primera búsqueda:
        // es lo que la persona ya había tipeado.
        String textoPrevio = getIntent().getStringExtra(EXTRA_TEXTO);
        if (textoPrevio != null && !textoPrevio.trim().isEmpty()) {
            etBuscar.setText(textoPrevio.trim());
        }

        if (BuildConfig.MAPS_API_KEY == null || BuildConfig.MAPS_API_KEY.isEmpty()) {
            // Sin clave el mapa se dibuja en gris y sin decir por qué. Se
            // avisa y se deja el resto de la pantalla usable: escribir la
            // dirección a mano sigue siendo una forma válida de cargarla.
            findViewById(R.id.tv_sin_clave).setVisibility(View.VISIBLE);
            // El pin queda flotando sobre el cartel si no se lo esconde.
            findViewById(R.id.pin).setVisibility(View.GONE);
            findViewById(R.id.btn_buscar).setEnabled(false);
            tvDireccion.setText("Escribí la dirección en el campo de arriba");
            return;
        }

        SupportMapFragment frag =
            (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapa);
        if (frag != null) frag.getMapAsync(this);
    }

    @Override
    public void onBackPressed() {
        // Con la lista de resultados abierta, "atrás" cierra la lista y
        // vuelve al mapa, no sale de la pantalla.
        if (panelResultados.getVisibility() == View.VISIBLE) {
            panelResultados.setVisibility(View.GONE);
            return;
        }
        super.onBackPressed();
    }

    // ═══ El mapa ═══════════════════════════════════════════════

    @Override
    public void onMapReady(GoogleMap google) {
        mapa = google;
        mapa.getUiSettings().setMapToolbarEnabled(false);
        mapa.getUiSettings().setZoomControlsEnabled(true);

        boolean hayPunto = lat != 0 || lng != 0;
        mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(
            hayPunto ? new LatLng(lat, lng) : ASUNCION,
            hayPunto ? ZOOM_LUGAR : 12f));

        // Al soltar el mapa, el centro es la ubicación elegida.
        mapa.setOnCameraIdleListener(() -> {
            LatLng c = mapa.getCameraPosition().target;
            lat = c.latitude;
            lng = c.longitude;
            programarConsultaDeDireccion();
        });

        if (hayPunto) {
            programarConsultaDeDireccion();
        } else if (etBuscar.getText() != null && etBuscar.getText().length() > 0) {
            // Sin coordenadas pero con texto: se busca solo, para no abrir
            // en un lugar cualquiera.
            buscar();
        }
    }

    private void moverMapaA(double aLat, double aLng) {
        if (mapa == null) return;
        mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(
            new LatLng(aLat, aLng), ZOOM_LUGAR));
    }

    // ═══ De coordenadas a dirección ════════════════════════════

    private void programarConsultaDeDireccion() {
        if (consultaPendiente != null) enPantalla.removeCallbacks(consultaPendiente);

        tvDireccion.setText("Buscando la dirección…");

        consultaPendiente = this::consultarDireccion;
        enPantalla.postDelayed(consultaPendiente, ESPERA_ANTES_DE_CONSULTAR_MS);
    }

    private void consultarDireccion() {
        final double aLat = lat, aLng = lng;

        fondo.execute(() -> {
            String encontrada = "";
            try {
                Geocoder geo = new Geocoder(this, new Locale("es"));
                List<Address> r = geo.getFromLocation(aLat, aLng, 1);
                if (r != null && !r.isEmpty()) encontrada = textoDe(r.get(0));
            } catch (IOException | IllegalArgumentException e) {
                // Sin servicio de geocoding o sin red: se muestran las
                // coordenadas. No es motivo para bloquear nada.
            }

            final String resultado = encontrada;
            enPantalla.post(() -> {
                // Si el mapa se siguió moviendo, esta respuesta ya no
                // corresponde al punto que está en pantalla.
                if (aLat != lat || aLng != lng) return;

                direccion = resultado;
                tvDireccion.setText(resultado.isEmpty()
                    ? String.format(Locale.getDefault(),
                        "Punto en el mapa (%.5f, %.5f)", aLat, aLng)
                    : resultado);
            });
        });
    }

    /** Arma una línea legible con lo que el Geocoder haya traído. */
    private String textoDe(Address a) {
        if (a.getMaxAddressLineIndex() >= 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i <= a.getMaxAddressLineIndex(); i++) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.getAddressLine(i));
            }
            return sb.toString();
        }
        // Sin línea armada, se junta lo que haya suelto.
        StringBuilder sb = new StringBuilder();
        if (a.getFeatureName() != null)  sb.append(a.getFeatureName());
        if (a.getThoroughfare() != null) agregar(sb, a.getThoroughfare());
        if (a.getLocality() != null)     agregar(sb, a.getLocality());
        if (a.getCountryName() != null)  agregar(sb, a.getCountryName());
        return sb.toString();
    }

    private void agregar(StringBuilder sb, String parte) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(parte);
    }

    // ═══ Búsqueda por nombre ═══════════════════════════════════

    private void buscar() {
        String q = etBuscar.getText() != null ? etBuscar.getText().toString().trim() : "";
        if (q.isEmpty()) return;

        esconderTeclado();
        cargando.setVisibility(View.VISIBLE);

        fondo.execute(() -> {
            final List<Address> encontrados = new ArrayList<>();
            String error = null;
            try {
                Geocoder geo = new Geocoder(this, new Locale("es"));
                List<Address> r = geo.getFromLocationName(q, 8);
                if (r != null) encontrados.addAll(r);
            } catch (IOException e) {
                error = "No se pudo buscar: revisá la conexión.";
            } catch (IllegalArgumentException e) {
                error = "No se entendió lo que escribiste.";
            }

            final String elError = error;
            enPantalla.post(() -> {
                cargando.setVisibility(View.GONE);

                if (elError != null) {
                    Toast.makeText(this, elError, Toast.LENGTH_LONG).show();
                    return;
                }
                if (encontrados.isEmpty()) {
                    Toast.makeText(this,
                        "No encontramos ese lugar. Probá con menos palabras,"
                            + " o movés el mapa a mano.",
                        Toast.LENGTH_LONG).show();
                    return;
                }
                // Un solo resultado no merece una lista de una fila.
                if (encontrados.size() == 1) { elegir(encontrados.get(0)); return; }
                mostrarResultados(encontrados);
            });
        });
    }

    private void mostrarResultados(List<Address> lugares) {
        resultadosContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Address lugar : lugares) {
            String completo = textoDe(lugar);
            if (completo.isEmpty()) continue;

            View fila = inflater.inflate(R.layout.item_resultado_lugar,
                resultadosContainer, false);

            int coma = completo.indexOf(',');
            ((TextView) fila.findViewById(R.id.tv_lugar_titulo)).setText(
                coma > 0 ? completo.substring(0, coma).trim() : completo);
            ((TextView) fila.findViewById(R.id.tv_lugar_detalle)).setText(
                coma > 0 ? completo.substring(coma + 1).trim() : "");

            fila.setOnClickListener(v -> elegir(lugar));
            resultadosContainer.addView(fila);
        }
        panelResultados.setVisibility(View.VISIBLE);
    }

    private void elegir(Address lugar) {
        panelResultados.setVisibility(View.GONE);
        lat = lugar.getLatitude();
        lng = lugar.getLongitude();
        direccion = textoDe(lugar);
        tvDireccion.setText(direccion);
        moverMapaA(lat, lng);
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
        fondo.shutdownNow();
        super.onDestroy();
    }
}
