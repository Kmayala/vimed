package com.tesis.vimed.api.auth;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.tesis.vimed.BuildConfig;

import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Pide a Google el token de identidad, sin salir de la app.
 *
 * Credential Manager muestra la hoja de cuentas del sistema por encima de
 * la pantalla, así que la persona nunca sale a un navegador. Lo que
 * devuelve es un id_token firmado por Google, que después se canjea por
 * una sesión de Supabase (ver {@link SupabaseAuthService#signInConIdToken}).
 *
 * SOBRE EL NONCE. Se genera un número al azar y se le manda a Google el
 * HASH; Supabase recibe el original y compara. Sirve para que un token
 * interceptado no se pueda reutilizar: solo vale para la petición que lo
 * generó. Por eso el resultado devuelve los dos valores juntos —el token
 * y el nonce sin hashear— y hay que mandarlos de a pares.
 *
 * Para que esto funcione, Google tiene que reconocer a la app: paquete
 * com.tesis.vimed más la huella SHA-1 de la firma, registrados en el
 * cliente OAuth de Android. Si no coinciden, falla con un error de
 * configuración que no dice cuál de los dos está mal.
 */
public final class LoginGoogle {

    public interface Callback {
        /** @param nonce el valor SIN hashear, para mandárselo a Supabase. */
        void onToken(String idToken, String nonce);

        /** @param cancelado true si la persona cerró la hoja de cuentas. */
        void onError(String mensaje, boolean cancelado);
    }

    private LoginGoogle() {}

    public static void pedirToken(Activity activity, Callback cb) {
        String nonce = UUID.randomUUID().toString();
        String nonceHasheado;
        try {
            nonceHasheado = sha256(nonce);
        } catch (Exception e) {
            cb.onError("No se pudo preparar el inicio de sesión", false);
            return;
        }

        GetGoogleIdOption opcion = new GetGoogleIdOption.Builder()
            // false: muestra TODAS las cuentas del celular, no solo las que
            // ya usaron esta app. En el primer login no hay ninguna
            // "autorizada", así que con true la hoja saldría vacía.
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonceHasheado)
            .build();

        GetCredentialRequest pedido = new GetCredentialRequest.Builder()
            .addCredentialOption(opcion)
            .build();

        CredentialManager.create(activity).getCredentialAsync(
            activity, pedido, new CancellationSignal(),
            Executors.newSingleThreadExecutor(),
            new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                @Override
                public void onResult(GetCredentialResponse respuesta) {
                    try {
                        GoogleIdTokenCredential cred = GoogleIdTokenCredential
                            .createFrom(respuesta.getCredential().getData());
                        activity.runOnUiThread(
                            () -> cb.onToken(cred.getIdToken(), nonce));
                    } catch (Exception e) {
                        activity.runOnUiThread(() -> cb.onError(
                            "Google devolvió una credencial inesperada", false));
                    }
                }

                @Override
                public void onError(@NonNull GetCredentialException e) {
                    // NoCredentialException: el celular no tiene ninguna
                    // cuenta de Google configurada. Es el caso más común y
                    // no es una falla de la app, así que se explica aparte.
                    String tipo = e.getClass().getSimpleName();
                    boolean cancelado = tipo.contains("Cancel");
                    String msg = tipo.contains("NoCredential")
                        ? "No hay ninguna cuenta de Google en este celular. "
                            + "Agregala desde Ajustes o entrá con tu correo."
                        : "No se pudo entrar con Google: " + e.getMessage();
                    activity.runOnUiThread(() -> cb.onError(msg, cancelado));
                }
            });
    }

    private static String sha256(String texto) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest(texto.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
