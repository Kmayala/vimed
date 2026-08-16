package com.tesis.vimed.utils;

import android.app.Activity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tesis.vimed.AppointmentsActivity;
import com.tesis.vimed.ChatbotActivity;
import com.tesis.vimed.CuidadorActivity;
import com.tesis.vimed.DashboardActivity;
import com.tesis.vimed.MainActivity;
import com.tesis.vimed.MedsListActivity;
import com.tesis.vimed.R;

/**
 * La barra de abajo de las pantallas que comparten los dos roles.
 *
 * Antes cada pantalla repetía el mismo bloque de veinte líneas con la
 * misma cadena de if/else. Ahora está una sola vez, que es lo que permite
 * que el modo cuidador —donde "Inicio" va a otra pantalla y cada destino
 * tiene que arrastrar el id del paciente— no haya que enchufarlo tres
 * veces con el riesgo de olvidarse en una.
 */
public final class NavInferior {

    private NavInferior() {}

    /**
     * @param seleccionado id del ítem que corresponde a la pantalla actual.
     * @param cerrarAlNavegar true en las pantallas secundarias, para que la
     *        pila no se llene de copias al ir y venir por la barra. En las
     *        pantallas de inicio va false: cerrarlas dejaría a la persona
     *        sin nada atrás.
     */
    public static void configurar(Activity activity, ModoPaciente modo,
                                  int seleccionado, boolean cerrarAlNavegar) {
        BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
        if (nav == null) return;

        if (modo.esDeOtro()) {
            // Mismos ids, distinto destino de "Inicio". Ver bottom_nav_cuidador.
            nav.getMenu().clear();
            nav.inflateMenu(R.menu.bottom_nav_cuidador);
        }

        nav.setSelectedItemId(seleccionado);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == seleccionado) return true;

            Class<?> destino = destinoDe(id, modo);
            if (destino == null) return false;

            android.content.Intent i = modo.intent(activity, destino);
            if (id == R.id.nav_home) {
                // Volver al inicio reusa la pantalla que ya está abajo en la
                // pila en vez de apilar una copia nueva. Sin esto, ir y
                // venir por la barra deja una fila de inicios y hay que
                // apretar "atrás" una vez por cada vuelta.
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
            activity.startActivity(i);
            activity.overridePendingTransition(0, 0);
            if (cerrarAlNavegar) activity.finish();
            return true;
        });
    }

    private static Class<?> destinoDe(int idItem, ModoPaciente modo) {
        if (idItem == R.id.nav_home) {
            return modo.esDeOtro() ? CuidadorActivity.class : MainActivity.class;
        }
        if (idItem == R.id.nav_meds)         return MedsListActivity.class;
        if (idItem == R.id.nav_appointments) return AppointmentsActivity.class;
        if (idItem == R.id.nav_stats)        return DashboardActivity.class;
        if (idItem == R.id.nav_vita)         return ChatbotActivity.class;
        return null;
    }
}
