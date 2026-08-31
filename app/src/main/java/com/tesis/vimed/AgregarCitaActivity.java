package com.tesis.vimed;

import android.app.DatePickerDialog;
import android.content.Intent;
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

    /**
     * Id de la cita que se está editando, o -1 si es una cita nueva.
     * La pantalla es la misma para las dos cosas: los campos, las
     * validaciones y el selector de mapa son idénticos, y lo único que
     * cambia es si al final se hace un POST o un PATCH.
     */
    public static final String EXTRA_ID_CITA = "id_cita";
    private int idCitaEditando = -1;

    /** Punto elegido en el mapa, o null si la persona no lo abrió. */
    private Double latitudElegida = null;
    private Double longitudElegida = null;

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

        // El toque se escucha en el campo Y en su caja: los campos no
        // son enfocables (abren un selector, no el teclado), así que el
        // listener de foco nunca dispara, y si el dedo cae en el borde o
        // en el ícono el evento no llega al EditText.
        View.OnClickListener abrirFecha = v -> mostrarDatePicker();
        View.OnClickListener abrirHora  = v -> mostrarTimePicker();
        etFecha.setOnClickListener(abrirFecha);
        etHora.setOnClickListener(abrirHora);
        findViewById(R.id.til_fecha).setOnClickListener(abrirFecha);
        findViewById(R.id.til_hora).setOnClickListener(abrirHora);

        // La hora arranca con la actual para que nunca quede vacía; la
        // fecha no, porque elegirla es obligatorio y hay que notarlo.
        etHora.setText(String.format(Locale.getDefault(), "%02d:%02d", selHour, selMinute));

        idCitaEditando = getIntent().getIntExtra(EXTRA_ID_CITA, -1);
        if (editando()) cargarParaEditar();

        findViewById(R.id.btn_elegir_mapa).setOnClickListener(v -> abrirMapa());

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save).setOnClickListener(v -> guardarCita());
    }

    // ═══ Modo edición ══════════════════════════════════════════

    private boolean editando() { return idCitaEditando > 0; }

    /**
     * Trae la cita del servidor y llena el formulario con lo que ya tenía.
     *
     * Se consulta en vez de recibirla por Intent: la pantalla de detalle
     * podría estar mostrando datos viejos, y editar sobre datos viejos
     * significa pisar con ellos lo que esté guardado.
     */
    private void cargarParaEditar() {
        ((TextView) findViewById(R.id.tv_titulo_pantalla)).setText("Editar cita");

        com.tesis.vimed.api.VimedRepo.buscarCita(idCitaEditando,
            new com.tesis.vimed.api.VimedRepo.Cb<CitaMedica>() {
                @Override public void onOk(CitaMedica c) { llenarCon(c); }
                @Override public void onError(String msg) {
                    Toast.makeText(AgregarCitaActivity.this, msg, Toast.LENGTH_LONG).show();
                    finish();
                }
            });
    }

    private void llenarCon(CitaMedica c) {
        etMedico.setText(c.getMedico());
        etLugar.setText(c.getLugar());
        etNotas.setText(c.getNotas());

        // La especialidad puede ser una de la grilla o un texto libre; si no
        // coincide con ninguna, se marca "Otra" y se muestra el campo.
        Especialidad e = Especialidad.desdeNombre(c.getEspecialidad());
        if (e != null) {
            elegirEspecialidad(e);
        } else if (c.getEspecialidad() != null && !c.getEspecialidad().isEmpty()) {
            for (Especialidad otra : Especialidad.values()) {
                if (otra.esOtra()) { elegirEspecialidad(otra); break; }
            }
            etEspecialidad.setText(c.getEspecialidad());
        }

        try {
            String[] ymd = c.fechaYMD().split("-");
            selYear  = Integer.parseInt(ymd[0]);
            selMonth = Integer.parseInt(ymd[1]) - 1;
            selDay   = Integer.parseInt(ymd[2]);
            fechaSeleccionada = true;
            etFecha.setText(String.format(Locale.getDefault(),
                "%02d/%02d/%d", selDay, selMonth + 1, selYear));

            String[] hm = c.horaHM().split(":");
            selHour   = Integer.parseInt(hm[0]);
            selMinute = Integer.parseInt(hm[1]);
            etHora.setText(c.horaHM());
        } catch (Exception ignored) {
            // Fecha ilegible: queda la de hoy y la persona la vuelve a elegir.
        }

        if (c.tieneUbicacion()) {
            latitudElegida  = c.getLatitud();
            longitudElegida = c.getLongitud();
            pintarUbicacionElegida();
        }
    }

    private void guardarEdicion(CitaMedica cambios) {
        com.tesis.vimed.api.VimedRepo.actualizarCita(idCitaEditando, cambios,
            new com.tesis.vimed.api.VimedRepo.Cb<Void>() {
                @Override public void onOk(Void v) {
                    // La fecha pudo haber cambiado: los avisos viejos apuntan
                    // al día anterior y hay que rehacerlos, no sumarlos.
                    cambios.setId(idCitaEditando);
                    com.tesis.vimed.utils.RecordatorioCita
                        .cancelar(AgregarCitaActivity.this, idCitaEditando);
                    if (idUsuarioDestino <= 0) {
                        com.tesis.vimed.utils.RecordatorioCita
                            .programar(AgregarCitaActivity.this, cambios);
                    }
                    Toast.makeText(AgregarCitaActivity.this,
                        "Cambios guardados", Toast.LENGTH_SHORT).show();
                    finish();
                }
                @Override public void onError(String msg) {
                    Toast.makeText(AgregarCitaActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
    }

    // ═══ Ubicación en el mapa ══════════════════════════════════

    /**
     * Abre el selector de mapa. Le pasa lo que ya haya: las coordenadas si
     * la persona vuelve a entrar, o el texto que escribió a mano, que el
     * mapa usa como primera búsqueda en vez de abrir en cualquier lado.
     */
    private void abrirMapa() {
        Intent i = new Intent(this, SeleccionarUbicacionActivity.class);
        if (latitudElegida != null && longitudElegida != null) {
            i.putExtra(SeleccionarUbicacionActivity.EXTRA_LAT, latitudElegida);
            i.putExtra(SeleccionarUbicacionActivity.EXTRA_LNG, longitudElegida);
        }
        i.putExtra(SeleccionarUbicacionActivity.EXTRA_TEXTO,
            etLugar.getText() != null ? etLugar.getText().toString() : "");
        selectorDeMapa.launch(i);
    }

    /**
     * Vuelta del selector: se completan el campo de texto y las
     * coordenadas juntos. El texto es lo que la persona va a leer en la
     * lista de citas; las coordenadas, lo que abre el mapa después.
     */
    private final androidx.activity.result.ActivityResultLauncher<Intent> selectorDeMapa =
        registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                if (resultado.getResultCode() != RESULT_OK || resultado.getData() == null) return;
                Intent datos = resultado.getData();

                latitudElegida  = datos.getDoubleExtra(SeleccionarUbicacionActivity.RESULT_LAT, 0);
                longitudElegida = datos.getDoubleExtra(SeleccionarUbicacionActivity.RESULT_LNG, 0);

                String direccion = datos.getStringExtra(
                    SeleccionarUbicacionActivity.RESULT_DIRECCION);
                if (direccion != null && !direccion.isEmpty()) {
                    etLugar.setText(direccion);
                }
                pintarUbicacionElegida();
            });

    private void pintarUbicacionElegida() {
        TextView aviso = findViewById(R.id.tv_ubicacion_elegida);
        if (aviso == null) return;

        boolean hay = latitudElegida != null && longitudElegida != null;
        aviso.setVisibility(hay ? View.VISIBLE : View.GONE);
        if (hay) aviso.setText("✓ Ubicación marcada en el mapa");
    }

    private void mostrarDatePicker() {
        DatePickerDialog dlg = new DatePickerDialog(this, (view, year, month, day) -> {
            selYear = year;
            selMonth = month;
            selDay = day;
            fechaSeleccionada = true;
            etFecha.setText(String.format(Locale.getDefault(), "%02d/%02d/%d", day, month + 1, year));

            // Si eligió hoy y la hora que ya venía puesta pasó, se avisa acá
            // en vez de esperar a que toque "Guardar" y no entienda por qué
            // se lo rechaza.
            if (esHoy() && yaPaso()) {
                Toast.makeText(this,
                    "Las " + horaTexto() + " ya pasaron. Elegí una hora más tarde.",
                    Toast.LENGTH_LONG).show();
            }
        }, selYear, selMonth, selDay);

        // El calendario no deja ni tocar los días anteriores a hoy: una cita
        // en el pasado no tiene sentido y no habría nada que recordar. Se
        // resta un segundo porque setMinDate, recibiendo exactamente la
        // medianoche, en algunos equipos redondea al día siguiente.
        // El tope de "no antes de hoy" no aplica al editar: la fecha
        // original puede ser pasada y hay que poder volver a elegirla.
        if (!editando()) {
            Calendar hoy = Calendar.getInstance();
            hoy.set(Calendar.HOUR_OF_DAY, 0);
            hoy.set(Calendar.MINUTE, 0);
            hoy.set(Calendar.SECOND, 0);
            hoy.set(Calendar.MILLISECOND, 0);
            dlg.getDatePicker().setMinDate(hoy.getTimeInMillis() - 1000);
        }
        dlg.show();
    }

    private void mostrarTimePicker() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            selHour = hour;
            selMinute = minute;
            etHora.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));

            if (fechaSeleccionada && esHoy() && yaPaso()) {
                Toast.makeText(this,
                    "Esa hora ya pasó. Elegí una hora más tarde.",
                    Toast.LENGTH_LONG).show();
            }
        }, selHour, selMinute, true).show();
    }

    // ═══ Fecha y hora elegidas ═════════════════════════════════

    /** Fecha + hora seleccionadas, con los segundos en cero. */
    private Calendar momentoElegido() {
        Calendar c = Calendar.getInstance();
        c.set(selYear, selMonth, selDay, selHour, selMinute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private boolean esHoy() {
        Calendar hoy = Calendar.getInstance();
        return hoy.get(Calendar.YEAR) == selYear
            && hoy.get(Calendar.MONTH) == selMonth
            && hoy.get(Calendar.DAY_OF_MONTH) == selDay;
    }

    /** True si el momento elegido ya quedó atrás. */
    private boolean yaPaso() {
        return momentoElegido().getTimeInMillis() <= System.currentTimeMillis();
    }

    private String horaTexto() {
        return String.format(Locale.getDefault(), "%02d:%02d", selHour, selMinute);
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

        // Segunda barrera además del setMinDate del calendario: la hora se
        // elige aparte, así que "hoy a las 08:00" a las 10 de la mañana pasa
        // el filtro del día pero sigue siendo pasado. Y si la pantalla quedó
        // abierta cruzando la medianoche, la fecha elegida ayer ya venció.
        // Editando NO se bloquea: una cita vieja se corrige justamente
        // después de que pasó —el nombre del médico, el lugar, una nota— y
        // exigirle una fecha futura obligaría a inventar una.
        if (!editando() && yaPaso()) {
            Toast.makeText(this,
                "No se puede agendar una cita en el pasado. "
                    + "Elegí una fecha y hora futuras.",
                Toast.LENGTH_LONG).show();
            return;
        }

        // Formato estándar para el DB: "yyyy-MM-dd HH:mm"
        String fechaHora = String.format(Locale.getDefault(), "%d-%02d-%02d %s",
            selYear, selMonth + 1, selDay, hora.isEmpty() ? "00:00" : hora);

        // El id_usuario lo completa el repositorio con el de public.usuarios
        CitaMedica cita = new CitaMedica(0, medico, especialidad, fechaHora, lugar, notas);
        cita.setEstado(CitaMedica.ESTADO_PENDIENTE);
        cita.setLatitud(latitudElegida);
        cita.setLongitud(longitudElegida);

        if (editando()) { guardarEdicion(cita); return; }

        com.tesis.vimed.api.VimedRepo.Cb<CitaMedica> alGuardar =
            new com.tesis.vimed.api.VimedRepo.Cb<CitaMedica>() {
                @Override public void onOk(CitaMedica creada) {
                    // Los avisos (un día antes y dos horas antes) van en el
                    // celular de quien tiene la cita. Si el cuidador la agenda
                    // para otra persona, no se programan acá: el teléfono del
                    // adulto mayor los levanta solo, vía AlarmaSync.
                    if (idUsuarioDestino <= 0) {
                        com.tesis.vimed.utils.RecordatorioCita
                            .programar(AgregarCitaActivity.this, creada);
                    }

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
