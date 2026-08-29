package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.utils.MedCache;
import com.tesis.vimed.utils.MedicamentoUI;
import com.tesis.vimed.utils.ModoPaciente;
import com.tesis.vimed.utils.NotificationHelper;
import com.tesis.vimed.utils.VencimientoChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Todo lo que sabemos de un medicamento, en una pantalla.
 *
 * Antes, tocar un medicamento abría un menú de dos opciones —reponer y
 * eliminar— y nada más. La dosis, las instrucciones, las horas en que suena
 * y el vencimiento existían en la base pero no había dónde mirarlos: para
 * saber a qué hora tocaba había que leer la línea chica de la lista, y el
 * vencimiento no se veía en ningún lado salvo cuando ya estaba por vencer.
 *
 * Acá se lee la ficha completa, y desde acá salen las tres cosas que se
 * pueden hacer: reponer, editar y dar de baja.
 */
public class MedicamentoDetalleActivity extends AppCompatActivity {

    public static final String EXTRA_ID_MEDICAMENTO = "id_medicamento";

    private ModoPaciente modo;
    private int idMedicamento = -1;

    private Medicamento med;
    private final List<Horario> horarios = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicamento_detalle);

        modo = ModoPaciente.de(this);
        idMedicamento = getIntent().getIntExtra(EXTRA_ID_MEDICAMENTO, -1);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_reponer).setOnClickListener(v -> pedirReposicion());
        findViewById(R.id.btn_editar).setOnClickListener(v -> abrirEdicion());
        findViewById(R.id.btn_eliminar).setOnClickListener(v -> confirmarEliminar());

        if (idMedicamento <= 0) {
            Toast.makeText(this, "No se pudo abrir el medicamento", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargar();   // vuelve de editar: hay que repintar con lo nuevo
    }

    // ═══ Carga ═════════════════════════════════════════════════

    private void cargar() {
        VimedRepo.buscarMedicamento(idMedicamento, new VimedRepo.Cb<Medicamento>() {
            @Override public void onOk(Medicamento m) {
                if (m == null) {
                    Toast.makeText(MedicamentoDetalleActivity.this,
                        "Este medicamento ya no está", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                med = m;
                pintar();
                cargarHorarios();
            }
            @Override public void onError(String msg) {
                Toast.makeText(MedicamentoDetalleActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void cargarHorarios() {
        VimedRepo.listarHorarios(idMedicamento, new VimedRepo.Cb<List<Horario>>() {
            @Override public void onOk(List<Horario> hs) {
                horarios.clear();
                horarios.addAll(hs);
                pintar();   // la ficha incluye las horas: se repinta entera
            }
            @Override public void onError(String msg) { /* queda "Cargando…" */ }
        });
    }

    // ═══ Pintado ═══════════════════════════════════════════════

    private void pintar() {
        if (med == null) return;

        String nombre = med.getNombre() != null ? med.getNombre() : "Medicamento";
        ((TextView) findViewById(R.id.tv_toolbar_titulo)).setText(
            modo.esDeOtro() ? "De " + modo.primerNombre() : "Medicamento");
        ((TextView) findViewById(R.id.tv_nombre)).setText(nombre);
        ((TextView) findViewById(R.id.tv_dosis)).setText(MedicamentoUI.dosis(med));
        ((TextView) findViewById(R.id.tv_inicial)).setText(
            nombre.isEmpty() ? "M"
                : String.valueOf(nombre.charAt(0)).toUpperCase(Locale.getDefault()));

        pintarAlertaVencimiento();
        pintarFicha();
        pintarEnvase();
    }

    private void pintarAlertaVencimiento() {
        TextView alerta = findViewById(R.id.tv_alerta_vencimiento);
        if (!med.venceProto()) {
            alerta.setVisibility(View.GONE);
            return;
        }

        String nombre = med.getNombre() != null ? med.getNombre() : "Este medicamento";
        alerta.setText(med.estaVencido()
            ? nombre + " venció el " + MedicamentoUI.fechaLegible(med.getFechaVencimiento())
                + ". No conviene seguir tomándolo: conseguí un envase nuevo."
            : med.vencimientoLegible() + " ("
                + MedicamentoUI.fechaLegible(med.getFechaVencimiento())
                + "). Aprovechá para comprar el reemplazo.");
        alerta.setVisibility(View.VISIBLE);
    }

    private void pintarFicha() {
        LinearLayout card = findViewById(R.id.card_ficha);
        card.removeAllViews();

        agregarDato(card, "Presentación", vacioOguion(med.getPresentacion()));
        agregarDato(card, "Cómo tomarlo",
            vacioOguion(MedicamentoUI.instruccion(med.getInstrucciones())));
        agregarDato(card, "Horarios",
            horarios.isEmpty() ? "Cargando…" : MedicamentoUI.horarios(horarios));
    }

    private void pintarEnvase() {
        LinearLayout card = findViewById(R.id.card_envase);
        card.removeAllViews();

        // El stock se dice con su unidad y con el aviso pegado, no en una
        // fila aparte: "quedan 3" y "por acabarse" separados obligan a
        // relacionarlos, y quien mira esta pantalla quiere saber de una si
        // tiene que ir a la farmacia.
        String stock = med.getStockActual()
            + (med.getStockActual() == 1 ? " unidad" : " unidades");
        if (med.isStockBajo()) stock += "  ·  Por acabarse";
        agregarDato(card, "Cuántas quedan", stock,
            med.isStockBajo() ? R.color.warn : 0);

        agregarDato(card, "Avisar cuando queden",
            med.getStockMinimo() + (med.getStockMinimo() == 1 ? " unidad" : " unidades"));

        String vence = MedicamentoUI.fechaLegible(med.getFechaVencimiento());
        agregarDato(card, "Vencimiento",
            vence.isEmpty()
                ? "Sin cargar — tocá Editar para ponerlo"
                : vence + "  ·  " + med.vencimientoLegible(),
            med.venceProto() ? R.color.warn : 0);
    }

    private void agregarDato(LinearLayout card, String etiqueta, String valor) {
        agregarDato(card, etiqueta, valor, 0);
    }

    /** @param colorValor 0 para el color de siempre. */
    private void agregarDato(LinearLayout card, String etiqueta, String valor, int colorValor) {
        View fila = LayoutInflater.from(this)
            .inflate(R.layout.item_dato_detalle, card, false);
        ((TextView) fila.findViewById(R.id.tv_etiqueta)).setText(etiqueta);

        TextView tvValor = fila.findViewById(R.id.tv_valor);
        tvValor.setText(valor);
        if (colorValor != 0) tvValor.setTextColor(ContextCompat.getColor(this, colorValor));

        card.addView(fila);
    }

    private String vacioOguion(String s) {
        return s == null || s.trim().isEmpty() ? "—" : s.trim();
    }

    // ═══ Acciones ══════════════════════════════════════════════

    private void abrirEdicion() {
        if (med == null) return;
        Intent i = modo.intent(this, EditarMedicamentoActivity.class);
        i.putExtra(EditarMedicamentoActivity.EXTRA_ID_MEDICAMENTO, idMedicamento);
        startActivity(i);
    }

    /** Suma unidades al stock. Suma, no reemplaza: se está reponiendo. */
    private void pedirReposicion() {
        if (med == null) return;

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Cantidad a agregar");
        input.setTextSize(19);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
            .setTitle("Reponer " + med.getNombre())
            .setMessage(modo.frase("tiene " + med.getStockActual()
                + " unidades.") + " ¿Cuántas agregás?")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Agregar", (d, w) -> {
                String txt = input.getText().toString().trim();
                if (txt.isEmpty()) return;
                int suma;
                try {
                    suma = Integer.parseInt(txt);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Número inválido", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (suma <= 0) return;
                reponer(med.getStockActual() + suma);
            })
            .show();
    }

    private void reponer(int nuevoStock) {
        VimedRepo.corregirStock(med.getId(), nuevoStock, med.getFechaVencimiento(),
            new VimedRepo.Cb<Void>() {
                @Override public void onOk(Void v) {
                    // Sin esto la alarma seguiría creyendo que el frasco está
                    // vacío hasta el próximo AlarmaSync.
                    MedCache.guardarStock(MedicamentoDetalleActivity.this,
                        med.getId(), nuevoStock);
                    cargar();
                    Toast.makeText(MedicamentoDetalleActivity.this,
                        "Stock actualizado ✓", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String msg) {
                    Toast.makeText(MedicamentoDetalleActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
    }

    private void confirmarEliminar() {
        if (med == null) return;

        new AlertDialog.Builder(this)
            .setTitle("¿Eliminar " + med.getNombre() + "?")
            .setMessage("Se van a cancelar sus recordatorios. "
                + "El historial de tomas anteriores se conserva.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (d, w) -> {
                // Cancelar las alarmas ANTES de dar de baja: si no, seguirían
                // sonando para algo que ya no existe.
                //
                // No se recorren los horarios: si la consulta que los trae no
                // había llegado —o falló—, la lista está vacía y no se
                // cancelaba nada. cancelarAlarmas se ocupa de todas.
                NotificationHelper.cancelarAlarmas(this, idMedicamento);

                VimedRepo.eliminarMedicamento(med.getId(), new VimedRepo.Cb<Void>() {
                    @Override public void onOk(Void v) {
                        // marcarBorrado y no borrar: deja la marca que le
                        // permite cortarse sola a cualquier alarma que haya
                        // quedado agendada y no hayamos podido cancelar.
                        MedCache.marcarBorrado(MedicamentoDetalleActivity.this, idMedicamento);
                        VencimientoChecker.olvidarAviso(
                            MedicamentoDetalleActivity.this, idMedicamento);
                        Toast.makeText(MedicamentoDetalleActivity.this,
                            "Medicamento eliminado", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    @Override public void onError(String msg) {
                        Toast.makeText(MedicamentoDetalleActivity.this,
                            msg, Toast.LENGTH_LONG).show();
                    }
                });
            })
            .show();
    }
}
