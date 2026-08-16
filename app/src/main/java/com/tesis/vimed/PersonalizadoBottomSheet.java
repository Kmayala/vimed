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

public class PersonalizadoBottomSheet extends BottomSheetDialogFragment {

    public interface Callback {
        void onConfirmar(String hora, int intervalo);
    }

    private final String horaActual;
    private final Callback callback;

    public PersonalizadoBottomSheet(String horaActual, Callback callback) {
        this.horaActual = horaActual;
        this.callback = callback;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_bottom_sheet_personalizado, container, false);

        TimePicker tp = view.findViewById(R.id.tp_personalizado);
        tp.setIs24HourView(true);

        // Pre-cargar la hora actual del medicamento
        try {
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

            String hora = String.format("%02d:%02d", tp.getHour(), tp.getMinute());
            if (callback != null) callback.onConfirmar(hora, intervalo);
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
