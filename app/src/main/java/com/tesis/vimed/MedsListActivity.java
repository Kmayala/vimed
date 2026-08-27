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

import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.utils.MedCache;
import com.tesis.vimed.utils.MedicamentoUI;
import com.tesis.vimed.utils.ModoPaciente;
import com.tesis.vimed.utils.NavInferior;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MedsListActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private LinearLayout medsContainer;
    private View emptyMeds;
    private TextView tvSubtitle;

    /** De quién son los medicamentos que se están mostrando. */
    private ModoPaciente modo = ModoPaciente.propio();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meds_list);

        sessionManager = new SessionManager(this);
        modo = ModoPaciente.de(this);

        medsContainer = findViewById(R.id.meds_container);
        emptyMeds = findViewById(R.id.empty_meds);
        tvSubtitle = findViewById(R.id.tv_meds_subtitle);

        mostrarCartelDePaciente();

        // El intent arrastra el paciente: si no, el cuidador se cargaría el
        // medicamento a sí mismo sin darse cuenta.
        View.OnClickListener agregar = v ->
            startActivity(modo.intent(this, AgregarMedicamentoActivity.class));

        findViewById(R.id.btn_add_med).setOnClickListener(agregar);

        View btnAddFirst = findViewById(R.id.btn_add_first);
        if (btnAddFirst != null) btnAddFirst.setOnClickListener(agregar);

        setupBottomNav();
        loadMeds();
    }

    /**
     * Los textos del XML tutean al adulto mayor. Cuando la pantalla la abre
     * el cuidador hablan de él, que no toma nada: "Aún no tenés
     * medicamentos" leído por la hija dice que los medicamentos faltantes
     * son suyos.
     */
    private void adaptarTextosAlPaciente() {
        if (!modo.esDeOtro()) return;

        TextView vacio = findViewById(R.id.tv_meds_vacio);
        if (vacio != null) {
            vacio.setText(modo.frase("todavía no tiene medicamentos"));
        }
    }

    private void mostrarCartelDePaciente() {
        adaptarTextosAlPaciente();

        TextView cartel = findViewById(R.id.tv_cartel_paciente);
        if (cartel == null) return;
        if (!modo.esDeOtro()) { cartel.setVisibility(View.GONE); return; }
        cartel.setText(modo.cartel("los medicamentos"));
        cartel.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMeds();   // loadMeds ya limpia el contenedor al recibir la respuesta
    }

    private void loadMeds() {
        tvSubtitle.setText("Cargando…");

        VimedRepo.Cb<List<Medicamento>> cb = new VimedRepo.Cb<List<Medicamento>>() {
            @Override
            public void onOk(List<Medicamento> meds) {
                medsContainer.removeAllViews();

                if (meds.isEmpty()) {
                    emptyMeds.setVisibility(View.VISIBLE);
                    medsContainer.setVisibility(View.GONE);
                    tvSubtitle.setText("0 activos");
                    return;
                }

                emptyMeds.setVisibility(View.GONE);
                medsContainer.setVisibility(View.VISIBLE);
                tvSubtitle.setText(meds.size() + (meds.size() == 1 ? " activo" : " activos"));

                LayoutInflater inflater = LayoutInflater.from(MedsListActivity.this);
                for (Medicamento med : meds) {
                    View item = inflater.inflate(R.layout.item_med_card, medsContainer, false);
                    bindMedCard(item, med);
                    medsContainer.addView(item);
                }
            }

            @Override
            public void onError(String msg) {
                tvSubtitle.setText("Sin conexión");
                Toast.makeText(MedsListActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        };

        if (modo.esDeOtro()) {
            VimedRepo.listarMedicamentosDe(modo.idUsuario, cb);
        } else {
            VimedRepo.listarMedicamentos(this, cb);
        }
    }

    private void bindMedCard(View item, Medicamento med) {
        TextView tvName = item.findViewById(R.id.tv_med_name);
        TextView tvDosis = item.findViewById(R.id.tv_med_dosis);
        TextView tvInst = item.findViewById(R.id.tv_med_inst);
        TextView tvHorario = item.findViewById(R.id.tv_med_horario);
        TextView tvInitial = item.findViewById(R.id.tv_med_initial);
        TextView tvStock = item.findViewById(R.id.tv_med_stock);
        FrameLayout iconContainer = item.findViewById(R.id.med_icon_container);

        tvName.setText(med.getNombre());

        String dosisText = med.getDosis() > 0
            ? (int) med.getDosis() + " " + (med.getUnidad() != null ? med.getUnidad() : "")
            : "";
        tvDosis.setText(dosisText);
        tvInst.setText(MedicamentoUI.instruccion(med.getInstrucciones()));

        // Los horarios se piden aparte; mientras tanto mostramos un placeholder.
        // La lista queda guardada para poder cancelar las alarmas al eliminar.
        final List<Horario> horariosDelMed = new ArrayList<>();
        tvHorario.setText("Cargando horario…");
        VimedRepo.listarHorarios(med.getId(), new VimedRepo.Cb<List<Horario>>() {
            @Override
            public void onOk(List<Horario> horarios) {
                horariosDelMed.clear();
                horariosDelMed.addAll(horarios);
                tvHorario.setText(MedicamentoUI.horarios(horarios));
            }

            @Override
            public void onError(String msg) {
                tvHorario.setText("Sin horario configurado");
            }
        });

        // Chip de stock — cambia de color si está por acabarse.
        // El vencimiento comparte el chip en vez de sumar uno al lado: en una
        // fila que ya tiene nombre, dosis, instrucción y horario, un chip más
        // deja de leerse. Y cuando el medicamento está por vencer, ESO es lo
        // que importa, así que se queda con el chip entero.
        if (tvStock != null) {
            String unidades = "Quedan " + med.getStockActual()
                + (med.getStockActual() == 1 ? " unidad" : " unidades");

            if (med.venceProto()) {
                tvStock.setText(med.vencimientoLegible() + " · " + unidades);
                tvStock.setBackgroundResource(R.drawable.shape_chip_warn);
                tvStock.setTextColor(getColor(R.color.warn));
            } else {
                tvStock.setText(unidades);
                if (med.isStockBajo()) {
                    tvStock.setBackgroundResource(R.drawable.shape_chip_warn);
                    tvStock.setTextColor(getColor(R.color.warn));
                } else {
                    tvStock.setBackgroundResource(R.drawable.shape_chip_success);
                    tvStock.setTextColor(getColor(R.color.success));
                }
            }
        }

        String nombre = med.getNombre();
        tvInitial.setText(nombre.length() > 0 ? String.valueOf(nombre.charAt(0)).toUpperCase() : "M");

        int bgColor = colorForMed(med.getColorIcono());
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(bgColor);
        iconContainer.setBackground(circle);

        // La tarjeta ahora responde al toque (antes la flecha no hacía nada)
        // Tocar abre la ficha completa. Antes abría un menú de dos opciones
        // —reponer y eliminar— y no había ningún lugar donde ver la dosis,
        // las instrucciones ni el vencimiento.
        item.setOnClickListener(v -> {
            android.content.Intent i = modo.intent(this,
                MedicamentoDetalleActivity.class);
            i.putExtra(MedicamentoDetalleActivity.EXTRA_ID_MEDICAMENTO, med.getId());
            startActivity(i);
        });
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

    private void setupBottomNav() {
        NavInferior.configurar(this, modo, R.id.nav_meds, true);
    }
}
