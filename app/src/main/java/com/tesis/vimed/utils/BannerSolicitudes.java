package com.tesis.vimed.utils;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.tesis.vimed.R;
import com.tesis.vimed.VincularFamiliarActivity;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.Vinculacion;

import java.util.List;

/**
 * Pinta el aviso de "tenés una solicitud de vínculo esperando".
 *
 * Vive acá y no en cada Activity porque el home del adulto mayor y el del
 * cuidador lo necesitan igual: los dos pueden recibir una solicitud, y en
 * los dos la pantalla para responderla está escondida detrás del menú de
 * perfil. Sin este aviso, una solicitud se queda sin responder para siempre
 * y quien la mandó no entiende por qué no ve nada.
 */
public final class BannerSolicitudes {

    private BannerSolicitudes() {}

    /**
     * Consulta las solicitudes pendientes y muestra u oculta el banner.
     * Conviene llamarlo desde onResume: al volver de responder una, el
     * aviso tiene que desaparecer solo.
     */
    public static void revisar(Activity activity) {
        View banner = activity.findViewById(R.id.banner_solicitudes);
        if (banner == null) return;

        // Se oculta antes de preguntar: si quedó visible de la vez anterior
        // y ya no hay nada pendiente, no puede seguir en pantalla mientras
        // viaja la consulta.
        banner.setVisibility(View.GONE);

        VimedRepo.listarSolicitudesPendientes(activity,
            new VimedRepo.Cb<List<Vinculacion>>() {
                @Override public void onOk(List<Vinculacion> pendientes) {
                    if (activity.isFinishing() || pendientes.isEmpty()) return;

                    TextView titulo = activity.findViewById(
                        R.id.tv_banner_solicitudes_titulo);
                    titulo.setText(pendientes.size() == 1
                        ? "Tenés una solicitud de vínculo"
                        : "Tenés " + pendientes.size() + " solicitudes de vínculo");

                    banner.setVisibility(View.VISIBLE);
                    banner.setOnClickListener(v -> activity.startActivity(
                        new Intent(activity, VincularFamiliarActivity.class)));
                }

                @Override public void onError(String msg) {
                    // Sin red no se avisa nada. El banner ya está oculto.
                }
            });
    }
}
