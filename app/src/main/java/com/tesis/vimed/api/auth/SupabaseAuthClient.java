package com.tesis.vimed.api.auth;

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
 * Cliente para los endpoints /auth/v1 de Supabase.
 *
 * Separado del {@link com.tesis.vimed.api.SupabaseClient} porque:
 *   - Base URL distinta (/auth/v1/ vs /rest/v1/)
 *   - No usa Accept-Profile (no es PostgREST)
 *   - El bearer token cambia según el endpoint (no se puede hardcodear)
 *
 * Uso:
 *   AuthPayloads.SignInRequest req = new AuthPayloads.SignInRequest(email, password);
 *   SupabaseAuthClient.getService().signIn(req).enqueue(...);
 */
public final class SupabaseAuthClient {

    private static volatile SupabaseAuthService service;

    private SupabaseAuthClient() {}

    public static SupabaseAuthService getService() {
        if (service == null) {
            synchronized (SupabaseAuthClient.class) {
                if (service == null) service = build();
            }
        }
        return service;
    }

    private static SupabaseAuthService build() {
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(BuildConfig.DEBUG
            ? HttpLoggingInterceptor.Level.BODY
            : HttpLoggingInterceptor.Level.NONE);

        Interceptor apiKeyHeader = chain -> {
            Request original = chain.request();
            Request.Builder b = original.newBuilder()
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY);

            // Si el endpoint no trajo Authorization propio (signin/signup/recover),
            // usar el anon key como Bearer — Supabase Auth lo requiere para que
            // gotrue acepte la request.
            if (original.header("Authorization") == null) {
                b.header("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);
            }
            if (original.header("Content-Type") == null
                    && !"GET".equalsIgnoreCase(original.method())) {
                b.header("Content-Type", "application/json");
            }
            return chain.proceed(b.build());
        };

        OkHttpClient http = new OkHttpClient.Builder()
            .addInterceptor(apiKeyHeader)
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
            .baseUrl(base + "auth/v1/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SupabaseAuthService.class);
    }
}
