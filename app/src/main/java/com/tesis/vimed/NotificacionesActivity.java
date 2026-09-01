package com.tesis.vimed;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Notificacion;
import com.tesis.vimed.utils.ModoPaciente;

import java.util.List;

/**
 * Historial de avisos: lo que la app fue anotando a lo largo del tiempo.
 *
 * Es lo que abre la campana del inicio. Hasta ahora esa campana no tenía
 * listener: se dibujaba, se podía tocar, y no pasaba nada — el peor tipo
 * de control, porque promete algo y no lo cumple.
 *
 * Los datos ya existían: NotificacionSync viene escribiendo en
 * public.notificaciones desde siempre, y el cuidador los veía en su panel
 * bajo "Actividad reciente". El dueño de esos avisos era el único que no
 * tenía dónde mirarlos.
 */
public class NotificacionesActivity extends AppCompatActivity {

    /** Cuántas se muestran. Más que esto ya no es historial, es archivo. */
    private static final int TOPE = 50;

    private LinearLayout lista;
    private View vacio;

    private ModoPaciente modo = ModoPaciente.propio();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notificaciones);

        modo  = ModoPaciente.de(this);
        lista = findViewById(R.id.lista);
        vacio = findViewById(R.id.vacio);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.tv_subtitulo)).setText(modo.esDeOtro()
            ? "Lo que la app fue registrando de " + modo.primerNombre()
            : "Lo que la app fue registrando de tu medicación");

        if (modo.esDeOtro()) {
            ((TextView) findViewById(R.id.tv_vacio_detalle)).setText(
                "Acá van a aparecer las tomas confirmadas, los medicamentos"
                    + " que se estén acabando y los recordatorios de sus citas.");
        }

        cargar();
    }

    private void cargar() {
        int id = modo.esDeOtro()
            ? modo.idUsuario
            : new SessionManager(this).getSupabaseIdUsuario();

        if (id <= 0) { mostrarVacio(); return; }

        VimedRepo.listarNotificacionesDe(id, new VimedRepo.Cb<List<Notificacion>>() {
            @Override public void onOk(List<Notificacion> notis) { pintar(notis); }
            @Override public void onError(String msg) {
                mostrarVacio();
                Toast.makeText(NotificacionesActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarVacio() {
        vacio.setVisibility(View.VISIBLE);
        lista.setVisibility(View.GONE);
    }

    private void pintar(List<Notificacion> notis) {
        lista.removeAllViews();

        if (notis == null || notis.isEmpty()) { mostrarVacio(); return; }
        vacio.setVisibility(View.GONE);
        lista.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        int puestas = 0;

        for (Notificacion n : notis) {
            if (puestas++ >= TOPE) break;

            View item = inflater.inflate(R.layout.item_actividad, lista, false);
            ((TextView) item.findViewById(R.id.act_mensaje)).setText(n.getMensaje());
            ((TextView) item.findViewById(R.id.act_fecha)).setText(
                fechaLegible(n.getFechaEnvio()));

            pintarIcono(item, n.getTipo());
            lista.addView(item);
        }
    }

    /**
     * El tipo se lee por el color y el dibujo antes que por el texto: en una
     * lista larga de avisos, todos con el mismo ícono gris, no se distingue
     * un stock bajo de una toma confirmada sin leer renglón por renglón.
     */
    private void pintarIcono(View item, String tipo) {
        ImageView icono = item.findViewById(R.id.act_icon);
        View fondo = item.findViewById(R.id.act_icon_bg);

        int dibujo, color;
        if (Notificacion.TIPO_STOCK.equals(tipo)) {
            dibujo = R.drawable.ic_warn;         color = R.color.amarillo_solido;
        } else if (Notificacion.TIPO_CITA.equals(tipo)) {
            dibujo = R.drawable.ic_nav_calendar; color = R.color.azul_solido;
        } else if (Notificacion.TIPO_INTERACCION.equals(tipo)) {
            dibujo = R.drawable.ic_warn;         color = R.color.naranja_solido;
        } else {
            dibujo = R.drawable.ic_nav_meds;     color = R.color.verde_solido;
        }

        icono.setImageResource(dibujo);
        icono.setColorFilter(ContextCompat.getColor(this, R.color.white));

        GradientDrawable circulo = new GradientDrawable();
        circulo.setShape(GradientDrawable.OVAL);
        circulo.setColor(ContextCompat.getColor(this, color));
        fondo.setBackground(circulo);
    }

    /** "31 Ago · 19:02" desde el timestamp de Postgres. */
    private String fechaLegible(String iso) {
        if (iso == null || iso.length() < 16) return "";
        try {
            String[] meses = {"Ene","Feb","Mar","Abr","May","Jun",
                              "Jul","Ago","Sep","Oct","Nov","Dic"};
            int mes = Integer.parseInt(iso.substring(5, 7));
            int dia = Integer.parseInt(iso.substring(8, 10));
            return dia + " " + meses[mes - 1] + " · " + iso.substring(11, 16);
        } catch (Exception e) {
            return "";
        }
    }
}
