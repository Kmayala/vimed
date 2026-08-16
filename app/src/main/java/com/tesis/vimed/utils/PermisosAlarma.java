package com.tesis.vimed.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Todo lo que el sistema puede impedirle a la alarma, en un solo lugar.
 *
 * Una alarma de medicación tiene que sonar con la pantalla bloqueada y la
 * app cerrada, que es justo el escenario que las versiones nuevas de
 * Android y —sobre todo— las capas de Huawei, Oppo, vivo y Xiaomi
 * restringen por defecto. Cada fabricante lo llama distinto y lo esconde
 * en un lugar distinto, así que sin una pantalla que lo revise, la
 * persona no tiene forma de saber por qué no le sonó.
 *
 * Nada de esto se puede activar desde el código: son permisos que solo
 * concede la persona. Lo que sí podemos es DETECTAR qué falta y llevarla
 * directo al ajuste correcto.
 */
public final class PermisosAlarma {

    private PermisosAlarma() {}

    /** Un requisito: qué es, si está cumplido y cómo se arregla. */
    public static class Requisito {
        public final String titulo;
        public final String detalle;
        public final boolean cumplido;
        /** null cuando no hay pantalla de ajustes a la que llevar. */
        public final Runnable abrirAjuste;

        Requisito(String titulo, String detalle, boolean cumplido, Runnable abrirAjuste) {
            this.titulo = titulo;
            this.detalle = detalle;
            this.cumplido = cumplido;
            this.abrirAjuste = abrirAjuste;
        }
    }

    // ═══ Batería ═══════════════════════════════════════════════

    /**
     * Sin esto el sistema mete la app a dormir y le cancela las alarmas.
     * Es la causa más común de "no me sonó".
     */
    public static boolean bateriaSinRestriccion(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }

    /**
     * Abre el diálogo del sistema para quitar la optimización.
     *
     * Se usa el intent SIN el permiso REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     * declarado, que abre la LISTA de ajustes en vez del diálogo directo.
     * Pedirlo directo requiere declarar ese permiso, y Google Play lo
     * rechaza salvo en categorías muy puntuales; para una app de tesis no
     * vale la pena arriesgar la publicación por un toque menos.
     */
    @SuppressLint("BatteryLife")
    public static void abrirAjusteBateria(Activity act) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if (!intentar(act, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            abrirAjustesDeLaApp(act);
        }
    }

    // ═══ Capas de fabricante ═══════════════════════════════════

    /** True si el celular es de una marca con gestor propio de arranque. */
    public static boolean tieneGestorPropio() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        return m.contains("huawei") || m.contains("honor") || m.contains("xiaomi")
            || m.contains("redmi")  || m.contains("poco")  || m.contains("oppo")
            || m.contains("vivo")   || m.contains("realme")|| m.contains("meizu")
            || m.contains("samsung");
    }

    /**
     * Intenta abrir la pantalla de "inicio automático" del fabricante.
     *
     * Estos componentes no son API pública: cambian entre versiones y
     * pueden desaparecer. Por eso se prueban en orden y, si ninguno
     * responde, se cae a los ajustes de la app — que siempre existen.
     */
    public static void abrirInicioAutomatico(Activity act) {
        String[][] candidatos = {
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
            {"com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC"},
        };
        for (String[] c : candidatos) {
            Intent i = new Intent();
            i.setComponent(new ComponentName(c[0], c[1]));
            if (intentar(act, i)) return;
        }
        abrirAjustesDeLaApp(act);
    }

    /** Ajustes de la app: el último recurso, siempre existe. */
    public static void abrirAjustesDeLaApp(Activity act) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + act.getPackageName()));
        intentar(act, i);
    }

    private static boolean intentar(Activity act, Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ═══ La lista completa ═════════════════════════════════════

    /**
     * Los requisitos en el orden en que conviene resolverlos: primero los
     * que el sistema concede con un toque, al final los del fabricante,
     * que son los más engorrosos y no siempre hacen falta.
     */
    public static java.util.List<Requisito> revisar(Activity act) {
        java.util.List<Requisito> lista = new java.util.ArrayList<>();

        lista.add(new Requisito(
            "Mostrar notificaciones",
            "Sin esto no aparece ningún aviso de medicación.",
            NotificationHelper.tienePermisoNotificaciones(act),
            () -> NotificationHelper.pedirPermisoNotificaciones(act)));

        lista.add(new Requisito(
            "Alarmas exactas",
            "Para que suene a la hora justa y no cuando el sistema tenga ganas.",
            NotificationHelper.puedeAlarmasExactas(act),
            () -> NotificationHelper.pedirPermisoAlarmasExactas(act)));

        lista.add(new Requisito(
            "Alarma a pantalla completa",
            "Es lo que hace que la alarma se abra sola con el celular bloqueado.",
            NotificationHelper.puedeFullScreenIntent(act),
            () -> NotificationHelper.pedirPermisoFullScreenIntent(act)));

        lista.add(new Requisito(
            "Batería sin restricciones",
            "Si el sistema duerme la app, le cancela las alarmas.",
            bateriaSinRestriccion(act),
            () -> abrirAjusteBateria(act)));

        if (tieneGestorPropio()) {
            // No hay forma de consultar este estado: los gestores de
            // fabricante no exponen API. Se muestra siempre como pendiente
            // de revisar, con el texto explicando que puede ya estar bien.
            lista.add(new Requisito(
                "Inicio automático (" + Build.MANUFACTURER + ")",
                "Tu celular tiene su propio gestor que cierra apps en segundo plano. "
                    + "Buscá Vimed en la lista y activalo. No podemos comprobarlo desde acá.",
                false,
                () -> abrirInicioAutomatico(act)));
        }

        return lista;
    }

    /** Cuántos requisitos que SÍ podemos comprobar están sin cumplir. */
    public static int faltantesComprobables(Activity act) {
        int n = 0;
        if (!NotificationHelper.tienePermisoNotificaciones(act)) n++;
        if (!NotificationHelper.puedeAlarmasExactas(act)) n++;
        if (!NotificationHelper.puedeFullScreenIntent(act)) n++;
        if (!bateriaSinRestriccion(act)) n++;
        return n;
    }
}
