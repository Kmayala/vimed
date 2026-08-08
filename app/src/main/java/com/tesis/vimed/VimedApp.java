package com.tesis.vimed;

import android.app.Application;

import com.tesis.vimed.api.SupabaseClient;

/**
 * Punto único de inicialización de la app.
 * Registrada en AndroidManifest.xml como android:name=".VimedApp".
 */
public class VimedApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // El cliente Supabase necesita un context para leer el access_token
        // del SessionManager en cada request.
        SupabaseClient.init(this);
    }
}
