package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tesis.vimed.adherencia.ResumenAdherencia;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.utils.ModoPaciente;
import com.tesis.vimed.models.CitaMedica;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.Notificacion;
import com.tesis.vimed.models.RegistroToma;
import com.tesis.vimed.models.UsuarioSupabase;
import com.tesis.vimed.models.Vinculacion;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Home del rol FAMILIAR: en vez de gestionar su propia medicación,
 * monitorea al adulto mayor vinculado — tomas del día, alertas de
 * stock y el historial de notificaciones que la app del adulto
 * espeja en Supabase.
 */
public class CuidadorActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private View emptyPaciente, contentPaciente;
    private LinearLayout tomasContainer, actividadContainer, medsContainer, citasContainer;

    /** id_usuario (Supabase) del adulto que se está mostrando. */
    private int idAdulto = -1;
    private String nombreAdulto = "";

    /**
     * id_horario → medicamento al que pertenece.
     * registro_tomas guarda id_horario, así que sin este mapa no podríamos
     * decir QUÉ medicamento se tomó — solo que "hubo una toma".
     */
    private final Map<Integer, Medicamento> medPorHorario = new HashMap<>();

    /**
     * id_medicamento → sus horarios. Lo que el cuidador pregunta por teléfono
     * es "¿a qué hora la toma?" y "¿cada cuánto?", y hasta ahora la tarjeta
     * del medicamento no lo decía: había que entrar a la pantalla de
     * medicamentos y abrir la ficha.
     */
    private final Map<Integer, List<Horario>> horariosPorMed = new HashMap<>();

    /** Todos los familiares a cargo de este cuidador. */
    private final List<Vinculacion> misVinculos = new ArrayList<>();

    /** id_usuario → nombre, para el selector cuando hay más de uno. */
    private final Map<Integer, String> nombrePorAdulto = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cuidador);

        sessionManager = new SessionManager(this);

        emptyPaciente      = findViewById(R.id.empty_paciente);
        contentPaciente    = findViewById(R.id.content_paciente);

        findViewById(R.id.btn_vincular_paciente).setOnClickListener(v ->
            startActivity(new Intent(this, VincularFamiliarActivity.class)));
        tomasContainer     = findViewById(R.id.tomas_container);
        actividadContainer = findViewById(R.id.actividad_container);
        medsContainer      = findViewById(R.id.meds_container);
        citasContainer     = findViewById(R.id.citas_container);

        String nombre = sessionManager.getNombre();
        ((TextView) findViewById(R.id.tv_greeting)).setText(
            "Hola, " + (nombre != null && !nombre.isEmpty() ? nombre : "cuidador") + ".");

        findViewById(R.id.btn_profile).setOnClickListener(v -> menuPerfil());
        setupBottomNav();

        // Lo que el cuidador carga acá aparece en la app del adulto mayor
        findViewById(R.id.btn_agregar_med_cuidador).setOnClickListener(v -> {
            if (idAdulto <= 0) return;
            Intent i = new Intent(this, AgregarMedicamentoActivity.class);
            i.putExtra(AgregarMedicamentoActivity.EXTRA_PARA_ID_USUARIO, idAdulto);
            i.putExtra(AgregarMedicamentoActivity.EXTRA_PARA_NOMBRE, nombreAdulto);
            startActivity(i);
        });

        findViewById(R.id.btn_agregar_cita_cuidador).setOnClickListener(v -> {
            if (idAdulto <= 0) return;
            Intent i = new Intent(this, AgregarCitaActivity.class);
            i.putExtra(AgregarCitaActivity.EXTRA_PARA_ID_USUARIO, idAdulto);
            i.putExtra(AgregarCitaActivity.EXTRA_PARA_NOMBRE, nombreAdulto);
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Al volver del chatbot o de la pantalla de vínculos, la barra
        // quedaría marcando esa sección y no la que se está viendo.
        BottomNavigationView nav = findViewById(R.id.bottom_nav_cuidador);
        if (nav != null) nav.setSelectedItemId(R.id.nav_home);

        cargarVinculo();
    }

    private void menuPerfil() {
        // "Vincular paciente" vive acá desde que la barra de abajo pasó a
        // replicar la del adulto mayor: es una acción ocasional, no un
        // destino que merezca un lugar fijo en la navegación.
        new AlertDialog.Builder(this)
            .setTitle(sessionManager.getNombre())
            .setItems(new String[]{"Actualizar", "Vincular paciente",
                                   "Configuración", "Cerrar sesión"}, (d, w) -> {
                if (w == 0) {
                    cargarVinculo();
                } else if (w == 1) {
                    startActivity(new Intent(this, VincularFamiliarActivity.class));
                } else if (w == 2) {
                    // Se arrastra el paciente que se está mirando: el peso y
                    // la edad que la pantalla edita son LOS SUYOS. Sin esto,
                    // el cuidador cargaba sus propios kilos en su propia
                    // fila, que no lee nadie, y el dato no aparecía nunca
                    // del lado del paciente.
                    startActivity(idAdulto > 0
                        ? ModoPaciente.de(idAdulto, nombreAdulto)
                            .intent(this, ConfiguracionActivity.class)
                        : new Intent(this, ConfiguracionActivity.class));
                } else {
                    sessionManager.logout();
                    Intent i = new Intent(this, WelcomeActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                }
            })
            .show();
    }

    // ═══ Cargar el vínculo y después todo lo demás ═════════════

    private void cargarVinculo() {
        com.tesis.vimed.utils.BannerSolicitudes.revisar(this);

        // Solo los aceptados: un vínculo pendiente todavía no da acceso, y
        // tratarlo como paciente dejaría la pantalla pidiendo datos que el
        // RLS no va a devolver.
        VimedRepo.listarPacientesAceptados(this, new VimedRepo.Cb<List<Vinculacion>>() {
            @Override
            public void onOk(List<Vinculacion> vinculos) {
                if (vinculos.isEmpty()) {
                    mostrarVacio();
                    return;
                }
                misVinculos.clear();
                misVinculos.addAll(vinculos);

                // Respetamos al paciente que el cuidador venía mirando; si ya
                // no está vinculado, caemos al primero.
                int guardado = sessionManager != null ? pacienteGuardado() : -1;
                idAdulto = -1;
                for (Vinculacion v : vinculos) {
                    if (v.getIdAdulto() == guardado) { idAdulto = guardado; break; }
                }
                if (idAdulto <= 0) idAdulto = vinculos.get(0).getIdAdulto();

                mostrarSelectorSiHayVarios();
                resolverNombresDelSelector();
                cargarPaciente();
            }

            @Override
            public void onError(String msg) {
                mostrarVacio();
                Toast.makeText(CuidadorActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ═══ Varios familiares a cargo ═════════════════════════════

    /**
     * Con un solo vínculo la cabecera se comporta como antes. Con dos o más
     * se vuelve tocable: sin esto, el segundo familiar era invisible y no
     * había forma de llegar a él.
     */
    private void mostrarSelectorSiHayVarios() {
        View header = findViewById(R.id.card_paciente);
        TextView pista = findViewById(R.id.tv_cambiar_paciente);

        boolean varios = misVinculos.size() > 1;
        if (pista != null) pista.setVisibility(varios ? View.VISIBLE : View.GONE);
        if (header == null) return;

        if (varios) {
            header.setOnClickListener(v -> elegirPaciente());
        } else {
            header.setOnClickListener(null);
            header.setClickable(false);
        }
    }

    /**
     * Pide el nombre de los familiares que NO se están mostrando, para que
     * el diálogo de cambio diga "Rosa" y no "Familiar #12". El que se está
     * viendo ya lo resuelve cargarPaciente().
     */
    private void resolverNombresDelSelector() {
        if (misVinculos.size() < 2) return;
        for (Vinculacion v : misVinculos) {
            final int id = v.getIdAdulto();
            if (id == idAdulto || nombrePorAdulto.containsKey(id)) continue;
            VimedRepo.buscarPerfilPorId(id, new VimedRepo.Cb<UsuarioSupabase>() {
                @Override public void onOk(UsuarioSupabase perfil) {
                    if (perfil != null && perfil.getNombre() != null) {
                        nombrePorAdulto.put(id, perfil.getNombre());
                    }
                }
            });
        }
    }

    private void elegirPaciente() {
        // Los nombres ya se resolvieron al pintar cada paciente; los que
        // falten se muestran por id hasta que se carguen.
        final List<Vinculacion> opciones = new ArrayList<>(misVinculos);
        String[] etiquetas = new String[opciones.size()];
        for (int i = 0; i < opciones.size(); i++) {
            int id = opciones.get(i).getIdAdulto();
            String nombre = nombrePorAdulto.get(id);
            etiquetas[i] = nombre != null ? nombre : "Familiar #" + id;
        }

        new AlertDialog.Builder(this)
            .setTitle("¿A quién querés ver?")
            .setItems(etiquetas, (d, w) -> {
                int elegido = opciones.get(w).getIdAdulto();
                if (elegido == idAdulto) return;
                idAdulto = elegido;
                guardarPacienteElegido(elegido);
                limpiarSeccionesDelPacienteAnterior();
                cargarPaciente();
            })
            .show();
    }

    /**
     * Las secciones se pintan a medida que van llegando las respuestas de
     * red. Si no se vacían al cambiar de paciente, durante ese rato se ven
     * los datos del anterior bajo el nombre del nuevo.
     */
    private void limpiarSeccionesDelPacienteAnterior() {
        tomasContainer.removeAllViews();
        actividadContainer.removeAllViews();
        medsContainer.removeAllViews();
        citasContainer.removeAllViews();
        medPorHorario.clear();
        horariosPorMed.clear();
        findViewById(R.id.alert_stock_cuidador).setVisibility(View.GONE);
        findViewById(R.id.alert_olvidos_cuidador).setVisibility(View.GONE);
        findViewById(R.id.card_adherencia).setVisibility(View.GONE);
    }

    private static final String PREFS_CUIDADOR = "vimed_cuidador";
    private static final String K_PACIENTE = "ultimo_paciente";

    private int pacienteGuardado() {
        return getSharedPreferences(PREFS_CUIDADOR, MODE_PRIVATE)
            .getInt(K_PACIENTE, -1);
    }

    private void guardarPacienteElegido(int idAdulto) {
        getSharedPreferences(PREFS_CUIDADOR, MODE_PRIVATE)
            .edit().putInt(K_PACIENTE, idAdulto).apply();
    }

    private void mostrarVacio() {
        emptyPaciente.setVisibility(View.VISIBLE);
        contentPaciente.setVisibility(View.GONE);
    }

    private void cargarPaciente() {
        emptyPaciente.setVisibility(View.GONE);
        contentPaciente.setVisibility(View.VISIBLE);

        // Nombre del adulto
        VimedRepo.buscarPerfilPorId(idAdulto, new VimedRepo.Cb<UsuarioSupabase>() {
            @Override
            public void onOk(UsuarioSupabase perfil) {
                String nombre = perfil != null && perfil.getNombre() != null
                    ? perfil.getNombre() : "Adulto mayor";
                nombreAdulto = nombre;
                nombrePorAdulto.put(idAdulto, nombre);
                ((TextView) findViewById(R.id.tv_paciente_nombre)).setText(nombre);
                ((TextView) findViewById(R.id.tv_paciente_initial)).setText(
                    nombre.substring(0, 1).toUpperCase(Locale.getDefault()));
                ((TextView) findViewById(R.id.tv_paciente_sub)).setText(
                    perfil != null && perfil.getCorreo() != null ? perfil.getCorreo() : "");
                ((TextView) findViewById(R.id.tv_titulo_meds)).setText(
                    "Medicamentos de " + nombre.split(" ")[0]);
            }
        });

        // Medicamentos → stock, listado y (encadenado) el mapa de horarios
        // que las tomas necesitan para saber a qué medicamento pertenecen.
        VimedRepo.listarMedicamentosDe(idAdulto, new VimedRepo.Cb<List<Medicamento>>() {
            @Override
            public void onOk(List<Medicamento> meds) {
                pintarAlertaStock(meds);
                pintarMedicamentos(meds);
                cargarHorariosYTomas(meds);
            }
        });

        // Citas médicas
        VimedRepo.listarCitasDe(idAdulto, new VimedRepo.Cb<List<CitaMedica>>() {
            @Override
            public void onOk(List<CitaMedica> citas) {
                pintarCitas(citas);
            }
        });

        // Olvidos de la semana
        cargarOlvidosDelPaciente();

        // Actividad reciente (notificaciones espejadas)
        VimedRepo.listarNotificacionesDe(idAdulto, new VimedRepo.Cb<List<Notificacion>>() {
            @Override
            public void onOk(List<Notificacion> notis) {
                pintarActividad(notis);
            }
        });
    }

    /**
     * Cuántas tomas quedaron sin confirmar en la semana. Es un contador, no
     * una lista: el detalle vive en {@link OlvidosActivity}, que además deja
     * corregirlas. Meter la lista entera acá alargaría justo la pantalla que
     * hay que acortar.
     */
    private void cargarOlvidosDelPaciente() {
        View banner = findViewById(R.id.alert_olvidos_cuidador);
        banner.setVisibility(View.GONE);

        final int paciente = idAdulto;
        java.util.Calendar desde = java.util.Calendar.getInstance();
        desde.add(java.util.Calendar.DAY_OF_YEAR, -7);
        String desdeYMD = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(desde.getTime());

        VimedRepo.listarOlvidos(this, paciente, desdeYMD,
            new VimedRepo.Cb<List<RegistroToma>>() {
                @Override public void onOk(List<RegistroToma> olvidos) {
                    // Puede haber cambiado de paciente mientras viajaba la
                    // consulta; el número del anterior no va acá.
                    if (paciente != idAdulto || olvidos.isEmpty()) return;

                    String nombre = nombreAdulto.isEmpty()
                        ? "Tu familiar" : nombreAdulto.split(" ")[0];
                    ((TextView) findViewById(R.id.tv_olvidos_cuidador)).setText(
                        olvidos.size() == 1
                            ? nombre + " tiene 1 toma sin confirmar esta semana"
                            : nombre + " tiene " + olvidos.size()
                                + " tomas sin confirmar esta semana");

                    banner.setVisibility(View.VISIBLE);
                    banner.setOnClickListener(v -> startActivity(
                        ModoPaciente.de(idAdulto, nombreAdulto)
                            .intent(CuidadorActivity.this, OlvidosActivity.class)));
                }
                @Override public void onError(String msg) {
                    // Sin red no se muestra nada. Ya está oculto.
                }
            });
    }

    /**
     * Arma el mapa id_horario → medicamento y recién después pide las tomas.
     * Si lo hiciéramos en paralelo, las tomas podrían llegar antes que el
     * mapa y se pintarían sin el nombre del medicamento.
     */
    private void cargarHorariosYTomas(List<Medicamento> meds) {
        final Map<Integer, Medicamento> porIdMed = new HashMap<>();
        final List<Integer> ids = new ArrayList<>();
        for (Medicamento m : meds) {
            porIdMed.put(m.getId(), m);
            ids.add(m.getId());
        }

        VimedRepo.listarHorariosDe(ids, new VimedRepo.Cb<List<Horario>>() {
            @Override
            public void onOk(List<Horario> horarios) {
                medPorHorario.clear();
                horariosPorMed.clear();
                for (Horario h : horarios) {
                    Medicamento m = porIdMed.get(h.getIdMedicamento());
                    if (m != null) medPorHorario.put(h.getId(), m);

                    List<Horario> delMed = horariosPorMed.get(h.getIdMedicamento());
                    if (delMed == null) {
                        delMed = new ArrayList<>();
                        horariosPorMed.put(h.getIdMedicamento(), delMed);
                    }
                    delMed.add(h);
                }
                // Se repinta: la primera pasada corrió sin horarios (vienen
                // en una segunda consulta) y las tarjetas quedaron sin la
                // hora ni la frecuencia, que es lo que se vino a ver.
                pintarMedicamentos(meds);
                cargarTomasDeHoy();
                cargarResumenAdherencia(meds, horarios);
            }

            @Override
            public void onError(String msg) {
                cargarTomasDeHoy();   // sin nombres, pero mostramos las tomas
            }
        });
    }

    private void cargarTomasDeHoy() {
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        VimedRepo.listarTomasDelDiaDe(idAdulto, hoy, new VimedRepo.Cb<List<RegistroToma>>() {
            @Override
            public void onOk(List<RegistroToma> tomas) {
                pintarTomasHoy(tomas, hoy);
            }
        });
    }

    // ═══ Cómo viene el tratamiento ═════════════════════════════

    /**
     * El familiar es quien puede hacer algo con esta información, así que
     * el resumen del último mes va acá y no en la app del adulto mayor.
     *
     * A diferencia del dashboard del adulto, esta tarjeta NO propone nada:
     * son hechos para conversar con el médico. Sugerirle a un familiar que
     * cambie el tratamiento de otra persona es un lugar al que la app no
     * tiene por qué meterse.
     */
    private void cargarResumenAdherencia(List<Medicamento> meds, List<Horario> horarios) {
        final String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(new Date());
        String desde = ResumenAdherencia.restarDias(hoy, ResumenAdherencia.DIAS_LARGO);

        VimedRepo.listarTomasDelDiaDe(idAdulto, desde, new VimedRepo.Cb<List<RegistroToma>>() {
            @Override
            public void onOk(List<RegistroToma> historial) {
                pintarResumenAdherencia(
                    ResumenAdherencia.calcular(meds, horarios, historial, hoy));
            }

            @Override
            public void onError(String msg) {
                findViewById(R.id.card_adherencia).setVisibility(View.GONE);
            }
        });
    }

    private void pintarResumenAdherencia(ResumenAdherencia.Resumen r) {
        View card = findViewById(R.id.card_adherencia);
        card.setVisibility(View.VISIBLE);

        ((TextView) findViewById(R.id.tv_adherencia_titular)).setText(r.titular());

        pintarPorcentaje(R.id.tv_adherencia_semana, R.id.tv_adherencia_semana_sub,
            r.porcentajeCorto(), r.confirmadasCorto, r.totalCorto, "Últimos 7 días");
        pintarPorcentaje(R.id.tv_adherencia_mes, R.id.tv_adherencia_mes_sub,
            r.porcentajeLargo(), r.confirmadasLargo, r.totalLargo, "Últimos 30 días");

        // Horarios que se pierden seguido
        LinearLayout cont = findViewById(R.id.puntos_flojos_container);
        View titulo = findViewById(R.id.tv_puntos_flojos_titulo);
        cont.removeAllViews();

        if (r.puntosFlojos.isEmpty()) {
            titulo.setVisibility(View.GONE);
            return;
        }
        titulo.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ResumenAdherencia.PuntoFlojo p : r.puntosFlojos) {
            View item = inflater.inflate(R.layout.item_actividad, cont, false);
            ((ImageView) item.findViewById(R.id.act_icon))
                .setImageResource(R.drawable.ic_warn);
            ((TextView) item.findViewById(R.id.act_mensaje))
                .setText(p.nombreMedicamento
                    + (p.hora.isEmpty() ? "" : " · " + p.hora));
            ((TextView) item.findViewById(R.id.act_fecha)).setText(p.detalle());
            cont.addView(item);
        }
    }

    private void pintarPorcentaje(int idNumero, int idSub, int pct,
                                  int confirmadas, int total, String etiqueta) {
        TextView tvNum = findViewById(idNumero);
        TextView tvSub = findViewById(idSub);

        if (pct < 0) {
            // Sin tomas en la ventana: un "0%" ahí diría que no tomó nada,
            // que es muy distinto de que no hubiera nada para tomar.
            tvNum.setText("—");
            tvSub.setText(etiqueta + ": sin tomas");
            return;
        }
        tvNum.setText(pct + "%");
        tvSub.setText(etiqueta + " · " + confirmadas + " de " + total);
    }

    // ═══ Navegación ════════════════════════════════════════════

    /**
     * Mismas pantallas que el adulto mayor, pero apuntando al paciente.
     *
     * El destino se arma en el momento del toque y no al crear la barra:
     * el vínculo llega por red y el cuidador puede cambiar de familiar sin
     * salir de acá, así que un intent construido de antemano quedaría
     * apuntando al paciente equivocado.
     */
    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav_cuidador);
        if (nav == null) return;
        nav.setSelectedItemId(R.id.nav_home);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;

            Class<?> destino;
            if (id == R.id.nav_meds)              destino = MedsListActivity.class;
            else if (id == R.id.nav_appointments) destino = AppointmentsActivity.class;
            else if (id == R.id.nav_stats)        destino = DashboardActivity.class;
            else if (id == R.id.nav_vita)         destino = ChatbotActivity.class;
            else return false;

            if (idAdulto <= 0) {
                Toast.makeText(this, "Todavía no hay ningún paciente vinculado",
                    Toast.LENGTH_SHORT).show();
                return false;
            }

            startActivity(ModoPaciente.de(idAdulto, nombreAdulto).intent(this, destino));
            overridePendingTransition(0, 0);
            return true;
        });
    }

    // ═══ Secciones ═════════════════════════════════════════════

    private void pintarAlertaStock(List<Medicamento> meds) {
        View alerta = findViewById(R.id.alert_stock_cuidador);
        TextView tv = findViewById(R.id.tv_stock_cuidador);

        StringBuilder bajos = new StringBuilder();
        for (Medicamento m : meds) {
            if (m.isStockBajo()) {
                if (bajos.length() > 0) bajos.append(", ");
                bajos.append(m.getNombre()).append(" (").append(m.getStockActual()).append(")");
            }
        }

        if (bajos.length() == 0) {
            alerta.setVisibility(View.GONE);
        } else {
            tv.setText("Medicamentos por acabarse: " + bajos + ". Coordiná la reposición.");
            alerta.setVisibility(View.VISIBLE);
        }
    }

    private void pintarTomasHoy(List<RegistroToma> tomas, String hoy) {
        tomasContainer.removeAllViews();
        View vacio = findViewById(R.id.tv_tomas_empty);

        int confirmadas = 0, total = 0;
        LayoutInflater inflater = LayoutInflater.from(this);

        for (RegistroToma t : tomas) {
            String prog = t.getFechaHoraProgramada();
            if (prog == null || !prog.startsWith(hoy)) continue;
            total++;
            if ("confirmada".equals(t.getEstado())) confirmadas++;

            View item = inflater.inflate(R.layout.item_actividad, tomasContainer, false);
            ImageView icon = item.findViewById(R.id.act_icon);
            TextView msg = item.findViewById(R.id.act_mensaje);
            TextView fecha = item.findViewById(R.id.act_fecha);

            String hora = prog.length() >= 16 ? prog.substring(11, 16) : "";

            // El nombre sale PRIMERO de la propia fila de la toma, donde
            // quedó copiado al registrarla. El mapa es el respaldo: sirve
            // para las filas viejas, anteriores a esa columna, pero se cae
            // en cuanto el medicamento se da de baja —el mapa se arma con
            // los activos— y era lo que dejaba todo el historial diciendo
            // "Medicamento".
            Medicamento med = medPorHorario.get(t.getIdHorario());
            String nombreMed = t.nombreParaMostrar();
            if (nombreMed == null) {
                nombreMed = med != null && med.getNombre() != null
                    ? med.getNombre() : "Medicamento";
            }

            // Mismo código de color que en la app del adulto mayor: verde
            // tomada, ámbar pospuesta, rojo sin confirmar. Así el familiar
            // ve cómo viene el día sin leer fila por fila.
            String estado;
            switch (t.getEstado() != null ? t.getEstado() : "omitida") {
                case "confirmada":
                    estado = "Tomó " + nombreMed + " ✓";
                    icon.setImageResource(R.drawable.ic_check);
                    icon.setColorFilter(getColor(R.color.success));
                    break;
                case "pospuesta":
                    estado = nombreMed + " — pospuesta";
                    icon.setImageResource(R.drawable.ic_refresh);
                    icon.setColorFilter(getColor(R.color.warn));
                    break;
                default:
                    estado = nombreMed + " — sin confirmar";
                    icon.setImageResource(R.drawable.ic_warn);
                    icon.setColorFilter(getColor(R.color.danger));
                    break;
            }

            msg.setText(estado);
            String detalle = "Programada a las " + hora;
            String dosisTxt = t.getDosisTexto() != null && !t.getDosisTexto().isEmpty()
                ? t.getDosisTexto()
                : (med != null ? dosisLegible(med) : "");
            if (!dosisTxt.isEmpty()) detalle += " · " + dosisTxt;
            fecha.setText(detalle);
            tomasContainer.addView(item);
        }

        ((TextView) findViewById(R.id.tv_resumen_hoy)).setText(
            total == 0 ? "Sin tomas programadas hoy"
                       : "Hoy: " + confirmadas + " de " + total
                         + (total == 1 ? " toma confirmada" : " tomas confirmadas"));

        vacio.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
    }

    /** Listado completo de lo que el adulto tiene cargado. Tocar = editar. */
    private void pintarMedicamentos(List<Medicamento> meds) {
        medsContainer.removeAllViews();
        View vacio = findViewById(R.id.tv_meds_empty);

        if (meds.isEmpty()) {
            vacio.setVisibility(View.VISIBLE);
            return;
        }
        vacio.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Medicamento m : meds) {
            View item = inflater.inflate(R.layout.item_actividad, medsContainer, false);
            ImageView icon = item.findViewById(R.id.act_icon);
            TextView msg = item.findViewById(R.id.act_mensaje);
            TextView fecha = item.findViewById(R.id.act_fecha);

            icon.setImageResource(R.drawable.ic_nav_meds);
            msg.setText(m.getNombre() != null ? m.getNombre() : "Medicamento");

            // Dos renglones y nada más. La pantalla del cuidador tiene seis
            // secciones; si cada medicamento ocupa cuatro líneas, lo de
            // abajo no se lee nunca. Va lo que se pregunta por teléfono:
            // a qué hora, cada cuánto, cuántas quedan.
            fecha.setText(pautaDe(m) + System.lineSeparator() + inventarioDe(m));

            // Misma puerta que en la app del adulto mayor: la ficha completa.
            // El menú de dos opciones que había acá dejaba al cuidador sin
            // ver la dosis ni el vencimiento de lo que estaba mirando.
            item.setOnClickListener(v -> {
                Intent i = ModoPaciente.de(idAdulto, nombreAdulto)
                    .intent(this, MedicamentoDetalleActivity.class);
                i.putExtra(MedicamentoDetalleActivity.EXTRA_ID_MEDICAMENTO, m.getId());
                startActivity(i);
            });
            medsContainer.addView(item);
        }
    }

    /** "50 mg · primera a las 08:00 · cada 8 h" */
    private String pautaDe(Medicamento m) {
        StringBuilder sb = new StringBuilder(dosisLegible(m));

        List<Horario> hs = horariosPorMed.get(m.getId());
        if (hs == null || hs.isEmpty()) return sb.toString();

        // La "primera dosis" es la hora más temprana del día, no la primera
        // fila que devolvió la base: con dos horarios cargados en cualquier
        // orden, tomar la primera fila mostraría la de la tarde.
        String primera = null;
        int intervalo = 0;
        for (Horario h : hs) {
            String hi = h.getHoraInicio();
            if (hi == null || hi.length() < 5) continue;
            if (primera == null || hi.compareTo(primera) < 0) primera = hi;
            if (h.getIntervaloHoras() > 0) intervalo = h.getIntervaloHoras();
        }

        if (primera != null) sb.append(" · primera a las ").append(primera.substring(0, 5));
        if (intervalo > 0) {
            sb.append(intervalo == 24 ? " · una vez al día"
                                      : " · cada " + intervalo + " h");
        }
        return sb.toString();
    }

    /** "Quedan 12 · Vence en 2 días", con los avisos que correspondan. */
    private String inventarioDe(Medicamento m) {
        StringBuilder sb = new StringBuilder("Quedan ").append(m.getStockActual());
        if (m.isStockBajo()) sb.append(" ⚠ por acabarse");

        String vence = m.vencimientoLegible();
        if (!vence.isEmpty()) {
            sb.append(" · ").append(vence);
            if (m.venceProto()) sb.append(" ⚠");
        }
        return sb.toString();
    }

    private void pintarCitas(List<CitaMedica> citas) {
        citasContainer.removeAllViews();
        View vacio = findViewById(R.id.tv_citas_empty);

        if (citas.isEmpty()) {
            vacio.setVisibility(View.VISIBLE);
            return;
        }
        vacio.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        int mostradas = 0;
        for (CitaMedica c : citas) {
            if (mostradas++ >= 5) break;

            View item = inflater.inflate(R.layout.item_actividad, citasContainer, false);
            ImageView icon = item.findViewById(R.id.act_icon);
            TextView msg = item.findViewById(R.id.act_mensaje);
            TextView fecha = item.findViewById(R.id.act_fecha);

            icon.setImageResource(R.drawable.ic_nav_calendar);
            String medico = c.getMedico() != null ? c.getMedico() : "Consulta";
            String esp = c.getEspecialidad();
            msg.setText(esp != null && !esp.isEmpty() ? medico + " · " + esp : medico);

            // fechaLegible: sin esto se veía el timestamp crudo de Postgres
            // ("2026-08-20T15:00:00+00:00") en la tarjeta de la cita.
            String cuando = fechaLegible(c.getFechaHora());
            String lugar = c.getLugar();
            fecha.setText(lugar != null && !lugar.isEmpty() && !cuando.isEmpty()
                ? cuando + " · " + lugar
                : (cuando.isEmpty() ? (lugar != null ? lugar : "") : cuando));
            citasContainer.addView(item);
        }
    }

    /** "500 mg" — sin el .0 cuando la dosis es entera. */
    private String dosisLegible(Medicamento m) {
        float d = m.getDosis();
        String num = d == (int) d ? String.valueOf((int) d) : String.valueOf(d);
        return num + " " + (m.getUnidad() != null ? m.getUnidad() : "");
    }

    private void pintarActividad(List<Notificacion> notis) {
        actividadContainer.removeAllViews();
        View vacio = findViewById(R.id.tv_actividad_empty);

        if (notis.isEmpty()) {
            vacio.setVisibility(View.VISIBLE);
            return;
        }
        vacio.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        int mostradas = 0;
        for (Notificacion n : notis) {
            if (mostradas++ >= 10) break;   // solo lo más reciente

            View item = inflater.inflate(R.layout.item_actividad, actividadContainer, false);
            ImageView icon = item.findViewById(R.id.act_icon);
            TextView msg = item.findViewById(R.id.act_mensaje);
            TextView fecha = item.findViewById(R.id.act_fecha);

            String tipo = n.getTipo() != null ? n.getTipo() : "";
            switch (tipo) {
                case Notificacion.TIPO_STOCK:       icon.setImageResource(R.drawable.ic_nav_meds); break;
                case Notificacion.TIPO_INTERACCION: icon.setImageResource(R.drawable.ic_warn);     break;
                case Notificacion.TIPO_CITA:        icon.setImageResource(R.drawable.ic_nav_calendar); break;
                default:                            icon.setImageResource(R.drawable.ic_bell);     break;
            }

            msg.setText(n.getMensaje() != null ? n.getMensaje() : "");
            fecha.setText(fechaLegible(n.getFechaEnvio()));
            actividadContainer.addView(item);
        }
    }

    /** "2026-08-06T18:35:01+00:00" → "6 Ago · 18:35" (best effort). */
    private String fechaLegible(String iso) {
        if (iso == null || iso.length() < 16) return "";
        try {
            String[] meses = {"Ene","Feb","Mar","Abr","May","Jun",
                              "Jul","Ago","Sep","Oct","Nov","Dic"};
            int mes = Integer.parseInt(iso.substring(5, 7));
            int dia = Integer.parseInt(iso.substring(8, 10));
            String hora = iso.substring(11, 16);
            return dia + " " + meses[mes - 1] + " · " + hora;
        } catch (Exception e) {
            return "";
        }
    }
}
