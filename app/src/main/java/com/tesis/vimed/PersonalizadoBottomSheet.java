package com.tesis.vimed;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TimePicker;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

/**
 * Hoja inferior para elegir hora de inicio e intervalo a mano.
 *
 * OJO con cómo se crea y cómo devuelve el resultado: el sistema puede
 * destruir y recrear este fragment solo (rotar la pantalla, o matar la
 * actividad en segundo plano por memoria). Cuando lo recrea, lo hace con
 * el constructor VACÍO, así que nada que se pase por constructor
 * sobrevive: los datos de entrada van por {@link #newInstance} en los
 * arguments, y la respuesta sale por setFragmentResult en vez de un
 * callback en memoria. Con un callback, después de recrearse la hoja
 * quedaba muda: la persona tocaba "Confirmar" y no pasaba nada.
 */
public class PersonalizadoBottomSheet extends BottomSheetDialogFragment {

    /** Clave con la que la actividad escucha el resultado. */
    public static final String REQUEST_KEY = "personalizado_horario";

    /** "HH:mm" elegido en el tambor. */
    public static final String RESULT_HORA = "hora";
    /** Cada cuántas horas se repite la toma (1..24). */
    public static final String RESULT_INTERVALO = "intervalo";

    private static final String ARG_HORA_ACTUAL = "hora_actual";

    /** Necesario para que el sistema pueda recrear el fragment. */
    public PersonalizadoBottomSheet() { }

    public static PersonalizadoBottomSheet newInstance(String horaActual) {
        PersonalizadoBottomSheet hoja = new PersonalizadoBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_HORA_ACTUAL, horaActual);
        hoja.setArguments(args);
        return hoja;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_bottom_sheet_personalizado, container, false);

        TimePicker tp = view.findViewById(R.id.tp_personalizado);
        tp.setIs24HourView(true);

        // Pre-cargar la hora actual del medicamento
        try {
            String horaActual = requireArguments().getString(ARG_HORA_ACTUAL);
            String[] partes = horaActual.split(":");
            tp.setHour(Integer.parseInt(partes[0]));
            tp.setMinute(Integer.parseInt(partes[1]));
        } catch (Exception ignored) {
            tp.setHour(8);
            tp.setMinute(0);
        }

        TextInputLayout tilIntervalo = view.findViewById(R.id.til_intervalo);
        TextInputEditText etIntervalo = view.findViewById(R.id.et_intervalo_horas);
        Button btnConfirmar = view.findViewById(R.id.btn_confirmar_personalizado);

        btnConfirmar.setOnClickListener(v -> {
            String intervaloStr = etIntervalo.getText() != null
                ? etIntervalo.getText().toString().trim() : "";

            if (intervaloStr.isEmpty()) {
                tilIntervalo.setError(getString(R.string.error_empty_field));
                return;
            }

            int intervalo;
            try {
                intervalo = Integer.parseInt(intervaloStr);
                if (intervalo < 1 || intervalo > 24) {
                    tilIntervalo.setError("Debe ser entre 1 y 24 horas");
                    return;
                }
            } catch (NumberFormatException e) {
                tilIntervalo.setError("Número inválido");
                return;
            }

            Bundle resultado = new Bundle();
            resultado.putString(RESULT_HORA,
                String.format(Locale.getDefault(), "%02d:%02d", tp.getHour(), tp.getMinute()));
            resultado.putInt(RESULT_INTERVALO, intervalo);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, resultado);
            dismiss();
        });

        return view;
    }

    /**
     * Ajustes de la ventana del diálogo. Sin esto la hoja abría a media
     * altura y con el teclado encima: el campo de las horas y el botón de
     * confirmar quedaban fuera de la pantalla, sin forma de llegar a ellos.
     */
    @Override
    public void onStart() {
        super.onStart();

        Dialog dialogo = getDialog();
        if (dialogo == null) return;

        Window ventana = dialogo.getWindow();
        if (ventana != null) {
            // STATE_HIDDEN: el teclado no aparece hasta que la persona toca
            // el campo. ADJUST_RESIZE: cuando aparece, la hoja se achica en
            // vez de quedar tapada — y el NestedScrollView deja llegar al
            // botón.
            ventana.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        View hoja = dialogo.findViewById(
            com.google.android.material.R.id.design_bottom_sheet);
        if (hoja != null) {
            BottomSheetBehavior<View> comportamiento = BottomSheetBehavior.from(hoja);
            comportamiento.setState(BottomSheetBehavior.STATE_EXPANDED);
            comportamiento.setSkipCollapsed(true);
        }
    }
}
