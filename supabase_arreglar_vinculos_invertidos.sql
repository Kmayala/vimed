-- =============================================================
-- VIMED — Arreglar los vínculos guardados al revés
--
-- Qué pasó: VincularFamiliarActivity guardaba siempre
-- (id_adulto = yo, id_familiar = el del correo), asumiendo que quien
-- vincula es el adulto mayor. Pero el cuidador entra a la misma
-- pantalla desde su menú de perfil, así que al cargar a su paciente la
-- fila quedaba invertida: él como adulto y su paciente como cuidador.
--
-- Consecuencia: CuidadorActivity busca filas con id_familiar = él, no
-- encontraba ninguna, y su app se veía vacía.
--
-- La app ya guarda bien las filas nuevas. Este script arregla las
-- viejas. Se detectan por el ROL de cada punta: una fila donde el
-- "adulto" tiene rol 'familiar' y el "familiar" tiene rol
-- 'adulto_mayor' está invertida.
--
-- Cómo correrlo:
--   Supabase Dashboard → SQL Editor → New query
--   1) Correr el PASO 1 y mirar el resultado.
--   2) Si la lista es la esperada, correr el PASO 2.
-- =============================================================


-- ── PASO 1: VER qué se va a cambiar (no modifica nada) ───────
-- Revisá esta lista antes de seguir. Si sale vacía, no hay nada
-- invertido y podés parar acá.

SELECT
    v.id_vinculo,
    v.id_adulto                       AS guardado_como_adulto,
    a.nombre                          AS nombre_de_esa_punta,
    a.rol                             AS rol_de_esa_punta,
    v.id_familiar                     AS guardado_como_familiar,
    f.nombre                          AS nombre_de_la_otra,
    f.rol                             AS rol_de_la_otra
FROM   public.vinculacion_familiar v
JOIN   public.usuarios a ON a.id_usuario = v.id_adulto
JOIN   public.usuarios f ON f.id_usuario = v.id_familiar
WHERE  a.rol = 'familiar'
  AND  f.rol = 'adulto_mayor'
ORDER  BY v.id_vinculo;


-- ── PASO 2: DAR VUELTA las filas invertidas ──────────────────
-- Se hace en una transacción: si algo falla, no queda a medio camino.
--
-- El UNIQUE (id_adulto, id_familiar) puede rebotar si el vínculo
-- correcto YA existe —porque después el paciente vinculó al cuidador
-- desde su app—. En ese caso la fila invertida sobra: el DELETE de más
-- abajo la saca.

BEGIN;

-- 2a. Borrar las invertidas que ya tienen su equivalente correcto.
--     Si no, el UPDATE de 2b choca contra el UNIQUE.
DELETE FROM public.vinculacion_familiar v
USING  public.usuarios a, public.usuarios f
WHERE  a.id_usuario = v.id_adulto
  AND  f.id_usuario = v.id_familiar
  AND  a.rol = 'familiar'
  AND  f.rol = 'adulto_mayor'
  AND  EXISTS (
        SELECT 1 FROM public.vinculacion_familiar ok
        WHERE  ok.id_adulto   = v.id_familiar
          AND  ok.id_familiar = v.id_adulto
  );

-- 2b. Dar vuelta las que quedan.
UPDATE public.vinculacion_familiar v
SET    id_adulto   = v.id_familiar,
       id_familiar = v.id_adulto
FROM   public.usuarios a, public.usuarios f
WHERE  a.id_usuario = v.id_adulto
  AND  f.id_usuario = v.id_familiar
  AND  a.rol = 'familiar'
  AND  f.rol = 'adulto_mayor';

COMMIT;


-- ── PASO 3: VERIFICAR ────────────────────────────────────────
-- Tiene que devolver 0 filas. Cada vínculo debería quedar con un
-- adulto_mayor de un lado y un familiar del otro.

SELECT COUNT(*) AS todavia_invertidos
FROM   public.vinculacion_familiar v
JOIN   public.usuarios a ON a.id_usuario = v.id_adulto
JOIN   public.usuarios f ON f.id_usuario = v.id_familiar
WHERE  a.rol = 'familiar'
  AND  f.rol = 'adulto_mayor';


-- Vínculos que quedaron, para revisarlos a ojo.
SELECT
    v.id_vinculo,
    a.nombre  AS paciente,
    f.nombre  AS cuidador,
    v.estado
FROM   public.vinculacion_familiar v
JOIN   public.usuarios a ON a.id_usuario = v.id_adulto
JOIN   public.usuarios f ON f.id_usuario = v.id_familiar
ORDER  BY v.id_vinculo;
