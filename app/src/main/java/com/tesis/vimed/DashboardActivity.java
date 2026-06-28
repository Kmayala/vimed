package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.MedicamentoDAO;
import com.tesis.vimed.database.RegistroTomaDAO;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        sessionManager = new SessionManager(this);

        // Mes actual en la cabecera
        String mes = new SimpleDateFormat("MMMM yyyy", new Locale("es")).format(new Date());
        mes = mes.substring(0, 1).toUpperCase() + mes.substring(1);
        ((TextView) findViewById(R.id.tv_month)).setText(mes);

        cargarAdherencia();
        setupBottomNav();
    }

    private void cargarAdherencia() {
        int idUsuario = sessionManager.getIdUsuario();
        TextView tvPct = findViewById(R.id.tv_adherence_pct);

        if (idUsuario == -1) {
            tvPct.setText("–%");
            return;
        }

        // Verificar si tiene medicamentos
        MedicamentoDAO medDAO = new MedicamentoDAO(DatabaseHelper.getInstance(this));
        if (medDAO.listarPorUsuario(idUsuario).isEmpty()) {
            tvPct.setText("–%");
            return;
        }

        // Calcular adherencia del mes actual (formato "yyyy-MM")
        String mesAnio = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        RegistroTomaDAO regDAO = new RegistroTomaDAO(DatabaseHelper.getInstance(this));
        float pct = regDAO.calcularAdherenciaMes(idUsuario, mesAnio);

        if (pct == 0) {
            tvPct.setText("0%");
        } else {
            tvPct.setText(String.format(Locale.getDefault(), "%.0f%%", pct));
        }
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_stats);
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
                startActivity(new Intent(this, AppointmentsActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_stats) {
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
