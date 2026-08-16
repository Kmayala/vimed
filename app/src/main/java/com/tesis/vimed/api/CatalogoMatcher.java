package com.tesis.vimed.api;

import com.tesis.vimed.models.CatalogoMedicamento;

import java.util.List;
import java.util.Locale;

/**
 * Encuentra a qué entrada del catálogo corresponde un nombre escrito a mano.
 *
 * Vive aparte porque lo necesitan dos chequeos distintos de seguridad
 * —interacciones y dosis— y la heurística tiene que ser LA MISMA en los
 * dos: si cada uno matcheara distinto, la app podría avisar de una
 * interacción de un medicamento y comparar la dosis contra otro.
 */
public final class CatalogoMatcher {

    private CatalogoMatcher() {}

    /**
     * Match case-insensitive: el nombre del usuario contiene el
     * nombre_comercial del catálogo, o viceversa, o coincide con el
     * principio_activo. Prioriza lo más específico (comercial > principio).
     *
     * @return la entrada del catálogo, o null si no hay forma de saber de
     *         qué medicamento se trata. Null NO significa "está todo bien":
     *         significa que no podemos opinar.
     */
    public static CatalogoMedicamento buscar(String tipeado,
                                             List<CatalogoMedicamento> catalogo) {
        if (tipeado == null || catalogo == null) return null;
        String norm = tipeado.trim().toLowerCase(Locale.ROOT);
        if (norm.isEmpty()) return null;

        CatalogoMedicamento porComercial = null;
        CatalogoMedicamento porPrincipio = null;

        for (CatalogoMedicamento m : catalogo) {
            String nc = m.getNombreComercial() != null
                ? m.getNombreComercial().toLowerCase(Locale.ROOT) : "";
            String pa = m.getPrincipioActivo() != null
                ? m.getPrincipioActivo().toLowerCase(Locale.ROOT) : "";

            if (!nc.isEmpty() && (norm.contains(nc) || nc.contains(norm))) {
                porComercial = m;
                break;   // el primer match comercial gana
            }
            if (porPrincipio == null && !pa.isEmpty()
                    && (norm.contains(pa) || pa.contains(norm))) {
                porPrincipio = m;
            }
        }
        return porComercial != null ? porComercial : porPrincipio;
    }

    /** Igual que {@link #buscar}, pero devuelve solo el id. */
    public static Integer buscarId(String tipeado, List<CatalogoMedicamento> catalogo) {
        CatalogoMedicamento m = buscar(tipeado, catalogo);
        return m != null ? m.getIdCatalogo() : null;
    }
}
