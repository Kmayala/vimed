-- =============================================================
-- VIMED — De dónde salió cada interacción
--
-- La tabla interacciones_catalogo tiene 32 pares y ninguno dice de
-- dónde salió. Para una app que le muestra advertencias clínicas a
-- una persona mayor sobre sus propios medicamentos, eso es el punto
-- más débil del trabajo: es contenido que hay que creer porque sí.
--
-- Esta migración NO cambia ninguna advertencia. Solo agrega dónde
-- anotar la referencia y si alguien la verificó, y deja todo como
-- NO VERIFICADO, que es la verdad hoy. Marcar algo como verificado
-- sin haberlo mirado sería peor que no tener la columna.
--
-- Idempotente. Supabase Dashboard → SQL Editor → Run.
-- =============================================================


-- ── 1. LAS COLUMNAS ──────────────────────────────────────────

ALTER TABLE public.interacciones_catalogo
    ADD COLUMN IF NOT EXISTS fuente TEXT;

ALTER TABLE public.interacciones_catalogo
    ADD COLUMN IF NOT EXISTS verificado BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.interacciones_catalogo
    ADD COLUMN IF NOT EXISTS verificado_el DATE;

COMMENT ON COLUMN public.interacciones_catalogo.fuente IS
    'Referencia de donde se tomó esta interacción. Formato sugerido: '
    '"Beers 2023, Tabla 3" / "CIMA AEMPS, ficha técnica de Warfarina, 4.5" / '
    '"STOPP/START v3, criterio D5". Una por fila, la que se consultó.';

COMMENT ON COLUMN public.interacciones_catalogo.verificado IS
    'TRUE solo cuando una persona abrió la fuente y confirmó que esta fila '
    'dice lo mismo. No lo pongas en TRUE por lote.';


-- ── 2. QUE NO SE PUEDA MARCAR VERIFICADO SIN FUENTE ──────────
-- El error que esta columna existe para evitar es justamente ese:
-- dar por buena una fila sin poder decir contra qué se la comparó.

ALTER TABLE public.interacciones_catalogo
    DROP CONSTRAINT IF EXISTS verificado_necesita_fuente;

ALTER TABLE public.interacciones_catalogo
    ADD CONSTRAINT verificado_necesita_fuente
    CHECK (NOT verificado OR (fuente IS NOT NULL AND LENGTH(TRIM(fuente)) > 0));


-- ── 3. REFRESCAR EL CACHE DE POSTGREST ───────────────────────
NOTIFY pgrst, 'reload schema';


-- ── 4. LA LISTA PARA IR COMPLETANDO ──────────────────────────
-- Exportá esto a CSV con el botón "Download CSV" del editor y
-- completá la columna fuente mientras verificás cada par.

SELECT
    i.id_interaccion_cat,
    a.principio_activo  AS farmaco_a,
    b.principio_activo  AS farmaco_b,
    i.nivel_riesgo,
    i.descripcion,
    COALESCE(i.fuente, '')                      AS fuente,
    CASE WHEN i.verificado THEN 'si' ELSE 'NO' END AS verificado
FROM   public.interacciones_catalogo i
JOIN   public.catalogo_medicamentos a ON a.id_catalogo = i.id_catalogo_a
JOIN   public.catalogo_medicamentos b ON b.id_catalogo = i.id_catalogo_b
ORDER  BY
    CASE i.nivel_riesgo WHEN 'alto' THEN 0 WHEN 'medio' THEN 1 ELSE 2 END,
    a.principio_activo, b.principio_activo;


-- ── 5. CÓMO ANOTAR CADA UNA ──────────────────────────────────
-- Una vez verificada una fila, corré algo así. El id sale de la
-- consulta de arriba.
--
--   UPDATE public.interacciones_catalogo
--   SET    fuente        = 'CIMA AEMPS, ficha técnica Warfarina, sección 4.5',
--          verificado    = TRUE,
--          verificado_el = CURRENT_DATE
--   WHERE  id_interaccion_cat = 1;
--
-- Si al verificar resulta que la descripción está mal, corregila en
-- el mismo UPDATE agregando descripcion = '...'. Para eso es el
-- repaso: no es solo poner la cita, es comprobar que lo que dice la
-- fila sea lo que dice la fuente.
--
-- Y si una interacción no aparece en ninguna fuente, borrala. Una
-- advertencia que no se puede respaldar no debería mostrarse:
--
--   DELETE FROM public.interacciones_catalogo WHERE id_interaccion_cat = N;


-- ── 6. CUÁNTO FALTA ──────────────────────────────────────────
-- Para ir midiendo el avance.

SELECT
    COUNT(*)                                        AS total,
    COUNT(*) FILTER (WHERE verificado)              AS verificadas,
    COUNT(*) FILTER (WHERE NOT verificado)          AS pendientes,
    COUNT(*) FILTER (WHERE NOT verificado
                       AND nivel_riesgo = 'alto')   AS pendientes_de_riesgo_alto
FROM public.interacciones_catalogo;
