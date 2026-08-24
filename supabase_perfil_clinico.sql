-- =============================================================
-- VIMED — Peso y edad del paciente + referencia de dosis por kilo
--
-- Agrega:
--   - public.usuarios.peso_kg          → peso del paciente
--   - public.usuarios.anio_nacimiento  → de acá sale la edad
--   - catalogo_medicamentos.*          → referencia de dosificación
--
-- Idempotente: usa IF NOT EXISTS, se puede re-ejecutar.
--
-- Cómo correrlo:
--   Supabase Dashboard → SQL Editor → New query → pegar y Run
-- =============================================================


-- ── 1. DATOS CLÍNICOS DEL PACIENTE ───────────────────────────
--
-- Por qué anio_nacimiento y no una columna `edad`: una edad guardada
-- como número queda mal al año siguiente y nadie la corrige. Como este
-- dato alimenta un chequeo de dosis, envejecer mal en silencio no es
-- aceptable. El año de nacimiento es igual de fácil de cargar (la app
-- pide la edad y guarda el año) y la edad se recalcula sola.
--
-- Ambas columnas son NULL: son opcionales, y el chequeo de dosis
-- simplemente no dice nada cuando faltan.

ALTER TABLE public.usuarios
    ADD COLUMN IF NOT EXISTS peso_kg REAL
        CHECK (peso_kg IS NULL OR (peso_kg > 0 AND peso_kg < 400));

ALTER TABLE public.usuarios
    ADD COLUMN IF NOT EXISTS anio_nacimiento INTEGER
        CHECK (anio_nacimiento IS NULL
               OR anio_nacimiento BETWEEN 1900 AND EXTRACT(YEAR FROM NOW())::INT);


-- ── 2. REFERENCIA DE DOSIFICACIÓN DEL CATÁLOGO ───────────────
--
-- Hasta ahora el catálogo solo tenía `dosis_comun`: la presentación
-- habitual (un comprimido de 50 mg), que no alcanza para decir nada
-- sobre si una dosis es apropiada para una persona concreta.
--
-- Estas columnas son las que permiten usar el peso. TODAS NACEN EN
-- NULL A PROPÓSITO: mientras estén vacías, la app no dice nada sobre
-- dosis y peso, que es el comportamiento correcto. Cargarlas requiere
-- una fuente clínica (vademécum, ficha técnica del producto, o el
-- criterio de un profesional) — no se completan a ojo.

ALTER TABLE public.catalogo_medicamentos
    ADD COLUMN IF NOT EXISTS dosis_mg_kg_dia_min REAL
        CHECK (dosis_mg_kg_dia_min IS NULL OR dosis_mg_kg_dia_min > 0);

ALTER TABLE public.catalogo_medicamentos
    ADD COLUMN IF NOT EXISTS dosis_mg_kg_dia_max REAL
        CHECK (dosis_mg_kg_dia_max IS NULL OR dosis_mg_kg_dia_max > 0);

-- Techo absoluto diario, independiente del peso. Muchos medicamentos
-- se dosifican por kilo HASTA un tope: sin esto, una persona de 110 kg
-- obtendría una referencia por encima de la dosis máxima real.
ALTER TABLE public.catalogo_medicamentos
    ADD COLUMN IF NOT EXISTS dosis_max_dia REAL
        CHECK (dosis_max_dia IS NULL OR dosis_max_dia > 0);

-- Marca los medicamentos que en adultos mayores suelen indicarse a
-- dosis menores. Es un SÍ/NO, no un porcentaje: cuánto se reduce
-- depende de la función renal de cada persona, que la app no conoce.
ALTER TABLE public.catalogo_medicamentos
    ADD COLUMN IF NOT EXISTS ajustar_en_mayores BOOLEAN NOT NULL DEFAULT FALSE;

-- Nota corta y en castellano llano que la app muestra tal cual cuando
-- ajustar_en_mayores es TRUE. Va en la base y no en el código para que
-- se pueda corregir sin publicar una versión nueva de la app.
ALTER TABLE public.catalogo_medicamentos
    ADD COLUMN IF NOT EXISTS nota_mayores TEXT;


-- ── 3. COHERENCIA DEL RANGO ──────────────────────────────────
-- Un mínimo mayor que el máximo daría un rango imposible y la app
-- mostraría un disparate. Se ataja acá y no en el cliente.

ALTER TABLE public.catalogo_medicamentos
    DROP CONSTRAINT IF EXISTS rango_mg_kg_coherente;

ALTER TABLE public.catalogo_medicamentos
    ADD CONSTRAINT rango_mg_kg_coherente CHECK (
        dosis_mg_kg_dia_min IS NULL
        OR dosis_mg_kg_dia_max IS NULL
        OR dosis_mg_kg_dia_min <= dosis_mg_kg_dia_max
    );


-- ── 4. CÓMO CARGAR UN MEDICAMENTO ────────────────────────────
-- Ejemplo de la FORMA que tiene un UPDATE. Los números de abajo son
-- deliberadamente un placeholder y están comentados: ponerlos requiere
-- la ficha técnica del producto. No descomentar sin esa fuente.
--
-- UPDATE public.catalogo_medicamentos
--    SET dosis_mg_kg_dia_min = <de la ficha técnica>,
--        dosis_mg_kg_dia_max = <de la ficha técnica>,
--        dosis_max_dia       = <de la ficha técnica>,
--        ajustar_en_mayores  = <TRUE/FALSE>,
--        nota_mayores        = '<una frase para el paciente>'
--  WHERE principio_activo = '<principio activo>';


-- ── 5. VERIFICACIÓN ──────────────────────────────────────────
-- Cuántas entradas del catálogo tienen ya la referencia por kilo.
-- Recién cuando este número sea > 0 la app empieza a usar el peso.
SELECT
    COUNT(*)                                                  AS total,
    COUNT(dosis_mg_kg_dia_min)                                AS con_referencia_por_kilo,
    COUNT(*) FILTER (WHERE ajustar_en_mayores)                AS marcados_para_mayores
FROM public.catalogo_medicamentos
WHERE activo;
