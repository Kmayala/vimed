-- =============================================================
-- VIMED — Diagnóstico del error PGRST204 al guardar peso y edad
--
-- Correr entero en Supabase Dashboard → SQL Editor y mirar las
-- tres respuestas.
-- =============================================================


-- ── 1. ¿DÓNDE ESTÁ LA TABLA usuarios? ────────────────────────
-- La app le pega a PostgREST, que por defecto solo expone el
-- esquema `public`. Si esto devuelve `vimed` y no `public`, el
-- problema es otro: la migración tocó una tabla que la app no usa.

SELECT table_schema, table_name
FROM   information_schema.tables
WHERE  table_name = 'usuarios';


-- ── 2. ¿EXISTEN LAS COLUMNAS? ────────────────────────────────
-- Tienen que salir las dos filas: peso_kg y anio_nacimiento.
--
--   Si NO salen  → falta correr supabase_perfil_clinico.sql.
--   Si SÍ salen  → la tabla está bien y el problema es el cache
--                  de PostgREST: seguí al paso 3.

SELECT column_name, data_type, is_nullable
FROM   information_schema.columns
WHERE  table_schema = 'public'
  AND  table_name   = 'usuarios'
  AND  column_name IN ('peso_kg', 'anio_nacimiento')
ORDER  BY column_name;


-- ── 3. REFRESCAR EL CACHE ────────────────────────────────────
-- PostgREST mantiene en memoria una copia del esquema. Una
-- columna recién creada no existe para él hasta que la relee, y
-- hasta entonces devuelve PGRST204 aunque la columna esté.

NOTIFY pgrst, 'reload schema';


-- ── 4. SI DESPUÉS DE ESTO SIGUE FALLANDO ─────────────────────
-- Dashboard → Settings → API → "Restart server", o esperar un par
-- de minutos: el cache también se refresca solo.
--
-- Y si el paso 2 no devolvió nada, correr:
--     supabase_perfil_clinico.sql
