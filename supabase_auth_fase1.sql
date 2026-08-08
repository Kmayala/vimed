-- =============================================================
-- VIMED — Fase 1 de migración a Supabase Auth
-- =============================================================
-- Objetivo: vincular cada fila de public.usuarios con su
-- contraparte en auth.users (que vive en el schema gestionado
-- por Supabase Auth), SIN romper las foreign keys existentes
-- en medicamentos, horarios, citas_medicas, etc.
--
-- Estrategia:
--   - Agregar columna nullable `auth_user_id UUID` a public.usuarios
--   - id_usuario (BIGSERIAL) SIGUE siendo el PK y el target de las FKs
--   - Cuando un usuario hace login con Auth, busca su id_usuario por
--     auth_user_id y lo usa para todo lo demás
--
-- Esto permite migración progresiva: los usuarios actuales
-- (Rosa, Carla) siguen funcionando como hoy. Los nuevos
-- registros se crean primero en auth.users y después se
-- linkean acá.
--
-- Cómo correr:
--   Supabase Dashboard → SQL Editor → New query → pegar → Run
-- =============================================================

-- ── Columna nueva: link a auth.users ──────────────────────────
ALTER TABLE public.usuarios
    ADD COLUMN IF NOT EXISTS auth_user_id UUID UNIQUE
    REFERENCES auth.users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_usuarios_auth ON public.usuarios(auth_user_id);

COMMENT ON COLUMN public.usuarios.auth_user_id IS
    'FK al uuid de auth.users. NULL para usuarios legacy (Rosa, Carla del seed). '
    'Se completa al registrarse vía Supabase Auth.';


-- ── Helper para resolver id_usuario desde el JWT ──────────────
-- Lo van a usar las políticas RLS en Fase 6 y se puede invocar
-- desde queries SQL si hace falta.
CREATE OR REPLACE FUNCTION public.current_id_usuario()
RETURNS BIGINT
LANGUAGE SQL STABLE SECURITY DEFINER
SET search_path = public, auth
AS $$
    SELECT id_usuario
    FROM   public.usuarios
    WHERE  auth_user_id = auth.uid()
    LIMIT  1;
$$;

GRANT EXECUTE ON FUNCTION public.current_id_usuario() TO anon, authenticated;


-- =============================================================
-- VERIFICACIÓN
-- =============================================================
-- 1) Esperamos ver auth_user_id como columna nueva
SELECT column_name, data_type, is_nullable
FROM   information_schema.columns
WHERE  table_schema = 'public'
  AND  table_name   = 'usuarios'
ORDER  BY ordinal_position;

-- 2) Confirmar que la función existe
SELECT routine_name FROM information_schema.routines
WHERE  routine_schema = 'public' AND routine_name = 'current_id_usuario';
