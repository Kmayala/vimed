-- =============================================================
-- VIMED — El vínculo tiene que aceptarse
--
-- Antes: quien vinculaba escribía la fila directamente con
-- estado = 'aceptado'. Cualquiera que supiera tu correo quedaba
-- vinculado y veía tu medicación sin que te enteraras.
--
-- Ahora: la fila nace en 'pendiente' y la OTRA punta tiene que
-- aceptarla. Como el RLS de los datos del paciente ya exige
-- estado = 'aceptado' (ver es_mi_paciente), un vínculo pendiente no
-- da acceso a nada.
--
-- Idempotente. Correr en Supabase Dashboard → SQL Editor.
-- Correr DESPUÉS de supabase_arreglar_vinculos_invertidos.sql.
-- =============================================================


-- ── 1. QUIÉN PIDIÓ EL VÍNCULO ────────────────────────────────
--
-- Sin esta columna no se puede saber a quién le toca aceptar: la
-- fila tiene un adulto y un familiar, pero cualquiera de los dos
-- pudo haberla creado, y el que la creó ya dio su consentimiento
-- al crearla. Hacerlo aceptar de nuevo sería pedirle permiso a
-- quien ya lo dio, y dejaría al otro sin decidir nada.

ALTER TABLE public.vinculacion_familiar
    ADD COLUMN IF NOT EXISTS solicitado_por BIGINT
    REFERENCES public.usuarios(id_usuario) ON DELETE SET NULL;

-- Los vínculos que ya existen se dan por aceptados. Cambiarlos a
-- pendiente cortaría el acceso de cuidadores que hoy funcionan, y
-- nadie sabría por qué dejó de andar.
UPDATE public.vinculacion_familiar
   SET estado = 'aceptado'
 WHERE estado IS NULL OR estado = '';


-- ── 2. QUE EL SOLICITANTE SEA UNA DE LAS DOS PUNTAS ──────────
-- Sin esto se podría escribir solicitado_por = un tercero y dejar
-- la fila sin nadie que pueda aceptarla.

ALTER TABLE public.vinculacion_familiar
    DROP CONSTRAINT IF EXISTS solicitante_es_una_punta;

ALTER TABLE public.vinculacion_familiar
    ADD CONSTRAINT solicitante_es_una_punta CHECK (
        solicitado_por IS NULL
        OR solicitado_por IN (id_adulto, id_familiar)
    );


-- ── 3. RLS: QUIÉN PUEDE ACEPTAR ──────────────────────────────
--
-- La política vieja (vinc_owner, FOR ALL) dejaba a cualquiera de
-- las dos puntas escribir cualquier cosa en la fila — incluido
-- que el solicitante se autoacepte, que es justo lo que hay que
-- impedir. Se parte en tres políticas con permisos distintos.

DROP POLICY IF EXISTS vinc_owner        ON public.vinculacion_familiar;
DROP POLICY IF EXISTS vinc_lee          ON public.vinculacion_familiar;
DROP POLICY IF EXISTS vinc_crea         ON public.vinculacion_familiar;
DROP POLICY IF EXISTS vinc_responde     ON public.vinculacion_familiar;
DROP POLICY IF EXISTS vinc_borra        ON public.vinculacion_familiar;

-- 3a. LEER: las dos puntas ven la fila, aceptada o pendiente. El
--     que recibió la solicitud necesita verla para poder responder.
CREATE POLICY vinc_lee ON public.vinculacion_familiar
    FOR SELECT TO authenticated
    USING (id_adulto   = public.current_id_usuario()
        OR id_familiar = public.current_id_usuario());

-- 3b. CREAR: solo pidiendo por uno mismo, y solo en 'pendiente'.
--     El estado se fuerza acá y no en la app: un cliente modificado
--     podría mandar 'aceptado' de entrada y saltearse todo.
CREATE POLICY vinc_crea ON public.vinculacion_familiar
    FOR INSERT TO authenticated
    WITH CHECK (
        solicitado_por = public.current_id_usuario()
        AND solicitado_por IN (id_adulto, id_familiar)
        AND estado = 'pendiente'
    );

-- 3c. RESPONDER: solo la punta que NO pidió el vínculo, y solo
--     mientras siga pendiente. Un vínculo ya resuelto no se
--     reescribe: para cambiarlo hay que borrarlo y pedirlo de nuevo.
CREATE POLICY vinc_responde ON public.vinculacion_familiar
    FOR UPDATE TO authenticated
    USING (
        estado = 'pendiente'
        AND solicitado_por IS DISTINCT FROM public.current_id_usuario()
        AND public.current_id_usuario() IN (id_adulto, id_familiar)
    )
    WITH CHECK (estado IN ('aceptado', 'rechazado'));

-- 3d. BORRAR: las dos puntas, siempre. Cortar el vínculo no
--     necesita permiso del otro — ni para cancelar lo que pedí ni
--     para dejar de compartir mis datos.
CREATE POLICY vinc_borra ON public.vinculacion_familiar
    FOR DELETE TO authenticated
    USING (id_adulto   = public.current_id_usuario()
        OR id_familiar = public.current_id_usuario());


-- ── 4. VER EL NOMBRE DE QUIEN TE MANDÓ LA SOLICITUD ──────────
--
-- El RLS de usuarios solo deja ver tu fila y la de los pacientes
-- que ya cuidás (es_mi_paciente exige 'aceptado'). Con eso, una
-- solicitud pendiente aparecería como "Cargando…" para siempre y
-- habría que aceptar a ciegas.
--
-- No es una fuga nueva: para mandar la solicitud hay que conocer
-- el correo, y buscar_usuario_por_correo ya devuelve nombre y
-- correo de cualquier cuenta.

CREATE OR REPLACE FUNCTION public.tengo_vinculo_con(id_consultado BIGINT)
RETURNS BOOLEAN
LANGUAGE SQL STABLE SECURITY DEFINER
SET search_path = public, auth
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM   public.vinculacion_familiar v
        WHERE  (v.id_adulto   = id_consultado
                AND v.id_familiar = public.current_id_usuario())
           OR  (v.id_familiar = id_consultado
                AND v.id_adulto   = public.current_id_usuario())
    );
$$;

GRANT EXECUTE ON FUNCTION public.tengo_vinculo_con(BIGINT) TO anon, authenticated;

DROP POLICY IF EXISTS vinculado_lee_perfil ON public.usuarios;

CREATE POLICY vinculado_lee_perfil ON public.usuarios
    FOR SELECT TO authenticated
    USING (public.tengo_vinculo_con(id_usuario));


-- ── 5. VERIFICACIÓN ──────────────────────────────────────────

SELECT policyname, cmd
FROM   pg_policies
WHERE  schemaname = 'public'
  AND  tablename  = 'vinculacion_familiar'
ORDER  BY policyname;

SELECT estado, COUNT(*)
FROM   public.vinculacion_familiar
GROUP  BY estado;
