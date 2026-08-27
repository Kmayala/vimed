-- =============================================================
-- VIMED — Una sola fila por dosis programada
--
-- Qué pasaba: cada camino que registra una toma insertaba su propia
-- fila sin mirar si ya existía —la alarma al sonar, la alarma otra
-- vez al volver de un "posponer", y el botón "Ya tomé" del panel,
-- que no conoce el id de la fila que creó la alarma—. En el
-- historial del cuidador la misma dosis de las 07:50 aparecía dos y
-- tres veces, con estados que se contradecían entre sí:
--
--     Sulfato Ferroso — sin confirmar     07:50
--     Tomó Sulfato Ferroso ✓              07:50
--
-- La app ya no inserta a ciegas. Este script limpia lo que quedó y
-- pone un índice único para que no vuelva a pasar.
--
-- Correr en Supabase Dashboard → SQL Editor, paso por paso.
-- =============================================================


-- ── PASO 1: VER los duplicados (no modifica nada) ────────────
-- Cada fila de este resultado es una dosis registrada más de una vez.

SELECT
    r.id_horario,
    r.fecha_hora_programada,
    COUNT(*)                              AS filas,
    ARRAY_AGG(r.estado ORDER BY r.id_registro) AS estados,
    ARRAY_AGG(r.id_registro ORDER BY r.id_registro) AS ids
FROM   public.registro_tomas r
GROUP  BY r.id_horario, r.fecha_hora_programada
HAVING COUNT(*) > 1
ORDER  BY r.fecha_hora_programada DESC;


-- ── PASO 2: DEJAR UNA SOLA FILA POR DOSIS ────────────────────
--
-- Cuál se conserva, en este orden:
--
--   1. La confirmada. Que alguien haya confirmado la toma es el dato
--      más fuerte que tenemos: 'omitida' es solo el estado inicial
--      que pone la alarma y significa "todavía nadie dijo nada",
--      no "no la tomó". Si se conservara esa, el historial diría que
--      la persona se saltó una dosis que sí tomó, y eso además
--      ensucia el cálculo de adherencia.
--   2. Si ninguna está confirmada, la pospuesta antes que la omitida.
--   3. A igualdad de estado, la más vieja (id_registro menor), que es
--      la que creó la alarma en el momento real de la dosis.
--
-- Las demás se borran.

BEGIN;

WITH ranking AS (
    SELECT
        id_registro,
        ROW_NUMBER() OVER (
            PARTITION BY id_horario, fecha_hora_programada
            ORDER BY
                CASE estado
                    WHEN 'confirmada' THEN 0
                    WHEN 'pospuesta'  THEN 1
                    ELSE 2
                END,
                id_registro
        ) AS puesto
    FROM public.registro_tomas
)
DELETE FROM public.registro_tomas
WHERE id_registro IN (SELECT id_registro FROM ranking WHERE puesto > 1);

COMMIT;


-- ── PASO 3: QUE NO VUELVA A PASAR ────────────────────────────
--
-- El arreglo del cliente (buscar antes de insertar) cubre el caso
-- normal, pero no una carrera: la alarma suena y la persona aprieta
-- "Ya tomé" en el panel casi al mismo tiempo, las dos consultas no
-- encuentran nada y las dos insertan. Este índice hace que la
-- segunda pierda con un error en vez de duplicar en silencio — la
-- app lo maneja volviendo a buscar la fila que ganó.
--
-- Si este CREATE falla con "could not create unique index", quedaron
-- duplicados: volvé al PASO 1.

CREATE UNIQUE INDEX IF NOT EXISTS registro_tomas_dosis_unica
    ON public.registro_tomas (id_horario, fecha_hora_programada);


-- ── PASO 4: VERIFICAR ────────────────────────────────────────
-- Tiene que devolver 0.

SELECT COUNT(*) AS dosis_duplicadas
FROM (
    SELECT 1
    FROM   public.registro_tomas
    GROUP  BY id_horario, fecha_hora_programada
    HAVING COUNT(*) > 1
) d;


NOTIFY pgrst, 'reload schema';
