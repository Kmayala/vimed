package com.tesis.vimed;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.CitaMedica;
import com.tesis.vimed.utils.ModoPaciente;
import com.tesis.vimed.utils.NavInferior;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Citas médicas con dos modos: Lista y Calendario.
 *
 * El calendario se dibuja a mano (no hay CalendarView de Material que
 * permita marcar días con puntos), así que armamos la grilla del mes
 * con LinearLayouts: 7 columnas por 6 filas como máximo.
 */
public class AppointmentsActivity extends AppCompatActivity {

    private static final String[] MESES = {
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };
    private static final String[] DIAS_CORTOS = {"LUN","MAR","MIÉ","JUE","VIE","SÁB","DOM"};
    private static final String[] DIAS_LARGOS = {
        "Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"
    };

    private LinearLayout appointmentsContainer, dayContainer, monthGrid, weekStrip, weekdayHeader;
    private View emptyAppointments, viewLista, viewCalendario, tvDayEmpty;
    private TextView tvSubtitle, tvMonthLabel, tvSelectedDay, tvDayCount;

    /** Todas las citas del usuario, cacheadas para no re-consultar al cambiar de día. */
    private final List<CitaMedica> citas = new ArrayList<>();

    /** Mes que se está mostrando y día elegido. */
    private final Calendar mesVisible = Calendar.getInstance();
    private final Calendar diaElegido = Calendar.getInstance();

    private boolean modoCalendario = false;

