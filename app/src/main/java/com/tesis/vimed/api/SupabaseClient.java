package com.tesis.vimed.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tesis.vimed.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Punto de entrada al backend Supabase (PostgREST).
 *
 * Las credenciales se leen desde local.properties → BuildConfig,
 * por eso NO aparecen en el repo. Configurá en local.properties:
 *
 *   SUPABASE_URL=https://xxxx.supabase.co
 *   SUPABASE_ANON_KEY=eyJhbGciOi...
 *
 * Llamar siempre vía {@link #getService()}.
 */
public final class SupabaseClient {

    private static final String SCHEMA = "vimed";

    private static volatile SupabaseService service;

    private SupabaseClient() {}

    public static SupabaseService getService() {
        if (service == null) {
            synchronized (SupabaseClient.class) {
                if (service == null) {
                    service = build();
                }
            }
        }
        return service;
    }

    private static SupabaseService build() {
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(BuildConfig.DEBUG
            ? HttpLoggingInterceptor.Level.BODY
            : HttpLoggingInterceptor.Level.NONE);

        Interceptor authHeaders = chain -> {
            Request original = chain.request();
            Request.Builder b = original.newBuilder()
                .header("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                // El esquema vimed está fuera de public — hay que decírselo a PostgREST
                .header("Accept-Profile",  SCHEMA)
                .header("Content-Profile", SCHEMA);

            if (original.header("Content-Type") == null
                    && !"GET".equalsIgnoreCase(original.method())
                    && !"DELETE".equalsIgnoreCase(original.method())) {
                b.header("Content-Type", "application/json");
            }
            // Por defecto pedir que PostgREST devuelva la fila insertada/actualizada
            if (original.header("Prefer") == null) {
                b.header("Prefer", "return=representation");
            }
            return chain.proceed(b.build());
        };

        OkHttpClient http = new OkHttpClient.Builder()
            .addInterceptor(authHeaders)
            .addInterceptor(logger)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

        String base = BuildConfig.SUPABASE_URL;
        if (!base.endsWith("/")) base = base + "/";

        // Traduce automáticamente idUsuario ↔ id_usuario, stockActual ↔ stock_actual, etc.
        // Para el PK por tabla (id_usuario, id_medicamento…) usar @SerializedName en el modelo.
        Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

        return new Retrofit.Builder()
            .baseUrl(base + "rest/v1/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SupabaseService.class);
    }
}
