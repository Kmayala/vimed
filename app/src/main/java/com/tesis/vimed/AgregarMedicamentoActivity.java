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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.tesis.vimed.api.CatalogoMatcher;
import com.tesis.vimed.api.DosisChecker;
import com.tesis.vimed.api.InteraccionChecker;
import com.tesis.vimed.api.SupabaseClient;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.CatalogoMedicamento;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.utils.NotificationHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AgregarMedicamentoActivity extends AppCompatActivity {

    /**
     * Extras opcionales: cuando el CUIDADOR abre esta pantalla, el
     * medicamento se guarda a nombre del adulto mayor, no del que lo carga.
     * Sin estos extras la pantalla funciona como siempre (el adulto se
     * carga su propia medicación).
     */
    public static final String EXTRA_PARA_ID_USUARIO = "para_id_usuario";
    public static final String EXTRA_PARA_NOMBRE     = "para_nombre";

    /** -1 = me lo cargo a mí mismo. */
    private int idUsuarioDestino = -1;
    private String nombreDestino = null;

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

    /** Vencimiento elegido, "yyyy-MM-dd", o null si la persona no lo cargó. */
    private String fechaVencimiento = null;
    // Nombre de color (no hex) — así lo interpreta MainActivity.colorForMed()
    private String colorIcono = "azul";

    // ── Pasos y estado ─────────────────────────────────────────
    private View[] pasos;
    private int pasoActual = 0;

    /** Catálogo traído de Supabase — null hasta que responda la red. */
    private List<CatalogoMedicamento> catalogo;

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

    /** Unidades de dosis. Doblan como tag de su botón en el paso 2. */
    private static final String[] UNIDADES = {"mg", "ml", "mcg", "g", "UI"};

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

        idUsuarioDestino = getIntent().getIntExtra(EXTRA_PARA_ID_USUARIO, -1);
        nombreDestino    = getIntent().getStringExtra(EXTRA_PARA_NOMBRE);

        if (idUsuarioDestino > 0) {
            TextView avisoDestino = findViewById(R.id.tv_para_quien);
            avisoDestino.setText("Estás cargando este medicamento para "
                + nombreSeguroDestino() + ". Le va a aparecer en su app con la alarma.");
            avisoDestino.setVisibility(View.VISIBLE);
        }

        // Refresca peso y edad en la sesión mientras la persona recorre los
        // pasos. El chequeo del final los lee de ahí, y si el cuidador cargó
        // el peso desde su teléfono este es el momento de enterarse. Sin red
        // se usa lo último que teníamos.
        VimedRepo.cargarDatosClinicos(this, -1,
            new VimedRepo.Cb<com.tesis.vimed.models.UsuarioSupabase>() {
                @Override public void onOk(com.tesis.vimed.models.UsuarioSupabase p) {}
            });

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

        // Reflejar lo que ya está elegido (puede venir precargado del catálogo)
        // La unidad puede haber cambiado en el paso 1 al elegir del catálogo,
        // así que el resaltado se refresca al entrar y no solo al crear.
        if (index == 1) resaltarSeleccion(pasos[1], UNIDADES, unidad);
        if (index == 2) resaltarSeleccion(pasos[2], PRESENTACIONES, presentacion);
        if (index == 3) resaltarSeleccion(pasos[3], INSTRUCCIONES_TAGS, instrucciones);

        // El último paso muestra un resumen de todo lo elegido
        if (index == pasos.length - 1) actualizarResumen();
    }

    /** Arma el texto del recuadro RESUMEN del último paso. */
    private void actualizarResumen() {
        TextView tv = pasos[6].findViewById(R.id.tv_resumen);
        if (tv == null) return;

        String dosisTxt = dosis > 0
            ? (dosis == (int) dosis ? String.valueOf((int) dosis) : String.valueOf(dosis)) + " " + unidad
            : "sin dosis";

        String frecuencia = intervaloHoras >= 24
            ? "1 vez por día"
            : "cada " + intervaloHoras + " horas";

        tv.setText(nombre + " · " + dosisTxt + "\n"
            + presentacion + " · " + instruccionLegible(instrucciones) + "\n"
            + "Desde las " + horaInicio + " · " + frecuencia);
    }

    /** Versión legible de los tags de instrucciones. */
    private String instruccionLegible(String tag) {
        if (tag == null) return "";
        switch (tag) {
            case "despues_comer":   return "Después de comer";
            case "antes_comer":     return "Antes de comer";
            case "ayunas":          return "En ayunas";
            case "con_agua":        return "Con agua";
            case "con_leche":       return "Con leche";
            case "antes_dormir":    return "Antes de dormir";
            case "al_despertar":    return "Al despertar";
            case "sin_restriccion": return "Sin restricción";
            default:                return tag;
        }
    }

    // ── Paso 1: Nombre ─────────────────────────────────────────
    private void configurarPaso1() {
        AutoCompleteTextView etNombre = pasos[0].findViewById(R.id.et_nombre);

        // Lista de respaldo: si no hay red, al menos ofrecemos algo.
        String[] respaldo = {
            "Metformina", "Enalapril", "Aspirineta", "Warfarina",
            "Omeprazol", "Losartán", "Atorvastatina", "Glibenclamida",
            "Furosemida", "Amlodipina", "Paracetamol", "Ibuprofeno",
            "Levotiroxina", "Carvedilol", "Clopidogrel", "Alprazolam"
        };
        setAdaptadorNombres(etNombre, new ArrayList<>(Arrays.asList(respaldo)));

        // La lista se abre al tocar el campo o al recibir foco — sin tener
        // que escribir nada primero.
        etNombre.setOnClickListener(v -> etNombre.showDropDown());
        etNombre.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etNombre.showDropDown();
        });

        // Al elegir del catálogo, precargamos dosis / presentación / instrucciones.
        // En el campo dejamos solo el nombre, sin la dosis: la dosis se guarda
        // en su propia columna y se elige en el paso 2.
        etNombre.setOnItemClickListener((parent, view, position, id) -> {
            String elegido = String.valueOf(parent.getItemAtPosition(position));
            aplicarDatosDelCatalogo(elegido);
            String limpio = limpiarNombre(elegido);
            etNombre.setText(limpio, false);   // false = no volver a filtrar/abrir
            etNombre.setSelection(limpio.length());
            etNombre.dismissDropDown();
        });

        pasos[0].findViewById(R.id.btn_siguiente_1).setOnClickListener(v -> {
            nombre = etNombre.getText().toString().trim();
            if (!nombre.isEmpty()) mostrarPaso(1);
            else etNombre.setError(getString(R.string.error_empty_field));
        });

        cargarCatalogo(etNombre);
    }

    /** Trae el catálogo de Supabase y reemplaza la lista de respaldo. */
    private void cargarCatalogo(AutoCompleteTextView etNombre) {
        SupabaseClient.getService()
            .getCatalogo("eq.true", "nombre_comercial.asc")
            .enqueue(new retrofit2.Callback<List<CatalogoMedicamento>>() {
                @Override
                public void onResponse(retrofit2.Call<List<CatalogoMedicamento>> c,
                                       retrofit2.Response<List<CatalogoMedicamento>> r) {
                    if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) return;
                    catalogo = r.body();
                    List<String> nombres = new ArrayList<>();
                    for (CatalogoMedicamento m : catalogo) {
                        nombres.add(m.getNombreComercial());
                    }
                    setAdaptadorNombres(etNombre, nombres);
                }

                @Override
                public void onFailure(retrofit2.Call<List<CatalogoMedicamento>> c, Throwable t) {
                    // Nos quedamos con la lista de respaldo.
                }
            });
    }

    /**
     * Quita la dosis del final del nombre comercial.
     * "Metformina 850" → "Metformina" · "Tamsulosina 0.4" → "Tamsulosina"
     * Los que no terminan en número quedan igual ("Complejo B", "Insulina NPH").
     */
    private String limpiarNombre(String nombreComercial) {
        if (nombreComercial == null) return "";
        String limpio = nombreComercial.trim().replaceAll("\\s+\\d+([.,]\\d+)?\\s*$", "");
        return limpio.isEmpty() ? nombreComercial.trim() : limpio;
    }

    private void setAdaptadorNombres(AutoCompleteTextView etNombre, List<String> nombres) {
        // Fila propia en vez de simple_dropdown_item_1line: la del sistema
        // usa su tipografía y filas de 40dp, difíciles de acertar para la
        // persona a la que apunta la app.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            R.layout.item_dropdown_vimed, nombres);
        etNombre.setAdapter(adapter);
        etNombre.setThreshold(1);
    }

    /**
     * Cuando la persona elige un medicamento del catálogo, precargamos los
     * campos que ya conocemos para que no tenga que completarlos a mano.
     */
    private void aplicarDatosDelCatalogo(String nombreComercial) {
        if (catalogo == null) return;
        CatalogoMedicamento encontrado = null;
        for (CatalogoMedicamento m : catalogo) {
            if (nombreComercial.equalsIgnoreCase(m.getNombreComercial())) {
                encontrado = m;
                break;
            }
        }
        if (encontrado == null) return;

        if (encontrado.getDosisComun() > 0) dosis = encontrado.getDosisComun();
        if (encontrado.getUnidad() != null)        unidad = encontrado.getUnidad();
        if (encontrado.getPresentacion() != null)  presentacion = encontrado.getPresentacion();
        if (encontrado.getInstrucciones() != null) instrucciones = encontrado.getInstrucciones();

        TextView aviso = pasos[0].findViewById(R.id.tv_precargado);
        if (aviso != null) {
            String dosisTxt = dosis == (int) dosis
                ? String.valueOf((int) dosis) : String.valueOf(dosis);
            aviso.setText("Precargamos los datos habituales: " + dosisTxt + " " + unidad
                + ", " + presentacion.toLowerCase(Locale.ROOT)
                + ", " + instruccionLegible(instrucciones).toLowerCase(Locale.ROOT)
                + ". Podés cambiarlos en los próximos pasos.");
            aviso.setVisibility(View.VISIBLE);
        }
    }

    // ── Paso 2: Dosis ──────────────────────────────────────────
    private void configurarPaso2() {
        // Campo manual y desplegable de unidad (ocultos al inicio)
        TextInputEditText etDosis = pasos[1].findViewById(R.id.et_dosis_manual);
        View btnSiguiente2 = pasos[1].findViewById(R.id.btn_siguiente_2);

        // Unidad: botones a la vista, no un desplegable del sistema. Ver la
        // nota en el layout — el popup de Android ni se parece al resto de
        // la app y obliga a dos toques sobre ítems chicos.
        for (String u : UNIDADES) {
            View btn = pasos[1].findViewWithTag(u);
            if (btn == null) continue;
            btn.setOnClickListener(v -> {
                unidad = u;
                resaltarSeleccion(pasos[1], UNIDADES, unidad);
            });
        }
        resaltarSeleccion(pasos[1], UNIDADES, unidad);

        // Presets rápidos — navegan directo al paso 3, salvo que la dosis
        // no se parezca a la del catálogo: ahí primero avisamos.
        pasos[1].findViewById(R.id.btn_500).setOnClickListener(v -> {
            dosis = 500; unidad = "mg"; avisarSiLaDosisEsRara(() -> mostrarPaso(2));
        });
        pasos[1].findViewById(R.id.btn_850).setOnClickListener(v -> {
            dosis = 850; unidad = "mg"; avisarSiLaDosisEsRara(() -> mostrarPaso(2));
        });
        pasos[1].findViewById(R.id.btn_1000).setOnClickListener(v -> {
            dosis = 1000; unidad = "mg"; avisarSiLaDosisEsRara(() -> mostrarPaso(2));
        });

        // Mostrar campo manual
        pasos[1].findViewById(R.id.btn_otra_dosis).setOnClickListener(v ->
            mostrarCampoDosisManual());

        // Confirmar dosis manual
        btnSiguiente2.setOnClickListener(v -> {
            String dosisStr = etDosis.getText() != null
                ? etDosis.getText().toString().trim() : "";
            if (dosisStr.isEmpty()) {
                etDosis.setError(getString(R.string.error_empty_field));
                return;
            }
            try {
                dosis = Float.parseFloat(dosisStr);
                // La unidad ya quedó elegida al tocar su botón.
                avisarSiLaDosisEsRara(() -> mostrarPaso(2));
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
                    resaltarColor(color);
                });
            }
        }
        // Marcar el color por defecto al entrar
        resaltarColor(colorIcono);

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

        // El listener se registra siempre, no al abrir la hoja: si el sistema
        // recrea la actividad con la hoja abierta (rotar la pantalla), el
        // resultado tiene que encontrar a alguien escuchando igual.
        getSupportFragmentManager().setFragmentResultListener(
            PersonalizadoBottomSheet.REQUEST_KEY, this, (clave, datos) -> {
                horaInicio = datos.getString(PersonalizadoBottomSheet.RESULT_HORA, horaInicio);
                intervaloHoras = datos.getInt(PersonalizadoBottomSheet.RESULT_INTERVALO,
                    intervaloHoras);
                personalizado = true;
                mostrarPaso(6);
            });

        pasos[5].findViewById(R.id.btn_personalizado).setOnClickListener(v ->
            PersonalizadoBottomSheet.newInstance(horaInicio)
                .show(getSupportFragmentManager(), "personalizado"));
    }

    // ── Paso 6: Stock ──────────────────────────────────────────
    private void configurarPaso6() {
        TextInputEditText etStock    = pasos[6].findViewById(R.id.et_stock);
        TextInputEditText etStockMin = pasos[6].findViewById(R.id.et_stock_minimo);
        TextInputEditText etVence    = pasos[6].findViewById(R.id.et_vencimiento);

        etStockMin.setText("5");

        // El click va también en el TextInputLayout: el campo es focusable
        // false para que no salte el teclado, y sin esto tocar el borde de
        // la caja —que es la mitad del área— no hace nada.
        View.OnClickListener abrirCalendario = v -> elegirVencimiento(etVence);
        etVence.setOnClickListener(abrirCalendario);
        pasos[6].findViewById(R.id.til_vencimiento).setOnClickListener(abrirCalendario);

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
            chequearDosisYSeguir();
        });
    }

    /**
     * Calendario para el vencimiento.
     *
     * Arranca dentro de un año, que es donde cae la mayoría de las cajas, y
     * no deja elegir una fecha pasada: cargar un medicamento ya vencido es
     * casi siempre un error de tipeo, y si de verdad está vencido lo que
     * hay que hacer es tirarlo, no agendarlo.
     */
    private void elegirVencimiento(TextInputEditText campo) {
        java.util.Calendar inicial = java.util.Calendar.getInstance();
        if (fechaVencimiento != null) {
            try {
                inicial.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd",
                    Locale.US).parse(fechaVencimiento));
            } catch (Exception ignored) { }
        } else {
            inicial.add(java.util.Calendar.YEAR, 1);
        }

        android.app.DatePickerDialog dlg = new android.app.DatePickerDialog(this,
            (view, year, month, day) -> {
                fechaVencimiento = String.format(Locale.US, "%04d-%02d-%02d",
                    year, month + 1, day);
                campo.setText(String.format(Locale.getDefault(), "%02d/%02d/%d",
                    day, month + 1, year));
            },
            inicial.get(java.util.Calendar.YEAR),
            inicial.get(java.util.Calendar.MONTH),
            inicial.get(java.util.Calendar.DAY_OF_MONTH));

        java.util.Calendar hoy = java.util.Calendar.getInstance();
        hoy.set(java.util.Calendar.HOUR_OF_DAY, 0);
        hoy.set(java.util.Calendar.MINUTE, 0);
        hoy.set(java.util.Calendar.SECOND, 0);
        hoy.set(java.util.Calendar.MILLISECOND, 0);
        // Menos un segundo: con la medianoche exacta, algunos equipos
        // redondean al día siguiente y no dejan elegir hoy.
        dlg.getDatePicker().setMinDate(hoy.getTimeInMillis() - 1000);
        dlg.show();
    }

    // ── Chequeo de dosis contra el catálogo ────────────────────

    /**
     * Última red de seguridad antes de guardar. El aviso normalmente ya
     * salió en el paso 2, pero el catálogo puede haber llegado tarde (viene
     * por red) y en ese momento no había con qué comparar.
     *
     * Solo interrumpe con el nivel ALTO —una dosis cinco veces fuera de lo
     * habitual, que casi siempre es un cero de más—. Una diferencia menor
     * ya se avisó y no justifica un segundo cartel.
     */
    private void chequearDosisYSeguir() {
        // Acá SÍ se puede usar el peso: la frecuencia ya está elegida, y sin
        // ella no se sabe cuánto se toma por día — 50 mg pueden ser 50 o 200.
        // En el aviso del paso 2 todavía no se conoce.
        DosisChecker.Aviso aviso = DosisChecker.revisar(
            CatalogoMatcher.buscar(nombre, catalogo), dosis, unidad,
            new SessionManager(this).getPerfilClinico(), tomasPorDia(),
            idUsuarioDestino > 0);

        // ALTO interrumpe siempre. Un REVISAR común ya se mostró en el paso
        // de la dosis y un segundo cartel igual solo enseña a saltearlos;
        // pero el que sale del peso o la edad es la PRIMERA vez que aparece
        // —antes no se conocía la frecuencia—, así que ese sí se muestra.
        boolean valeLaPena = aviso.nivel == DosisChecker.Nivel.ALTO
            || (aviso.hayAlgoQueDecir() && aviso.usaPerfil);

        if (!valeLaPena) {
            chequearInteraccionesYGuardar();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(aviso.nivel == DosisChecker.Nivel.ALTO
                ? "Revisá la dosis" : "Sobre tu dosis diaria")
            .setMessage(aviso.texto)
            .setNegativeButton("Corregir", (d, w) -> mostrarPaso(1))
            .setPositiveButton("Está bien así", (d, w) -> chequearInteraccionesYGuardar())
            .show();
    }

    /**
     * Cuántas veces al día se toma, según la frecuencia elegida.
     * Misma cuenta que usa el programador de alarmas.
     */
    private int tomasPorDia() {
        return (intervaloHoras > 0 && intervaloHoras <= 24) ? 24 / intervaloHoras : 1;
    }

    /**
     * Muestra el aviso de dosis apenas se elige, que es cuando la persona
     * todavía tiene la caja o la receta en la mano.
     *
     * Avisa, no bloquea: una dosis alta puede estar perfectamente indicada
     * por el médico, y la app no tiene el rango terapéutico como para
     * afirmar lo contrario (ver DosisChecker).
     */
    private void avisarSiLaDosisEsRara(Runnable continuar) {
        // Sin perfil ni frecuencia todavía; lo único que cambia acá es de
        // quién es el médico del que habla el aviso.
        DosisChecker.Aviso aviso = DosisChecker.revisar(
            CatalogoMatcher.buscar(nombre, catalogo), dosis, unidad,
            null, 0, idUsuarioDestino > 0);

        TextView tvAviso = pasos[1].findViewById(R.id.tv_aviso_dosis);
        if (tvAviso != null) {
            tvAviso.setText(aviso.hayAlgoQueDecir() ? aviso.texto : "");
            tvAviso.setVisibility(aviso.hayAlgoQueDecir() ? View.VISIBLE : View.GONE);
        }

        if (!aviso.hayAlgoQueDecir()) {
            continuar.run();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(aviso.nivel == DosisChecker.Nivel.ALTO
                ? "Revisá la dosis" : "Dosis distinta a la habitual")
            .setMessage(aviso.texto)
            .setNegativeButton("Corregir", (d, w) -> mostrarCampoDosisManual())
            .setPositiveButton("Continuar", (d, w) -> continuar.run())
            .show();
    }

    /** Abre el campo de dosis a mano, para corregir sin salir del paso. */
    private void mostrarCampoDosisManual() {
        pasos[1].findViewById(R.id.til_dosis_manual).setVisibility(View.VISIBLE);
        pasos[1].findViewById(R.id.fila_unidades).setVisibility(View.VISIBLE);
        pasos[1].findViewById(R.id.btn_siguiente_2).setVisibility(View.VISIBLE);
        resaltarSeleccion(pasos[1], UNIDADES, unidad);
    }

    // ── Chequeo de interacciones antes de guardar ──────────────
    private void chequearInteraccionesYGuardar() {
        MaterialButton btnGuardar = pasos[6].findViewById(R.id.btn_guardar);
        String labelOriginal = btnGuardar.getText().toString();
        btnGuardar.setEnabled(false);
        btnGuardar.setText("Chequeando interacciones…");

        // Los medicamentos que ya tiene la persona ahora viven en Supabase.
        // Si carga el cuidador, hay que chequear contra los del ADULTO.
        VimedRepo.Cb<List<Medicamento>> cbExistentes = new VimedRepo.Cb<List<Medicamento>>() {
            @Override
            public void onOk(List<Medicamento> existentes) {
                List<String> nombres = new ArrayList<>();
                for (Medicamento m : existentes) {
                    if (m.getNombre() != null) nombres.add(m.getNombre());
                }
                if (nombres.isEmpty()) {
                    restaurarBotonGuardar(btnGuardar);
                    guardarMedicamento();
                    return;
                }
                correrChecker(btnGuardar, labelOriginal, nombres);
            }

            @Override
            public void onError(String msg) {
                // Sin red no podemos chequear: no bloqueamos a la persona.
                restaurarBotonGuardar(btnGuardar);
                guardarMedicamento();
            }
        };

        if (idUsuarioDestino > 0) {
            VimedRepo.listarMedicamentosDe(idUsuarioDestino, cbExistentes);
        } else {
            VimedRepo.listarMedicamentos(this, cbExistentes);
        }
    }

    private void correrChecker(MaterialButton btnGuardar, String labelOriginal,
                               List<String> nombresExistentes) {
        InteraccionChecker.chequear(nombre, nombresExistentes,
            new InteraccionChecker.Callback0() {
                @Override
                public void onResult(List<InteraccionChecker.Hallazgo> hallazgos) {
                    btnGuardar.setEnabled(true);
                    btnGuardar.setText(labelOriginal);
                    if (hallazgos.isEmpty()) {
                        guardarMedicamento();
                    } else {
                        mostrarDialogoInteracciones(hallazgos);
                    }
                }

                @Override
                public void onError(String msg) {
                    // Si falla el chequeo (offline, error de red), guardamos igual —
                    // no queremos bloquear a la persona por un problema de conexión.
                    btnGuardar.setEnabled(true);
                    btnGuardar.setText(labelOriginal);
                    guardarMedicamento();
                }
            });
    }

    private void mostrarDialogoInteracciones(List<InteraccionChecker.Hallazgo> hallazgos) {
        // Ordenamos por severidad: alto arriba
        int alto = 0, medio = 0, bajo = 0;
        StringBuilder body = new StringBuilder();
        for (InteraccionChecker.Hallazgo h : hallazgos) {
            String icono = h.esAlto() ? "🚨" : h.esMedio() ? "⚠️" : "ℹ️";
            body.append(icono).append(" ").append(nombre).append(" + ")
                .append(h.medContraChoca).append("\n");
            if (h.descripcion != null) body.append(h.descripcion).append("\n");
            body.append("\n");
            if (h.esAlto())  alto++;
            else if (h.esMedio()) medio++;
            else bajo++;
        }

        String titulo;
        if (alto > 0)       titulo = "Interacción de alto riesgo detectada";
        else if (medio > 0) titulo = "Precaución: posible interacción";
        else                titulo = "Aviso de interacción";

        // Espejar el aviso en la nube para que el familiar lo vea
        if (alto > 0 || medio > 0) {
            com.tesis.vimed.api.NotificacionSync.registrar(this,
                com.tesis.vimed.models.Notificacion.TIPO_INTERACCION,
                titulo + " al agregar " + nombre);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(body.toString().trim()
                + "\n\nConsultá con tu médico antes de continuar.")
            .setNegativeButton("Cancelar", (d, w) -> {})
            .setPositiveButton("Guardar igual", (d, w) -> guardarMedicamento());

        // Si hay riesgo alto, hacemos que el usuario tenga que confirmar dos veces
        if (alto > 0) {
            builder.setPositiveButton("Guardar igual", (d, w) -> {
                new AlertDialog.Builder(this)
                    .setTitle("¿Estás segura?")
                    .setMessage("Esta combinación puede ser peligrosa. "
                        + "Confirmá que ya lo hablaste con tu médico.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Sí, guardar", (d2, w2) -> guardarMedicamento())
                    .show();
            });
        }
        builder.show();
    }

    // ── Guardar en Supabase ────────────────────────────────────
    private void guardarMedicamento() {
        MaterialButton btnGuardar = pasos[6].findViewById(R.id.btn_guardar);
        btnGuardar.setEnabled(false);
        btnGuardar.setText("Guardando…");

        // El id_usuario lo completa el repositorio con el de public.usuarios
        Medicamento med = new Medicamento(
            0, nombre, presentacion,
            dosis, unidad, instrucciones, colorIcono, stockActual, stockMinimo
        );
        med.setFechaVencimiento(fechaVencimiento);   // null si no se cargó

        VimedRepo.Cb<Medicamento> alGuardar = new VimedRepo.Cb<Medicamento>() {
            @Override
            public void onOk(Medicamento creado) {
                // Guardamos nombre y dosis en el celular: la alarma los lee de
                // ahí para poder sonar aunque a esa hora no haya internet.
                com.tesis.vimed.utils.MedCache.guardar(
                    AgregarMedicamentoActivity.this, creado);

                // Recién ahora conocemos el id que generó Postgres,
                // así que el horario se crea encadenado.
                Horario horario = new Horario(
                    creado.getId(), horaInicio, intervaloHoras, personalizado);

                VimedRepo.crearHorario(horario, new VimedRepo.Cb<Horario>() {
                    @Override
                    public void onOk(Horario horCreado) {
                        // La alarma se programa solo si el medicamento es para
                        // MÍ. Si lo carga el cuidador, la agenda el celular del
                        // adulto vía AlarmaSync la próxima vez que abra la app.
                        if (idUsuarioDestino <= 0) {
                            NotificationHelper.programarAlarmas(
                                AgregarMedicamentoActivity.this,
                                creado.getId(), horCreado.getId(),
                                horaInicio, intervaloHoras);
                        }

                        Toast.makeText(AgregarMedicamentoActivity.this,
                            idUsuarioDestino > 0
                                ? "Medicamento agregado a " + nombreSeguroDestino() + " ✓"
                                : "Medicamento guardado ✓",
                            Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(String msg) {
                        // El medicamento quedó creado pero sin horario:
                        // avisamos para que la persona pueda reintentar.
                        restaurarBotonGuardar(btnGuardar);
                        Toast.makeText(AgregarMedicamentoActivity.this,
                            "Se guardó el medicamento pero no el horario: " + msg,
                            Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String msg) {
                restaurarBotonGuardar(btnGuardar);
                Toast.makeText(AgregarMedicamentoActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        };

        if (idUsuarioDestino > 0) {
            VimedRepo.crearMedicamentoPara(idUsuarioDestino, med, alGuardar);
        } else {
            VimedRepo.crearMedicamento(this, med, alGuardar);
        }
    }

    private String nombreSeguroDestino() {
        return nombreDestino != null && !nombreDestino.isEmpty() ? nombreDestino : "tu familiar";
    }

    private void restaurarBotonGuardar(MaterialButton btn) {
        btn.setEnabled(true);
        btn.setText(R.string.btn_guardar_med);
    }

    /**
     * Marca el color elegido con un aro alrededor.
     * Antes se agrandaba el círculo con setScale, pero eso lo recortaba
     * contra el borde de la celda y se veía deformado.
     */
    private void resaltarColor(String colorSeleccionado) {
        for (int j = 0; j < COLOR_BTN_IDS.length; j++) {
            View celda = pasos[2].findViewById(COLOR_BTN_IDS[j]);
            if (celda == null) continue;
            boolean elegido = COLORES[j].equals(colorSeleccionado);
            celda.setBackgroundResource(elegido ? R.drawable.shape_color_ring : 0);
            celda.setAlpha(elegido ? 1.0f : 0.55f);
        }
    }

    // ── Helper: resaltar botón seleccionado dentro de un grupo ─
    private void resaltarSeleccion(View container, String[] tags, String tagSeleccionado) {
        int brand     = ContextCompat.getColor(this, R.color.brand_500);
        int brandSoft = ContextCompat.getColor(this, R.color.brand_50);
        int textoOff  = ContextCompat.getColor(this, R.color.ink_2);
        int bordeOff  = ContextCompat.getColor(this, R.color.ink_7);

        for (String tag : tags) {
            View view = container.findViewWithTag(tag);
            if (!(view instanceof MaterialButton)) continue;

            MaterialButton mb = (MaterialButton) view;
            boolean elegido = tag.equals(tagSeleccionado);

            // Elegido: violeta lleno y con sombra. Sin elegir: tinte muy
            // suave en vez de blanco puro, para que no floten sobre el fondo.
            mb.setBackgroundTintList(ColorStateList.valueOf(elegido ? brand : brandSoft));
            mb.setTextColor(elegido ? Color.WHITE : textoOff);
            mb.setStrokeColor(ColorStateList.valueOf(elegido ? brand : bordeOff));
            mb.setElevation(elegido ? dpF(3) : 0f);
        }
    }

    private float dpF(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
