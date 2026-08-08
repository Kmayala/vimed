package com.tesis.vimed.api;

import android.content.Context;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tesis.vimed.BuildConfig;
import com.tesis.vimed.SessionManager;

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
 * IMPORTANTE: hay que llamar {@link #init(Context)} una sola vez
 * al arrancar la app (lo hace {@link com.tesis.vimed.VimedApp}).
 * Sin eso, las requests caen al anon key y RLS las va a rechazar
 * para todas las tablas que filtran por auth.uid().
 *
 * Estrategia del interceptor:
 *   - Si hay un access_token de sesión vigente → lo usa (auth.uid() funciona)
 *   - Si no, cae al anon key (solo sirve para catálogos públicos)
 */
public final class SupabaseClient {

    private static volatile SupabaseService service;
    private static Context appContext;

    private SupabaseClient() {}

    /** Llamar una sola vez desde Application.onCreate(). */
    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
    }

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
            String bearer = resolverBearerToken();

            Request.Builder b = original.newBuilder()
                .header("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + bearer);

            if (original.header("Content-Type") == null
                    && !"GET".equalsIgnoreCase(original.method())
                    && !"DELETE".equalsIgnoreCase(original.method())) {
                b.header("Content-Type", "application/json");
            }
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

    /**
     * Devuelve un access_token válido, renovándolo con el refresh_token
     * si venció. Cae al anon key solo si no hay sesión (por ejemplo para
     * leer el catálogo antes de loguearse).
     *
     * Ojo: con RLS estricto el anon key no puede leer NADA del usuario,
     * así que un fallback acá se traduce en 401 río abajo — es lo
     * correcto: significa que hay que volver a iniciar sesión.
     */
    private static String resolverBearerToken() {
        if (appContext == null) return BuildConfig.SUPABASE_ANON_KEY;
        String token = com.tesis.vimed.api.auth.TokenManager.tokenValido(appContext);
        return token != null ? token : BuildConfig.SUPABASE_ANON_KEY;
    }
}
