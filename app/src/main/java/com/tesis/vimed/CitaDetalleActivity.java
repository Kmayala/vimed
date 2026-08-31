package com.tesis.vimed;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.CitaMedica;
import com.tesis.vimed.models.Especialidad;
import com.tesis.vimed.utils.RecordatorioCita;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Todo lo que se puede hacer con UNA cita: verla completa, anotar qué pasó
 * con ella, editarla o borrarla.
 *
 * Antes esto era un menú de opciones sobre la tarjeta de la lista: se veía
 * el título y tres ítems, sin las notas, sin el lugar y sin poder corregir
 * nada — una cita mal cargada había que borrarla y hacerla de nuevo.
 *
 * Las acciones están agrupadas por consecuencia. Arriba las de estado, que
 * van y vienen; abajo editar y eliminar. Eliminar queda al final, en rojo y
 * con confirmación, porque es la única que no se deshace.
 */
public class CitaDetalleActivity extends AppCompatActivity {

    public static final String EXTRA_ID_CITA = "id_cita";

    private static final String[] MESES = {
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    };
    private static final String[] DIAS = {
        "Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"
    };

    private int idCita = -1;
    private CitaMedica cita;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cita_detalle);

        idCita = getIntent().getIntExtra(EXTRA_ID_CITA, -1);
        if (idCita <= 0) { finish(); return; }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_asistida).setOnClickListener(v ->
            cambiarEstado(CitaMedica.ESTADO_ASISTIDA, "Anotado: ya fuiste a esta cita"));
        findViewById(R.id.btn_confirmar).setOnClickListener(v ->
            cambiarEstado(CitaMedica.ESTADO_CONFIRMADA, "Cita confirmada"));
        findViewById(R.id.btn_reabrir).setOnClickListener(v ->
            cambiarEstado(CitaMedica.ESTADO_PENDIENTE, "La cita volvió a quedar pendiente"));
        findViewById(R.id.btn_cancelar_cita).setOnClickListener(v -> confirmarCancelacion());
        findViewById(R.id.btn_editar).setOnClickListener(v -> editar());
        findViewById(R.id.btn_eliminar).setOnClickListener(v -> confirmarEliminar());
        findViewById(R.id.card_lugar).setOnClickListener(v -> abrirMapa());
    }

    /**
     * Se recarga en onResume y no solo al crear: al volver de editarla, los
     * datos de la pantalla ya no son los que están guardados.
     */
    @Override
    protected void onResume() {
        super.onResume();
        cargar();
    }

    private void cargar() {
        VimedRepo.buscarCita(idCita, new VimedRepo.Cb<CitaMedica>() {
            @Override public void onOk(CitaMedica c) { cita = c; pintar(); }
            @Override public void onError(String msg) {
                Toast.makeText(CitaDetalleActivity.this, msg, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    // ═══ Pintar ════════════════════════════════════════════════

    private void pintar() {
        if (cita == null) return;

        String esp = cita.getEspecialidad();
        ((TextView) findViewById(R.id.tv_especialidad)).setText(
            esp != null && !esp.isEmpty() ? esp : "Consulta médica");

        String medico = cita.getMedico();
        ((TextView) findViewById(R.id.tv_medico)).setText(
            medico != null && !medico.isEmpty() ? medico : "Sin médico anotado");

        pintarIcono(esp);
        pintarEstado();
        pintarCuando();
        pintarLugar();
        pintarNotas();
    }

    private void pintarIcono(String texto) {
        ImageView icono = findViewById(R.id.iv_especialidad);
        View fondo = findViewById(R.id.icono_fondo);

        Especialidad e = Especialidad.desdeNombre(texto);
        icono.setImageResource(e != null ? e.icono : R.drawable.ic_esp_cruz);
        icono.setColorFilter(ContextCompat.getColor(this, R.color.white));

        GradientDrawable circulo = new GradientDrawable();
        circulo.setShape(GradientDrawable.OVAL);
        circulo.setColor(ContextCompat.getColor(this,
            e != null ? e.color : R.color.brand_500));
        fondo.setBackground(circulo);
    }

    private void pintarEstado() {
        TextView chip = findViewById(R.id.tv_estado);
        chip.setText(cita.estadoLegible());

        int fondo, texto;
        switch (cita.getEstado()) {
            case CitaMedica.ESTADO_ASISTIDA:
            case CitaMedica.ESTADO_CONFIRMADA:
                fondo = R.drawable.shape_chip_success; texto = R.color.success; break;
            case CitaMedica.ESTADO_CANCELADA:
                fondo = R.drawable.shape_chip_danger;  texto = R.color.danger;  break;
            default:
                fondo = R.drawable.shape_chip_pending; texto = R.color.warn;    break;
        }
        chip.setBackgroundResource(fondo);
        chip.setTextColor(ContextCompat.getColor(this, texto));

        // Una cita ya resuelta no ofrece volver a resolverla: en su lugar
        // aparece la salida, que es devolverla a pendiente.
        boolean resuelta = cita.estaAsistida() || cita.estaCancelada();
        findViewById(R.id.btn_asistida).setVisibility(resuelta ? View.GONE : View.VISIBLE);
        findViewById(R.id.btn_confirmar).setVisibility(
            resuelta || cita.estaConfirmada() ? View.GONE : View.VISIBLE);
        findViewById(R.id.btn_cancelar_cita).setVisibility(
            cita.estaCancelada() ? View.GONE : View.VISIBLE);
        findViewById(R.id.btn_reabrir).setVisibility(resuelta ? View.VISIBLE : View.GONE);
    }

    private void pintarCuando() {
        String ymd = cita.fechaYMD();
        String hm  = cita.horaHM();

        String cuando = ymd;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(ymd);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            cuando = DIAS[c.get(Calendar.DAY_OF_WEEK) - 1] + " "
                + c.get(Calendar.DAY_OF_MONTH) + " de "
                + MESES[c.get(Calendar.MONTH)] + " de " + c.get(Calendar.YEAR);
        } catch (Exception ignored) {}

        ((TextView) findViewById(R.id.tv_cuando)).setText(
            cuando + (hm.isEmpty() ? "" : " · " + hm + " hs"));

        ((TextView) findViewById(R.id.tv_falta)).setText(cuantoFalta());
    }

    /** "Es mañana", "Faltan 5 días", "Fue hace 2 días". */
    private String cuantoFalta() {
        long ms = RecordatorioCita.enMillis(cita);
        if (ms <= 0) return "";

        // Se compara por DÍA y no por horas: una cita de hoy a las 8 de la
        // mañana, mirada a las 10, no es "hace 2 horas" sino "es hoy".
        Calendar cita0 = aMedianoche(ms);
        Calendar hoy0  = aMedianoche(System.currentTimeMillis());
        long dias = Math.round(
            (cita0.getTimeInMillis() - hoy0.getTimeInMillis()) / 86_400_000d);

        if (dias == 0)  return "Es hoy";
        if (dias == 1)  return "Es mañana";
        if (dias == -1) return "Fue ayer";
        if (dias > 1)   return "Faltan " + dias + " días";
        return "Fue hace " + (-dias) + " días";
    }

    private Calendar aMedianoche(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private void pintarLugar() {
        String lugar = cita.getLugar();
        boolean hayTexto = lugar != null && !lugar.trim().isEmpty();
        boolean sePuedeAbrir = hayTexto || cita.tieneUbicacion();

        ((TextView) findViewById(R.id.tv_lugar)).setText(
            hayTexto ? lugar : "Sin lugar anotado");

        // La invitación a tocar solo aparece si el toque va a hacer algo.
        findViewById(R.id.tv_abrir_mapa).setVisibility(
            sePuedeAbrir ? View.VISIBLE : View.GONE);
        findViewById(R.id.card_lugar).setClickable(sePuedeAbrir);
    }

    private void pintarNotas() {
        String notas = cita.getNotas();
        boolean hay = notas != null && !notas.trim().isEmpty();
        findViewById(R.id.card_notas).setVisibility(hay ? View.VISIBLE : View.GONE);
        if (hay) ((TextView) findViewById(R.id.tv_notas)).setText(notas);
    }

    // ═══ Acciones ══════════════════════════════════════════════

    private void cambiarEstado(String nuevo, String mensaje) {
        VimedRepo.actualizarEstadoCita(idCita, nuevo, new VimedRepo.Cb<Void>() {
            @Override public void onOk(Void v) {
                cita.setEstado(nuevo);

                // Los recordatorios siguen al estado: una cita cancelada no
                // tiene que seguir avisando, y una que vuelve a pendiente
                // tiene que recuperar sus dos avisos.
                if (CitaMedica.ESTADO_CANCELADA.equals(nuevo)
                        || CitaMedica.ESTADO_ASISTIDA.equals(nuevo)) {
                    RecordatorioCita.cancelar(CitaDetalleActivity.this, idCita);
                } else {
                    RecordatorioCita.programar(CitaDetalleActivity.this, cita);
                }

                pintar();
                Toast.makeText(CitaDetalleActivity.this, mensaje, Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String msg) {
                Toast.makeText(CitaDetalleActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmarCancelacion() {
        new AlertDialog.Builder(this)
            .setTitle("¿Se canceló la cita?")
            .setMessage("Queda anotada como cancelada y dejás de recibir los"
                + " recordatorios. Podés volver a dejarla pendiente después.")
            .setNegativeButton("No", null)
            .setPositiveButton("Sí, se canceló", (d, w) ->
                cambiarEstado(CitaMedica.ESTADO_CANCELADA, "Cita cancelada"))
            .show();
    }

    private void editar() {
        Intent i = new Intent(this, AgregarCitaActivity.class);
        i.putExtra(AgregarCitaActivity.EXTRA_ID_CITA, idCita);
        startActivity(i);
    }

    private void confirmarEliminar() {
        new AlertDialog.Builder(this)
            .setTitle("Eliminar la cita")
            .setMessage("Se borra del todo y no se puede recuperar. Si solo"
                + " se canceló, mejor marcala como cancelada: así queda en el"
                + " historial.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (d, w) ->
                VimedRepo.eliminarCita(idCita, new VimedRepo.Cb<Void>() {
                    @Override public void onOk(Void v) {
                        RecordatorioCita.cancelar(CitaDetalleActivity.this, idCita);
                        Toast.makeText(CitaDetalleActivity.this,
                            "Cita eliminada", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    @Override public void onError(String msg) {
                        Toast.makeText(CitaDetalleActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                }))
            .show();
    }

    private void abrirMapa() {
        String lugar = cita.getLugar();
        boolean hayTexto = lugar != null && !lugar.trim().isEmpty();

        String destino;
        if (cita.tieneUbicacion()) {
            String etiqueta = Uri.encode(hayTexto ? lugar.trim() : "Cita médica");
            destino = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
                cita.getLatitud(), cita.getLongitud(),
                cita.getLatitud(), cita.getLongitud(), etiqueta);
        } else if (hayTexto) {
            destino = "geo:0,0?q=" + Uri.encode(lugar.trim());
        } else {
            return;
        }

        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(destino));
        if (i.resolveActivity(getPackageManager()) != null) {
            startActivity(i);
        } else {
            Toast.makeText(this, "No hay ninguna app de mapas instalada en este celular.",
                Toast.LENGTH_LONG).show();
        }
    }
}
