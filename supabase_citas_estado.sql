-- =============================================================
-- VIMED — Estado de las citas médicas
-- =============================================================
-- La tabla citas_medicas solo tenía `recordatorio_enviado`, que es
-- un detalle interno de la app. Para mostrar el chip
-- "Confirmada / Pendiente" hace falta un estado propio de la cita.
--
-- Correr en: Supabase Dashboard → SQL Editor → New query
-- Es idempotente: se puede re-ejecutar sin romper nada.
-- =============================================================

ALTER TABLE public.citas_medicas
    ADD COLUMN IF NOT EXISTS estado TEXT NOT NULL DEFAULT 'pendiente'
    CHECK (estado IN ('pendiente', 'confirmada', 'cancelada'));

COMMENT ON COLUMN public.citas_medicas.estado IS
    'pendiente = agendada pero sin confirmar con el consultorio; '
    'confirmada = ya confirmada; cancelada = se dio de baja.';

CREATE INDEX IF NOT EXISTS idx_cit_estado ON public.citas_medicas(estado);


-- ── VERIFICACIÓN ─────────────────────────────────────────────
SELECT column_name, data_type, column_default
FROM   information_schema.columns
WHERE  table_schema = 'public' AND table_name = 'citas_medicas'
ORDER  BY ordinal_position;
