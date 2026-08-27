package com.tesis.vimed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.Medicamento;
import com.tesis.vimed.models.RegistroToma;
import com.tesis.vimed.utils.ModoPaciente;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Las dosis que quedaron sin confirmar, agrupadas por día, con la opción de
 * marcar "sí, la tomé".
 *
 * POR QUÉ ES UNA PANTALLA APARTE. Los olvidos estaban repartidos: en el home
 * aparecían solo los de hoy, y en Progreso solo como un porcentaje. Una toma
 * que se perdió el martes no tenía dónde verse ni cómo corregirse, así que el
 * número de adherencia arrastraba para siempre olvidos que en realidad no
 * habían pasado —la persona la tomó, no apretó el botón—.
 *
 * POR QUÉ SE PUEDE CORREGIR. La fila nace en 'omitida' y solo pasa a
 * 'confirmada' si alguien aprieta el botón de la alarma. Un celular en
 * silencio, una alarma que sonó mientras la persona estaba en la ducha, o
 * simplemente haberla tomado sin mirar el teléfono: todos esos casos quedan
 * registrados como olvido. Si no hay forma de arreglarlo, el dato deja de
 * describir la realidad y el resumen que ve el médico miente.
 *
 * QUÉ NO HACE. No descuenta stock. Corregir una toma de hace cuatro días y
 * mover el contador de unidades hoy dejaría el stock peor de lo que estaba:
 * el envase ya se descontó —o no— cuando correspondía, y esta pantalla no
 * tiene forma de saber cuál de los dos casos es.
 */
public class OlvidosActivity extends AppCompatActivity {

    /** Cuántos días para atrás se miran. */
    private static final int DIAS = 7;

    private static final SimpleDateFormat SDF_DIA =
        new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private ModoPaciente modo;
    private SessionManager sesion;
    private LinearLayout contenedor;

