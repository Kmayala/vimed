// Edge Function: notificar-cuidador
//
// La app del ADULTO MAYOR la llama cuando pasa algo que su familia debería
// saber (confirmó una toma, no la confirmó, se le acaba un medicamento).
// La función busca a los cuidadores vinculados y les manda un push por FCM.
//
// Por qué existe en vez de que la app hable directo con Firebase:
//   - Mandar un push exige una credencial de servidor. Si viviera dentro del
//     APK, cualquiera podría extraerla y notificar en nombre de Vimed.
//   - Los destinatarios los decide el servidor a partir del JWT de quien
//     llama. La app no puede pedir que se le mande un push a un tercero.
//
// Deploy:
//   supabase functions deploy notificar-cuidador
//
// Secrets que necesita (supabase secrets set ...):
//   SERVICE_ROLE_KEY          → Settings ▸ API ▸ service_role
//   FIREBASE_SERVICE_ACCOUNT  → el JSON de la cuenta de servicio de Firebase

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { JWT } from "https://esm.sh/google-auth-library@9";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SERVICE_ROLE_KEY")!;
const FIREBASE_SERVICE_ACCOUNT = Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!;

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return json({ error: "Solo POST" }, 405);
  }

  try {
    // ── 1. Quién llama ────────────────────────────────────────
    // El JWT viene del login del adulto mayor. Si no es válido, cortamos:
    // nadie sin sesión puede disparar notificaciones.
    const authHeader = req.headers.get("Authorization") ?? "";
    const jwt = authHeader.replace("Bearer ", "").trim();
    if (!jwt) return json({ error: "Falta el token" }, 401);

    const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

    const { data: userData, error: userErr } = await admin.auth.getUser(jwt);
    if (userErr || !userData?.user) {
      return json({ error: "Token inválido" }, 401);
    }
    const authUserId = userData.user.id;

    // ── 2. Su fila en public.usuarios ─────────────────────────
    const { data: perfil } = await admin
      .from("usuarios")
      .select("id_usuario, nombre")
      .eq("auth_user_id", authUserId)
      .maybeSingle();

    if (!perfil) return json({ error: "Perfil no encontrado" }, 404);

    // ── 3. Sus cuidadores ─────────────────────────────────────
    const { data: vinculos } = await admin
      .from("vinculacion_familiar")
      .select("id_familiar")
      .eq("id_adulto", perfil.id_usuario);

    const idsCuidadores = (vinculos ?? []).map((v) => v.id_familiar);
    if (idsCuidadores.length === 0) {
      return json({ ok: true, enviados: 0, motivo: "sin cuidadores vinculados" });
    }

    // ── 4. Los celulares de esos cuidadores ───────────────────
    const { data: dispositivos } = await admin
      .from("dispositivos")
      .select("token")
      .in("id_usuario", idsCuidadores);

    const tokens = (dispositivos ?? []).map((d) => d.token).filter(Boolean);
    if (tokens.length === 0) {
      return json({ ok: true, enviados: 0, motivo: "sin dispositivos registrados" });
    }

    // ── 5. Enviar ─────────────────────────────────────────────
    const body = await req.json().catch(() => ({}));
    const titulo = String(body.titulo ?? "Vimed");
    const mensaje = String(body.mensaje ?? "");

    const cuenta = JSON.parse(FIREBASE_SERVICE_ACCOUNT);
    const accessToken = await obtenerAccessToken(cuenta);

    // Un push por token. Los mandamos en paralelo: son pocos (los celulares
    // de la familia) y así no encadenamos latencias.
    const resultados = await Promise.all(
      tokens.map((token) =>
        enviarPush(cuenta.project_id, accessToken, token, titulo, mensaje)
      ),
    );

    const enviados = resultados.filter((r) => r.ok).length;

    // Un token puede estar muerto (app desinstalada). Lo limpiamos para no
    // seguir intentando por siempre.
    const muertos = resultados.filter((r) => r.invalido).map((r) => r.token);
    if (muertos.length > 0) {
      await admin.from("dispositivos").delete().in("token", muertos);
    }

    return json({ ok: true, enviados, limpiados: muertos.length });
  } catch (e) {
    console.error("notificar-cuidador:", e);
    return json({ error: String(e) }, 500);
  }
});

// ═══ FCM HTTP v1 ═══════════════════════════════════════════════

/** Cambia la cuenta de servicio por un access token OAuth de 1 hora. */
async function obtenerAccessToken(cuenta: Record<string, string>) {
  const client = new JWT({
    email: cuenta.client_email,
    key: cuenta.private_key,
    scopes: ["https://www.googleapis.com/auth/firebase.messaging"],
  });
  const { access_token } = await client.authorize();
  return access_token as string;
}

async function enviarPush(
  projectId: string,
  accessToken: string,
  token: string,
  titulo: string,
  mensaje: string,
) {
  const url =
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

  // Solo "data", sin "notification": así el push entra siempre por
  // VimedFcmService.onMessageReceived y la app arma la notificación, también
  // con la app abierta. Con "notification", Android la dibujaría solo.
  const payload = {
    message: {
      token,
      data: { titulo, mensaje },
      android: { priority: "HIGH" },
    },
  };

  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (res.ok) return { ok: true, invalido: false, token };

  const detalle = await res.text();
  // 404 UNREGISTERED / 400 INVALID_ARGUMENT → el token ya no sirve.
  const invalido = res.status === 404 ||
    detalle.includes("UNREGISTERED") ||
    detalle.includes("INVALID_ARGUMENT");

  console.warn(`FCM ${res.status} para ${token.slice(0, 12)}…: ${detalle}`);
  return { ok: false, invalido, token };
}

function json(cuerpo: unknown, status = 200) {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
