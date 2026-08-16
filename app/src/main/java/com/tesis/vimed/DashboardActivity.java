package com.tesis.vimed;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.tesis.vimed.adherencia.EstadisticasProgreso;
import com.tesis.vimed.adherencia.ResumenAdherencia;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.RegistroToma;
import com.tesis.vimed.utils.ModoPaciente;
import com.tesis.vimed.utils.NavInferior;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Progreso: cómo viene el tratamiento, para mirar de verdad.
 *
 * Antes era un anillo, siete puntitos y dos números, sin nada tocable y
 * con la ventana fija en la semana. Ahora se puede elegir el período,
 * ver día por día, qué medicamento se cumple menos y qué horarios se
 * pierden — que son las preguntas que alguien se hace cuando entra acá.
 *
 * La misma pantalla sirve a los dos roles: el cuidador llega con el id
 * del paciente y ve exactamente lo mismo, pero de su familiar
 * (ver {@link ModoPaciente}).
 */
public class DashboardActivity extends AppCompatActivity {

    /** Períodos del selector, en días. */
    private static final int[] PERIODOS = {7, 30, 90};

    private ModoPaciente modo = ModoPaciente.propio();
    private int diasElegidos = 7;

    /** Datos crudos: se piden una vez con la ventana más larga y se
     *  recalcula en memoria al cambiar de período. */
    private final List<RegistroToma> historial = new ArrayList<>();
    private final List<Medicamento> medicamentos = new ArrayList<>();
    private final List<Horario> horarios = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        modo = ModoPaciente.de(this);

        String mes = new SimpleDateFormat("MMMM yyyy", new Locale("es")).format(new Date());
        mes = mes.substring(0, 1).toUpperCase() + mes.substring(1);
        ((TextView) findViewById(R.id.tv_month)).setText(mes);

