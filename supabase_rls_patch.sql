-- =============================================================
-- VIMED — Parche RLS: permitir acceso desde la anon key
-- =============================================================
-- PROBLEMA:
--   Las políticas actuales (permit_authenticated) solo permiten
--   acceso al rol `authenticated`, pero la app Android usa la
--   anon key → cualquier query tira 401 permission denied.
--
-- ESTA SOLUCIÓN (corta plazo, para tesis sin Auth):
--   Reemplazar las políticas para que también acepten `anon`.
--   Esto es INSEGURO en producción — cualquiera con la anon key
--   puede leer/escribir cualquier fila. Pero permite avanzar con
--   la tesis sin implementar Supabase Auth.
--
-- TODO (cuando se implemente Supabase Auth):
--   Volver a estrechar las políticas a `authenticated` y filtrar
--   por auth.uid(). Ejemplo para medicamentos:
--     USING (id_usuario = (auth.jwt() ->> 'id_usuario')::bigint)
--
-- Cómo correr:
--   Supabase Dashboard → SQL Editor → New query → pegar → Run
-- =============================================================

-- Helper local para no repetir 10 veces el mismo patrón
DO $$
DECLARE
    t TEXT;
    tablas TEXT[] := ARRAY[
        'usuarios',
        'vinculacion_familiar',
        'medicamentos',
        'horarios',
        'registro_tomas',
        'notificaciones',
        'interacciones',
        'citas_medicas',
        'chatbot_historial',
        'stock_movimientos'
    ];
BEGIN
    FOREACH t IN ARRAY tablas LOOP
        -- Borrar la política vieja si existe
        EXECUTE format('DROP POLICY IF EXISTS permit_authenticated ON public.%I', t);
        EXECUTE format('DROP POLICY IF EXISTS permit_anon_y_auth   ON public.%I', t);

        -- Crear la nueva: permite todo a anon Y authenticated
        EXECUTE format(
            'CREATE POLICY permit_anon_y_auth ON public.%I
             FOR ALL TO anon, authenticated
             USING (true) WITH CHECK (true)',
            t
        );
    END LOOP;
END $$;


-- =============================================================
-- VERIFICACIÓN
-- =============================================================
-- Debería listar 10 filas, una por tabla, todas con
-- "permit_anon_y_auth" y roles {anon, authenticated}.
SELECT
    schemaname,
    tablename,
    policyname,
    roles
FROM   pg_policies
WHERE  schemaname = 'public'
  AND  tablename IN (
        'usuarios','vinculacion_familiar','medicamentos','horarios',
        'registro_tomas','notificaciones','interacciones',
        'citas_medicas','chatbot_historial','stock_movimientos'
  )
ORDER  BY tablename;
