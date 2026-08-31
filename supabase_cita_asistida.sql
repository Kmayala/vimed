-- =============================================================
-- VIMED — Estado "asistida" para las citas
--
-- La tabla admitía pendiente | confirmada | cancelada, y ninguno de
-- los tres dice "ya fui". "Confirmada" es que la cita está en pie;
-- que la persona haya ido es otra cosa, y es la que importa después
-- de la fecha.
--
-- Idempotente. Supabase Dashboard → SQL Editor → Run.
-- =============================================================


-- ── 1. AMPLIAR LOS ESTADOS ───────────────────────────────────
-- El CHECK viejo se cae y se rehace con el estado nuevo. Las filas
-- que ya existen no se tocan: siguen siendo válidas.

ALTER TABLE public.citas_medicas
    DROP CONSTRAINT IF EXISTS citas_medicas_estado_check;

ALTER TABLE public.citas_medicas
    ADD CONSTRAINT citas_medicas_estado_check
    CHECK (estado IN ('pendiente', 'confirmada', 'asistida', 'cancelada'));


-- ── 2. REFRESCAR EL CACHE DE POSTGREST ───────────────────────
NOTIFY pgrst, 'reload schema';


-- ── 3. VERIFICACIÓN ──────────────────────────────────────────
SELECT estado, COUNT(*)
FROM   public.citas_medicas
GROUP  BY estado
ORDER  BY estado;
