-- =============================================================
-- VIMED — Fase 6: RLS estricto con auth.uid()
-- =============================================================
-- Reemplaza las políticas "permit_anon_y_auth" (que permitían
-- todo a cualquiera con la anon key) por políticas que solo
-- dan acceso al dueño de los datos, identificado vía Supabase Auth.
--
-- PRECONDICIONES:
--   - Fase 1 corrida (existe public.usuarios.auth_user_id +
--     función public.current_id_usuario())
--   - Cliente Android envía el access_token del usuario en
--     Authorization Bearer (no el anon key)
--
-- CATÁLOGO: las tablas catalogo_medicamentos e interacciones_catalogo
-- SIGUEN siendo legibles por anon (es referencia pública, sin
-- datos sensibles).
-- =============================================================


-- ── 1. USUARIOS ──────────────────────────────────────────────
DROP POLICY IF EXISTS permit_anon_y_auth ON public.usuarios;
DROP POLICY IF EXISTS permit_authenticated ON public.usuarios;
DROP POLICY IF EXISTS user_self_read   ON public.usuarios;
DROP POLICY IF EXISTS user_self_write  ON public.usuarios;
DROP POLICY IF EXISTS user_self_insert ON public.usuarios;

-- El usuario solo se ve a sí mismo (matcheado por auth.uid())
CREATE POLICY user_self_read   ON public.usuarios
    FOR SELECT TO authenticated USING (auth_user_id = auth.uid());

CREATE POLICY user_self_write  ON public.usuarios
    FOR UPDATE TO authenticated
    USING      (auth_user_id = auth.uid())
    WITH CHECK (auth_user_id = auth.uid());

-- INSERT permitido al usuario autenticado siempre que asocie
-- la fila a su propio auth.uid().
CREATE POLICY user_self_insert ON public.usuarios
    FOR INSERT TO authenticated WITH CHECK (auth_user_id = auth.uid());


-- ── 2. MEDICAMENTOS ──────────────────────────────────────────
DROP POLICY IF EXISTS permit_anon_y_auth   ON public.medicamentos;
DROP POLICY IF EXISTS permit_authenticated ON public.medicamentos;
DROP POLICY IF EXISTS med_owner            ON public.medicamentos;

CREATE POLICY med_owner ON public.medicamentos
    FOR ALL TO authenticated
    USING      (id_usuario = public.current_id_usuario())
    WITH CHECK (id_usuario = public.current_id_usuario());


-- ── 3. HORARIOS (vía medicamento) ────────────────────────────
DROP POLICY IF EXISTS permit_anon_y_auth   ON public.horarios;
DROP POLICY IF EXISTS permit_authenticated ON public.horarios;
DROP POLICY IF EXISTS hor_owner            ON public.horarios;

CREATE POLICY hor_owner ON public.horarios
    FOR ALL TO authenticated
    USING (id_medicamento IN (
        SELECT id_medicamento FROM public.medicamentos
        WHERE id_usuario = public.current_id_usuario()
    ))
    WITH CHECK (id_medicamento IN (
        SELECT id_medicamento FROM public.medicamentos
        WHERE id_usuario = public.current_id_usuario()
    ));


-- ── 4. REGISTRO_TOMAS ────────────────────────────────────────
DROP POLICY IF EXISTS permit_anon_y_auth   ON public.registro_tomas;
DROP POLICY IF EXISTS permit_authenticated ON public.registro_tomas;
DROP POLICY IF EXISTS reg_owner            ON public.registro_tomas;

CREATE POLICY reg_owner ON public.registro_tomas
    FOR ALL TO authenticated
    USING      (id_usuario = public.current_id_usuario())
    WITH CHECK (id_usuario = public.current_id_usuario());


-- ── 5. NOTIFICACIONES (por destinatario) ─────────────────────
DROP POLICY IF EXISTS permit_anon_y_auth   ON public.notificaciones;
DROP POLICY IF EXISTS permit_authenticated ON public.notificaciones;
DROP POLICY IF EXISTS not_owner            ON public.notificaciones;

