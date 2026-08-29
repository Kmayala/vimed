package com.tesis.vimed;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.tesis.vimed.adherencia.AjustesAdherencia;
import com.tesis.vimed.adherencia.AnalizadorAdherencia;
import com.tesis.vimed.adherencia.Sugerencia;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.CitaMedica;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.RegistroToma;
import com.tesis.vimed.utils.ModoPaciente;
import com.tesis.vimed.utils.NavInferior;
import com.tesis.vimed.utils.NotificationHelper;
import com.tesis.vimed.utils.TomaManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private LinearLayout dosesContainer;
    private View emptyDoses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationHelper.crearCanal(this);
        sessionManager = new SessionManager(this);

        pedirPermisosNotificacion();

        setupGreeting();
        setupBottomNav();

        dosesContainer = findViewById(R.id.doses_container);
        emptyDoses = findViewById(R.id.empty_doses);

        View btnAddFirst = findViewById(R.id.btn_add_first_med);
        if (btnAddFirst != null) {
            btnAddFirst.setOnClickListener(v ->
                startActivity(new Intent(this, AgregarMedicamentoActivity.class)));
        }

        // Accesos rápidos
        findViewById(R.id.quick_meds).setOnClickListener(v ->
            startActivity(new Intent(this, MedsListActivity.class)));
        findViewById(R.id.quick_reminders).setOnClickListener(v ->
            startActivity(new Intent(this, MedsListActivity.class)));

        // La carga la dispara onResume (que corre siempre después de onCreate),
        // así evitamos pedir los mismos datos dos veces al abrir la pantalla.
    }

    /**
     * Antes esto abría hasta tres diálogos encadenados al entrar, y si la
     * persona tocaba "Ahora no" no había forma de volver a encontrarlos.
     * Ahora el permiso de notificaciones —el único que se concede sin
     * salir de la app— se pide directo, y del resto avisa un cartel que
     * lleva a la revisión completa en Configuración.
     */
    private void pedirPermisosNotificacion() {
        if (!NotificationHelper.tienePermisoNotificaciones(this)) {
            NotificationHelper.pedirPermisoNotificaciones(this);
        }
    }

    /** Cartel de "la alarma puede no sonar", con lo que falta. */
    private void revisarPermisosDeAlarma() {
        View aviso = findViewById(R.id.alert_permisos);
        TextView texto = findViewById(R.id.tv_permisos_text);
        if (aviso == null || texto == null) return;

        int faltan = com.tesis.vimed.utils.PermisosAlarma.faltantesComprobables(this);
        if (faltan == 0) {
            aviso.setVisibility(View.GONE);
            return;
        }

        texto.setText(faltan == 1
            ? "Falta un permiso para que la alarma suene con el celular bloqueado. Tocá para revisarlo."
            : "Faltan " + faltan + " permisos para que la alarma suene con el celular "
                + "bloqueado. Tocá para revisarlos.");
        aviso.setVisibility(View.VISIBLE);
        aviso.setOnClickListener(v ->
            startActivity(new Intent(this, ConfiguracionActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTodayDoses();          // pintarTomas limpia el contenedor al recibir la respuesta
        setupAppointmentPlaceholder();
        revisarPermisosDeAlarma();  // en onResume: al volver de los ajustes tiene que actualizarse
        // Toma las alarmas de los medicamentos que haya cargado el cuidador
        // desde su propio teléfono (AlarmManager es local a cada aparato).
        com.tesis.vimed.utils.AlarmaSync.sincronizar(this);
        com.tesis.vimed.utils.BannerOlvidos.revisar(this);
        // Un cuidador que pide seguir tu medicación tiene que pasar por acá:
        // es tu decisión, y la pantalla para responder está escondida en el
        // menú de perfil.
        com.tesis.vimed.utils.BannerSolicitudes.revisar(this);
    }

    private void setupGreeting() {
        String nombre = sessionManager.getNombre();
        TextView tvGreeting = findViewById(R.id.tv_greeting);
        TextView tvName = findViewById(R.id.tv_name);
        TextView tvDate = findViewById(R.id.tv_date);

        String hora = new SimpleDateFormat("H", Locale.getDefault()).format(new Date());
        int h = Integer.parseInt(hora);
        String saludo = h < 12 ? "Buenos días," : h < 19 ? "Buenas tardes," : "Buenas noches,";
        tvGreeting.setText(saludo);
        tvName.setText(nombre != null ? nombre + "." : "");

        // Fecha: "Hoy · Lunes 14 de mayo"
        Calendar cal = Calendar.getInstance();
        String[] dias = {"Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"};
        String[] meses = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
                "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        String fecha = "Hoy · " + dias[cal.get(Calendar.DAY_OF_WEEK) - 1]
                + " " + cal.get(Calendar.DAY_OF_MONTH)
                + " de " + meses[cal.get(Calendar.MONTH)];
        tvDate.setText(fecha);

        // Icono de perfil → menú de opciones
        findViewById(R.id.btn_profile).setOnClickListener(v -> {
            String[] opciones = {"Vincular familiar", "Configuración", "Cerrar sesión"};
            new AlertDialog.Builder(this)
                .setTitle(nombre != null ? nombre : "Perfil")
                .setItems(opciones, (d, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, VincularFamiliarActivity.class));
                    } else if (which == 1) {
                        startActivity(new Intent(this, ConfiguracionActivity.class));
                    } else {
                        new AlertDialog.Builder(this)
                            .setTitle("Cerrar sesión")
                            .setMessage("¿Desea salir de su cuenta?")
                            .setPositiveButton("Cerrar sesión", (d2, w2) -> {
                                sessionManager.logout();
                                Intent intent = new Intent(this, WelcomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                    }
                })
                .show();
        });
    }

    /**
     * Carga las tomas de hoy desde Supabase. Son tres consultas encadenadas:
     * medicamentos → horarios de cada uno → registros del día.
     */
    private void loadTodayDoses() {
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        VimedRepo.listarMedicamentos(this, new VimedRepo.Cb<List<Medicamento>>() {
            @Override
            public void onOk(List<Medicamento> meds) {
                // Refrescamos la copia local que usan las alarmas para poder
                // sonar sin conexión. Cubre los medicamentos que ya existían
                // antes de que hubiera caché.
                for (Medicamento m : meds) {
                    com.tesis.vimed.utils.MedCache.guardar(MainActivity.this, m);
                }
                mostrarAlertaStock(meds);
                actualizarAccesosRapidos(meds);
                if (meds.isEmpty()) { pintarTomas(new ArrayList<>()); return; }

                VimedRepo.listarTomasDelDia(MainActivity.this, hoy,
                    new VimedRepo.Cb<List<RegistroToma>>() {
                        @Override
                        public void onOk(List<RegistroToma> registrosHoy) {
                            cargarHorariosYPintar(meds, registrosHoy);
                        }
                        @Override
                        public void onError(String msg) {
                            // Sin registros igual mostramos las tomas como pendientes
                            cargarHorariosYPintar(meds, new ArrayList<>());
                        }
                    });
            }

            @Override
            public void onError(String msg) {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                pintarTomas(new ArrayList<>());
            }
        });
    }

    /** Pide los horarios de cada medicamento y arma la grilla del día. */
    private void cargarHorariosYPintar(List<Medicamento> meds, List<RegistroToma> registrosHoy) {
        final List<DoseItem> doses = new ArrayList<>();
        final List<Horario> todosLosHorarios = new ArrayList<>();
        final int[] pendientes = { meds.size() };

        for (Medicamento med : meds) {
            VimedRepo.listarHorarios(med.getId(), new VimedRepo.Cb<List<Horario>>() {
                @Override
                public void onOk(List<Horario> horarios) {
                    for (Horario hor : horarios) {
                        agregarTomasDelHorario(doses, med, hor, registrosHoy);
                        todosLosHorarios.add(hor);
                    }
                    if (--pendientes[0] == 0) terminar();
                }
                @Override
                public void onError(String msg) {
                    if (--pendientes[0] == 0) terminar();
                }

                private void terminar() {
                    pintarTomas(doses);
                    // Los horarios ya están en memoria: aprovechamos para
                    // mirar si el historial sugiere ajustar alguno.
                    buscarSugerencias(meds, todosLosHorarios);
                }
            });
        }
    }

    /** Expande un horario (hora inicio + intervalo) en todas las tomas del día. */
    private void agregarTomasDelHorario(List<DoseItem> doses, Medicamento med,
                                        Horario hor, List<RegistroToma> registrosHoy) {
        if (hor.getHoraInicio() == null) return;
        String[] parts = hor.getHoraInicio().split(":");
        if (parts.length < 2) return;

        int startMins = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        int interval = hor.getIntervaloHoras() * 60;
        if (interval <= 0) interval = 24 * 60;

        int t = startMins;
        while (t < 24 * 60) {
            String hora = String.format(Locale.getDefault(), "%02d:%02d", t / 60, t % 60);
            RegistroToma reg = registroDeToma(hor.getId(), hora, registrosHoy);
            String estado = reg != null
                ? (reg.getEstado() != null ? reg.getEstado() : "omitida")
                : "pendiente";
            int idReg = reg != null ? reg.getId() : -1;
            doses.add(new DoseItem(med, hora, estado, t, hor.getId(), idReg));
            t += interval;
        }
    }

    /** Dibuja la lista y actualiza los contadores. */
    private void pintarTomas(List<DoseItem> doses) {
        dosesContainer.removeAllViews();

        // Ordenar por hora
        Collections.sort(doses, (a, b) -> a.minutos - b.minutos);

        int total = doses.size();
        int taken = 0;
        for (DoseItem d : doses) {
            if ("confirmada".equals(d.estado) || "tomado".equals(d.estado)) taken++;
        }

        // Actualizar progreso
        updateProgress(taken, total);

        // Mostrar tomas
        View hintTap = findViewById(R.id.tv_hint_tap);
        if (doses.isEmpty()) {
            emptyDoses.setVisibility(View.VISIBLE);
            dosesContainer.setVisibility(View.GONE);
            if (hintTap != null) hintTap.setVisibility(View.GONE);
        } else {
            emptyDoses.setVisibility(View.GONE);
            dosesContainer.setVisibility(View.VISIBLE);
            if (hintTap != null) hintTap.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(this);
            for (DoseItem dose : doses) {
                View item = inflater.inflate(R.layout.item_dose, dosesContainer, false);
                bindDoseItem(item, dose);
                dosesContainer.addView(item);
            }
        }

        // Chip de conteo
        TextView tvChip = findViewById(R.id.tv_total_chip);
        tvChip.setText(total + (total == 1 ? " toma" : " tomas"));

        // Próxima toma pendiente (o aviso de omitidas)
        TextView tvNext = findViewById(R.id.tv_next_dose);
        DoseItem proxima = null;
        int omitidas = 0;
        for (DoseItem d : doses) {
            if ("pendiente".equals(d.estado) || "pospuesta".equals(d.estado)) {
                if (proxima == null) proxima = d;
            } else if ("omitida".equals(d.estado)) {
                omitidas++;
            }
        }
        if (proxima != null) {
            tvNext.setText("Siguiente a las " + proxima.hora);
        } else if (omitidas > 0) {
            tvNext.setText(omitidas == 1
                ? "1 toma sin confirmar"
                : omitidas + " tomas sin confirmar");
        } else {
            tvNext.setText("Todo al día ✓");
        }
    }

    /**
     * Busca la fila de registro_tomas que corresponde a esta toma del día,
     * o null si la alarma todavía no disparó.
     *
     * Match por id_horario + prefijo de fecha_hora_programada (HH:MM del día actual).
     */
    private RegistroToma registroDeToma(int idHorario, String horaHHMM,
                                        List<RegistroToma> registros) {
        RegistroToma encontrado = null;
        for (RegistroToma r : registros) {
            if (r.getIdHorario() != idHorario) continue;
            String prog = r.getFechaHoraProgramada();
            if (prog == null) continue;
            // formato "yyyy-MM-dd HH:mm:ss" — comparamos posiciones 11..15
            if (prog.length() >= 16 && prog.substring(11, 16).equals(horaHHMM)) {
                // Puede haber MÁS DE UNA fila para la misma toma: la alarma
                // crea una como "omitida" al sonar, y si al confirmar no
                // llegó a conocer su id, se crea otra ya confirmada. Ante
                // varias, gana la confirmada — si la persona dijo que la
                // tomó, mostrarle "omitida" es lo peor que puede pasar acá.
                if ("confirmada".equals(r.getEstado())) return r;
                if (encontrado == null) encontrado = r;
            }
        }
        return encontrado;
    }

    /** Diálogo al tocar una toma: confirmar, o deshacer si ya estaba confirmada. */
    private void onDoseClick(DoseItem dose) {
        String nombreMed = dose.med.getNombre();

        if (dose.estaConfirmada()) {
            new AlertDialog.Builder(this)
                .setTitle(nombreMed + " · " + dose.hora)
                .setMessage("Ya marcaste esta toma como tomada. ¿Querés deshacerlo?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Deshacer", (d, w) ->
                    TomaManager.deshacer(this, dose.med, dose.idRegistro,
                        new VimedRepo.Cb<Void>() {
                            @Override public void onOk(Void v) { refrescarTomas(); }
                            @Override public void onError(String msg) {
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(nombreMed + " · " + dose.hora)
            .setMessage("¿Confirmás que tomaste este medicamento?")
            .setNegativeButton("Todavía no", null)
            .setPositiveButton("Sí, ya lo tomé", (d, w) ->
                TomaManager.confirmar(this, dose.med, dose.idRegistro,
                    dose.idHorario, dose.hora, new VimedRepo.Cb<Void>() {
                        @Override public void onOk(Void v) {
                            Toast.makeText(MainActivity.this,
                                "Toma confirmada ✓", Toast.LENGTH_SHORT).show();
                            refrescarTomas();
                        }
                        @Override public void onError(String msg) {
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    }))
            .show();
    }

    private void refrescarTomas() {
        loadTodayDoses();   // pintarTomas ya limpia el contenedor
    }

    // ═══ Recordatorios que se ajustan al hábito real ═══════════

    /**
     * Mira el historial del último mes y, si encuentra un patrón, ofrece un
     * ajuste. Se muestra UNA sugerencia por vez: el dashboard de un adulto
     * mayor no es lugar para una lista de recomendaciones.
     */
    private void buscarSugerencias(List<Medicamento> meds, List<Horario> horarios) {
        if (horarios.isEmpty()) { ocultarSugerencia(); return; }

        Calendar desde = Calendar.getInstance();
        desde.add(Calendar.DAY_OF_YEAR, -AnalizadorAdherencia.DIAS_HISTORIAL);
        String desdeYMD = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(desde.getTime());

        VimedRepo.listarTomasDesde(this, desdeYMD, new VimedRepo.Cb<List<RegistroToma>>() {
            @Override
            public void onOk(List<RegistroToma> historial) {
                Map<Integer, String> originales = new HashMap<>();
                for (Horario h : horarios) {
                    String orig = AjustesAdherencia.horaOriginal(MainActivity.this, h.getId());
                    if (orig != null) originales.put(h.getId(), orig);
                }

                List<Sugerencia> sugerencias = AnalizadorAdherencia.analizar(
                    meds, horarios, historial, originales);

                for (Sugerencia s : sugerencias) {
                    // Las que ya se rechazaron no vuelven a aparecer por un tiempo.
                    if (!AjustesAdherencia.estaSilenciada(MainActivity.this, s)) {
                        mostrarSugerencia(s);
                        return;
                    }
                }
                ocultarSugerencia();
            }

            @Override
            public void onError(String msg) {
                // Sin historial no hay nada que sugerir; no molestamos con el error.
                ocultarSugerencia();
            }
        });
    }

    private void mostrarSugerencia(Sugerencia s) {
        View card = findViewById(R.id.card_sugerencia);
        if (card == null) return;

        ((TextView) findViewById(R.id.tv_sugerencia_titulo)).setText(s.titulo());
        ((TextView) findViewById(R.id.tv_sugerencia_detalle)).setText(s.detalle());

        Button btnSi = findViewById(R.id.btn_sugerencia_si);
        btnSi.setText(s.textoAceptar());
        btnSi.setOnClickListener(v -> aplicarSugerencia(s));

        findViewById(R.id.btn_sugerencia_no).setOnClickListener(v -> {
            AjustesAdherencia.silenciar(this, s);
            ocultarSugerencia();
        });

        card.setVisibility(View.VISIBLE);
    }

    private void ocultarSugerencia() {
        View card = findViewById(R.id.card_sugerencia);
        if (card != null) card.setVisibility(View.GONE);
    }

    private void aplicarSugerencia(Sugerencia s) {
        if (s.tipo == Sugerencia.Tipo.REFORZAR) {
            AjustesAdherencia.activarRefuerzo(this, s.idHorario);
            ocultarSugerencia();
            Toast.makeText(this, "Listo: esa alarma va a sonar más tiempo.",
                Toast.LENGTH_LONG).show();
            return;
        }

        // Antes del primer ajuste guardamos la hora que indicó el médico: es
        // la referencia que impide que el recordatorio se aleje sin control.
        AjustesAdherencia.recordarHoraOriginal(this, s.idHorario, s.horaActual);

        Button btnSi = findViewById(R.id.btn_sugerencia_si);
        if (btnSi != null) btnSi.setEnabled(false);

        VimedRepo.actualizarHoraInicio(s.idHorario, s.horaSugerida,
            new VimedRepo.Cb<Void>() {
                @Override
                public void onOk(Void v) {
                    // Reprograma las alarmas de este celular con la hora nueva.
                    // Pisa las viejas: mismo request code por medicamento.
                    com.tesis.vimed.utils.AlarmaSync.sincronizar(MainActivity.this);
                    ocultarSugerencia();
                    Toast.makeText(MainActivity.this,
                        s.afectaVariasTomas()
                            ? "Listo: las " + s.tomasPorDia() + " tomas del día"
                                + " se movieron " + Math.abs(s.desfaseMinutos)
                                + " minutos"
                            : "Recordatorio movido a las " + s.horaSugerida,
                        Toast.LENGTH_LONG).show();
                    refrescarTomas();
                }

                @Override
                public void onError(String msg) {
                    if (btnSi != null) btnSi.setEnabled(true);
                    Toast.makeText(MainActivity.this,
                        "No se pudo cambiar la hora: " + msg, Toast.LENGTH_LONG).show();
                }
            });
    }

    /** Subtítulos de las tarjetas de acceso rápido. */
    private void actualizarAccesosRapidos(List<Medicamento> meds) {
        TextView tvMeds = findViewById(R.id.tv_quick_meds_sub);
        TextView tvRem  = findViewById(R.id.tv_quick_reminders_sub);
        if (tvMeds == null || tvRem == null) return;

        int total = meds.size();
        int pendientesStock = 0;
        for (Medicamento m : meds) {
            if (m.isStockBajo()) pendientesStock++;
        }

        tvMeds.setText(pendientesStock > 0
            ? pendientesStock + (pendientesStock == 1 ? " pendiente" : " pendientes")
            : total + (total == 1 ? " activo" : " activos"));

        // Cada medicamento activo tiene su recordatorio programado
        tvRem.setText(total + (total == 1 ? " activo" : " activos"));
    }

    /** Banner con los medicamentos que llegaron al stock mínimo. */
    private void mostrarAlertaStock(List<Medicamento> meds) {
        View alerta = findViewById(R.id.alert_stock);
        TextView tvStock = findViewById(R.id.tv_stock_text);
        if (alerta == null || tvStock == null) return;

        List<String> bajos = new ArrayList<>();
        for (Medicamento m : meds) {
            if (m.isStockBajo()) {
                bajos.add(m.getNombre() + " (" + m.getStockActual() + ")");
            }
        }

        if (bajos.isEmpty()) {
            alerta.setVisibility(View.GONE);
            return;
        }

        String texto = bajos.size() == 1
            ? "Se está por acabar " + bajos.get(0) + ". Acordate de reponerlo."
            : "Se están por acabar " + bajos.size() + " medicamentos: "
              + android.text.TextUtils.join(", ", bajos) + ".";
        tvStock.setText(texto);
        alerta.setVisibility(View.VISIBLE);
        alerta.setOnClickListener(v -> startActivity(new Intent(this, MedsListActivity.class)));
    }

    private void bindDoseItem(View item, DoseItem dose) {
        item.setOnClickListener(v -> onDoseClick(dose));

        TextView tvNameDose = item.findViewById(R.id.tv_med_name_dose);
        TextView tvInst = item.findViewById(R.id.tv_instructions);
        TextView tvTime = item.findViewById(R.id.tv_time);
        TextView tvStatus = item.findViewById(R.id.tv_status);
        TextView tvInitial = item.findViewById(R.id.tv_med_initial);
        FrameLayout iconContainer = item.findViewById(R.id.med_icon_container);

        String nombre = dose.med.getNombre();
        String dosis = dose.med.getDosis() > 0
                ? " · " + (int) dose.med.getDosis() + " " + (dose.med.getUnidad() != null ? dose.med.getUnidad() : "")
                : "";
        tvNameDose.setText(nombre + dosis);
        tvInst.setText(instruccionLegible(dose.med.getInstrucciones()));
        tvTime.setText(dose.hora);
        tvInitial.setText(nombre.length() > 0 ? String.valueOf(nombre.charAt(0)).toUpperCase() : "M");

        // Color del ícono
        int bgColor = colorForMed(dose.med.getColorIcono());
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(bgColor);
        iconContainer.setBackground(circle);

        // Tilde de la derecha: verde lleno si está confirmada, gris si no
        // El tilde de la derecha acompaña al chip: verde si se tomó, rojo
        // si se perdió, ámbar si quedó pospuesta. Con un solo color gris
        // para todo lo no confirmado, la fila no decía nada de lejos.
        android.widget.ImageView ivCheck = item.findViewById(R.id.iv_check);
        if (ivCheck != null) {
            int tinte;
            if (dose.estaConfirmada())              tinte = R.color.success;
            else if ("omitida".equals(dose.estado)) tinte = R.color.danger;
            else if ("pospuesta".equals(dose.estado)) tinte = R.color.warn;
            else                                    tinte = R.color.ink_6;
            ivCheck.setColorFilter(getColor(tinte));
        }

        // Estado
        switch (dose.estado) {
            case "confirmada":
            case "tomado":
                tvTime.setTextColor(getColor(R.color.ink_4));
                tvStatus.setText("Tomado");
                tvStatus.setBackgroundResource(R.drawable.shape_chip_success);
                tvStatus.setTextColor(getColor(R.color.success));
                break;
            // Cada estado con su color. Antes pendiente, pospuesta y
            // omitida usaban el mismo chip gris y solo cambiaba la
            // palabra: había que leer cada fila para saber cómo venía el
            // día, que es justo lo contrario de lo que tiene que hacer un
            // dashboard.
            case "pospuesta":
                tvTime.setTextColor(getColor(R.color.ink));
                tvStatus.setText("Pospuesta");
                tvStatus.setBackgroundResource(R.drawable.shape_chip_warn);
                tvStatus.setTextColor(getColor(R.color.warn));
                break;
            case "omitida":
                tvTime.setTextColor(getColor(R.color.ink_4));
                tvStatus.setText("Omitida");
                tvStatus.setBackgroundResource(R.drawable.shape_chip_danger);
                tvStatus.setTextColor(getColor(R.color.danger));
                break;
            default: // pendiente
                tvTime.setTextColor(getColor(R.color.ink));
                tvStatus.setText("Pendiente");
                tvStatus.setBackgroundResource(R.drawable.shape_chip_ink);
                tvStatus.setTextColor(getColor(R.color.ink_3));
                break;
        }
    }

    private void updateProgress(int taken, int total) {
        CircularProgressIndicator ring = findViewById(R.id.progress_ring);
        TextView tvPct = findViewById(R.id.tv_progress_pct);
        TextView tvSummary = findViewById(R.id.tv_doses_summary);

        int pct = total > 0 ? (taken * 100 / total) : 0;
        ring.setProgress(pct, true);
        tvPct.setText(pct + "%");
        tvSummary.setText(taken + " de " + total + (total == 1 ? " toma" : " tomas"));
    }

    private void setupAppointmentPlaceholder() {
        VimedRepo.listarCitas(this, new VimedRepo.Cb<List<CitaMedica>>() {
            @Override
            public void onOk(List<CitaMedica> citas) {
                // El endpoint trae todas ordenadas por fecha; nos quedamos
                // con la primera que todavía no pasó.
                String ahora = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(new Date());
                CitaMedica proxima = null;
                for (CitaMedica c : citas) {
                    if (c.getFechaHora() != null && c.getFechaHora().compareTo(ahora) >= 0) {
                        proxima = c;
                        break;
                    }
                }
                pintarCita(proxima);
            }

            @Override
            public void onError(String msg) {
                pintarCita(null);
            }
        });
    }

    private void pintarCita(CitaMedica proxima) {
        if (proxima == null) {
            findViewById(R.id.appointment_card).setVisibility(View.GONE);
            findViewById(R.id.empty_appointments).setVisibility(View.VISIBLE);
            return;
        }
        findViewById(R.id.appointment_card).setVisibility(View.VISIBLE);
        findViewById(R.id.empty_appointments).setVisibility(View.GONE);
        try {
            // Puede venir "yyyy-MM-dd HH:mm:ss" o ISO "yyyy-MM-ddTHH:mm:ss+00:00"
            String soloFecha = proxima.getFechaHora().substring(0, 10);
            String[] fecha = soloFecha.split("-");
            String[] meses = {"ENE","FEB","MAR","ABR","MAY","JUN","JUL","AGO","SEP","OCT","NOV","DIC"};
            ((TextView) findViewById(R.id.tv_appt_day)).setText(String.valueOf(Integer.parseInt(fecha[2])));
            ((TextView) findViewById(R.id.tv_appt_month)).setText(meses[Integer.parseInt(fecha[1]) - 1]);
        } catch (Exception ignored) {}
        ((TextView) findViewById(R.id.tv_appt_doctor)).setText(proxima.getMedico() != null ? proxima.getMedico() : "");
        ((TextView) findViewById(R.id.tv_appt_specialty)).setText(proxima.getEspecialidad() != null ? proxima.getEspecialidad() : "");
        ((TextView) findViewById(R.id.tv_appt_place)).setText(proxima.getLugar() != null ? proxima.getLugar() : "");
    }

    private void setupBottomNav() {
        // Esta es la pantalla de inicio del adulto mayor: cerrarla al
        // navegar lo dejaría sin nada atrás, de ahí el false.
        NavInferior.configurar(this, ModoPaciente.propio(), R.id.nav_home, false);
    }

    private int colorForMed(String colorKey) {
        if (colorKey == null) return Color.parseColor("#0d8b7d");
        switch (colorKey.toLowerCase()) {
            case "azul":    return Color.parseColor("#1e5ca8");
            case "verde":   return Color.parseColor("#2e7d58");
            case "rojo":    return Color.parseColor("#b3261e");
            case "amarillo":return Color.parseColor("#b86a00");
            case "morado":  return Color.parseColor("#6750A4");
            case "gris":    return Color.parseColor("#9aa39f");
            default:        return Color.parseColor("#0d8b7d");
        }
    }

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

    // Clase interna para representar una toma del día
    private static class DoseItem {
        Medicamento med;
        String hora;
        String estado;
        int minutos;
        int idHorario;
        int idRegistro;   // -1 si la alarma todavía no disparó

        DoseItem(Medicamento med, String hora, String estado, int minutos,
                 int idHorario, int idRegistro) {
            this.med = med;
            this.hora = hora;
            this.estado = estado;
            this.minutos = minutos;
            this.idHorario = idHorario;
            this.idRegistro = idRegistro;
        }

        boolean estaConfirmada() {
            return "confirmada".equals(estado) || "tomado".equals(estado);
        }
    }
}
