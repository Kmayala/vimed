-- =============================================================
-- VIMED — Que el historial recuerde QUÉ se tomó
--
-- El problema. registro_tomas guarda id_horario, y de ahí se llega
-- al medicamento. Pero dar de baja un medicamento lo pone en
-- activo = false, y todas las consultas de la app filtran por
-- activo = true. Resultado: apenas se da de baja, todo el historial
-- de esa persona pasa a decir "Medicamento" en vez del nombre.
--
-- Y eso vacía a la app de sentido. El registro de qué tomó alguien
-- y cuándo es el motivo por el que existe: es lo que se le muestra
-- al médico. Un historial que dice "Medicamento — sin confirmar,
-- 14 de marzo" no sirve para nada.
--
-- La solución no es dejar de filtrar por activo. Aunque hoy la baja
-- es lógica, el nombre seguiría dependiendo de una fila que se
-- puede editar o borrar más adelante, y renombrar un medicamento
-- reescribiría el pasado. Lo que se tomó el martes no cambia porque
-- hoy cambie la ficha.
--
-- Se guarda una COPIA del nombre y de la dosis en la fila de la
-- toma, en el momento en que se registra. Es duplicar un dato a
-- propósito: en un registro clínico, lo correcto es lo que era
-- cierto cuando pasó, no lo que es cierto ahora.
--
-- Idempotente. Supabase Dashboard → SQL Editor → New query.
-- =============================================================


-- ── 1. LAS COLUMNAS ──────────────────────────────────────────

ALTER TABLE public.registro_tomas
    ADD COLUMN IF NOT EXISTS nombre_medicamento TEXT;

-- La dosis va como TEXTO ya armado ("50 mg", "10 ml") y no como
-- número + unidad: es lo que se muestra, y guardarla formateada
-- evita que un cambio de unidad en la ficha reinterprete un número
-- viejo. 500 leído como mg o como mcg no es lo mismo.
ALTER TABLE public.registro_tomas
    ADD COLUMN IF NOT EXISTS dosis_texto TEXT;

COMMENT ON COLUMN public.registro_tomas.nombre_medicamento IS
    'Copia del nombre en el momento de la toma. Existe para que el '
    'historial sobreviva a la baja o al renombrado del medicamento. '
    'NULL solo en filas anteriores a esta columna que el backfill no '
    'pudo resolver.';


-- ── 2. RELLENAR LO QUE YA ESTÁ ───────────────────────────────
--
-- Se resuelve por el camino de siempre —toma → horario →
-- medicamento— pero SIN filtrar por activo: acá lo que se busca es
-- justamente el nombre de los que ya se dieron de baja.
--
-- Solo toca las filas en NULL, así que se puede re-ejecutar.

UPDATE public.registro_tomas r
   SET nombre_medicamento = m.nombre,
       dosis_texto = CASE
           WHEN m.dosis IS NULL THEN NULL
           -- Sin decimales cuando es un entero: "50 mg", no "50.0 mg".
           WHEN m.dosis = TRUNC(m.dosis)
               THEN TRUNC(m.dosis)::INT::TEXT || ' ' || COALESCE(m.unidad, '')
           ELSE TRIM(TRAILING '0' FROM m.dosis::TEXT) || ' ' || COALESCE(m.unidad, '')
       END
  FROM public.horarios h
  JOIN public.medicamentos m ON m.id_medicamento = h.id_medicamento
 WHERE h.id_horario = r.id_horario
   AND r.nombre_medicamento IS NULL;


NOTIFY pgrst, 'reload schema';


-- ── 3. VERIFICACIÓN ──────────────────────────────────────────

-- Cuántas tomas quedaron sin nombre. Lo esperable es 0; si hay
-- alguna, es una toma cuyo horario ya no existe y no hay de dónde
-- sacarlo. Esas van a seguir mostrándose como "Medicamento".
SELECT
    COUNT(*)                                        AS total,
    COUNT(nombre_medicamento)                       AS con_nombre,
    COUNT(*) - COUNT(nombre_medicamento)            AS sin_nombre
FROM public.registro_tomas;

-- Las últimas 20, para mirarlas a ojo.
SELECT id_registro, fecha_hora_programada, estado,
       nombre_medicamento, dosis_texto
FROM   public.registro_tomas
ORDER  BY fecha_hora_programada DESC
LIMIT  20;
