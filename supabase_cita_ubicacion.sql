-- =============================================================
-- VIMED — Ubicación de la cita en el mapa
--
-- Guarda el punto que la persona eligió en el selector de mapa
-- (OpenStreetMap), además del texto de la dirección que ya existía
-- en la columna `lugar`.
--
-- Las dos columnas son NULL: la ubicación es opcional y una cita
-- cargada a mano, sin abrir el mapa, sigue siendo válida.
--
-- Idempotente. Supabase Dashboard → SQL Editor → Run.
-- =============================================================


-- ── 1. COORDENADAS ───────────────────────────────────────────
--
-- Se guardan como dos DOUBLE PRECISION y no como un tipo geográfico
-- de PostGIS: lo único que hace la app es abrir un mapa en ese punto,
-- y para eso no hace falta consultar por cercanía ni calcular
-- distancias. Sumar la extensión PostGIS por dos números sería
-- complicar el esquema para nada.

ALTER TABLE public.citas_medicas
    ADD COLUMN IF NOT EXISTS latitud DOUBLE PRECISION
    CHECK (latitud IS NULL OR (latitud BETWEEN -90 AND 90));

ALTER TABLE public.citas_medicas
    ADD COLUMN IF NOT EXISTS longitud DOUBLE PRECISION
    CHECK (longitud IS NULL OR (longitud BETWEEN -180 AND 180));


-- ── 2. LAS DOS O NINGUNA ─────────────────────────────────────
-- Una latitud sin longitud no ubica nada, y dejaría a la app abriendo
-- el mapa en un punto a medias.

ALTER TABLE public.citas_medicas
    DROP CONSTRAINT IF EXISTS ubicacion_completa;

ALTER TABLE public.citas_medicas
    ADD CONSTRAINT ubicacion_completa CHECK (
        (latitud IS NULL AND longitud IS NULL)
        OR (latitud IS NOT NULL AND longitud IS NOT NULL)
    );


-- ── 3. REFRESCAR EL CACHE DE POSTGREST ───────────────────────
-- Sin esto, el INSERT de una cita con ubicación vuelve con
-- "PGRST204 — Could not find the 'latitud' column" aunque el ALTER
-- de arriba haya salido bien.

NOTIFY pgrst, 'reload schema';


-- ── 4. VERIFICACIÓN ──────────────────────────────────────────
-- Tienen que aparecer las dos filas.

SELECT column_name, data_type
FROM   information_schema.columns
WHERE  table_schema = 'public'
  AND  table_name   = 'citas_medicas'
  AND  column_name IN ('latitud', 'longitud')
ORDER  BY column_name;
