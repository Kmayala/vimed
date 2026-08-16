package com.tesis.vimed;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.utils.ModoPaciente;
import com.tesis.vimed.utils.NavInferior;
import com.tesis.vimed.utils.NotificationHelper;

import java.util.ArrayList;
import java.util.List;

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

    private void mostrarCartelDePaciente() {
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
        tvInst.setText(instruccionLegible(med.getInstrucciones()));

        // Los horarios se piden aparte; mientras tanto mostramos un placeholder.
        // La lista queda guardada para poder cancelar las alarmas al eliminar.
        final List<Horario> horariosDelMed = new ArrayList<>();
        tvHorario.setText("Cargando horario…");
        VimedRepo.listarHorarios(med.getId(), new VimedRepo.Cb<List<Horario>>() {
            @Override
            public void onOk(List<Horario> horarios) {
                horariosDelMed.clear();
                horariosDelMed.addAll(horarios);
                if (!horarios.isEmpty()) {
                    Horario h = horarios.get(0);
                    String intervalo = h.getIntervaloHoras() == 24
                        ? "Una vez al día" : "Cada " + h.getIntervaloHoras() + "h";
                    tvHorario.setText(h.getHoraInicio() + " · " + intervalo);
                } else {
                    tvHorario.setText("Sin horario configurado");
                }
            }

            @Override
            public void onError(String msg) {
                tvHorario.setText("Sin horario configurado");
            }
        });

        // Chip de stock — cambia de color si está por acabarse
        if (tvStock != null) {
            tvStock.setText("Quedan " + med.getStockActual()
                + (med.getStockActual() == 1 ? " unidad" : " unidades"));
            if (med.isStockBajo()) {
                tvStock.setBackgroundResource(R.drawable.shape_chip_warn);
                tvStock.setTextColor(getColor(R.color.warn));
            } else {
                tvStock.setBackgroundResource(R.drawable.shape_chip_success);
                tvStock.setTextColor(getColor(R.color.success));
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
        item.setOnClickListener(v -> mostrarOpciones(med, horariosDelMed));
    }

    /** Menú de acciones sobre un medicamento. */
    private void mostrarOpciones(Medicamento med, List<Horario> horarios) {
        String[] opciones = {"Reponer stock", "Eliminar medicamento"};

        new AlertDialog.Builder(this)
            .setTitle(med.getNombre())
            .setItems(opciones, (d, which) -> {
                if (which == 0) pedirReposicion(med);
                else            confirmarEliminar(med, horarios);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Diálogo para sumar unidades al stock. */
    private void pedirReposicion(Medicamento med) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Cantidad a agregar");
        input.setTextSize(19);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
            .setTitle("Reponer " + med.getNombre())
            .setMessage("Tenés " + med.getStockActual() + " unidades. ¿Cuántas agregás?")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Agregar", (d, w) -> {
                String txt = input.getText().toString().trim();
                if (txt.isEmpty()) return;
                try {
                    int suma = Integer.parseInt(txt);
                    if (suma <= 0) return;
                    VimedRepo.actualizarStock(med.getId(), med.getStockActual() + suma,
                        new VimedRepo.Cb<Void>() {
                            @Override public void onOk(Void v) {
                                recargar();
                                Toast.makeText(MedsListActivity.this,
                                    "Stock actualizado ✓", Toast.LENGTH_SHORT).show();
                            }
                            @Override public void onError(String msg) {
                                Toast.makeText(MedsListActivity.this, msg, Toast.LENGTH_LONG).show();
                            }
                        });
                } catch (NumberFormatException ignored) {}
            })
            .show();
    }

    private void confirmarEliminar(Medicamento med, List<Horario> horarios) {
        new AlertDialog.Builder(this)
            .setTitle("¿Eliminar " + med.getNombre() + "?")
            .setMessage("Se van a cancelar sus recordatorios. El historial de tomas anteriores se conserva.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (d, w) -> {
                // Cancelar las alarmas antes de dar de baja el medicamento,
                // si no seguirían sonando para algo que ya no existe.
                for (Horario h : horarios) {
                    NotificationHelper.cancelarAlarmas(this, med.getId(), h.getIntervaloHoras());
                }
                VimedRepo.eliminarMedicamento(med.getId(), new VimedRepo.Cb<Void>() {
                    @Override public void onOk(Void v) {
                        recargar();
                        Toast.makeText(MedsListActivity.this,
                            med.getNombre() + " eliminado", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String msg) {
                        Toast.makeText(MedsListActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            })
            .show();
    }

    private void recargar() {
        loadMeds();
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
        NavInferior.configurar(this, modo, R.id.nav_meds, true);
    }
}
