package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tesis.vimed.database.CitaMedicaDAO;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.models.CitaMedica;

import java.util.List;

public class AppointmentsActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private LinearLayout appointmentsContainer;
    private View emptyAppointments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointments);

        sessionManager = new SessionManager(this);
        appointmentsContainer = findViewById(R.id.appointments_container);
        emptyAppointments = findViewById(R.id.empty_appointments);

        findViewById(R.id.btn_add_appt).setOnClickListener(v ->
            startActivity(new Intent(this, AgregarCitaActivity.class)));

        setupBottomNav();
        loadAppointments();
    }

    @Override
    protected void onResume() {
        super.onResume();
        appointmentsContainer.removeAllViews();
        loadAppointments();
    }

    private void loadAppointments() {
        int idUsuario = sessionManager.getIdUsuario();
        if (idUsuario == -1) return;

        CitaMedicaDAO dao = new CitaMedicaDAO(DatabaseHelper.getInstance(this));
        List<CitaMedica> citas = dao.listarProximas(idUsuario);

        if (citas.isEmpty()) {
            emptyAppointments.setVisibility(View.VISIBLE);
            appointmentsContainer.setVisibility(View.GONE);
        } else {
            emptyAppointments.setVisibility(View.GONE);
            appointmentsContainer.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(this);
            for (CitaMedica cita : citas) {
                View item = inflater.inflate(R.layout.item_appointment_card, appointmentsContainer, false);
                bindCard(item, cita, dao);
                appointmentsContainer.addView(item);
            }
        }
    }

    private void bindCard(View item, CitaMedica cita, CitaMedicaDAO dao) {
        TextView tvDay = item.findViewById(R.id.tv_appt_day);
        TextView tvMonth = item.findViewById(R.id.tv_appt_month);
        TextView tvDoctor = item.findViewById(R.id.tv_appt_doctor);
        TextView tvEsp = item.findViewById(R.id.tv_appt_especialidad);
        TextView tvTime = item.findViewById(R.id.tv_appt_time);
        TextView tvLugar = item.findViewById(R.id.tv_appt_lugar);

        // fechaHora stored as "yyyy-MM-dd HH:mm"
        try {
            String[] partes = cita.getFechaHora().split(" ");
            String[] fecha = partes[0].split("-");
            String[] meses = {"ENE","FEB","MAR","ABR","MAY","JUN","JUL","AGO","SEP","OCT","NOV","DIC"};
            tvDay.setText(String.valueOf(Integer.parseInt(fecha[2])));
            tvMonth.setText(meses[Integer.parseInt(fecha[1]) - 1]);
            tvTime.setText(partes.length > 1 ? partes[1] : "");
        } catch (Exception e) {
            tvDay.setText("-");
            tvMonth.setText("");
            tvTime.setText("");
        }

        tvDoctor.setText(cita.getMedico() != null ? cita.getMedico() : "");
        tvEsp.setText(cita.getEspecialidad() != null ? cita.getEspecialidad() : "");
        tvLugar.setText(cita.getLugar() != null ? cita.getLugar() : "");

        item.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Eliminar cita")
                .setMessage("¿Eliminar esta cita médica?")
                .setPositiveButton("Eliminar", (d, w) -> {
                    dao.eliminar(cita.getId());
                    appointmentsContainer.removeView(item);
                    if (appointmentsContainer.getChildCount() == 0) {
                        emptyAppointments.setVisibility(View.VISIBLE);
                        appointmentsContainer.setVisibility(View.GONE);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
            return true;
        });
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_appointments);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_meds) {
                startActivity(new Intent(this, MedsListActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_appointments) {
                return true;
            } else if (id == R.id.nav_stats) {
                startActivity(new Intent(this, DashboardActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_vita) {
                startActivity(new Intent(this, ChatbotActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }
}