    /** id_horario → medicamento, para poder decir QUÉ se olvidó. */
    private final Map<Integer, Medicamento> medPorHorario = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_olvidos);

        modo = ModoPaciente.de(this);
        sesion = new SessionManager(this);
        contenedor = findViewById(R.id.olvidos_container);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        if (modo.esDeOtro()) {
            ((TextView) findViewById(R.id.tv_titulo)).setText(
                "Olvidos de " + modo.primerNombre());
            ((TextView) findViewById(R.id.tv_empty_detalle)).setText(
                modo.primerNombre() + " confirmó todas sus tomas de los"
                    + " últimos " + DIAS + " días.");
        }
        ((TextView) findViewById(R.id.tv_subtitulo)).setText("Últimos " + DIAS + " días");
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargar();
    }

    // ═══ Carga ═════════════════════════════════════════════════

    /**
     * Primero los medicamentos y sus horarios, después los olvidos.
     *
     * Encadenado y no en paralelo: registro_tomas guarda id_horario, no
     * id_medicamento, así que sin el mapa armado la lista se pintaría con
     * "Medicamento" en vez del nombre — justo el dato que hace falta para
     * decidir si uno se acuerda de haberla tomado.
     */
    private void cargar() {
        VimedRepo.listarMedicamentosDe(idMirado(), new VimedRepo.Cb<List<Medicamento>>() {
            @Override public void onOk(List<Medicamento> meds) {
                Map<Integer, Medicamento> porId = new HashMap<>();
                List<Integer> ids = new ArrayList<>();
                for (Medicamento m : meds) {
                    porId.put(m.getId(), m);
                    ids.add(m.getId());
                }

                VimedRepo.listarHorariosDe(ids, new VimedRepo.Cb<List<Horario>>() {
                    @Override public void onOk(List<Horario> horarios) {
                        medPorHorario.clear();
                        for (Horario h : horarios) {
                            Medicamento m = porId.get(h.getIdMedicamento());
                            if (m != null) medPorHorario.put(h.getId(), m);
                        }
                        cargarOlvidos();
                    }
                    @Override public void onError(String msg) {
                        cargarOlvidos();   // sin nombres, pero con la lista
                    }
                });
            }
            @Override public void onError(String msg) { fallar(msg); }
        });
    }

    private void cargarOlvidos() {
        VimedRepo.listarOlvidos(this, modo.idUsuario, desde(),
            new VimedRepo.Cb<List<RegistroToma>>() {
                @Override public void onOk(List<RegistroToma> olvidos) { pintar(olvidos); }
                @Override public void onError(String msg) { fallar(msg); }
            });
    }

    private void fallar(String msg) {
        findViewById(R.id.tv_cargando).setVisibility(View.GONE);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /** id del que estamos mirando: el paciente en modo cuidador, o yo. */
    private int idMirado() {
        return modo.esDeOtro() ? modo.idUsuario : sesion.getSupabaseIdUsuario();
    }

    private String desde() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -DIAS);
        return SDF_DIA.format(c.getTime());
    }

    // ═══ Pintado ═══════════════════════════════════════════════

    private void pintar(List<RegistroToma> olvidos) {
        findViewById(R.id.tv_cargando).setVisibility(View.GONE);
        contenedor.removeAllViews();

        View vacio = findViewById(R.id.empty_olvidos);
        View resumen = findViewById(R.id.tv_resumen);

        if (olvidos.isEmpty()) {
            vacio.setVisibility(View.VISIBLE);
            resumen.setVisibility(View.GONE);
            return;
        }
        vacio.setVisibility(View.GONE);

        resumen.setVisibility(View.VISIBLE);
        ((TextView) resumen).setText(textoResumen(olvidos.size()));

        LayoutInflater inflater = LayoutInflater.from(this);
        String diaAnterior = "";

        for (RegistroToma t : olvidos) {
            String dia = diaDe(t);
            // Cabecera al cambiar de día. La lista viene ordenada de más
            // reciente a más viejo, así que basta con comparar con la
            // anterior — no hace falta agrupar en un mapa.
            if (!dia.equals(diaAnterior)) {
                contenedor.addView(cabeceraDeDia(dia));
                diaAnterior = dia;
            }
            contenedor.addView(filaDe(inflater, t));
        }
    }

    private String textoResumen(int cuantos) {
        String quien = modo.esDeOtro() ? modo.primerNombre() + " tiene" : "Tenés";
        return cuantos == 1
            ? quien + " 1 toma sin confirmar en los últimos " + DIAS + " días."
                + " Si la tomó y no lo marcó, corregila abajo."
            : quien + " " + cuantos + " tomas sin confirmar en los últimos "
                + DIAS + " días. Las que sí se tomaron se pueden corregir abajo.";
    }

    private TextView cabeceraDeDia(String ymd) {
        TextView tv = new TextView(this);
        tv.setText(nombreDelDia(ymd).toUpperCase(Locale.getDefault()));
        tv.setTextSize(13f);
        tv.setLetterSpacing(0.08f);
        tv.setTextColor(getColor(R.color.ink_3));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        int dp = (int) (getResources().getDisplayMetrics().density);
        lp.topMargin = 12 * dp;
        lp.bottomMargin = 8 * dp;
        tv.setLayoutParams(lp);
        return tv;
    }

    private View filaDe(LayoutInflater inflater, RegistroToma t) {
        View item = inflater.inflate(R.layout.item_olvido, contenedor, false);

        Medicamento med = medPorHorario.get(t.getIdHorario());
        String nombre = med != null && med.getNombre() != null
            ? med.getNombre() : "Medicamento";

        ((TextView) item.findViewById(R.id.olv_nombre)).setText(nombre);

        String detalle = "Estaba programada a las " + horaDe(t);
        if (med != null) detalle += " · " + dosisLegible(med);
        ((TextView) item.findViewById(R.id.olv_detalle)).setText(detalle);

        MaterialButton btn = item.findViewById(R.id.olv_btn_tome);
        btn.setOnClickListener(v -> confirmar(t, nombre, btn, item));
        return item;
    }

    // ═══ Corregir ══════════════════════════════════════════════

    /**
     * Pregunta antes de escribir. No es una confirmación de trámite: acá se
     * está afirmando que una dosis se tomó, y ese dato después alimenta el
     * porcentaje que el médico mira. Marcar una de más por un toque
     * accidental lo ensucia sin que nadie se entere.
     */
    private void confirmar(RegistroToma t, String nombre, MaterialButton btn, View item) {
        String pregunta = modo.esDeOtro()
            ? "¿" + modo.primerNombre() + " tomó " + nombre + " de las "
                + horaDe(t) + " del " + nombreDelDia(diaDe(t)).toLowerCase(Locale.getDefault())
                + "?\n\nVa a quedar registrado que lo marcaste vos."
            : "¿Tomaste " + nombre + " de las " + horaDe(t) + " del "
                + nombreDelDia(diaDe(t)).toLowerCase(Locale.getDefault()) + "?";

        new AlertDialog.Builder(this)
            .setTitle("Confirmar la toma")
            .setMessage(pregunta)
            .setNegativeButton("No", null)
            .setPositiveButton("Sí, la tomó", (d, w) -> escribir(t, btn, item))
            .show();
    }

    private void escribir(RegistroToma t, MaterialButton btn, View item) {
        btn.setEnabled(false);
        btn.setText("Guardando…");

        // Solo se manda "quién" cuando NO es el propio paciente: en la
        // columna, null significa "la confirmó él mismo".
        int quien = modo.esDeOtro() ? sesion.getSupabaseIdUsuario() : 0;

        VimedRepo.confirmarOlvido(t.getId(), quien, new VimedRepo.Cb<Void>() {
            @Override public void onOk(Void v) {
                // La fila no se saca de la lista: verla cambiar de estado en
                // el lugar donde estaba es lo que confirma que el toque hizo
                // algo. Desaparece sola en la próxima carga.
                btn.setVisibility(View.GONE);
                TextView ok = item.findViewById(R.id.olv_resuelto);
                ok.setText(modo.esDeOtro()
                    ? "Corregida por vos ✓" : "Marcada como tomada ✓");
                ok.setVisibility(View.VISIBLE);
                item.findViewById(R.id.olv_icon).setVisibility(View.INVISIBLE);
            }
            @Override public void onError(String msg) {
                btn.setEnabled(true);
                btn.setText("Sí, la tomé");
                Toast.makeText(OlvidosActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ═══ Textos ════════════════════════════════════════════════

    private String diaDe(RegistroToma t) {
        String p = t.getFechaHoraProgramada();
        return p != null && p.length() >= 10 ? p.substring(0, 10) : "";
    }

    private String horaDe(RegistroToma t) {
        String p = t.getFechaHoraProgramada();
        return p != null && p.length() >= 16 ? p.substring(11, 16) : "";
    }

    /** "Hoy", "Ayer", o "martes 12 de agosto" para lo más viejo. */
    private String nombreDelDia(String ymd) {
        if (ymd.isEmpty()) return "Sin fecha";

        String hoy = SDF_DIA.format(new Date());
        if (ymd.equals(hoy)) return "Hoy";

        Calendar ayer = Calendar.getInstance();
        ayer.add(Calendar.DAY_OF_YEAR, -1);
        if (ymd.equals(SDF_DIA.format(ayer.getTime()))) return "Ayer";

        try {
            Date d = SDF_DIA.parse(ymd);
            return new SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return ymd;
        }
    }

    /** "50 mg" — misma forma que en el resto de la app. */
    private String dosisLegible(Medicamento m) {
        float d = m.getDosis();
        String num = d == Math.floor(d)
            ? String.valueOf((int) d)
            : String.valueOf(d);
        return num + " " + (m.getUnidad() != null ? m.getUnidad() : "");
    }
}
