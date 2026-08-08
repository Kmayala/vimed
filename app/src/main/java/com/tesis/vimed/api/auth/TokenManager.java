package com.tesis.vimed.api.auth;

import android.content.Context;
import android.util.Log;

import com.tesis.vimed.SessionManager;

import java.io.IOException;

import retrofit2.Response;

/**
 * Mantiene vivo el access_token de Supabase.
 *
 * Los tokens duran ~1 hora. Antes, cuando vencía, el cliente caía al
 * anon key y — con RLS estricto — todo devolvía 401. Acá lo renovamos
 * usando el refresh_token guardado en {@link SessionManager}.
 *
 * refrescarSync() es BLOQUEANTE: se llama desde el interceptor de OkHttp,
 * que ya corre en un hilo de fondo.
 */
public final class TokenManager {

    private static final String TAG = "TokenManager";

    /** Evita que varias requests simultáneas disparen refrescos en paralelo. */
    private static final Object LOCK = new Object();

    private TokenManager() {}

    /**
     * Devuelve un access_token válido, renovándolo si hace falta.
     * @return el token, o null si no hay sesión o el refresh falló.
     */
    public static String tokenValido(Context ctx) {
        SessionManager s = new SessionManager(ctx);

        if (s.hasValidAccessToken()) return s.getAccessToken();

        String refresh = s.getRefreshToken();
        if (refresh == null) return null;

        synchronized (LOCK) {
            // Otra request pudo haberlo renovado mientras esperábamos el lock
            SessionManager s2 = new SessionManager(ctx);
            if (s2.hasValidAccessToken()) return s2.getAccessToken();
            return refrescarSync(ctx, s2, refresh);
        }
    }

    private static String refrescarSync(Context ctx, SessionManager s, String refreshToken) {
        try {
            Response<AuthPayloads.AuthResponse> r = SupabaseAuthClient.getService()
                .refresh(new AuthPayloads.RefreshRequest(refreshToken))
                .execute();

            if (r.isSuccessful() && r.body() != null && r.body().accessToken != null) {
                AuthPayloads.AuthResponse body = r.body();
                long expiraEn = body.expiresAt > 0
                    ? body.expiresAt
                    : (System.currentTimeMillis() / 1000) + body.expiresIn;

                s.saveAuthTokens(
                    body.user != null ? body.user.id : s.getAuthUserId(),
                    body.accessToken,
                    body.refreshToken != null ? body.refreshToken : refreshToken,
                    expiraEn);

                Log.d(TAG, "Token renovado, vence en " + body.expiresIn + "s");
                return body.accessToken;
            }

            // 400/401 acá = refresh_token inválido o revocado: hay que re-loguear
            Log.w(TAG, "Refresh rechazado (código " + r.code() + ")");
            return null;

        } catch (IOException e) {
            Log.w(TAG, "Sin red para renovar el token: " + e.getMessage());
            return null;
        }
    }
}
