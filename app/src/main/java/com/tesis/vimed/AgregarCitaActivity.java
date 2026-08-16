package com.tesis.vimed;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.tesis.vimed.database.CitaMedicaDAO;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.models.CitaMedica;
import com.tesis.vimed.models.Especialidad;

import java.util.Calendar;
import java.util.Locale;

public class AgregarCitaActivity extends AppCompatActivity {

    /** Extras opcionales: el cuidador agenda la cita a nombre del adulto mayor. */
    public static final String EXTRA_PARA_ID_USUARIO = "para_id_usuario";
    public static final String EXTRA_PARA_NOMBRE     = "para_nombre";

    private TextInputEditText etMedico, etEspecialidad, etFecha, etHora, etLugar, etNotas;
    private SessionManager sessionManager;
    private int selYear, selMonth, selDay, selHour, selMinute;
    private boolean fechaSeleccionada = false;

    /** -1 = la cita es para mí. */
    private int idUsuarioDestino = -1;
    private String nombreDestino = null;

    /** Especialidad elegida en la grilla, o null si todavía ninguna. */
    private Especialidad especialidadElegida = null;

    /** Cuántas especialidades entran por fila. */
    private static final int COLUMNAS = 3;

    // ═══ Grilla de especialidades ══════════════════════════════

    /**
     * Arma las filas desde el enum en vez de escribir once bloques en el
     * XML: agregar una especialidad es una línea en {@link Especialidad}.
     */
    private void armarGrillaDeEspecialidades() {
        LinearLayout grid = findViewById(R.id.especialidades_grid);
        if (grid == null) return;
        grid.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        Especialidad[] todas = Especialidad.values();
        LinearLayout fila = null;

        for (int i = 0; i < todas.length; i++) {
            if (i % COLUMNAS == 0) {
                fila = new LinearLayout(this);
                fila.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(8);
                fila.setLayoutParams(lp);
                grid.addView(fila);
            }

            final Especialidad esp = todas[i];
            View item = inflater.inflate(R.layout.item_especialidad, fila, false);
            ((LinearLayout.LayoutParams) item.getLayoutParams())
                .setMarginEnd(i % COLUMNAS == COLUMNAS - 1 ? 0 : dp(8));

            ((TextView) item.findViewById(R.id.tv_nombre)).setText(esp.nombre);
            item.setTag(esp);
            item.setOnClickListener(v -> elegirEspecialidad(esp));
            fila.addView(item);
        }

        // La última fila puede quedar corta; se rellena con huecos vacíos
        // para que los botones no se estiren al doble de ancho.
        int sobran = todas.length % COLUMNAS;
        if (sobran != 0 && fila != null) {
            for (int i = 0; i < COLUMNAS - sobran; i++) {
                View hueco = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, 1, 1f);
                lp.setMarginStart(dp(8));
                hueco.setLayoutParams(lp);
                fila.addView(hueco);
            }
        }

