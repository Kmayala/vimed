package com.tesis.vimed;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.utils.MedCache;
import com.tesis.vimed.utils.MedicamentoUI;
import com.tesis.vimed.utils.ModoPaciente;
import com.tesis.vimed.utils.NotificationHelper;
import com.tesis.vimed.utils.VencimientoChecker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Corregir un medicamento ya cargado.
 *
 * Es una pantalla plana y no el asistente de siete pasos con el que se
 * carga uno nuevo. El asistente sirve para preguntar de a una cosa a quien
 * todavía no sabe cuántas le van a preguntar; para corregir, la persona ya
 * sabe qué viene a cambiar, y hacerla pasar por siete pantallas para tocar
 * un número es un castigo.
 *
 * QUÉ NO SE PUEDE HACER ACÁ. Vaciar el vencimiento una vez cargado: Gson
 * omite los campos en null, así que un "borralo" viajaría como "no lo
 * toques" y la fecha quedaría igual sin que nadie se entere. Se puede
 * cambiar por otra, que es lo que pasa al reponer una caja.
 */
public class EditarMedicamentoActivity extends AppCompatActivity {

    public static final String EXTRA_ID_MEDICAMENTO = "id_medicamento";

    private static final String[] UNIDADES = {"mg", "ml", "mcg", "g", "UI"};

    private static final String[] PRESENTACIONES = {
        "Comprimido", "Cápsula", "Jarabe", "Inyectable",
        "Gotas", "Parche", "Inhalador", "Otro"
    };

    private static final String[] INSTRUCCIONES_TAGS = {
        "despues_comer", "antes_comer", "ayunas", "con_agua",
        "con_leche", "antes_dormir", "al_despertar", "sin_restriccion"
    };

    /** Las mismas que ofrece el asistente al cargar. */
    private static final int[] INTERVALOS = {4, 6, 8, 12, 24};

    private ModoPaciente modo;
    private int idMedicamento = -1;

    private Medicamento med;
    private Horario horario;             // el primero; ver cargarHorarios()
    private final List<Horario> todos = new ArrayList<>();

    // Lo elegido en los campos que no se escriben a mano
    private String unidad = "mg";
    private String presentacion = "Comprimido";
    private String instrucciones = "sin_restriccion";
    private String horaInicio = "08:00";
    private int intervaloHoras = 24;
    private String fechaVencimiento = null;

