-- =============================================================
-- VIMED — Saber si los avisos le van a llegar al cuidador
--
-- La app dice "vinculado" y da por hecho que los avisos llegan. No
-- es cierto: hacen falta dos cosas más, y las dos son invisibles.
-- El cuidador tiene que haber abierto Vimed en su celular al menos
-- una vez con la sesión iniciada —ahí se guarda el token de push—
-- y el vínculo tiene que estar aceptado.
--
-- Mientras tanto el paciente marca sus tomas tranquilo creyendo que
-- del otro lado alguien las está viendo. Esa es la falla más cara
-- que puede tener esta app: la que no se nota.
--
-- POR QUÉ UNA FUNCIÓN Y NO UN SELECT. La tabla dispositivos tiene
-- RLS: cada quien ve solamente los tokens de sus propios aparatos,
-- que es como tiene que ser —un token es un dato sensible, con él
-- se le puede mandar una notificación a alguien—. El paciente no
-- puede consultarla. Esta función corre como dueña de la tabla y
-- devuelve ÚNICAMENTE un sí o un no, nunca el token.
--
-- Idempotente. Supabase Dashboard → SQL Editor → Run.
-- =============================================================


-- ── LA FUNCIÓN ───────────────────────────────────────────────
-- Devuelve una fila por cada vínculo ACEPTADO de quien pregunta,
-- diciendo si el cuidador de ese vínculo tiene algún celular
-- registrado.
--
-- Solo los vínculos propios: el WHERE de abajo compara contra
-- current_id_usuario(), que sale del JWT y no de un parámetro. Sin
-- eso, cualquiera podría preguntar por los vínculos de otro.

CREATE OR REPLACE FUNCTION public.mis_vinculos_reciben_avisos()
RETURNS TABLE (
    id_vinculo     BIGINT,
    id_familiar    BIGINT,
    recibe_avisos  BOOLEAN
)
LANGUAGE SQL STABLE SECURITY DEFINER
SET search_path = public, auth
AS $$
    SELECT
        v.id_vinculo,
        v.id_familiar,
        EXISTS (
            SELECT 1
            FROM   public.dispositivos d
            WHERE  d.id_usuario = v.id_familiar
        ) AS recibe_avisos
    FROM   public.vinculacion_familiar v
    WHERE  v.estado = 'aceptado'
      AND  (v.id_adulto    = public.current_id_usuario()
         OR v.id_familiar  = public.current_id_usuario());
$$;

COMMENT ON FUNCTION public.mis_vinculos_reciben_avisos() IS
    'Por cada vínculo aceptado propio, si el cuidador tiene al menos un '
    'celular registrado para push. Devuelve un booleano, nunca el token: '
    'dispositivos tiene RLS justamente para que nadie lea tokens ajenos.';

REVOKE EXECUTE ON FUNCTION public.mis_vinculos_reciben_avisos() FROM anon;
GRANT  EXECUTE ON FUNCTION public.mis_vinculos_reciben_avisos() TO authenticated;


-- ── REFRESCAR EL CACHE DE POSTGREST ──────────────────────────
NOTIFY pgrst, 'reload schema';


-- ── VERIFICACIÓN ─────────────────────────────────────────────
-- Corrido desde el SQL Editor devuelve VACÍO, y está bien: acá no
-- hay JWT, así que current_id_usuario() es NULL y no hay vínculos
-- propios. Que no dé error es la señal de que quedó bien creada.
--
-- Para ver los datos de verdad, esta consulta hace lo mismo sin
-- depender de la sesión:

SELECT
    v.id_vinculo,
    v.estado,
    a.nombre AS paciente,
    f.nombre AS cuidador,
    EXISTS (SELECT 1 FROM public.dispositivos d
            WHERE d.id_usuario = v.id_familiar) AS recibe_avisos
FROM   public.vinculacion_familiar v
JOIN   public.usuarios a ON a.id_usuario = v.id_adulto
JOIN   public.usuarios f ON f.id_usuario = v.id_familiar
WHERE  v.estado = 'aceptado'
ORDER  BY v.id_vinculo;