        pintarSeleccion();
    }

    private void elegirEspecialidad(Especialidad esp) {
        especialidadElegida = esp;
        pintarSeleccion();

        View til = findViewById(R.id.til_especialidad);
        til.setVisibility(esp.esOtra() ? View.VISIBLE : View.GONE);
        if (esp.esOtra()) etEspecialidad.requestFocus();
    }

    private void pintarSeleccion() {
        LinearLayout grid = findViewById(R.id.especialidades_grid);
        if (grid == null) return;

        for (int f = 0; f < grid.getChildCount(); f++) {
            View filaV = grid.getChildAt(f);
            if (!(filaV instanceof LinearLayout)) continue;
            LinearLayout fila = (LinearLayout) filaV;

            for (int c = 0; c < fila.getChildCount(); c++) {
                View item = fila.getChildAt(c);
                if (!(item.getTag() instanceof Especialidad)) continue;

                Especialidad esp = (Especialidad) item.getTag();
                boolean elegida = esp == especialidadElegida;
                int color = ContextCompat.getColor(this, esp.color);

                ImageView icono = item.findViewById(R.id.iv_icono);
                TextView nombre = item.findViewById(R.id.tv_nombre);

                // Elegida: tarjeta del color de la especialidad con todo en
                // blanco. Sin elegir: ícono a color sobre tarjeta neutra.
                GradientDrawable fondo = new GradientDrawable();
                fondo.setCornerRadius(dp(18));
                if (elegida) {
                    fondo.setColor(color);
                    icono.setColorFilter(ContextCompat.getColor(this, R.color.white));
                    nombre.setTextColor(ContextCompat.getColor(this, R.color.white));
                } else {
                    fondo.setColor(ContextCompat.getColor(this, R.color.paper_2));
                    fondo.setStroke(dp(1), ContextCompat.getColor(this, R.color.ink_7));
                    icono.setColorFilter(color);
                    nombre.setTextColor(ContextCompat.getColor(this, R.color.ink_2));
                }
                item.setBackground(fondo);
                icono.setImageResource(esp.icono);
            }
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agregar_cita);

        sessionManager = new SessionManager(this);

        idUsuarioDestino = getIntent().getIntExtra(EXTRA_PARA_ID_USUARIO, -1);
        nombreDestino    = getIntent().getStringExtra(EXTRA_PARA_NOMBRE);

        etMedico = findViewById(R.id.et_medico);
        etEspecialidad = findViewById(R.id.et_especialidad);
        etFecha = findViewById(R.id.et_fecha);
        etHora = findViewById(R.id.et_hora);
        etLugar = findViewById(R.id.et_lugar);
        etNotas = findViewById(R.id.et_notas);

        // Inicializar con fecha/hora actual
        Calendar cal = Calendar.getInstance();
        selYear = cal.get(Calendar.YEAR);
        selMonth = cal.get(Calendar.MONTH);
        selDay = cal.get(Calendar.DAY_OF_MONTH);
        selHour = cal.get(Calendar.HOUR_OF_DAY);
        selMinute = cal.get(Calendar.MINUTE);

        armarGrillaDeEspecialidades();

        etFecha.setOnClickListener(v -> mostrarDatePicker());
        etFecha.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) mostrarDatePicker(); });
        etHora.setOnClickListener(v -> mostrarTimePicker());
        etHora.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) mostrarTimePicker(); });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save).setOnClickListener(v -> guardarCita());
    }

    private void mostrarDatePicker() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            selYear = year;
            selMonth = month;
            selDay = day;
            fechaSeleccionada = true;
            etFecha.setText(String.format(Locale.getDefault(), "%02d/%02d/%d", day, month + 1, year));
        }, selYear, selMonth, selDay).show();
    }

    private void mostrarTimePicker() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            selHour = hour;
            selMinute = minute;
            etHora.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
        }, selHour, selMinute, true).show();
    }

    private void guardarCita() {
        String medico = etMedico.getText() != null ? etMedico.getText().toString().trim() : "";
        // La grilla define qué se guarda; el campo libre solo cuenta
        // cuando se eligió "Otra".
        String especialidad = "";
        if (especialidadElegida != null) {
            especialidad = especialidadElegida.esOtra()
                ? (etEspecialidad.getText() != null
                    ? etEspecialidad.getText().toString().trim() : "")
                : especialidadElegida.nombre;
        }
        String hora = etHora.getText() != null ? etHora.getText().toString().trim() : "";
        String lugar = etLugar.getText() != null ? etLugar.getText().toString().trim() : "";
        String notas = etNotas.getText() != null ? etNotas.getText().toString().trim() : "";

        if (medico.isEmpty()) {
            etMedico.setError("Ingrese el nombre del médico");
            etMedico.requestFocus();
            return;
        }
        if (!fechaSeleccionada) {
            Toast.makeText(this, "Seleccione una fecha para la cita", Toast.LENGTH_SHORT).show();
            return;
        }

        // Formato estándar para el DB: "yyyy-MM-dd HH:mm"
        String fechaHora = String.format(Locale.getDefault(), "%d-%02d-%02d %s",
            selYear, selMonth + 1, selDay, hora.isEmpty() ? "00:00" : hora);

        // El id_usuario lo completa el repositorio con el de public.usuarios
        CitaMedica cita = new CitaMedica(0, medico, especialidad, fechaHora, lugar, notas);
        cita.setEstado(CitaMedica.ESTADO_PENDIENTE);

        com.tesis.vimed.api.VimedRepo.Cb<CitaMedica> alGuardar =
            new com.tesis.vimed.api.VimedRepo.Cb<CitaMedica>() {
                @Override public void onOk(CitaMedica creada) {
                    Toast.makeText(AgregarCitaActivity.this,
                        idUsuarioDestino > 0
                            ? "Cita agendada para " + nombreDestino
                            : "Cita guardada",
                        Toast.LENGTH_SHORT).show();
                    finish();
                }
                @Override public void onError(String msg) {
                    Toast.makeText(AgregarCitaActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            };

        if (idUsuarioDestino > 0) {
            com.tesis.vimed.api.VimedRepo.crearCitaPara(idUsuarioDestino, cita, alGuardar);
        } else {
            com.tesis.vimed.api.VimedRepo.crearCita(this, cita, alGuardar);
        }
    }
}
