package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.RegistroToma;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pantalla de Progreso: objetivo semanal (anillo + 7 dots, uno por día)
 * y estadísticas del mes. Datos desde Supabase (registro_tomas).
 */
public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        String mes = new SimpleDateFormat("MMMM yyyy", new Locale("es")).format(new Date());
        mes = mes.substring(0, 1).toUpperCase() + mes.substring(1);
        ((TextView) findViewById(R.id.tv_month)).setText(mes);

        cargarProgreso();
        setupBottomNav();
    }

    private void cargarProgreso() {
        // Traemos los registros desde el lunes de esta semana; el mismo
        // fetch (desde inicio de mes) alimenta las dos secciones.
        Calendar inicioMes = Calendar.getInstance();
        inicioMes.set(Calendar.DAY_OF_MONTH, 1);
        String desde = ymd(inicioMes);

        // Si el lunes de esta semana cae en el mes anterior, ampliar el rango
        Calendar lunes = lunesDeEstaSemana();
        if (ymd(lunes).compareTo(desde) < 0) desde = ymd(lunes);

        VimedRepo.listarTomasDelDia(this, desde, new VimedRepo.Cb<List<RegistroToma>>() {
            @Override
            public void onOk(List<RegistroToma> registros) {
                pintarSemana(registros);
                pintarMes(registros);
            }

            @Override
            public void onError(String msg) {
                pintarSemana(java.util.Collections.emptyList());
                pintarMes(java.util.Collections.emptyList());
            }
        });
    }

    /** Anillo + mensaje + 7 dots. Un día cuenta como "cumplido" si tuvo
     *  al menos una toma y ninguna quedó omitida. */
    private void pintarSemana(List<RegistroToma> registros) {
        Calendar lunes = lunesDeEstaSemana();
        String hoyYmd = ymd(Calendar.getInstance());

        // Agrupar registros por día
        Map<String, int[]> porDia = new HashMap<>();   // ymd → [confirmadas, total]
        for (RegistroToma r : registros) {
            String prog = r.getFechaHoraProgramada();
            if (prog == null || prog.length() < 10) continue;
            String dia = prog.substring(0, 10);
            int[] acc = porDia.get(dia);
            if (acc == null) { acc = new int[2]; porDia.put(dia, acc); }
            if ("confirmada".equals(r.getEstado())) acc[0]++;
            acc[1]++;
        }

        Set<String> diasCumplidos = new HashSet<>();
        int diasTranscurridos = 0;

        LinearLayout dots = findViewById(R.id.dots_dias);
        dots.removeAllViews();

        Calendar cursor = (Calendar) lunes.clone();
        for (int i = 0; i < 7; i++) {
            String dia = ymd(cursor);
            boolean pasado = dia.compareTo(hoyYmd) <= 0;
            int[] acc = porDia.get(dia);
            boolean cumplido = pasado && acc != null && acc[1] > 0 && acc[0] == acc[1];

            if (pasado) diasTranscurridos++;
            if (cumplido) diasCumplidos.add(dia);

            dots.addView(dot(cumplido));
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        int cumplidos = diasCumplidos.size();
        int pct = diasTranscurridos > 0 ? (cumplidos * 100 / diasTranscurridos) : 0;

        CircularProgressIndicator ring = findViewById(R.id.progress_semana);
        TextView tvPct = findViewById(R.id.tv_adherence_pct);
        TextView tvAnimo = findViewById(R.id.tv_animo);
        TextView tvResumen = findViewById(R.id.tv_resumen_semana);

        ring.setProgress(pct, true);
        tvPct.setText(pct + "%");

        if (diasTranscurridos == 0 || porDia.isEmpty()) {
            tvAnimo.setText("Sin datos todavía");
            tvResumen.setText("Cuando confirmes tus tomas vas a ver tu avance acá.");
        } else if (pct >= 80) {
            tvAnimo.setText("¡Vas muy bien!");
            tvResumen.setText("Completaste " + cumplidos + " de " + diasTranscurridos
                + (diasTranscurridos == 1 ? " día" : " días") + " esta semana.");
        } else if (pct >= 50) {
            tvAnimo.setText("¡Buen ritmo, seguí así!");
            tvResumen.setText("Completaste " + cumplidos + " de " + diasTranscurridos
                + (diasTranscurridos == 1 ? " día" : " días") + " esta semana.");
        } else {
            tvAnimo.setText("Podés mejorar");
            tvResumen.setText("Completaste " + cumplidos + " de " + diasTranscurridos
                + (diasTranscurridos == 1 ? " día" : " días")
                + ". Confirmá cada toma para llevar el registro.");
        }
    }

    private void pintarMes(List<RegistroToma> registros) {
        String mesPrefijo = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
            .format(new Date());

        int confirmadas = 0, omitidas = 0;
        for (RegistroToma r : registros) {
            String prog = r.getFechaHoraProgramada();
            if (prog == null || !prog.startsWith(mesPrefijo)) continue;
            if ("confirmada".equals(r.getEstado())) confirmadas++;
            else if ("omitida".equals(r.getEstado())) omitidas++;
        }

        ((TextView) findViewById(R.id.tv_stat_confirmadas)).setText(String.valueOf(confirmadas));
        ((TextView) findViewById(R.id.tv_stat_omitidas)).setText(String.valueOf(omitidas));
    }

    /** Dot circular: violeta con tilde si el día se cumplió, gris si no. */
    private View dot(boolean cumplido) {
        android.widget.ImageView iv = new android.widget.ImageView(this);
        int size = dp(26);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.rightMargin = dp(7);
        iv.setLayoutParams(lp);
        iv.setBackgroundResource(cumplido
            ? R.drawable.shape_dot_done : R.drawable.shape_dot_todo);
        if (cumplido) {
            iv.setImageResource(R.drawable.ic_check);
            iv.setColorFilter(ContextCompat.getColor(this, R.color.white));
            int pad = dp(5);
            iv.setPadding(pad, pad, pad, pad);
        }
        return iv;
    }

    private Calendar lunesDeEstaSemana() {
        Calendar c = Calendar.getInstance();
        int dow = c.get(Calendar.DAY_OF_WEEK);
        int desdeLunes = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY;
        c.add(Calendar.DAY_OF_MONTH, -desdeLunes);
        return c;
    }

    private String ymd(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
