package com.tesis.vimed.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.MedicamentoDAO;
import com.tesis.vimed.models.Medicamento;

public class AlarmaReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int idMedicamento = intent.getIntExtra("id_medicamento", -1);
        if (idMedicamento == -1) return;

        DatabaseHelper db = DatabaseHelper.getInstance(context);
        MedicamentoDAO medDAO = new MedicamentoDAO(db);
        Medicamento med = medDAO.buscarPorId(idMedicamento);
        if (med == null) return;

        // Notificación de toma
        String titulo = "Hora de tu medicamento";
        String dosis = med.getDosis() == (int) med.getDosis()
            ? String.valueOf((int) med.getDosis())
            : String.valueOf(med.getDosis());
        String mensaje = med.getNombre() + " — " + dosis + " " + med.getUnidad();

        NotificationHelper.mostrarNotificacion(context, titulo, mensaje, idMedicamento);

        // Reducir stock
        int nuevoStock = med.getStockActual() - 1;
        if (nuevoStock >= 0) {
            medDAO.actualizarStock(idMedicamento, nuevoStock);
        }

        // Alerta si stock bajo
        if (nuevoStock <= med.getStockMinimo()) {
            NotificationHelper.mostrarNotificacion(context,
                "Stock bajo — " + med.getNombre(),
                "Quedan " + nuevoStock + " unidades. Acordate de comprar más.",
                idMedicamento + 10000);
        }
    }
}
