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

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.HorarioDAO;
import com.tesis.vimed.database.MedicamentoDAO;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;

import java.util.List;

public class MedsListActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private LinearLayout medsContainer;
    private View emptyMeds;
    private TextView tvSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meds_list);

        sessionManager = new SessionManager(this);

        medsContainer = findViewById(R.id.meds_container);
        emptyMeds = findViewById(R.id.empty_meds);
        tvSubtitle = findViewById(R.id.tv_meds_subtitle);

        findViewById(R.id.btn_add_med).setOnClickListener(v ->
            startActivity(new Intent(this, AgregarMedicamentoActivity.class)));

        setupBottomNav();
        loadMeds();
    }

    @Override
    protected void onResume() {
        super.onResume();
        medsContainer.removeAllViews();
        loadMeds();
    }

    private void loadMeds() {
        int idUsuario = sessionManager.getIdUsuario();
        if (idUsuario == -1) return;

        MedicamentoDAO medDAO = new MedicamentoDAO(DatabaseHelper.getInstance(this));
        HorarioDAO horDAO = new HorarioDAO(DatabaseHelper.getInstance(this));
        List<Medicamento> meds = medDAO.listarPorUsuario(idUsuario);

        if (meds.isEmpty()) {
            emptyMeds.setVisibility(View.VISIBLE);
            medsContainer.setVisibility(View.GONE);
            tvSubtitle.setText("0 activos");
        } else {
            emptyMeds.setVisibility(View.GONE);
            medsContainer.setVisibility(View.VISIBLE);
            tvSubtitle.setText(meds.size() + (meds.size() == 1 ? " activo" : " activos"));

            LayoutInflater inflater = LayoutInflater.from(this);
            for (Medicamento med : meds) {
                View item = inflater.inflate(R.layout.item_med_card, medsContainer, false);
                bindMedCard(item, med, horDAO);
                medsContainer.addView(item);
            }
        }
    }

    private void bindMedCard(View item, Medicamento med, HorarioDAO horDAO) {
        TextView tvName = item.findViewById(R.id.tv_med_name);
        TextView tvDosis = item.findViewById(R.id.tv_med_dosis);
        TextView tvInst = item.findViewById(R.id.tv_med_inst);
        TextView tvHorario = item.findViewById(R.id.tv_med_horario);
        TextView tvInitial = item.findViewById(R.id.tv_med_initial);
        FrameLayout iconContainer = item.findViewById(R.id.med_icon_container);

        tvName.setText(med.getNombre());

        String dosisText = med.getDosis() > 0
            ? (int) med.getDosis() + " " + (med.getUnidad() != null ? med.getUnidad() : "")
            : "";
        tvDosis.setText(dosisText);
        tvInst.setText(instruccionLegible(med.getInstrucciones()));

        List<Horario> horarios = horDAO.listarPorMedicamento(med.getId());
        if (!horarios.isEmpty()) {
            Horario h = horarios.get(0);
            String intervalo = h.getIntervaloHoras() == 24 ? "Una vez al día" : "Cada " + h.getIntervaloHoras() + "h";
            tvHorario.setText(h.getHoraInicio() + " · " + intervalo);
        } else {
            tvHorario.setText("Sin horario configurado");
        }

        String nombre = med.getNombre();
        tvInitial.setText(nombre.length() > 0 ? String.valueOf(nombre.charAt(0)).toUpperCase() : "M");

        int bgColor = colorForMed(med.getColorIcono());
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(bgColor);
        iconContainer.setBackground(circle);
    }

    private int colorForMed(String colorKey) {
        if (colorKey == null) return Color.parseColor("#0d8b7d");
        switch (colorKey.toLowerCase()) {
            case "azul":     return Color.parseColor("#1e5ca8");
            case "verde":    return Color.parseColor("#2e7d58");
            case "rojo":     return Color.parseColor("#b3261e");
            case "amarillo": return Color.parseColor("#b86a00");
            case "morado":   return Color.parseColor("#6750A4");
            case "gris":     return Color.parseColor("#9aa39f");
            default:         return Color.parseColor("#0d8b7d");
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

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_meds);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_meds) {
                return true;
            } else if (id == R.id.nav_appointments) {
                startActivity(new Intent(this, AppointmentsActivity.class));
                overridePendingTransition(0, 0);
                finish();
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
