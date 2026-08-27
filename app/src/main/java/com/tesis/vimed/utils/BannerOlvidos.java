package com.tesis.vimed.utils;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import com.tesis.vimed.OlvidosActivity;
import com.tesis.vimed.R;
import com.tesis.vimed.api.VimedRepo;
import com.tesis.vimed.models.RegistroToma;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Aviso de "tenés tomas sin confirmar esta semana" para el home del adulto
 * mayor, y puerta de entrada a {@link OlvidosActivity}.
 *
 * Existe por lo mismo que {@link BannerSolicitudes}: sin un aviso en el home,
 * a la pantalla de olvidos no llega nadie. Y a diferencia de una solicitud de
 * vínculo, esto no molesta a nadie más — el costo de no verlo lo paga la
 * propia adherencia, que queda contando como olvidos dosis que sí se tomaron.
 *
 * SE MIRA LA SEMANA Y NO EL MES. Corregir una toma requiere acordarse de si
 * se tomó, y a los diez días nadie se acuerda. Ofrecer marcarla igual sería
 * pedir que adivinen, y una adivinanza registrada como dato es peor que un
 * olvido registrado como olvido.
 */
public final class BannerOlvidos {

    /** Igual que el de OlvidosActivity: las dos pantallas cuentan lo mismo. */
    private static final int DIAS = 7;

    private BannerOlvidos() {}

    /** Conviene llamarlo desde onResume: al corregir una, el aviso se ajusta. */
    public static void revisar(Activity activity) {
        View banner = activity.findViewById(R.id.banner_olvidos);
        if (banner == null) return;

        // Se oculta antes de preguntar: si quedó visible de la vez anterior y
        // ya no hay nada pendiente, no puede seguir en pantalla mientras
        // viaja la consulta.
        banner.setVisibility(View.GONE);

        Calendar desde = Calendar.getInstance();
        desde.add(Calendar.DAY_OF_YEAR, -DIAS);
        String desdeYMD = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(desde.getTime());

        VimedRepo.listarOlvidos(activity, -1, desdeYMD,
            new VimedRepo.Cb<List<RegistroToma>>() {
                @Override public void onOk(List<RegistroToma> olvidos) {
                    if (activity.isFinishing() || olvidos.isEmpty()) return;

                    TextView titulo = activity.findViewById(R.id.tv_banner_olvidos_titulo);
                    titulo.setText(olvidos.size() == 1
                        ? "Quedó 1 toma sin confirmar"
                        : "Quedaron " + olvidos.size() + " tomas sin confirmar");

                    banner.setVisibility(View.VISIBLE);
                    banner.setOnClickListener(v -> activity.startActivity(
                        new android.content.Intent(activity, OlvidosActivity.class)));
                }

                @Override public void onError(String msg) {
                    // Sin red no se avisa nada. El banner ya está oculto.
                }
            });
    }
}