        mostrarCartelDePaciente();
        configurarSelector();
        cargarDatos();
        setupBottomNav();
    }

    private void mostrarCartelDePaciente() {
        TextView cartel = findViewById(R.id.tv_cartel_paciente);
        if (cartel == null) return;
        if (!modo.esDeOtro()) { cartel.setVisibility(View.GONE); return; }
        cartel.setText(modo.cartel("el progreso"));
        cartel.setVisibility(View.VISIBLE);
    }

    // ═══ Selector de período ═══════════════════════════════════

    private void configurarSelector() {
        int[] ids = {R.id.chip_7, R.id.chip_30, R.id.chip_90};
        for (int i = 0; i < ids.length; i++) {
            final int dias = PERIODOS[i];
            findViewById(ids[i]).setOnClickListener(v -> {
                if (diasElegidos == dias) return;
                diasElegidos = dias;
                marcarChipElegido();
                // Sin ir a la red: los datos ya están, solo cambia el corte.
                pintar();
            });
        }
        marcarChipElegido();
    }

    private void marcarChipElegido() {
        int[] ids = {R.id.chip_7, R.id.chip_30, R.id.chip_90};
        for (int i = 0; i < ids.length; i++) {
            findViewById(ids[i]).setSelected(PERIODOS[i] == diasElegidos);
        }
    }

    // ═══ Datos ═════════════════════════════════════════════════

    /**
     * Se pide SIEMPRE la ventana más larga (90 días) aunque se esté
     * mirando la semana: son tres consultas encadenadas y cambiar de
     * período no puede costar otra vuelta a la red cada vez.
     */
    private void cargarDatos() {
        VimedRepo.Cb<List<Medicamento>> cbMeds = new VimedRepo.Cb<List<Medicamento>>() {
            @Override
            public void onOk(List<Medicamento> meds) {
                medicamentos.clear();
                medicamentos.addAll(meds);
                cargarHorarios();
            }
            @Override
            public void onError(String msg) {
                Toast.makeText(DashboardActivity.this, msg, Toast.LENGTH_LONG).show();
                pintar();
            }
        };

        if (modo.esDeOtro()) VimedRepo.listarMedicamentosDe(modo.idUsuario, cbMeds);
        else                 VimedRepo.listarMedicamentos(this, cbMeds);
    }

    private void cargarHorarios() {
        List<Integer> ids = new ArrayList<>();
        for (Medicamento m : medicamentos) ids.add(m.getId());

        VimedRepo.listarHorariosDe(ids, new VimedRepo.Cb<List<Horario>>() {
            @Override
            public void onOk(List<Horario> hs) {
                horarios.clear();
                horarios.addAll(hs);
                cargarHistorial();
            }
            @Override
            public void onError(String msg) { cargarHistorial(); }
        });
    }

    private void cargarHistorial() {
        String desde = ResumenAdherencia.restarDias(hoy(), PERIODOS[PERIODOS.length - 1]);

        VimedRepo.Cb<List<RegistroToma>> cb = new VimedRepo.Cb<List<RegistroToma>>() {
            @Override
            public void onOk(List<RegistroToma> tomas) {
                historial.clear();
                historial.addAll(tomas);
                pintar();
            }
            @Override
            public void onError(String msg) { pintar(); }
        };

        if (modo.esDeOtro()) VimedRepo.listarTomasDelDiaDe(modo.idUsuario, desde, cb);
        else                 VimedRepo.listarTomasDesde(this, desde, cb);
    }

    // ═══ Pintado ═══════════════════════════════════════════════

    private void pintar() {
        EstadisticasProgreso.Progreso p = EstadisticasProgreso.calcular(
            medicamentos, horarios, historial, diasElegidos, hoy());

        findViewById(R.id.tv_sin_datos).setVisibility(p.hayDatos() ? View.GONE : View.VISIBLE);

        pintarResumen(p);
        pintarGrafico(p);
        pintarPorMedicamento(p);
        pintarPuntosFlojos(p);
    }

    private void pintarResumen(EstadisticasProgreso.Progreso p) {
        LinearProgressIndicator barra = findViewById(R.id.progress_semana);
        TextView tvPct = findViewById(R.id.tv_adherence_pct);
        TextView tvAnimo = findViewById(R.id.tv_animo);
        TextView tvResumen = findViewById(R.id.tv_resumen_semana);

        int pct = p.porcentaje();
        barra.setProgress(Math.max(pct, 0), true);
        barra.setIndicatorColor(ContextCompat.getColor(this, colorDeNivel(pct)));
        tvPct.setText(pct < 0 ? "—" : pct + "%");

        if (!p.hayDatos()) {
            tvAnimo.setText("Sin datos todavía");
            tvResumen.setText("Cuando confirmes tus tomas vas a ver tu avance acá.");
            findViewById(R.id.card_racha).setVisibility(View.GONE);
            return;
        }

        tvAnimo.setText(animo(pct));
        tvResumen.setText(p.confirmadas + " de " + p.total
            + (p.total == 1 ? " toma confirmada" : " tomas confirmadas")
            + " en " + diasElegidos + " días.");

        // Racha: solo si hay algo para celebrar. Un "0 días seguidos" no le
        // sirve a nadie y encima desanima.
        View card = findViewById(R.id.card_racha);
        TextView tvRacha = findViewById(R.id.tv_racha);
        if (p.racha >= 2) {
            tvRacha.setText("¡" + p.racha + " días seguidos cumpliendo!"
                + (p.mejorRacha > p.racha ? " Tu mejor racha fue de " + p.mejorRacha + "." : ""));
            card.setVisibility(View.VISIBLE);
        } else if (p.mejorRacha >= 3) {
            tvRacha.setText("Tu mejor racha en este período fue de "
                + p.mejorRacha + " días seguidos.");
            card.setVisibility(View.VISIBLE);
        } else {
            card.setVisibility(View.GONE);
        }
    }

    /**
     * Gráfico de barras a mano: una barra por día, con su riel de fondo
     * para que se vea la altura total aunque se haya cumplido poco.
     * No se usa librería de gráficos por una barra por día.
     */
    private void pintarGrafico(EstadisticasProgreso.Progreso p) {
        LinearLayout cont = findViewById(R.id.grafico_dias);
        cont.removeAllViews();

        int anchoBarra = dp(diasElegidos <= 7 ? 30 : diasElegidos <= 30 ? 16 : 9);
        int separacion = dp(diasElegidos <= 30 ? 6 : 3);
        int altoMax = dp(120);

        for (EstadisticasProgreso.Dia d : p.serie) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams lpCol = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpCol.setMarginEnd(separacion);
            col.setLayoutParams(lpCol);

            FrameLayout riel = new FrameLayout(this);
            riel.setLayoutParams(new LinearLayout.LayoutParams(anchoBarra, altoMax));
            riel.setBackgroundResource(R.drawable.shape_pista_barra);

            int pct = d.porcentaje();
            int alto = pct <= 0 ? dp(4) : Math.max(dp(6), Math.round(altoMax * pct / 100f));

            View barra = new View(this);
            FrameLayout.LayoutParams lpBarra =
                new FrameLayout.LayoutParams(anchoBarra, alto, android.view.Gravity.BOTTOM);
            barra.setLayoutParams(lpBarra);
            barra.setBackgroundResource(R.drawable.shape_barra_dia);
            barra.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, d.vacio() ? R.color.ink_7 : colorDeNivel(pct))));
            riel.addView(barra);
            col.addView(riel);

            // Etiqueta del día: con 90 barras no entra ninguna, así que
            // solo se rotula cada cinco.
            TextView lbl = new TextView(this);
            boolean rotular = diasElegidos <= 30 || p.serie.indexOf(d) % 5 == 0;
            lbl.setText(rotular ? d.diaDelMes() : "");
            lbl.setTextSize(11);
            lbl.setTextColor(ContextCompat.getColor(this, R.color.ink_5));
            lbl.setPadding(0, dp(6), 0, 0);
            col.addView(lbl);

            col.setOnClickListener(v -> Toast.makeText(this,
                textoDeDia(d), Toast.LENGTH_SHORT).show());

            cont.addView(col);
        }
    }

    private String textoDeDia(EstadisticasProgreso.Dia d) {
        String fecha = d.fecha.length() >= 10
            ? d.fecha.substring(8, 10) + "/" + d.fecha.substring(5, 7) : d.fecha;
        if (d.vacio()) return fecha + ": sin tomas agendadas";
        return fecha + ": " + d.confirmadas + " de " + d.total
            + (d.total == 1 ? " toma" : " tomas") + " · " + d.porcentaje() + "%";
    }

    private void pintarPorMedicamento(EstadisticasProgreso.Progreso p) {
        LinearLayout cont = findViewById(R.id.meds_container);
        View titulo = findViewById(R.id.tv_titulo_meds);
        cont.removeAllViews();

        if (p.porMedicamento.isEmpty()) {
            titulo.setVisibility(View.GONE);
            return;
        }
        titulo.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (EstadisticasProgreso.PorMedicamento m : p.porMedicamento) {
            View item = inflater.inflate(R.layout.item_med_progreso, cont, false);

            TextView inicial = item.findViewById(R.id.tv_inicial);
            inicial.setText(m.nombre.isEmpty()
                ? "M" : m.nombre.substring(0, 1).toUpperCase(Locale.getDefault()));
            GradientDrawable circulo = new GradientDrawable();
            circulo.setShape(GradientDrawable.OVAL);
            circulo.setColor(colorDeMedicamento(m.colorIcono));
            inicial.setBackground(circulo);

            ((TextView) item.findViewById(R.id.tv_nombre)).setText(m.nombre);
            ((TextView) item.findViewById(R.id.tv_detalle)).setText(
                m.confirmadas + " de " + m.total + (m.total == 1 ? " toma" : " tomas"));

            int pct = m.porcentaje();
            TextView tvPct = item.findViewById(R.id.tv_pct);
            tvPct.setText(pct + "%");
            tvPct.setTextColor(ContextCompat.getColor(this, colorDeNivelTexto(pct)));

            View barra = item.findViewById(R.id.barra);
            barra.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, colorDeNivel(pct))));
            // El ancho se fija cuando el riel ya midió: antes de eso no
            // sabemos contra qué calcular el porcentaje.
            View riel = (View) barra.getParent();
            riel.post(() -> {
                android.view.ViewGroup.LayoutParams lp = barra.getLayoutParams();
                lp.width = Math.round(riel.getWidth() * Math.max(pct, 0) / 100f);
                barra.setLayoutParams(lp);
            });

            cont.addView(item);
        }
    }

    private void pintarPuntosFlojos(EstadisticasProgreso.Progreso p) {
        LinearLayout cont = findViewById(R.id.flojos_container);
        View titulo = findViewById(R.id.tv_titulo_flojos);
        cont.removeAllViews();

        if (p.puntosFlojos.isEmpty()) {
            titulo.setVisibility(View.GONE);
            return;
        }
        titulo.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ResumenAdherencia.PuntoFlojo f : p.puntosFlojos) {
            View item = inflater.inflate(R.layout.item_actividad, cont, false);
            android.widget.ImageView icono = item.findViewById(R.id.act_icon);
            icono.setImageResource(R.drawable.ic_alarma_reloj);
            icono.setColorFilter(ContextCompat.getColor(this, R.color.naranja_500));
            ((TextView) item.findViewById(R.id.act_mensaje)).setText(
                f.nombreMedicamento + (f.hora.isEmpty() ? "" : " · " + f.hora));
            ((TextView) item.findViewById(R.id.act_fecha)).setText(f.detalle());
            cont.addView(item);
        }
    }

    // ═══ Color y texto según el nivel ══════════════════════════

    /**
     * Color de RELLENO según el nivel: verde si viene bien, amarillo si
     * flojea, rojo si se está perdiendo. Son los tres colores vivos de la
     * paleta, sin apagar.
     */
    private int colorDeNivel(int pct) {
        if (pct < 0)  return R.color.ink_6;
        if (pct >= 80) return R.color.verde_500;
        if (pct >= 50) return R.color.amarillo_500;
        return R.color.danger;
    }

    /**
     * Color de TEXTO para el mismo nivel. Casi siempre es el mismo, pero el
     * amarillo vivo sobre fondo claro da 1.97 de contraste: como número
     * sería ilegible, así que ahí —y solo ahí— se usa el tono oscuro de la
     * familia. El relleno de la barra sigue siendo el amarillo vivo.
     */
    private int colorDeNivelTexto(int pct) {
        if (pct >= 50 && pct < 80) return R.color.amarillo_700;
        return colorDeNivel(pct);
    }

    private String animo(int pct) {
        if (pct >= 90) return "¡Excelente!";
        if (pct >= 80) return "Vas muy bien";
        if (pct >= 50) return "Buen ritmo, se puede mejorar";
        return "Se están perdiendo varias tomas";
    }

    private int colorDeMedicamento(String colorKey) {
        if (colorKey == null) return ContextCompat.getColor(this, R.color.pill_morado);
        switch (colorKey.toLowerCase(Locale.ROOT)) {
            case "azul":     return ContextCompat.getColor(this, R.color.pill_azul);
            case "verde":    return ContextCompat.getColor(this, R.color.pill_verde);
            case "rojo":     return ContextCompat.getColor(this, R.color.pill_rojo);
            case "amarillo": return ContextCompat.getColor(this, R.color.pill_amarillo);
            case "morado":   return ContextCompat.getColor(this, R.color.pill_morado);
            case "gris":     return ContextCompat.getColor(this, R.color.pill_gris);
            default:         return ContextCompat.getColor(this, R.color.pill_morado);
        }
    }

    // ═══ Helpers ═══════════════════════════════════════════════

    private String hoy() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void setupBottomNav() {
        NavInferior.configurar(this, modo, R.id.nav_stats, true);
    }
}
