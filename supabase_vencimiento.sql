-- =============================================================
-- VIMED — Fecha de vencimiento del medicamento
--
-- Un medicamento vencido puede haber perdido efecto —o algo peor,
-- según el principio activo—, y la caja está guardada en un cajón
-- donde nadie mira la fecha. La app ya sabe qué tiene cargado cada
-- persona; lo único que le faltaba era la fecha.
--
-- Se avisa DOS DÍAS ANTES como mínimo, no el mismo día: comprar el
-- reemplazo lleva una ida a la farmacia, y avisar cuando ya venció
-- solo sirve para dar la mala noticia.
--
-- Idempotente. Correr en: Supabase Dashboard → SQL Editor.
-- =============================================================

-- DATE y no TEXT: así Postgres valida la fecha al escribirla y se
-- puede filtrar por rango desde el servidor si algún día hace falta.
-- Es NULL a propósito — el campo es opcional, y mucha gente no va a
-- tener la caja a mano cuando carga el medicamento.
ALTER TABLE public.medicamentos
    ADD COLUMN IF NOT EXISTS fecha_vencimiento DATE;

COMMENT ON COLUMN public.medicamentos.fecha_vencimiento IS
    'Vencimiento del envase actual. NULL = no se cargó, y en ese caso la '
    'app no avisa nada. Se actualiza al reponer el stock.';

-- El aviso recorre los medicamentos que vencen pronto; sin índice
-- eso es un scan completo cada vez que se abre la app.
CREATE INDEX IF NOT EXISTS idx_med_vencimiento
    ON public.medicamentos (fecha_vencimiento)
    WHERE fecha_vencimiento IS NOT NULL;



-- ── REFRESCAR EL CACHE DE POSTGREST ──────────────────────────
-- PostgREST guarda en memoria una copia del esquema. Las columnas
-- nuevas no existen para él hasta que se refresca, y hasta entonces
-- la app falla con "PGRST204 — Could not find the ... column".
NOTIFY pgrst, 'reload schema';

-- ── VERIFICACIÓN ─────────────────────────────────────────────
-- Lo que ya está por vencer o vencido, si es que hay algo cargado.
SELECT id_medicamento, nombre, fecha_vencimiento,
       fecha_vencimiento - CURRENT_DATE AS dias_restantes
FROM   public.medicamentos
WHERE  activo
  AND  fecha_vencimiento IS NOT NULL
ORDER  BY fecha_vencimiento;
