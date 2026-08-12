package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.CitaMedica;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.Notificacion;
import com.tesis.vimed.models.RegistroToma;
import com.tesis.vimed.models.UsuarioSupabase;
import com.tesis.vimed.models.Vinculacion;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Home del rol FAMILIAR: en vez de gestionar su propia medicación,
 * monitorea al adulto mayor vinculado — tomas del día, alertas de
 * stock y el historial de notificaciones que la app del adulto
 * espeja en Supabase.
 */
public class CuidadorActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private View emptyPaciente, contentPaciente;
    private LinearLayout tomasContainer, actividadContainer, medsContainer, citasContainer;

    /** id_usuario (Supabase) del adulto que se está mostrando. */
    private int idAdulto = -1;
    private String nombreAdulto = "";

    /**
     * id_horario → medicamento al que pertenece.
     * registro_tomas guarda id_horario, así que sin este mapa no podríamos
     * decir QUÉ medicamento se tomó — solo que "hubo una toma".
     */
    private final Map<Integer, Medicamento> medPorHorario = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cuidador);

        sessionManager = new SessionManager(this);

        emptyPaciente      = findViewById(R.id.empty_paciente);
        contentPaciente    = findViewById(R.id.content_paciente);
        tomasContainer     = findViewById(R.id.tomas_container);
        actividadContainer = findViewById(R.id.actividad_container);
        medsContainer      = findViewById(R.id.meds_container);
        citasContainer     = findViewById(R.id.citas_container);

        String nombre = sessionManager.getNombre();
        ((TextView) findViewById(R.id.tv_greeting)).setText(
            "Hola, " + (nombre != null && !nombre.isEmpty() ? nombre : "cuidador") + ".");

        findViewById(R.id.btn_profile).setOnClickListener(v -> menuPerfil());

        // Lo que el cuidador carga acá aparece en la app del adulto mayor
        findViewById(R.id.btn_agregar_med_cuidador).setOnClickListener(v -> {
            if (idAdulto <= 0) return;
            Intent i = new Intent(this, AgregarMedicamentoActivity.class);
            i.putExtra(AgregarMedicamentoActivity.EXTRA_PARA_ID_USUARIO, idAdulto);
            i.putExtra(AgregarMedicamentoActivity.EXTRA_PARA_NOMBRE, nombreAdulto);
            startActivity(i);
        });

        findViewById(R.id.btn_agregar_cita_cuidador).setOnClickListener(v -> {
            if (idAdulto <= 0) return;
            Intent i = new Intent(this, AgregarCitaActivity.class);
            i.putExtra(AgregarCitaActivity.EXTRA_PARA_ID_USUARIO, idAdulto);
            i.putExtra(AgregarCitaActivity.EXTRA_PARA_NOMBRE, nombreAdulto);
            startActivity(i);
        });
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
                nombreAdulto = nombre;
                ((TextView) findViewById(R.id.tv_paciente_nombre)).setText(nombre);
                ((TextView) findViewById(R.id.tv_paciente_initial)).setText(
                    nombre.substring(0, 1).toUpperCase(Locale.getDefault()));
                ((TextView) findViewById(R.id.tv_paciente_sub)).setText(
                    perfil != null && perfil.getCorreo() != null ? perfil.getCorreo() : "");
                ((TextView) findViewById(R.id.tv_titulo_meds)).setText(
                    "Medicamentos de " + nombre.split(" ")[0]);
            }
        });

        // Medicamentos → stock, listado y (encadenado) el mapa de horarios
        // que las tomas necesitan para saber a qué medicamento pertenecen.
        VimedRepo.listarMedicamentosDe(idAdulto, new VimedRepo.Cb<List<Medicamento>>() {
            @Override
            public void onOk(List<Medicamento> meds) {
                pintarAlertaStock(meds);
                pintarMedicamentos(meds);
                cargarHorariosYTomas(meds);
            }
        });

        // Citas médicas
        VimedRepo.listarCitasDe(idAdulto, new VimedRepo.Cb<List<CitaMedica>>() {
            @Override
            public void onOk(List<CitaMedica> citas) {
                pintarCitas(citas);
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

    /**
     * Arma el mapa id_horario → medicamento y recién después pide las tomas.
     * Si lo hiciéramos en paralelo, las tomas podrían llegar antes que el
     * mapa y se pintarían sin el nombre del medicamento.
     */
    private void cargarHorariosYTomas(List<Medicamento> meds) {
        final Map<Integer, Medicamento> porIdMed = new HashMap<>();
        final List<Integer> ids = new ArrayList<>();
        for (Medicamento m : meds) {
            porIdMed.put(m.getId(), m);
            ids.add(m.getId());
        }

        VimedRepo.listarHorariosDe(ids, new VimedRepo.Cb<List<Horario>>() {
            @Override
            public void onOk(List<Horario> horarios) {
                medPorHorario.clear();
                for (Horario h : horarios) {
                    Medicamento m = porIdMed.get(h.getIdMedicamento());
                    if (m != null) medPorHorario.put(h.getId(), m);
                }
                cargarTomasDeHoy();
            }

            @Override
            public void onError(String msg) {
                cargarTomasDeHoy();   // sin nombres, pero mostramos las tomas
            }
        });
    }

    private void cargarTomasDeHoy() {
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        VimedRepo.listarTomasDelDiaDe(idAdulto, hoy, new VimedRepo.Cb<List<RegistroToma>>() {
            @Override
            public void onOk(List<RegistroToma> tomas) {
                pintarTomasHoy(tomas, hoy);
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

            String hora = prog.length() >= 16 ? prog.substring(11, 16) : "";

            // Nombre del medicamento (vacío si todavía no cargó el mapa)
            Medicamento med = medPorHorario.get(t.getIdHorario());
            String nombreMed = med != null && med.getNombre() != null
                ? med.getNombre() : "Medicamento";

            String estado;
            switch (t.getEstado() != null ? t.getEstado() : "omitida") {
                case "confirmada":
                    estado = "Tomó " + nombreMed + " ✓";
                    icon.setImageResource(R.drawable.ic_check);
                    break;
                case "pospuesta":
                    estado = nombreMed + " — pospuesta";
                    icon.setImageResource(R.drawable.ic_refresh);
                    break;
                default:
                    estado = nombreMed + " — sin confirmar";
                    icon.setImageResource(R.drawable.ic_warn);
                    break;
            }

            msg.setText(estado);
            String detalle = "Programada a las " + hora;
            if (med != null) detalle += " · " + dosisLegible(med);
            fecha.setText(detalle);
            tomasContainer.addView(item);
        }

        ((TextView) findViewById(R.id.tv_resumen_hoy)).setText(
            total == 0 ? "Sin tomas programadas hoy"
                       : "Hoy: " + confirmadas + " de " + total
                         + (total == 1 ? " toma confirmada" : " tomas confirmadas"));

        vacio.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
    }

    /** Listado completo de lo que el adulto tiene cargado. Tocar = editar. */
    private void pintarMedicamentos(List<Medicamento> meds) {
        medsContainer.removeAllViews();
        View vacio = findViewById(R.id.tv_meds_empty);

        if (meds.isEmpty()) {
            vacio.setVisibility(View.VISIBLE);
            return;
        }
        vacio.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Medicamento m : meds) {
            View item = inflater.inflate(R.layout.item_actividad, medsContainer, false);
            ImageView icon = item.findViewById(R.id.act_icon);
            TextView msg = item.findViewById(R.id.act_mensaje);
            TextView fecha = item.findViewById(R.id.act_fecha);

            icon.setImageResource(R.drawable.ic_nav_meds);
            msg.setText(m.getNombre() != null ? m.getNombre() : "Medicamento");

            String stock = "Stock: " + m.getStockActual();
            if (m.isStockBajo()) stock += " ⚠ por acabarse";
            fecha.setText(dosisLegible(m) + " · " + stock);

            item.setOnClickListener(v -> menuMedicamento(m));
            medsContainer.addView(item);
        }
    }

    /** Opciones del cuidador sobre un medicamento del adulto. */
    private void menuMedicamento(Medicamento m) {
        new AlertDialog.Builder(this)
            .setTitle(m.getNombre())
            .setItems(new String[]{"Cambiar stock", "Dar de baja"}, (d, w) -> {
                if (w == 0) dialogoStock(m);
                else confirmarBaja(m);
            })
            .show();
    }

    private void dialogoStock(Medicamento m) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(m.getStockActual()));
        input.setSelection(input.getText().length());

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);

        new AlertDialog.Builder(this)
            .setTitle("Stock de " + m.getNombre())
            .setMessage("¿Cuántas unidades le quedan?")
            .setView(wrap)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (d, w) -> {
                int nuevo;
                try {
                    nuevo = Integer.parseInt(input.getText().toString().trim());
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Número inválido", Toast.LENGTH_SHORT).show();
                    return;
                }
                VimedRepo.actualizarStock(m.getId(), nuevo, new VimedRepo.Cb<Void>() {
                    @Override public void onOk(Void v) {
                        Toast.makeText(CuidadorActivity.this,
                            "Stock actualizado ✓", Toast.LENGTH_SHORT).show();
                        cargarPaciente();
                    }
                    @Override public void onError(String msg) {
                        Toast.makeText(CuidadorActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            })
            .show();
    }

    private void confirmarBaja(Medicamento m) {
        new AlertDialog.Builder(this)
            .setTitle("¿Dar de baja " + m.getNombre() + "?")
            .setMessage("Va a dejar de aparecer en la app de "
                + (nombreAdulto.isEmpty() ? "tu familiar" : nombreAdulto)
                + " y no le va a sonar más la alarma.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Dar de baja", (d, w) ->
                VimedRepo.eliminarMedicamento(m.getId(), new VimedRepo.Cb<Void>() {
                    @Override public void onOk(Void v) {
                        Toast.makeText(CuidadorActivity.this,
                            "Medicamento dado de baja", Toast.LENGTH_SHORT).show();
                        cargarPaciente();
                    }
                    @Override public void onError(String msg) {
                        Toast.makeText(CuidadorActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                }))
            .show();
    }

    private void pintarCitas(List<CitaMedica> citas) {
        citasContainer.removeAllViews();
        View vacio = findViewById(R.id.tv_citas_empty);

        if (citas.isEmpty()) {
            vacio.setVisibility(View.VISIBLE);
            return;
        }
        vacio.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        int mostradas = 0;
        for (CitaMedica c : citas) {
            if (mostradas++ >= 5) break;

            View item = inflater.inflate(R.layout.item_actividad, citasContainer, false);
            ImageView icon = item.findViewById(R.id.act_icon);
            TextView msg = item.findViewById(R.id.act_mensaje);
            TextView fecha = item.findViewById(R.id.act_fecha);

            icon.setImageResource(R.drawable.ic_nav_calendar);
            String medico = c.getMedico() != null ? c.getMedico() : "Consulta";
            String esp = c.getEspecialidad();
            msg.setText(esp != null && !esp.isEmpty() ? medico + " · " + esp : medico);

            String cuando = c.getFechaHora() != null ? c.getFechaHora() : "";
            String lugar = c.getLugar();
            fecha.setText(lugar != null && !lugar.isEmpty() ? cuando + " · " + lugar : cuando);
            citasContainer.addView(item);
        }
    }

    /** "500 mg" — sin el .0 cuando la dosis es entera. */
    private String dosisLegible(Medicamento m) {
        float d = m.getDosis();
        String num = d == (int) d ? String.valueOf((int) d) : String.valueOf(d);
        return num + " " + (m.getUnidad() != null ? m.getUnidad() : "");
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
