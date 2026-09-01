-- =============================================================
-- VIMED — Un solo vínculo por par de personas
--
-- En la pantalla de vínculos aparecía la MISMA persona dos veces.
-- No era un problema de la app: hay dos filas en la tabla.
--
-- El esquema original (supabase_schema.sql, esquema vimed) tenía
-- CONSTRAINT vinculo_unico UNIQUE (id_adulto, id_familiar). La tabla
-- que usa la app —public.vinculacion_familiar— se creó sin él, así
-- que nada impedía insertar el mismo par dos veces: dos toques
-- seguidos en "Agregar", o pedirlo de nuevo después de un rechazo.
--
-- Idempotente. Supabase Dashboard → SQL Editor, paso por paso.
-- =============================================================


-- ── PASO 1: VER los duplicados (no modifica nada) ────────────

SELECT
    id_adulto,
    id_familiar,
    COUNT(*)                                        AS filas,
    ARRAY_AGG(estado      ORDER BY id_vinculo)      AS estados,
    ARRAY_AGG(id_vinculo  ORDER BY id_vinculo)      AS ids
FROM   public.vinculacion_familiar
GROUP  BY id_adulto, id_familiar
HAVING COUNT(*) > 1
ORDER  BY id_adulto;


-- ── PASO 2: DEJAR UNA SOLA FILA POR PAR ──────────────────────
--
-- Cuál se conserva, en este orden:
--   1. La aceptada. Si el vínculo ya funciona, borrarlo cortaría el
--      acceso del cuidador a los datos del paciente.
--   2. Si ninguna está aceptada, la pendiente antes que la rechazada:
--      una solicitud sin responder todavía se puede responder.
--   3. A igualdad de estado, la más vieja (id_vinculo menor).

BEGIN;

WITH ranking AS (
    SELECT
        id_vinculo,
        ROW_NUMBER() OVER (
            PARTITION BY id_adulto, id_familiar
            ORDER BY
                CASE estado
                    WHEN 'aceptado'  THEN 0
                    WHEN 'pendiente' THEN 1
                    ELSE 2
                END,
                id_vinculo
        ) AS puesto
    FROM public.vinculacion_familiar
)
DELETE FROM public.vinculacion_familiar
WHERE id_vinculo IN (SELECT id_vinculo FROM ranking WHERE puesto > 1);

COMMIT;


-- ── PASO 3: QUE NO VUELVA A PASAR ────────────────────────────
-- Si este CREATE falla con "could not create unique index", quedaron
-- duplicados: volvé al PASO 1.

CREATE UNIQUE INDEX IF NOT EXISTS vinculacion_par_unico
    ON public.vinculacion_familiar (id_adulto, id_familiar);


-- ── PASO 4: VERIFICAR ────────────────────────────────────────
-- Tiene que devolver 0.

SELECT COUNT(*) AS pares_duplicados
FROM (
    SELECT 1
    FROM   public.vinculacion_familiar
    GROUP  BY id_adulto, id_familiar
    HAVING COUNT(*) > 1
) d;


NOTIFY pgrst, 'reload schema';
