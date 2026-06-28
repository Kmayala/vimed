package com.tesis.vimed;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.HorarioDAO;
import com.tesis.vimed.database.MedicamentoDAO;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.utils.NotificationHelper;

public class AgregarMedicamentoActivity extends AppCompatActivity {

    // ── Datos recolectados en los 7 pasos ──────────────────────
    private String nombre = "";
    private float dosis = 0;
    private String unidad = "mg";
    private String presentacion = "Comprimido";
    private String instrucciones = "sin_restriccion";
    private String horaInicio = "08:00";
    private int intervaloHoras = 24;
    private boolean personalizado = false;
    private int stockActual = 0;
    private int stockMinimo = 5;
    private String colorIcono = "#2196F3";

    // ── Pasos y estado ─────────────────────────────────────────
    private View[] pasos;
    private int pasoActual = 0;

    private TextView tvTituloPaso, tvContadorPaso;
    private ProgressBar progressBar;

    private static final String[] TITULOS = {
        "Nombre", "Dosis", "Presentación",
        "Instrucciones", "Hora de inicio", "Frecuencia", "Stock"
    };

    private static final String[] PRESENTACIONES = {
        "Comprimido", "Cápsula", "Jarabe", "Inyectable",
        "Gotas", "Parche", "Inhalador", "Otro"
    };

    private static final String[] INSTRUCCIONES_TAGS = {
        "despues_comer", "antes_comer", "ayunas", "con_agua",
        "con_leche", "antes_dormir", "al_despertar", "sin_restriccion"
    };

    private static final int[] COLOR_BTN_IDS = {
        R.id.color_rojo, R.id.color_azul, R.id.color_amarillo,
        R.id.color_verde, R.id.color_morado, R.id.color_gris
    };

    private static final String[] COLORES = {
        "rojo", "azul", "amarillo",
        "verde", "morado", "gris"
    };

    // ── Lifecycle ──────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agregar_medicamento);

        tvTituloPaso  = findViewById(R.id.tv_titulo_paso);
        tvContadorPaso = findViewById(R.id.tv_contador_paso);
        progressBar   = findViewById(R.id.progress_pasos);

        pasos = new View[]{
            findViewById(R.id.paso1_nombre),
            findViewById(R.id.paso2_dosis),
            findViewById(R.id.paso3_presentacion),
            findViewById(R.id.paso4_instrucciones),
            findViewById(R.id.paso5a_hora),
            findViewById(R.id.paso5b_frecuencia),
            findViewById(R.id.paso6_stock)
        };

        mostrarPaso(0);
        configurarPaso1();
        configurarPaso2();
        configurarPaso3();
        configurarPaso4();
        configurarPaso5a();
        configurarPaso5b();
        configurarPaso6();

