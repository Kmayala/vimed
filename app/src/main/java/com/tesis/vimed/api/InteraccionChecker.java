package com.tesis.vimed.api;

import com.tesis.vimed.models.CatalogoMedicamento;
import com.tesis.vimed.models.InteraccionCatalogo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Chequea si el nuevo medicamento que quiere agregar el usuario
 * choca con alguno que ya tiene cargado. Usa las tablas
 * catalogo_medicamentos e interacciones_catalogo de Supabase.
 *
 * Estrategia:
 *   1. Traer el catálogo entero (~38 filas, una sola llamada)
 *   2. Matchear los nombres (nuevo + existentes) contra el catálogo
 *      por substring case-insensitive contra nombre_comercial y
 *      principio_activo
 *   3. Si el nuevo NO matchea → no podemos chequear, devolver vacío
 *   4. Traer interacciones que involucren al nuevo id_catalogo
 *   5. Filtrar solo las que también involucran algún existente
 */
public final class InteraccionChecker {

    private InteraccionChecker() {}

    /** Resultado enriquecido: la interacción + contra qué med existente choca. */
    public static class Hallazgo {
        public final String medContraChoca;   // nombre_comercial del catálogo
        public final String nivelRiesgo;      // "alto" | "medio" | "bajo"
        public final String descripcion;

        public Hallazgo(String medContraChoca, String nivelRiesgo, String descripcion) {
            this.medContraChoca = medContraChoca;
            this.nivelRiesgo = nivelRiesgo;
            this.descripcion = descripcion;
        }

        public boolean esAlto()  { return "alto".equalsIgnoreCase(nivelRiesgo); }
        public boolean esMedio() { return "medio".equalsIgnoreCase(nivelRiesgo); }
    }

    public interface Callback0 {
        void onResult(List<Hallazgo> hallazgos);
        void onError(String msg);
    }

    /**
     * Chequea interacciones.
     * @param nombreNuevo Nombre tal como lo tipeó el usuario en el paso 1.
     * @param nombresExistentes Nombres de los medicamentos que el usuario ya tiene.
     * @param cb Se llama en el hilo principal.
     */
    public static void chequear(String nombreNuevo,
                                List<String> nombresExistentes,
                                Callback0 cb) {
        if (nombreNuevo == null || nombreNuevo.trim().isEmpty()
                || nombresExistentes == null || nombresExistentes.isEmpty()) {
            cb.onResult(new ArrayList<>());
            return;
        }

        // Paso 1: catálogo completo
        SupabaseClient.getService()
            .getCatalogo("eq.true", "nombre_comercial.asc")
            .enqueue(new Callback<List<CatalogoMedicamento>>() {
                @Override
                public void onResponse(Call<List<CatalogoMedicamento>> c,
                                       Response<List<CatalogoMedicamento>> r) {
                    if (!r.isSuccessful() || r.body() == null) {
                        cb.onError("No se pudo obtener el catálogo (código " + r.code() + ")");
                        return;
                    }
                    procesarConCatalogo(nombreNuevo, nombresExistentes, r.body(), cb);
                }

                @Override
                public void onFailure(Call<List<CatalogoMedicamento>> c, Throwable t) {
                    cb.onError("Sin conexión al chequear interacciones: " + t.getMessage());
                }
            });
    }

    private static void procesarConCatalogo(String nombreNuevo,
                                            List<String> nombresExistentes,
                                            List<CatalogoMedicamento> catalogo,
                                            Callback0 cb) {
        // Paso 2: matchear nombres
        Integer idNuevo = matchearNombre(nombreNuevo, catalogo);
        if (idNuevo == null) {
            // El nuevo med no está en el catálogo → no podemos chequear nada
            cb.onResult(new ArrayList<>());
            return;
        }

        // Map de id_catalogo → nombre_comercial (para armar el mensaje)
        Map<Integer, String> nombrePorId = new HashMap<>();
        for (CatalogoMedicamento m : catalogo) {
            nombrePorId.put(m.getIdCatalogo(), m.getNombreComercial());
        }

        Set<Integer> idsExistentes = new HashSet<>();
        for (String nombreExist : nombresExistentes) {
            Integer id = matchearNombre(nombreExist, catalogo);
            if (id != null && id != idNuevo) idsExistentes.add(id);
        }
        if (idsExistentes.isEmpty()) {
            cb.onResult(new ArrayList<>());
            return;
        }

        // Paso 3: query interacciones donde participa idNuevo
        String orFilter = "(id_catalogo_a.eq." + idNuevo
                        + ",id_catalogo_b.eq." + idNuevo + ")";

        SupabaseClient.getService()
            .getInteraccionesDeMed(orFilter)
            .enqueue(new Callback<List<InteraccionCatalogo>>() {
                @Override
                public void onResponse(Call<List<InteraccionCatalogo>> c,
                                       Response<List<InteraccionCatalogo>> r) {
                    if (!r.isSuccessful() || r.body() == null) {
                        cb.onError("No se pudieron obtener las interacciones (código " + r.code() + ")");
                        return;
                    }
                    List<Hallazgo> hallazgos = new ArrayList<>();
                    for (InteraccionCatalogo i : r.body()) {
                        int otro = i.getIdCatalogoA() == idNuevo
                            ? i.getIdCatalogoB()
                            : i.getIdCatalogoA();
                        if (idsExistentes.contains(otro)) {
                            String nombreOtro = nombrePorId.getOrDefault(otro, "otro medicamento");
                            hallazgos.add(new Hallazgo(nombreOtro, i.getNivelRiesgo(), i.getDescripcion()));
                        }
                    }
                    cb.onResult(hallazgos);
                }

                @Override
                public void onFailure(Call<List<InteraccionCatalogo>> c, Throwable t) {
                    cb.onError("Sin conexión al chequear interacciones: " + t.getMessage());
                }
            });
    }

    /**
     * Match case-insensitive: el nombre del usuario contiene el nombre_comercial
     * del catálogo, o viceversa, o coincide con el principio_activo.
     * Prioriza matches más específicos (nombre_comercial > principio_activo).
     */
    private static Integer matchearNombre(String tipeado, List<CatalogoMedicamento> catalogo) {
        if (tipeado == null) return null;
        String norm = tipeado.trim().toLowerCase(Locale.ROOT);
        if (norm.isEmpty()) return null;

        Integer matchComercial = null;
        Integer matchPrincipio = null;

        for (CatalogoMedicamento m : catalogo) {
            String nc = m.getNombreComercial() != null
                ? m.getNombreComercial().toLowerCase(Locale.ROOT) : "";
            String pa = m.getPrincipioActivo() != null
                ? m.getPrincipioActivo().toLowerCase(Locale.ROOT) : "";

            // Nombre comercial: match si uno contiene al otro
            if (!nc.isEmpty() && (norm.contains(nc) || nc.contains(norm))) {
                matchComercial = m.getIdCatalogo();
                break; // el primer match comercial gana
            }
            // Principio activo: match si el nombre del usuario contiene el principio
            if (matchPrincipio == null && !pa.isEmpty()
                    && (norm.contains(pa) || pa.contains(norm))) {
                matchPrincipio = m.getIdCatalogo();
            }
        }
        return matchComercial != null ? matchComercial : matchPrincipio;
    }
}