CREATE POLICY not_owner ON public.notificaciones
    FOR ALL TO authenticated
    USING      (id_destinatario = public.current_id_usuario())
    WITH CHECK (id_destinatario = public.current_id_usuario());


-- ── 6. INTERACCIONES (vía medicamento — propias del usuario) ─
DROP POLICY IF EXISTS permit_anon_y_auth   ON public.interacciones;
DROP POLICY IF EXISTS permit_authenticated ON public.interacciones;
DROP POLICY IF EXISTS int_owner            ON public.interacciones;

CREATE POLICY int_owner ON public.interacciones
    FOR ALL TO authenticated
    USING (id_medicamento_a IN (
        SELECT id_medicamento FROM public.medicamentos
        WHERE id_usuario = public.current_id_usuario()
    ))
    WITH CHECK (id_medicamento_a IN (
        SELECT id_medicamento FROM public.medicamentos
        WHERE id_usuario = public.current_id_usuario()
    ));


-- ── 7. CITAS_MEDICAS ─────────────────────────────────────────
DROP POLICY IF EXISTS permit_anon_y_auth   ON public.citas_medicas;
DROP POLICY IF EXISTS permit_authenticated ON public.citas_medicas;
DROP POLICY IF EXISTS cit_owner            ON public.citas_medicas;

CREATE POLICY cit_owner ON public.citas_medicas
    FOR ALL TO authenticated
    USING      (id_usuario = public.current_id_usuario())
    WITH CHECK (id_usuario = public.current_id_usuario());


-- ── 8. CHATBOT_HISTORIAL ─────────────────────────────────────
DROP POLICY IF EXISTS permit_anon_y_auth   ON public.chatbot_historial;
DROP POLICY IF EXISTS permit_authenticated ON public.chatbot_historial;
DROP POLICY IF EXISTS chat_owner           ON public.chatbot_historial;

CREATE POLICY chat_owner ON public.chatbot_historial
    FOR ALL TO authenticated
    USING      (id_usuario = public.current_id_usuario())
    WITH CHECK (id_usuario = public.current_id_usuario());


-- ── 9. STOCK_MOVIMIENTOS (vía medicamento) ───────────────────
DROP POLICY IF EXISTS permit_anon_y_auth   ON public.stock_movimientos;
DROP POLICY IF EXISTS permit_authenticated ON public.stock_movimientos;
DROP POLICY IF EXISTS stk_owner            ON public.stock_movimientos;

CREATE POLICY stk_owner ON public.stock_movimientos
    FOR ALL TO authenticated
    USING (id_medicamento IN (
        SELECT id_medicamento FROM public.medicamentos
        WHERE id_usuario = public.current_id_usuario()
    ))
    WITH CHECK (id_medicamento IN (
        SELECT id_medicamento FROM public.medicamentos
        WHERE id_usuario = public.current_id_usuario()
    ));


-- ── 10. VINCULACION_FAMILIAR (ambos lados pueden leer) ───────
DROP POLICY IF EXISTS permit_anon_y_auth   ON public.vinculacion_familiar;
DROP POLICY IF EXISTS permit_authenticated ON public.vinculacion_familiar;
DROP POLICY IF EXISTS vinc_owner           ON public.vinculacion_familiar;

CREATE POLICY vinc_owner ON public.vinculacion_familiar
    FOR ALL TO authenticated
    USING (id_adulto    = public.current_id_usuario()
        OR id_familiar  = public.current_id_usuario())
    WITH CHECK (id_adulto = public.current_id_usuario()
        OR id_familiar    = public.current_id_usuario());


-- =============================================================
-- VERIFICACIÓN
-- =============================================================
-- Esperamos ver políticas con sufijo "_owner" o "self" en cada tabla,
-- y NINGUNA con nombre permit_anon_y_auth.
SELECT tablename, policyname, roles
FROM   pg_policies
WHERE  schemaname = 'public'
  AND  tablename IN (
        'usuarios','vinculacion_familiar','medicamentos','horarios',
        'registro_tomas','notificaciones','interacciones',
        'citas_medicas','chatbot_historial','stock_movimientos'
  )
ORDER  BY tablename, policyname;
