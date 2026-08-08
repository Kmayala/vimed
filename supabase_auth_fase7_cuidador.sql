-- =============================================================
-- VIMED — Fase 7: acceso de lectura para el cuidador
-- =============================================================
-- Fase 6 dejó a cada usuario viendo SOLO sus propios datos. Eso
-- rompe el panel del familiar: un cuidador vinculado necesita
-- LEER los datos del adulto mayor que cuida.
--
-- Este parche AGREGA políticas de solo lectura. No toca las de
-- Fase 6: el cuidador puede ver, pero no modificar ni borrar.
--
-- PRECONDICIÓN: Fase 1 y Fase 6 ya corridas.
-- Correr en: Supabase Dashboard → SQL Editor → New query
-- =============================================================


-- ── Helper: ¿ese id_usuario es un paciente mío? ──────────────
-- SECURITY DEFINER para poder consultar vinculacion_familiar sin
-- quedar atrapado en las políticas de esa misma tabla.
CREATE OR REPLACE FUNCTION public.es_mi_paciente(id_adulto_consultado BIGINT)
RETURNS BOOLEAN
LANGUAGE SQL STABLE SECURITY DEFINER
SET search_path = public, auth
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM   public.vinculacion_familiar v
        WHERE  v.id_adulto   = id_adulto_consultado
          AND  v.id_familiar = public.current_id_usuario()
          AND  v.estado      = 'aceptado'
    );
$$;

GRANT EXECUTE ON FUNCTION public.es_mi_paciente(BIGINT) TO anon, authenticated;


-- ── 1. USUARIOS: ver el perfil del paciente ──────────────────
DROP POLICY IF EXISTS cuidador_lee_perfil ON public.usuarios;

CREATE POLICY cuidador_lee_perfil ON public.usuarios
    FOR SELECT TO authenticated
    USING (public.es_mi_paciente(id_usuario));


-- ── 2. MEDICAMENTOS ──────────────────────────────────────────
DROP POLICY IF EXISTS cuidador_lee_medicamentos ON public.medicamentos;

CREATE POLICY cuidador_lee_medicamentos ON public.medicamentos
    FOR SELECT TO authenticated
    USING (public.es_mi_paciente(id_usuario));


-- ── 3. HORARIOS (vía medicamento del paciente) ───────────────
DROP POLICY IF EXISTS cuidador_lee_horarios ON public.horarios;

CREATE POLICY cuidador_lee_horarios ON public.horarios
    FOR SELECT TO authenticated
    USING (id_medicamento IN (
        SELECT m.id_medicamento
        FROM   public.medicamentos m
        WHERE  public.es_mi_paciente(m.id_usuario)
    ));


-- ── 4. REGISTRO DE TOMAS ─────────────────────────────────────
DROP POLICY IF EXISTS cuidador_lee_tomas ON public.registro_tomas;

CREATE POLICY cuidador_lee_tomas ON public.registro_tomas
    FOR SELECT TO authenticated
    USING (public.es_mi_paciente(id_usuario));


-- ── 5. NOTIFICACIONES (la actividad reciente del panel) ──────
DROP POLICY IF EXISTS cuidador_lee_notificaciones ON public.notificaciones;

CREATE POLICY cuidador_lee_notificaciones ON public.notificaciones
    FOR SELECT TO authenticated
    USING (public.es_mi_paciente(id_destinatario));


-- ── 6. CITAS MÉDICAS ─────────────────────────────────────────
DROP POLICY IF EXISTS cuidador_lee_citas ON public.citas_medicas;

CREATE POLICY cuidador_lee_citas ON public.citas_medicas
    FOR SELECT TO authenticated
    USING (public.es_mi_paciente(id_usuario));


-- ── 7. VINCULACIÓN: el adulto puede crear el vínculo ─────────
-- La política de Fase 6 ya cubre ambos lados (id_adulto o
-- id_familiar = current_id_usuario), así que no hace falta más.
-- Se deja este comentario para que quede explícito.


-- =============================================================
-- VERIFICACIÓN
-- =============================================================
-- Deberían aparecer las 6 políticas "cuidador_lee_*" además de
-- las "_owner" de Fase 6.
SELECT tablename, policyname, cmd, roles
FROM   pg_policies
WHERE  schemaname = 'public'
  AND  policyname LIKE 'cuidador_%'
ORDER  BY tablename;