        // Botón atrás del header
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (pasoActual > 0) mostrarPaso(pasoActual - 1);
            else finish();
        });
    }

    // ── Navegación ─────────────────────────────────────────────
    private void mostrarPaso(int index) {
        for (View p : pasos) p.setVisibility(View.GONE);
        pasos[index].setVisibility(View.VISIBLE);
        pasoActual = index;
        tvTituloPaso.setText(TITULOS[index]);
        tvContadorPaso.setText((index + 1) + " / " + pasos.length);
        progressBar.setMax(pasos.length);
        progressBar.setProgress(index + 1);
    }

    // ── Paso 1: Nombre ─────────────────────────────────────────
    private void configurarPaso1() {
        AutoCompleteTextView etNombre = pasos[0].findViewById(R.id.et_nombre);
        String[] medicamentosComunes = {
            "Metformina", "Enalapril", "Aspirina", "Warfarina",
            "Omeprazol", "Losartán", "Atorvastatina", "Glibenclamida",
            "Furosemida", "Amlodipina", "Lisinopril", "Carvedilol",
            "Ibuprofeno", "Paracetamol", "Amoxicilina", "Metoprolol",
            "Espironolactona", "Digoxina", "Prednisona", "Alprazolam"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_dropdown_item_1line, medicamentosComunes);
        etNombre.setAdapter(adapter);
        etNombre.setThreshold(1);

        pasos[0].findViewById(R.id.btn_siguiente_1).setOnClickListener(v -> {
            nombre = etNombre.getText().toString().trim();
            if (!nombre.isEmpty()) mostrarPaso(1);
            else etNombre.setError(getString(R.string.error_empty_field));
        });
    }

    // ── Paso 2: Dosis ──────────────────────────────────────────
    private void configurarPaso2() {
        // Campo manual y spinner (ocultos al inicio)
        View tilDosis  = pasos[1].findViewById(R.id.til_dosis_manual);
        TextInputEditText etDosis = pasos[1].findViewById(R.id.et_dosis_manual);
        Spinner spUnidad = pasos[1].findViewById(R.id.sp_unidad);
        View btnSiguiente2 = pasos[1].findViewById(R.id.btn_siguiente_2);

        ArrayAdapter<String> unidadAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"mg", "ml", "mcg", "g", "UI"});
        unidadAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spUnidad.setAdapter(unidadAdapter);

        // Presets rápidos — navegan directo al paso 3
        pasos[1].findViewById(R.id.btn_500).setOnClickListener(v -> {
            dosis = 500; unidad = "mg"; mostrarPaso(2);
        });
        pasos[1].findViewById(R.id.btn_850).setOnClickListener(v -> {
            dosis = 850; unidad = "mg"; mostrarPaso(2);
        });
        pasos[1].findViewById(R.id.btn_1000).setOnClickListener(v -> {
            dosis = 1000; unidad = "mg"; mostrarPaso(2);
        });

        // Mostrar campo manual
        pasos[1].findViewById(R.id.btn_otra_dosis).setOnClickListener(v -> {
            tilDosis.setVisibility(View.VISIBLE);
            spUnidad.setVisibility(View.VISIBLE);
            btnSiguiente2.setVisibility(View.VISIBLE);
        });

        // Confirmar dosis manual
        btnSiguiente2.setOnClickListener(v -> {
            String dosisStr = etDosis.getText() != null
                ? etDosis.getText().toString().trim() : "";
            if (dosisStr.isEmpty()) {
                etDosis.setError(getString(R.string.error_empty_field));
                return;
            }
            try {
                dosis  = Float.parseFloat(dosisStr);
                unidad = spUnidad.getSelectedItem().toString();
                mostrarPaso(2);
            } catch (NumberFormatException e) {
                etDosis.setError("Número inválido");
            }
        });
    }

    // ── Paso 3: Presentación + Color ───────────────────────────
    private void configurarPaso3() {
        for (String p : PRESENTACIONES) {
            View btn = pasos[2].findViewWithTag(p);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    presentacion = p;
                    resaltarSeleccion(pasos[2], PRESENTACIONES, p);
                });
            }
        }

        // Círculos de color
        for (int i = 0; i < COLOR_BTN_IDS.length; i++) {
            final String color = COLORES[i];
            View circulo = pasos[2].findViewById(COLOR_BTN_IDS[i]);
            if (circulo != null) {
                circulo.setOnClickListener(v -> {
                    colorIcono = color;
                    // Borde visual en el seleccionado
                    for (int j = 0; j < COLOR_BTN_IDS.length; j++) {
                        View c = pasos[2].findViewById(COLOR_BTN_IDS[j]);
                        if (c != null) {
                            c.setAlpha(COLORES[j].equals(color) ? 1.0f : 0.5f);
                        }
                    }
                });
            }
        }

        pasos[2].findViewById(R.id.btn_siguiente_3).setOnClickListener(v -> mostrarPaso(3));
    }

    // ── Paso 4: Instrucciones ──────────────────────────────────
    private void configurarPaso4() {
        for (String tag : INSTRUCCIONES_TAGS) {
            View btn = pasos[3].findViewWithTag(tag);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    instrucciones = tag;
                    resaltarSeleccion(pasos[3], INSTRUCCIONES_TAGS, tag);
                });
            }
        }
        pasos[3].findViewById(R.id.btn_siguiente_4).setOnClickListener(v -> mostrarPaso(4));
    }

    // ── Paso 5a: Hora de inicio ────────────────────────────────
    private void configurarPaso5a() {
        TimePicker tp = pasos[4].findViewById(R.id.time_picker);
        tp.setIs24HourView(true);

        pasos[4].findViewById(R.id.btn_siguiente_5a).setOnClickListener(v -> {
            horaInicio = String.format("%02d:%02d", tp.getHour(), tp.getMinute());
            mostrarPaso(5);
        });
    }

    // ── Paso 5b: Frecuencia ────────────────────────────────────
    private void configurarPaso5b() {
        pasos[5].findViewById(R.id.btn_6h).setOnClickListener(v  -> { intervaloHoras = 6;  mostrarPaso(6); });
        pasos[5].findViewById(R.id.btn_8h).setOnClickListener(v  -> { intervaloHoras = 8;  mostrarPaso(6); });
        pasos[5].findViewById(R.id.btn_12h).setOnClickListener(v -> { intervaloHoras = 12; mostrarPaso(6); });
        pasos[5].findViewById(R.id.btn_24h).setOnClickListener(v -> { intervaloHoras = 24; mostrarPaso(6); });

        pasos[5].findViewById(R.id.btn_personalizado).setOnClickListener(v -> {
            personalizado = true;
            PersonalizadoBottomSheet sheet = new PersonalizadoBottomSheet(horaInicio,
                (horaPersonalizada, intervalo) -> {
                    horaInicio = horaPersonalizada;
                    intervaloHoras = intervalo;
                    mostrarPaso(6);
                });
            sheet.show(getSupportFragmentManager(), "personalizado");
        });
    }

    // ── Paso 6: Stock ──────────────────────────────────────────
    private void configurarPaso6() {
        TextInputEditText etStock    = pasos[6].findViewById(R.id.et_stock);
        TextInputEditText etStockMin = pasos[6].findViewById(R.id.et_stock_minimo);

        etStockMin.setText("5");

        pasos[6].findViewById(R.id.btn_guardar).setOnClickListener(v -> {
            String stockStr    = etStock.getText()    != null ? etStock.getText().toString().trim()    : "";
            String stockMinStr = etStockMin.getText() != null ? etStockMin.getText().toString().trim() : "";

            if (stockStr.isEmpty()) {
                etStock.setError(getString(R.string.error_empty_field));
                return;
            }
            try {
                stockActual = Integer.parseInt(stockStr);
                if (!stockMinStr.isEmpty()) stockMinimo = Integer.parseInt(stockMinStr);
            } catch (NumberFormatException e) {
                etStock.setError("Número inválido");
                return;
            }
            guardarMedicamento();
        });
    }

    // ── Guardar en BD ──────────────────────────────────────────
    private void guardarMedicamento() {
        DatabaseHelper db   = DatabaseHelper.getInstance(this);
        SessionManager ses  = new SessionManager(this);
        MedicamentoDAO mDAO = new MedicamentoDAO(db);
        HorarioDAO     hDAO = new HorarioDAO(db);

        Medicamento med = new Medicamento(
            ses.getIdUsuario(), nombre, presentacion,
            dosis, unidad, instrucciones, colorIcono, stockActual, stockMinimo
        );
        long idMed = mDAO.insertar(med);

        Horario horario = new Horario((int) idMed, horaInicio, intervaloHoras, personalizado);
        hDAO.insertar(horario);

        NotificationHelper.programarAlarmas(this, (int) idMed, horaInicio, intervaloHoras);

        Toast.makeText(this, "Medicamento guardado ✓", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ── Helper: resaltar botón seleccionado dentro de un grupo ─
    private void resaltarSeleccion(View container, String[] tags, String tagSeleccionado) {
        int colorSel    = ContextCompat.getColor(this, R.color.turquesa);
        int colorNormal = ContextCompat.getColor(this, R.color.texto_principal);
        int colorBorde  = ContextCompat.getColor(this, R.color.gris_borde);

        for (String tag : tags) {
            View view = container.findViewWithTag(tag);
            if (view instanceof MaterialButton) {
                MaterialButton mb = (MaterialButton) view;
                if (tag.equals(tagSeleccionado)) {
                    mb.setBackgroundTintList(ColorStateList.valueOf(colorSel));
                    mb.setTextColor(Color.WHITE);
                    mb.setStrokeColor(ColorStateList.valueOf(colorSel));
                } else {
                    mb.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                    mb.setTextColor(colorNormal);
                    mb.setStrokeColor(ColorStateList.valueOf(colorBorde));
                }
            }
        }
    }
}