    private TextInputEditText etNombre, etDosis, etUnidad, etPresentacion,
        etInstrucciones, etHora, etFrecuencia, etStock, etStockMin, etVencimiento;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_medicamento);

        modo = ModoPaciente.de(this);
        idMedicamento = getIntent().getIntExtra(EXTRA_ID_MEDICAMENTO, -1);

        etNombre        = findViewById(R.id.et_nombre);
        etDosis         = findViewById(R.id.et_dosis);
        etUnidad        = findViewById(R.id.et_unidad);
        etPresentacion  = findViewById(R.id.et_presentacion);
        etInstrucciones = findViewById(R.id.et_instrucciones);
        etHora          = findViewById(R.id.et_hora);
        etFrecuencia    = findViewById(R.id.et_frecuencia);
        etStock         = findViewById(R.id.et_stock);
        etStockMin      = findViewById(R.id.et_stock_minimo);
        etVencimiento   = findViewById(R.id.et_vencimiento);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_guardar).setOnClickListener(v -> guardar());

        // Los campos de elegir son focusable=false para que no salte el
        // teclado; el click va también en la caja, que es la mitad del área
        // que el dedo apunta.
        alTocar(R.id.et_unidad,        R.id.til_unidad,        this::elegirUnidad);
        alTocar(R.id.et_presentacion,  R.id.til_presentacion,  this::elegirPresentacion);
        alTocar(R.id.et_instrucciones, R.id.til_instrucciones, this::elegirInstrucciones);
        alTocar(R.id.et_hora,          R.id.til_hora,          this::elegirHora);
        alTocar(R.id.et_frecuencia,    R.id.til_frecuencia,    this::elegirFrecuencia);
        alTocar(R.id.et_vencimiento,   R.id.til_vencimiento,   this::elegirVencimiento);

        if (idMedicamento <= 0) {
            Toast.makeText(this, "No se pudo abrir el medicamento", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        cargar();
    }

    private void alTocar(int idCampo, int idCaja, Runnable accion) {
        View.OnClickListener l = v -> accion.run();
        findViewById(idCampo).setOnClickListener(l);
        findViewById(idCaja).setOnClickListener(l);
    }

    // ═══ Carga ═════════════════════════════════════════════════

    private void cargar() {
        VimedRepo.buscarMedicamento(idMedicamento, new VimedRepo.Cb<Medicamento>() {
            @Override public void onOk(Medicamento m) {
                if (m == null) { finish(); return; }
                med = m;
                pintarMedicamento();
                cargarHorarios();
            }
            @Override public void onError(String msg) {
                Toast.makeText(EditarMedicamentoActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Se edita el PRIMER horario y no todos.
     *
     * Casi todos los medicamentos tienen uno solo: el asistente crea uno, y
     * de ahí salen todas las tomas del día por el intervalo. Los que tienen
     * varios vienen del horario personalizado, y reescribirlos todos con una
     * única hora los aplastaría. Cuando hay más de uno se avisa y se dejan
     * en paz: para eso está el horario personalizado.
     */
    private void cargarHorarios() {
        VimedRepo.listarHorarios(idMedicamento, new VimedRepo.Cb<List<Horario>>() {
            @Override public void onOk(List<Horario> hs) {
                todos.clear();
                todos.addAll(hs);
                horario = hs.isEmpty() ? null : hs.get(0);

                if (horario != null) {
                    if (horario.getHoraInicio() != null && horario.getHoraInicio().length() >= 5) {
                        horaInicio = horario.getHoraInicio().substring(0, 5);
                    }
                    if (horario.getIntervaloHoras() > 0) {
                        intervaloHoras = horario.getIntervaloHoras();
                    }
                }
                pintarHorario();
            }
            @Override public void onError(String msg) { pintarHorario(); }
        });
    }

    private void pintarMedicamento() {
        etNombre.setText(med.getNombre());

        float d = med.getDosis();
        etDosis.setText(d == (int) d ? String.valueOf((int) d) : String.valueOf(d));

        if (med.getUnidad() != null)        unidad = med.getUnidad();
        if (med.getPresentacion() != null)  presentacion = med.getPresentacion();
        if (med.getInstrucciones() != null) instrucciones = med.getInstrucciones();
        fechaVencimiento = med.getFechaVencimiento();

        etUnidad.setText(unidad);
        etPresentacion.setText(presentacion);
        etInstrucciones.setText(MedicamentoUI.instruccion(instrucciones));
        etStock.setText(String.valueOf(med.getStockActual()));
        etStockMin.setText(String.valueOf(med.getStockMinimo()));
        etVencimiento.setText(MedicamentoUI.fechaLegible(fechaVencimiento));
    }

    private void pintarHorario() {
        etHora.setText(horaInicio);
        etFrecuencia.setText(MedicamentoUI.frecuencia(intervaloHoras));
        pintarHorasResultantes();
    }

    /**
     * Las horas concretas en que va a sonar, debajo de los dos campos.
     *
     * "Cada 8 horas desde las 07:00" obliga a hacer la cuenta mentalmente, y
     * es justo la cuenta que se hace mal. Mostrarla evita guardar un horario
     * que suena a las 3 de la mañana sin haberlo querido.
     */
    private void pintarHorasResultantes() {
        Horario provisorio = new Horario(idMedicamento, horaInicio, intervaloHoras, false);
        List<String> horas = MedicamentoUI.horasDelDia(provisorio);

        String texto = horas.isEmpty()
            ? "Elegí la hora de la primera toma."
            : (horas.size() == 1
                ? "Va a sonar todos los días a las " + horas.get(0) + "."
                : "Va a sonar a las " + android.text.TextUtils.join(", ", horas) + ".");

        if (todos.size() > 1) {
            texto += "\n\nEste medicamento tiene " + todos.size() + " horarios"
                + " cargados. Acá se edita el primero; los demás quedan como"
                + " están.";
        }
        ((TextView) findViewById(R.id.tv_horas_resultantes)).setText(texto);
    }

    // ═══ Selectores ════════════════════════════════════════════

    private void elegirUnidad() {
        new AlertDialog.Builder(this)
            .setTitle("Unidad")
            .setItems(UNIDADES, (d, w) -> {
                unidad = UNIDADES[w];
                etUnidad.setText(unidad);
            })
            .show();
    }

    private void elegirPresentacion() {
        new AlertDialog.Builder(this)
            .setTitle("Presentación")
            .setItems(PRESENTACIONES, (d, w) -> {
                presentacion = PRESENTACIONES[w];
                etPresentacion.setText(presentacion);
            })
            .show();
    }

    private void elegirInstrucciones() {
        String[] etiquetas = new String[INSTRUCCIONES_TAGS.length];
        for (int i = 0; i < INSTRUCCIONES_TAGS.length; i++) {
            etiquetas[i] = MedicamentoUI.instruccion(INSTRUCCIONES_TAGS[i]);
        }
        new AlertDialog.Builder(this)
            .setTitle("Cómo tomarlo")
            .setItems(etiquetas, (d, w) -> {
                instrucciones = INSTRUCCIONES_TAGS[w];
                etInstrucciones.setText(etiquetas[w]);
            })
            .show();
    }

    private void elegirHora() {
        int hh = 8, mm = 0;
        try {
            hh = Integer.parseInt(horaInicio.substring(0, 2));
            mm = Integer.parseInt(horaInicio.substring(3, 5));
        } catch (Exception ignored) { }

        new TimePickerDialog(this, (view, hora, minuto) -> {
            horaInicio = String.format(Locale.getDefault(), "%02d:%02d", hora, minuto);
            etHora.setText(horaInicio);
            pintarHorasResultantes();
        }, hh, mm, true).show();
    }

    private void elegirFrecuencia() {
        String[] etiquetas = new String[INTERVALOS.length];
        for (int i = 0; i < INTERVALOS.length; i++) {
            etiquetas[i] = MedicamentoUI.frecuencia(INTERVALOS[i])
                + "  ·  " + MedicamentoUI.tomasPorDia(INTERVALOS[i])
                + (MedicamentoUI.tomasPorDia(INTERVALOS[i]) == 1
                    ? " vez al día" : " veces al día");
        }
        new AlertDialog.Builder(this)
            .setTitle("Cada cuánto")
            .setItems(etiquetas, (d, w) -> {
                intervaloHoras = INTERVALOS[w];
                etFrecuencia.setText(MedicamentoUI.frecuencia(intervaloHoras));
                pintarHorasResultantes();
            })
            .show();
    }

    private void elegirVencimiento() {
        Calendar inicial = Calendar.getInstance();
        if (fechaVencimiento != null && fechaVencimiento.length() >= 10) {
            try {
                inicial.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .parse(fechaVencimiento.substring(0, 10)));
            } catch (Exception ignored) { }
        } else {
            inicial.add(Calendar.YEAR, 1);
        }

        // Sin fecha mínima, al revés que al cargar uno nuevo: acá se puede
        // estar registrando la fecha real de una caja que YA venció, y
        // negarse a anotarla no la hace menos cierta.
        new DatePickerDialog(this, (view, year, month, day) -> {
            fechaVencimiento = String.format(Locale.US, "%04d-%02d-%02d",
                year, month + 1, day);
            etVencimiento.setText(MedicamentoUI.fechaLegible(fechaVencimiento));
        },
            inicial.get(Calendar.YEAR),
            inicial.get(Calendar.MONTH),
            inicial.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ═══ Guardar ═══════════════════════════════════════════════

    private void guardar() {
        if (med == null) return;

        String nombre = texto(etNombre);
        if (nombre.isEmpty()) {
            etNombre.setError("Poné el nombre");
            etNombre.requestFocus();
            return;
        }

        float dosis;
        try {
            dosis = Float.parseFloat(texto(etDosis).replace(',', '.'));
        } catch (NumberFormatException e) {
            etDosis.setError("Número inválido");
            etDosis.requestFocus();
            return;
        }
        if (dosis <= 0) {
            etDosis.setError("Tiene que ser mayor que cero");
            etDosis.requestFocus();
            return;
        }

        int stock, stockMin;
        try {
            stock = Integer.parseInt(texto(etStock));
            stockMin = Integer.parseInt(texto(etStockMin));
        } catch (NumberFormatException e) {
            etStock.setError("Número inválido");
            return;
        }
        if (stock < 0 || stockMin < 0) {
            etStock.setError("No puede ser negativo");
            return;
        }

        MaterialButton btn = findViewById(R.id.btn_guardar);
        btn.setEnabled(false);
        btn.setText("Guardando…");

        Medicamento cambios = new Medicamento(
            med.getIdUsuario(), nombre, presentacion,
            dosis, unidad, instrucciones, med.getColorIcono(), stock, stockMin);
        cambios.setFechaVencimiento(fechaVencimiento);

        VimedRepo.actualizarMedicamento(idMedicamento, cambios, new VimedRepo.Cb<Void>() {
            @Override public void onOk(Void v) {
                // La caché es lo que lee la alarma cuando dispara sin
                // conexión: si no se actualiza, seguiría anunciando el
                // nombre y la dosis viejos.
                cambios.setId(idMedicamento);
                MedCache.guardar(EditarMedicamentoActivity.this, cambios);
                // Si cambió el vencimiento, que pueda volver a avisar hoy.
                VencimientoChecker.olvidarAviso(EditarMedicamentoActivity.this, idMedicamento);
                guardarHorario();
            }
            @Override public void onError(String msg) {
                btn.setEnabled(true);
                btn.setText("Guardar cambios");
                Toast.makeText(EditarMedicamentoActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void guardarHorario() {
        if (horario == null) { terminar(); return; }

        boolean cambio = !horaInicio.equals(
                horario.getHoraInicio() != null && horario.getHoraInicio().length() >= 5
                    ? horario.getHoraInicio().substring(0, 5) : "")
            || intervaloHoras != horario.getIntervaloHoras();

        if (!cambio) { terminar(); return; }

        VimedRepo.actualizarHorario(horario.getId(), horaInicio, intervaloHoras,
            new VimedRepo.Cb<Void>() {
                @Override public void onOk(Void v) {
                    // Las alarmas son locales: hay que cancelar las viejas
                    // ANTES de programar las nuevas. Con otro intervalo son
                    // otra cantidad de alarmas, así que las que sobran no se
                    // pisan solas y seguirían sonando a la hora vieja.
                    NotificationHelper.cancelarAlarmas(EditarMedicamentoActivity.this,
                        idMedicamento, horario.getIntervaloHoras());
                    NotificationHelper.programarAlarmas(EditarMedicamentoActivity.this,
                        idMedicamento, horario.getId(), horaInicio, intervaloHoras);
                    terminar();
                }
                @Override public void onError(String msg) {
                    // El medicamento ya se guardó; lo que falló es el
                    // horario. Se dice cuál de las dos cosas, porque
                    // "no se pudo guardar" a secas haría volver a cargar
                    // todo el formulario al pedo.
                    Toast.makeText(EditarMedicamentoActivity.this,
                        "Se guardó el medicamento, pero no el horario: " + msg,
                        Toast.LENGTH_LONG).show();
                    terminar();
                }
            });
    }

    private void terminar() {
        Toast.makeText(this, "Cambios guardados ✓", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String texto(TextInputEditText campo) {
        return campo.getText() != null ? campo.getText().toString().trim() : "";
    }
}
