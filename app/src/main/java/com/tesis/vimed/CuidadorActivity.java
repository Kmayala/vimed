package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.Notificacion;
import com.tesis.vimed.models.RegistroToma;
import com.tesis.vimed.models.UsuarioSupabase;
import com.tesis.vimed.models.Vinculacion;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home del rol FAMILIAR: en vez de gestionar su propia medicación,
 * monitorea al adulto mayor vinculado — tomas del día, alertas de
 * stock y el historial de notificaciones que la app del adulto
 * espeja en Supabase.
 */
public class CuidadorActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private View emptyPaciente, contentPaciente;
    private LinearLayout tomasContainer, actividadContainer;

    /** id_usuario (Supabase) del adulto que se está mostrando. */
    private int idAdulto = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cuidador);

        sessionManager = new SessionManager(this);

        emptyPaciente      = findViewById(R.id.empty_paciente);
        contentPaciente    = findViewById(R.id.content_paciente);
        tomasContainer     = findViewById(R.id.tomas_container);
        actividadContainer = findViewById(R.id.actividad_container);

        String nombre = sessionManager.getNombre();
        ((TextView) findViewById(R.id.tv_greeting)).setText(
            "Hola, " + (nombre != null && !nombre.isEmpty() ? nombre : "cuidador") + ".");

        findViewById(R.id.btn_profile).setOnClickListener(v -> menuPerfil());
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarVinculo();
    }

    private void menuPerfil() {
        new AlertDialog.Builder(this)
            .setTitle(sessionManager.getNombre())
            .setItems(new String[]{"Actualizar", "Cerrar sesión"}, (d, w) -> {
                if (w == 0) {
                    cargarVinculo();
                } else {
                    sessionManager.logout();
                    Intent i = new Intent(this, WelcomeActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                }
            })
            .show();
    }

    // ═══ Cargar el vínculo y después todo lo demás ═════════════

    private void cargarVinculo() {
        VimedRepo.listarMisPacientes(this, new VimedRepo.Cb<List<Vinculacion>>() {
            @Override
            public void onOk(List<Vinculacion> vinculos) {
                if (vinculos.isEmpty()) {
                    mostrarVacio();
                    return;
                }
                // Por ahora un cuidador monitorea a su primer vínculo aceptado
                idAdulto = vinculos.get(0).getIdAdulto();
                cargarPaciente();
            }

            @Override
            public void onError(String msg) {
                mostrarVacio();
                Toast.makeText(CuidadorActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarVacio() {
        emptyPaciente.setVisibility(View.VISIBLE);
        contentPaciente.setVisibility(View.GONE);
    }

    private void cargarPaciente() {
        emptyPaciente.setVisibility(View.GONE);
        contentPaciente.setVisibility(View.VISIBLE);

        // Nombre del adulto
        VimedRepo.buscarPerfilPorId(idAdulto, new VimedRepo.Cb<UsuarioSupabase>() {
            @Override
            public void onOk(UsuarioSupabase perfil) {
                String nombre = perfil != null && perfil.getNombre() != null
                    ? perfil.getNombre() : "Adulto mayor";
                ((TextView) findViewById(R.id.tv_paciente_nombre)).setText(nombre);
                ((TextView) findViewById(R.id.tv_paciente_initial)).setText(
                    nombre.substring(0, 1).toUpperCase(Locale.getDefault()));
                ((TextView) findViewById(R.id.tv_paciente_sub)).setText(
                    perfil != null && perfil.getCorreo() != null ? perfil.getCorreo() : "");
            }
        });

        // Medicamentos → alerta de stock
        VimedRepo.listarMedicamentosDe(idAdulto, new VimedRepo.Cb<List<Medicamento>>() {
            @Override
            public void onOk(List<Medicamento> meds) {
                pintarAlertaStock(meds);
            }
        });

        // Tomas de hoy
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        VimedRepo.listarTomasDelDiaDe(idAdulto, hoy, new VimedRepo.Cb<List<RegistroToma>>() {
            @Override
            public void onOk(List<RegistroToma> tomas) {
                pintarTomasHoy(tomas, hoy);
            }
        });

        // Actividad reciente (notificaciones espejadas)
        VimedRepo.listarNotificacionesDe(idAdulto, new VimedRepo.Cb<List<Notificacion>>() {
            @Override
            public void onOk(List<Notificacion> notis) {
                pintarActividad(notis);
            }
        });
    }

    // ═══ Secciones ═════════════════════════════════════════════

    private void pintarAlertaStock(List<Medicamento> meds) {
        View alerta = findViewById(R.id.alert_stock_cuidador);
        TextView tv = findViewById(R.id.tv_stock_cuidador);

        StringBuilder bajos = new StringBuilder();
        for (Medicamento m : meds) {
            if (m.isStockBajo()) {
                if (bajos.length() > 0) bajos.append(", ");
                bajos.append(m.getNombre()).append(" (").append(m.getStockActual()).append(")");
            }
        }

        if (bajos.length() == 0) {
            alerta.setVisibility(View.GONE);
        } else {
            tv.setText("Medicamentos por acabarse: " + bajos + ". Coordiná la reposición.");
            alerta.setVisibility(View.VISIBLE);
        }
    }

    private void pintarTomasHoy(List<RegistroToma> tomas, String hoy) {
        tomasContainer.removeAllViews();
        View vacio = findViewById(R.id.tv_tomas_empty);

        int confirmadas = 0, total = 0;
        LayoutInflater inflater = LayoutInflater.from(this);

        for (RegistroToma t : tomas) {
            String prog = t.getFechaHoraProgramada();
            if (prog == null || !prog.startsWith(hoy)) continue;
            total++;
            if ("confirmada".equals(t.getEstado())) confirmadas++;

            View item = inflater.inflate(R.layout.item_actividad, tomasContainer, false);
            ImageView icon = item.findViewById(R.id.act_icon);
            TextView msg = item.findViewById(R.id.act_mensaje);
            TextView fecha = item.findViewById(R.id.act_fecha);

            icon.setImageResource(R.drawable.ic_check);
            String hora = prog.length() >= 16 ? prog.substring(11, 16) : "";
            String estado;
            switch (t.getEstado() != null ? t.getEstado() : "omitida") {
                case "confirmada": estado = "Toma confirmada ✓"; break;
                case "pospuesta":  estado = "Toma pospuesta";     break;
                default:           estado = "Toma sin confirmar"; break;
            }
            msg.setText(estado);
            fecha.setText("Programada a las " + hora);
            tomasContainer.addView(item);
        }

        ((TextView) findViewById(R.id.tv_resumen_hoy)).setText(
            total == 0 ? "Sin tomas programadas hoy"
                       : "Hoy: " + confirmadas + " de " + total
                         + (total == 1 ? " toma confirmada" : " tomas confirmadas"));

        vacio.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
    }

    private void pintarActividad(List<Notificacion> notis) {
        actividadContainer.removeAllViews();
        View vacio = findViewById(R.id.tv_actividad_empty);

        if (notis.isEmpty()) {
            vacio.setVisibility(View.VISIBLE);
            return;
        }
        vacio.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        int mostradas = 0;
        for (Notificacion n : notis) {
            if (mostradas++ >= 10) break;   // solo lo más reciente

            View item = inflater.inflate(R.layout.item_actividad, actividadContainer, false);
            ImageView icon = item.findViewById(R.id.act_icon);
            TextView msg = item.findViewById(R.id.act_mensaje);
            TextView fecha = item.findViewById(R.id.act_fecha);

            String tipo = n.getTipo() != null ? n.getTipo() : "";
            switch (tipo) {
                case Notificacion.TIPO_STOCK:       icon.setImageResource(R.drawable.ic_nav_meds); break;
                case Notificacion.TIPO_INTERACCION: icon.setImageResource(R.drawable.ic_warn);     break;
                case Notificacion.TIPO_CITA:        icon.setImageResource(R.drawable.ic_nav_calendar); break;
                default:                            icon.setImageResource(R.drawable.ic_bell);     break;
            }

            msg.setText(n.getMensaje() != null ? n.getMensaje() : "");
            fecha.setText(fechaLegible(n.getFechaEnvio()));
            actividadContainer.addView(item);
        }
    }

    /** "2026-08-06T18:35:01+00:00" → "6 Ago · 18:35" (best effort). */
    private String fechaLegible(String iso) {
        if (iso == null || iso.length() < 16) return "";
        try {
            String[] meses = {"Ene","Feb","Mar","Abr","May","Jun",
                              "Jul","Ago","Sep","Oct","Nov","Dic"};
            int mes = Integer.parseInt(iso.substring(5, 7));
            int dia = Integer.parseInt(iso.substring(8, 10));
            String hora = iso.substring(11, 16);
            return dia + " " + meses[mes - 1] + " · " + hora;
        } catch (Exception e) {
            return "";
        }
    }
}
