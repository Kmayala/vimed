-- =============================================================
-- VIMED — Cerrar dos agujeros del vínculo familiar
--
-- Correr DESPUÉS de supabase_vinculo_pendiente.sql, que ya está
-- corrido. Este parche corrige dos cosas de aquel.
--
-- Idempotente. Supabase Dashboard → SQL Editor → New query.
-- =============================================================


-- ── 1. QUIEN ACEPTA NO PUEDE REESCRIBIR DE QUIÉN ES ──────────
--
-- El problema. La política vinc_responde controla bien QUÉ FILA se
-- puede tocar: su USING exige que esté pendiente y que no la hayas
-- pedido vos. Pero su WITH CHECK —que es lo que valida cómo queda
-- la fila DESPUÉS— solo mira el estado:
--
--     WITH CHECK (estado IN ('aceptado', 'rechazado'))
--
-- No dice nada de id_adulto ni de id_familiar. Un cliente
-- modificado puede mandar en el MISMO PATCH:
--
--     estado = 'aceptado', id_adulto = <id de un tercero>
--
-- El USING ya lo dejó pasar mirando la fila vieja, y el WITH CHECK
-- no mira quién quedó del otro lado. Resultado: se autoconcede
-- acceso a la medicación de alguien que nunca supo nada.
--
-- Por qué no se arregla en la política. RLS decide QUÉ FILAS, no
-- QUÉ COLUMNAS. Agregarle condiciones al WITH CHECK no alcanza:
-- siempre queda alguna combinación en la que el atacante sigue
-- siendo una de las dos puntas de la fila que él mismo reescribió.
--
-- La herramienta correcta son los permisos por columna, que se
-- evalúan aparte del RLS y encima de él. Con esto, el único campo
-- que un usuario puede escribir en esta tabla es `estado`;
-- cualquier intento de tocar otro falla antes de llegar al RLS.

REVOKE UPDATE ON public.vinculacion_familiar FROM authenticated, anon;
GRANT  UPDATE (estado) ON public.vinculacion_familiar TO authenticated;

-- INSERT y DELETE siguen como estaban: los gobiernan vinc_crea y
-- vinc_borra, que sí pueden expresar todo lo que necesitan en el
-- WITH CHECK / USING porque miran la fila entera de una vez.


-- ── 2. UN "NO" TIENE QUE CERRAR LA PUERTA ────────────────────
--
-- tengo_vinculo_con() devolvía true para CUALQUIER vínculo,
-- incluidos los rechazados. Como esa función es la que habilita
-- vinculado_lee_perfil sobre public.usuarios, alguien a quien le
-- dijiste explícitamente que no seguía pudiendo leer tu nombre,
-- tu correo y tu rol — para siempre, porque la fila rechazada
-- queda como registro.
--
-- El pendiente sí se conserva: hace falta para poder ver de quién
-- es la solicitud que estás por responder, que si no habría que
-- aceptar a ciegas.

CREATE OR REPLACE FUNCTION public.tengo_vinculo_con(id_consultado BIGINT)
RETURNS BOOLEAN
LANGUAGE SQL STABLE SECURITY DEFINER
SET search_path = public, auth
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM   public.vinculacion_familiar v
        WHERE  v.estado IS DISTINCT FROM 'rechazado'
          AND  ((v.id_adulto   = id_consultado
                 AND v.id_familiar = public.current_id_usuario())
            OR  (v.id_familiar = id_consultado
                 AND v.id_adulto   = public.current_id_usuario()))
    );
$$;

GRANT EXECUTE ON FUNCTION public.tengo_vinculo_con(BIGINT) TO anon, authenticated;


NOTIFY pgrst, 'reload schema';


-- ── 3. VERIFICACIÓN ──────────────────────────────────────────

-- 3a. Sobre vinculacion_familiar tiene que aparecer UPDATE con
--     column_name = 'estado', y ninguna otra fila de UPDATE.
SELECT grantee, privilege_type, column_name
FROM   information_schema.column_privileges
WHERE  table_schema = 'public'
  AND  table_name   = 'vinculacion_familiar'
  AND  privilege_type = 'UPDATE'
  AND  grantee IN ('authenticated', 'anon')
ORDER  BY grantee, column_name;

-- 3b. Que no haya quedado un UPDATE a nivel tabla.
SELECT grantee, privilege_type
FROM   information_schema.table_privileges
WHERE  table_schema = 'public'
  AND  table_name   = 'vinculacion_familiar'
  AND  grantee IN ('authenticated', 'anon')
ORDER  BY grantee, privilege_type;
