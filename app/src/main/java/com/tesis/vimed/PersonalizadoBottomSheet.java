package com.tesis.vimed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TimePicker;

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
}
