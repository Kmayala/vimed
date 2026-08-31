package com.tesis.vimed.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Búsqueda de lugares contra Nominatim, el buscador de OpenStreetMap.
 *
 * POR QUÉ NOMINATIM Y NO GOOGLE PLACES. Places pide una clave de API con
 * facturación asociada y cobra por búsqueda; para una app de tesis eso
 * significa una tarjeta de crédito atada a algo que puede quedar
 * publicado. Nominatim es gratis, no pide clave y cubre de sobra lo que
 * hace falta acá: encontrar un hospital o una calle por su nombre.
 *
 * REGLAS DE USO. La política de Nominatim exige identificar la app con un
 * User-Agent propio y no bombardearlo a consultas. Lo primero se hace en
 * el interceptor de abajo; lo segundo lo resuelve la pantalla, que solo
 * consulta cuando la persona aprieta "Buscar" y no en cada tecla.
 */
public final class NominatimClient {

    private static final String BASE = "https://nominatim.openstreetmap.org/";

    /** Nominatim rechaza los pedidos sin un User-Agent que identifique a la app. */
    private static final String USER_AGENT =
        "Vimed/1.0 (app de adherencia a la medicación; contacto vía la tesis)";

    private static Service service;

    private NominatimClient() {}

    public interface Service {
        /** Busca lugares por texto libre. */
        @GET("search?format=json&addressdetails=1&limit=8")
        Call<List<Lugar>> buscar(
            @Query("q") String consulta,
            @Query("accept-language") String idioma,
            /* Sesga los resultados hacia el país, sin excluir al resto. */
            @Query("countrycodes") String paises
        );

        /** El camino inverso: de un punto del mapa a una dirección escrita. */
        @GET("reverse?format=json&zoom=18&addressdetails=1")
        Call<Lugar> direccionDe(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("accept-language") String idioma
        );
    }

    public static Service get() {
        if (service != null) return service;

        OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(new Interceptor() {
                @Override
                public okhttp3.Response intercept(Chain chain) throws java.io.IOException {
                    return chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build());
                }
            })
            .build();

        service = new Retrofit.Builder()
            .baseUrl(BASE)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Service.class);

        return service;
    }

    /** Un resultado de búsqueda. */
    public static class Lugar {
        /** Nombre completo: "Hospital de Clínicas, San Lorenzo, Paraguay". */
        @SerializedName("display_name")
        public String nombreCompleto;

        /** Vienen como texto aunque sean números; así los manda Nominatim. */
        public String lat;
        public String lon;

        public double latitud()  { return aDouble(lat); }
        public double longitud() { return aDouble(lon); }

        private static double aDouble(String s) {
            try { return Double.parseDouble(s); }
            catch (Exception e) { return 0; }
        }

        /**
         * La primera parte del nombre completo, que es la que identifica al
         * lugar. Mostrar los cinco niveles administrativos en el título
         * hace que todos los resultados se vean iguales.
         */
        public String titulo() {
            if (nombreCompleto == null || nombreCompleto.isEmpty()) return "Sin nombre";
            int coma = nombreCompleto.indexOf(',');
            return coma > 0 ? nombreCompleto.substring(0, coma).trim() : nombreCompleto;
        }

        /** El resto, para mostrar debajo del título. */
        public String detalle() {
            if (nombreCompleto == null) return "";
            int coma = nombreCompleto.indexOf(',');
            return coma > 0 ? nombreCompleto.substring(coma + 1).trim() : "";
        }
    }
}
