package com.tesis.vimed;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.tesis.vimed.database.CitaMedicaDAO;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.models.CitaMedica;

import java.util.Calendar;
import java.util.Locale;

public class AgregarCitaActivity extends AppCompatActivity {

    private TextInputEditText etMedico, etEspecialidad, etFecha, etHora, etLugar, etNotas;
    private SessionManager sessionManager;
    private int selYear, selMonth, selDay, selHour, selMinute;
    private boolean fechaSeleccionada = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agregar_cita);

        sessionManager = new SessionManager(this);

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
        String especialidad = etEspecialidad.getText() != null ? etEspecialidad.getText().toString().trim() : "";
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

        CitaMedica cita = new CitaMedica(
            sessionManager.getIdUsuario(), medico, especialidad, fechaHora, lugar, notas
        );

        new CitaMedicaDAO(DatabaseHelper.getInstance(this)).insertar(cita);

        Toast.makeText(this, "Cita guardada", Toast.LENGTH_SHORT).show();
        finish();
    }
}