    /** De quién son las citas que se están mostrando. */
    private ModoPaciente modo = ModoPaciente.propio();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointments);

        modo = ModoPaciente.de(this);

        appointmentsContainer = findViewById(R.id.appointments_container);
        dayContainer          = findViewById(R.id.day_container);
        monthGrid             = findViewById(R.id.month_grid);
        weekStrip             = findViewById(R.id.week_strip);
        weekdayHeader         = findViewById(R.id.weekday_header);
        emptyAppointments     = findViewById(R.id.empty_appointments);
        viewLista             = findViewById(R.id.view_lista);
        viewCalendario        = findViewById(R.id.view_calendario);
        tvDayEmpty            = findViewById(R.id.tv_day_empty);
        tvSubtitle            = findViewById(R.id.tv_appt_subtitle);
        tvMonthLabel          = findViewById(R.id.tv_month_label);
        tvSelectedDay         = findViewById(R.id.tv_selected_day);
        tvDayCount            = findViewById(R.id.tv_day_count);

        findViewById(R.id.btn_add_appt).setOnClickListener(v -> nuevaCita());
        findViewById(R.id.btn_add_first_appt).setOnClickListener(v -> nuevaCita());

        findViewById(R.id.tab_lista).setOnClickListener(v -> cambiarModo(false));
        findViewById(R.id.tab_calendario).setOnClickListener(v -> cambiarModo(true));

        findViewById(R.id.btn_month_prev).setOnClickListener(v -> moverMes(-1));
        findViewById(R.id.btn_month_next).setOnClickListener(v -> moverMes(1));
        findViewById(R.id.btn_week_prev).setOnClickListener(v -> moverDias(-7));
        findViewById(R.id.btn_week_next).setOnClickListener(v -> moverDias(7));

        findViewById(R.id.card_tip_recordatorios).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Recordatorios de citas")
                .setMessage("Los recordatorios de medicación ya están activos. "
                    + "El aviso previo a cada cita médica todavía está en desarrollo.")
                .setPositiveButton("Entendido", null)
                .show());

        mostrarCartelDePaciente();
        dibujarEncabezadoDias();
        cambiarModo(false);
        setupBottomNav();
    }

    private void mostrarCartelDePaciente() {
        TextView cartel = findViewById(R.id.tv_cartel_paciente);
        if (cartel == null) return;
        if (!modo.esDeOtro()) { cartel.setVisibility(View.GONE); return; }
        cartel.setText(modo.cartel("las citas"));
        cartel.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarCitas();
    }

    private void nuevaCita() {
        // El intent arrastra el paciente, si no la cita se la carga el
        // cuidador a sí mismo.
        startActivity(modo.intent(this, AgregarCitaActivity.class));
    }

    // ═══════════════════════════════════════════════════════════
    //  Datos
    // ═══════════════════════════════════════════════════════════

    private void cargarCitas() {
        VimedRepo.Cb<List<CitaMedica>> cb = new VimedRepo.Cb<List<CitaMedica>>() {
            @Override
            public void onOk(List<CitaMedica> data) {
                citas.clear();
                citas.addAll(data);
                pintarLista();
                pintarCalendario();
            }

            @Override
            public void onError(String msg) {
                Toast.makeText(AppointmentsActivity.this, msg, Toast.LENGTH_LONG).show();
                citas.clear();
                pintarLista();
                pintarCalendario();
            }
        };

        if (modo.esDeOtro()) {
            VimedRepo.listarCitasDe(modo.idUsuario, cb);
        } else {
            VimedRepo.listarCitas(this, cb);
        }
    }

    /** Citas cuya fecha es hoy o posterior, en orden. */
    private List<CitaMedica> proximas() {
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        List<CitaMedica> out = new ArrayList<>();
        for (CitaMedica c : citas) {
            if (c.fechaYMD().compareTo(hoy) >= 0) out.add(c);
        }
        return out;
    }

    private List<CitaMedica> delDia(String fechaYMD) {
        List<CitaMedica> out = new ArrayList<>();
        for (CitaMedica c : citas) {
            if (fechaYMD.equals(c.fechaYMD())) out.add(c);
        }
        return out;
    }

    // ═══════════════════════════════════════════════════════════
    //  Modo Lista / Calendario
    // ═══════════════════════════════════════════════════════════

    private void cambiarModo(boolean calendario) {
        modoCalendario = calendario;
        viewLista.setVisibility(calendario ? View.GONE : View.VISIBLE);
        viewCalendario.setVisibility(calendario ? View.VISIBLE : View.GONE);
        tvSubtitle.setText(calendario
            ? "Gestioná y organizá todas tus citas"
            : "Próximas citas");
        pintarToggle();
    }

    private void pintarToggle() {
        View tabLista = findViewById(R.id.tab_lista);
        View tabCal   = findViewById(R.id.tab_calendario);
        TextView tvL  = findViewById(R.id.tv_tab_lista);
        TextView tvC  = findViewById(R.id.tv_tab_cal);
        ImageView icL = findViewById(R.id.ic_tab_lista);
        ImageView icC = findViewById(R.id.ic_tab_cal);

        int activo   = ContextCompat.getColor(this, R.color.brand_600);
        int inactivo = ContextCompat.getColor(this, R.color.ink_3);

        tabLista.setBackgroundResource(modoCalendario ? 0 : R.drawable.shape_toggle_selected);
        tabCal.setBackgroundResource(modoCalendario ? R.drawable.shape_toggle_selected : 0);
        tvL.setTextColor(modoCalendario ? inactivo : activo);
        tvC.setTextColor(modoCalendario ? activo : inactivo);
        icL.setColorFilter(modoCalendario ? inactivo : activo);
        icC.setColorFilter(modoCalendario ? activo : inactivo);
    }

    // ═══════════════════════════════════════════════════════════
    //  Vista Lista
    // ═══════════════════════════════════════════════════════════

    private void pintarLista() {
        appointmentsContainer.removeAllViews();
        List<CitaMedica> prox = proximas();

        if (prox.isEmpty()) {
            emptyAppointments.setVisibility(View.VISIBLE);
            appointmentsContainer.setVisibility(View.GONE);
            return;
        }
        emptyAppointments.setVisibility(View.GONE);
        appointmentsContainer.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (CitaMedica cita : prox) {
            View item = inflater.inflate(R.layout.item_appointment_card,
                appointmentsContainer, false);
            bindCard(item, cita, true);
            appointmentsContainer.addView(item);
        }
    }

    private void bindCard(View item, CitaMedica cita, boolean mostrarFecha) {
        TextView tvEsp    = item.findViewById(R.id.tv_appt_especialidad);
        TextView tvDoctor = item.findViewById(R.id.tv_appt_doctor);
        TextView tvLugar  = item.findViewById(R.id.tv_appt_lugar);
        TextView tvTime   = item.findViewById(R.id.tv_appt_time);
        TextView tvEstado = item.findViewById(R.id.tv_appt_estado);
        TextView tvFecha  = item.findViewById(R.id.tv_appt_fecha);

        String esp = cita.getEspecialidad();
        tvEsp.setText(esp != null && !esp.isEmpty() ? esp : "Consulta médica");
        tvDoctor.setText(cita.getMedico() != null ? cita.getMedico() : "");
        tvLugar.setText(cita.getLugar() != null ? cita.getLugar() : "Sin lugar");
        tvTime.setText(cita.horaHM() + " hs");

        switch (cita.getEstado()) {
            case CitaMedica.ESTADO_CONFIRMADA:
                tvEstado.setText("Confirmada");
                tvEstado.setBackgroundResource(R.drawable.shape_chip_success);
                tvEstado.setTextColor(getColor(R.color.success));
                break;
            case CitaMedica.ESTADO_CANCELADA:
                tvEstado.setText("Cancelada");
                tvEstado.setBackgroundResource(R.drawable.shape_chip_danger);
                tvEstado.setTextColor(getColor(R.color.danger));
                break;
            default:
                tvEstado.setText("Pendiente");
                tvEstado.setBackgroundResource(R.drawable.shape_chip_pending);
                tvEstado.setTextColor(getColor(R.color.warn));
                break;
        }

        // En la lista mostramos la fecha; en el calendario ya se sabe el día.
        if (mostrarFecha) {
            tvFecha.setVisibility(View.VISIBLE);
            tvFecha.setText(fechaLegibleCorta(cita.fechaYMD()));
        } else {
            tvFecha.setVisibility(View.GONE);
        }

        item.setOnClickListener(v -> mostrarOpcionesCita(cita));
    }

    private void mostrarOpcionesCita(CitaMedica cita) {
        String[] opciones = {
            "Marcar como confirmada",
            "Marcar como pendiente",
            "Marcar como cancelada",
            "Eliminar cita"
        };
        String[] estados = {
            CitaMedica.ESTADO_CONFIRMADA,
            CitaMedica.ESTADO_PENDIENTE,
            CitaMedica.ESTADO_CANCELADA
        };

        new AlertDialog.Builder(this)
            .setTitle(cita.getEspecialidad() != null && !cita.getEspecialidad().isEmpty()
                ? cita.getEspecialidad() : "Cita médica")
            .setItems(opciones, (d, which) -> {
                if (which < estados.length) cambiarEstado(cita, estados[which]);
                else                        confirmarEliminar(cita);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void cambiarEstado(CitaMedica cita, String nuevo) {
        VimedRepo.actualizarEstadoCita(cita.getId(), nuevo, new VimedRepo.Cb<Void>() {
            @Override public void onOk(Void v) { cargarCitas(); }
            @Override public void onError(String msg) {
                Toast.makeText(AppointmentsActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmarEliminar(CitaMedica cita) {
        new AlertDialog.Builder(this)
            .setTitle("Eliminar cita")
            .setMessage("¿Eliminar esta cita médica?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (d, w) ->
                VimedRepo.eliminarCita(cita.getId(), new VimedRepo.Cb<Void>() {
                    @Override public void onOk(Void v) { cargarCitas(); }
                    @Override public void onError(String msg) {
                        Toast.makeText(AppointmentsActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                }))
            .show();
    }

    // ═══════════════════════════════════════════════════════════
    //  Vista Calendario
    // ═══════════════════════════════════════════════════════════

    private void moverMes(int delta) {
        mesVisible.add(Calendar.MONTH, delta);
        pintarCalendario();
    }

    private void moverDias(int delta) {
        diaElegido.add(Calendar.DAY_OF_MONTH, delta);
        mesVisible.setTime(diaElegido.getTime());
        pintarCalendario();
    }

    private void pintarCalendario() {
        tvMonthLabel.setText(MESES[mesVisible.get(Calendar.MONTH)]
            + " " + mesVisible.get(Calendar.YEAR));
        dibujarTiraSemanal();
        dibujarGrillaMes();
        pintarCitasDelDia();
    }

    /** Encabezado LUN..DOM del calendario mensual. */
    private void dibujarEncabezadoDias() {
        weekdayHeader.removeAllViews();
        for (String d : DIAS_CORTOS) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tv.setText(d);
            tv.setTextSize(12);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(ContextCompat.getColor(this, R.color.ink_4));
            tv.setGravity(Gravity.CENTER);
            weekdayHeader.addView(tv);
        }
    }

    /** Tira de 7 días alrededor del día elegido. */
    private void dibujarTiraSemanal() {
        weekStrip.removeAllViews();

        Calendar cur = (Calendar) diaElegido.clone();
        // Retroceder hasta el lunes de esa semana
        int dow = cur.get(Calendar.DAY_OF_WEEK);            // DOM=1 … SÁB=7
        int desdeLunes = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY;
        cur.add(Calendar.DAY_OF_MONTH, -desdeLunes);

        for (int i = 0; i < 7; i++) {
            final Calendar dia = (Calendar) cur.clone();
            String ymd = ymd(dia);
            boolean elegido = ymd.equals(ymd(diaElegido));
            boolean tieneCitas = !delDia(ymd).isEmpty();

            LinearLayout col = new LinearLayout(this);
            col.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setPadding(0, dp(8), 0, dp(8));
            if (elegido) col.setBackgroundResource(R.drawable.shape_toggle_selected);
            col.setOnClickListener(v -> {
                diaElegido.setTime(dia.getTime());
                mesVisible.setTime(dia.getTime());
                pintarCalendario();
            });

            TextView tvDow = new TextView(this);
            tvDow.setText(DIAS_CORTOS[i]);
            tvDow.setTextSize(11);
            tvDow.setTypeface(null, Typeface.BOLD);
            tvDow.setTextColor(ContextCompat.getColor(this,
                elegido ? R.color.brand_600 : R.color.ink_4));
            tvDow.setGravity(Gravity.CENTER);
            col.addView(tvDow);

            TextView tvNum = new TextView(this);
            tvNum.setText(String.valueOf(dia.get(Calendar.DAY_OF_MONTH)));
            tvNum.setTextSize(18);
            tvNum.setTypeface(null, Typeface.BOLD);
            tvNum.setTextColor(ContextCompat.getColor(this,
                elegido ? R.color.brand_600 : R.color.ink));
            tvNum.setGravity(Gravity.CENTER);
            col.addView(tvNum);

            col.addView(punto(tieneCitas));
            weekStrip.addView(col);

            cur.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    /** Grilla del mes completo, con punto en los días que tienen citas. */
    private void dibujarGrillaMes() {
        monthGrid.removeAllViews();

        Calendar primero = (Calendar) mesVisible.clone();
        primero.set(Calendar.DAY_OF_MONTH, 1);

        // Cuántos días del mes anterior hay que mostrar para empezar en lunes
        int dow = primero.get(Calendar.DAY_OF_WEEK);
        int offset = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY;

        Calendar cur = (Calendar) primero.clone();
        cur.add(Calendar.DAY_OF_MONTH, -offset);

        int mesActual = mesVisible.get(Calendar.MONTH);

        for (int fila = 0; fila < 6; fila++) {
            LinearLayout row = new LinearLayout(this);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int col = 0; col < 7; col++) {
                row.addView(celdaDia(cur, mesActual));
                cur.add(Calendar.DAY_OF_MONTH, 1);
            }
            monthGrid.addView(row);

            // Si ya pasamos el mes y completamos la semana, cortamos
            if (cur.get(Calendar.MONTH) != mesActual && fila >= 4) break;
        }
    }

    private View celdaDia(Calendar dia, int mesActual) {
        final Calendar fecha = (Calendar) dia.clone();
        String ymd = ymd(fecha);
        boolean esDeOtroMes = fecha.get(Calendar.MONTH) != mesActual;
        boolean elegido = ymd.equals(ymd(diaElegido));
        boolean tieneCitas = !delDia(ymd).isEmpty();

        LinearLayout celda = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        celda.setLayoutParams(lp);
        celda.setOrientation(LinearLayout.VERTICAL);
        celda.setGravity(Gravity.CENTER);
        if (elegido) celda.setBackgroundResource(R.drawable.shape_day_selected);

        TextView tv = new TextView(this);
        tv.setText(String.valueOf(fecha.get(Calendar.DAY_OF_MONTH)));
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        if (esDeOtroMes) {
            tv.setTextColor(ContextCompat.getColor(this, R.color.ink_6));
        } else if (elegido) {
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(ContextCompat.getColor(this, R.color.brand_600));
        } else {
            tv.setTextColor(ContextCompat.getColor(this, R.color.ink));
        }
        celda.addView(tv);
        celda.addView(punto(tieneCitas && !esDeOtroMes));

        if (!esDeOtroMes) {
            celda.setOnClickListener(v -> {
                diaElegido.setTime(fecha.getTime());
                pintarCalendario();
            });
        }
        return celda;
    }

    /** Puntito verde debajo del número. Invisible si el día no tiene citas. */
    private View punto(boolean visible) {
        View dot = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(5), dp(5));
        lp.topMargin = dp(3);
        dot.setLayoutParams(lp);
        dot.setBackgroundResource(R.drawable.shape_dot_brand);
        dot.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        return dot;
    }

    private void pintarCitasDelDia() {
        dayContainer.removeAllViews();

        int idxDia = (diaElegido.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            ? 6 : diaElegido.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
        tvSelectedDay.setText(DIAS_LARGOS[idxDia] + ", "
            + diaElegido.get(Calendar.DAY_OF_MONTH) + " de "
            + MESES[diaElegido.get(Calendar.MONTH)]);

        List<CitaMedica> delDia = delDia(ymd(diaElegido));
        tvDayCount.setText(delDia.size() == 1 ? "1 cita" : delDia.size() + " citas");

        if (delDia.isEmpty()) {
            tvDayEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvDayEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (CitaMedica cita : delDia) {
            View item = inflater.inflate(R.layout.item_appointment_card, dayContainer, false);
            bindCard(item, cita, false);
            dayContainer.addView(item);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private String ymd(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    private String fechaLegibleCorta(String ymd) {
        try {
            String[] p = ymd.split("-");
            return Integer.parseInt(p[2]) + " " + MESES[Integer.parseInt(p[1]) - 1].substring(0, 3);
        } catch (Exception e) {
            return "";
        }
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private void setupBottomNav() {
        NavInferior.configurar(this, modo, R.id.nav_appointments, true);
    }
}
