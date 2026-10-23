-- =============================================================
-- VIMED — Cuántas unidades por toma, y qué días de la semana
--
-- Dos cosas que el modelo no podía expresar:
--
--   1. "Tomar DOS comprimidos de 500 mg". Había dosis y unidad, pero
--      no cuántas unidades se toman por vez. La alarma decía "500 mg"
--      y el stock descontaba 1: a quien toma dos, el frasco se le
--      acababa al doble de velocidad de lo que la app creía.
--
--   2. "Solo los sábados". horarios.intervalo_horas tiene un CHECK
--      BETWEEN 1 AND 24, así que lo más espaciado que se podía decir
--      era cada 24 horas. Ni siquiera "cada 48" entraba.
--
-- Idempotente. Supabase Dashboard → SQL Editor → Run.
-- =============================================================


-- ── 1. CUÁNTAS UNIDADES POR TOMA ─────────────────────────────
-- Por omisión 1, que es lo que la app asumía hasta ahora: las
-- filas que ya existen quedan describiendo lo mismo que describían.

ALTER TABLE public.medicamentos
    ADD COLUMN IF NOT EXISTS cantidad_por_toma INTEGER NOT NULL DEFAULT 1;

ALTER TABLE public.medicamentos
    DROP CONSTRAINT IF EXISTS cantidad_por_toma_razonable;

-- El tope no es un capricho: veinte comprimidos de una vez es un
-- error de tipeo, y una app de medicación no debería guardarlo sin
-- chistar.
ALTER TABLE public.medicamentos
    ADD CONSTRAINT cantidad_por_toma_razonable
    CHECK (cantidad_por_toma BETWEEN 1 AND 20);

COMMENT ON COLUMN public.medicamentos.cantidad_por_toma IS
    'Unidades que se toman por vez: 2 = dos comprimidos de la dosis indicada. '
    'No aplica a líquidos, donde la cantidad son los ml de la propia dosis.';


-- ── 2. QUÉ DÍAS DE LA SEMANA ─────────────────────────────────
-- Siete caracteres, uno por día, empezando por el DOMINGO para que
-- coincida con Calendar.DAY_OF_WEEK de Android (1=domingo ... 7=sábado)
-- y no haya que convertir nada en el medio:
--
--     "0000001"  → solo sábados
--     "0100100"  → lunes y jueves
--     NULL       → todos los días (lo de siempre)
--
-- NULL y no "1111111" para las filas que ya existen: así se distingue
-- "nunca se eligieron días" de "se eligieron los siete", y el día que
-- haya que migrar algo, el dato dice la verdad.

ALTER TABLE public.horarios
    ADD COLUMN IF NOT EXISTS dias_semana TEXT;

ALTER TABLE public.horarios
    DROP CONSTRAINT IF EXISTS dias_semana_formato;

-- Siete ceros serían un horario que no suena nunca: se prohíbe.
ALTER TABLE public.horarios
    ADD CONSTRAINT dias_semana_formato
    CHECK (
        dias_semana IS NULL
        OR (dias_semana ~ '^[01]{7}$' AND dias_semana <> '0000000')
    );

COMMENT ON COLUMN public.horarios.dias_semana IS
    'Máscara de 7 caracteres 0/1 empezando por domingo, como '
    'Calendar.DAY_OF_WEEK. NULL = todos los días.';


-- ── 3. REFRESCAR EL CACHE DE POSTGREST ───────────────────────
NOTIFY pgrst, 'reload schema';


-- ── 4. VERIFICACIÓN ──────────────────────────────────────────
SELECT m.nombre,
       m.dosis, m.unidad,
       m.cantidad_por_toma,
       h.hora_inicio,
       h.intervalo_horas,
       COALESCE(h.dias_semana, 'todos los días') AS dias
FROM   public.medicamentos m
LEFT   JOIN public.horarios h ON h.id_medicamento = m.id_medicamento
WHERE  m.activo
ORDER  BY m.nombre;
