package com.tesis.vimed;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.HorarioDAO;
import com.tesis.vimed.database.MedicamentoDAO;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.utils.NotificationHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

        setupGreeting();
        setupBottomNav();

        dosesContainer = findViewById(R.id.doses_container);
        emptyDoses = findViewById(R.id.empty_doses);

        loadTodayDoses();
        setupAppointmentPlaceholder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        dosesContainer.removeAllViews();
        loadTodayDoses();
        setupAppointmentPlaceholder();
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
            String[] opciones = {"Vincular familiar", "Cerrar sesión"};
            new AlertDialog.Builder(this)
                .setTitle(nombre != null ? nombre : "Perfil")
                .setItems(opciones, (d, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, VincularFamiliarActivity.class));
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

    private void loadTodayDoses() {
        int idUsuario = sessionManager.getIdUsuario();
        if (idUsuario == -1) return;

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
        MedicamentoDAO medDAO = new MedicamentoDAO(dbHelper);
        HorarioDAO horDAO = new HorarioDAO(dbHelper);

        List<Medicamento> meds = medDAO.listarPorUsuario(idUsuario);

        // Generar lista de tomas del día
        List<DoseItem> doses = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        int nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        for (Medicamento med : meds) {
            List<Horario> horarios = horDAO.listarPorMedicamento(med.getId());
            for (Horario hor : horarios) {
                // Parsear hora inicio "HH:MM"
                String[] parts = hor.getHoraInicio().split(":");
                if (parts.length < 2) continue;
                int startMins = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                int interval = hor.getIntervaloHoras() * 60;
                if (interval <= 0) interval = 24 * 60;

                // Generar tomas del día
                int t = startMins;
                while (t < 24 * 60) {
                    int hh = t / 60;
                    int mm = t % 60;
                    String hora = String.format(Locale.getDefault(), "%02d:%02d", hh, mm);
                    boolean passed = t < nowMins;
                    doses.add(new DoseItem(med, hora, passed ? "tomado" : "pendiente", t));
                    t += interval;
                }
            }
        }

        // Ordenar por hora
        Collections.sort(doses, (a, b) -> a.minutos - b.minutos);

        int total = doses.size();
        int taken = 0;
        for (DoseItem d : doses) { if ("tomado".equals(d.estado)) taken++; }

        // Actualizar progreso
        updateProgress(taken, total);

        // Mostrar tomas
        if (doses.isEmpty()) {
            emptyDoses.setVisibility(View.VISIBLE);
            dosesContainer.setVisibility(View.GONE);
        } else {
            emptyDoses.setVisibility(View.GONE);
            dosesContainer.setVisibility(View.VISIBLE);
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

        // Próxima toma pendiente
        TextView tvNext = findViewById(R.id.tv_next_dose);
        DoseItem proxima = null;
        for (DoseItem d : doses) {
            if ("pendiente".equals(d.estado)) { proxima = d; break; }
        }
        tvNext.setText(proxima != null ? "Siguiente a las " + proxima.hora : "Todo al día ✓");
    }

    private void bindDoseItem(View item, DoseItem dose) {
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

        // Estado
        if ("tomado".equals(dose.estado)) {
            tvTime.setTextColor(getColor(R.color.ink_4));
            tvStatus.setText("Tomado");
            tvStatus.setBackgroundResource(R.drawable.shape_chip_success);
            tvStatus.setTextColor(getColor(R.color.success));
        } else {
            tvTime.setTextColor(getColor(R.color.ink));
            tvStatus.setText("Pendiente");
            tvStatus.setBackgroundResource(R.drawable.shape_chip_ink);
            tvStatus.setTextColor(getColor(R.color.ink_3));
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
        int idUsuario = sessionManager.getIdUsuario();
        if (idUsuario == -1) {
            findViewById(R.id.appointment_card).setVisibility(View.GONE);
            findViewById(R.id.empty_appointments).setVisibility(View.VISIBLE);
            return;
        }
        com.tesis.vimed.database.CitaMedicaDAO citaDAO =
            new com.tesis.vimed.database.CitaMedicaDAO(DatabaseHelper.getInstance(this));
        java.util.List<com.tesis.vimed.models.CitaMedica> citas = citaDAO.listarProximas(idUsuario);
        if (citas.isEmpty()) {
            findViewById(R.id.appointment_card).setVisibility(View.GONE);
            findViewById(R.id.empty_appointments).setVisibility(View.VISIBLE);
        } else {
            com.tesis.vimed.models.CitaMedica proxima = citas.get(0);
            findViewById(R.id.appointment_card).setVisibility(View.VISIBLE);
            findViewById(R.id.empty_appointments).setVisibility(View.GONE);
            try {
                String[] partes = proxima.getFechaHora().split(" ");
                String[] fecha = partes[0].split("-");
                String[] meses = {"ENE","FEB","MAR","ABR","MAY","JUN","JUL","AGO","SEP","OCT","NOV","DIC"};
                ((TextView) findViewById(R.id.tv_appt_day)).setText(String.valueOf(Integer.parseInt(fecha[2])));
                ((TextView) findViewById(R.id.tv_appt_month)).setText(meses[Integer.parseInt(fecha[1]) - 1]);
            } catch (Exception ignored) {}
            ((TextView) findViewById(R.id.tv_appt_doctor)).setText(proxima.getMedico() != null ? proxima.getMedico() : "");
            ((TextView) findViewById(R.id.tv_appt_specialty)).setText(proxima.getEspecialidad() != null ? proxima.getEspecialidad() : "");
            ((TextView) findViewById(R.id.tv_appt_place)).setText(proxima.getLugar() != null ? proxima.getLugar() : "");
        }
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_home);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_meds) {
                startActivity(new Intent(this, MedsListActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_appointments) {
                startActivity(new Intent(this, AppointmentsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_stats) {
                startActivity(new Intent(this, DashboardActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_vita) {
                startActivity(new Intent(this, ChatbotActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
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

        DoseItem(Medicamento med, String hora, String estado, int minutos) {
            this.med = med;
            this.hora = hora;
            this.estado = estado;
            this.minutos = minutos;
        }
    }
}
