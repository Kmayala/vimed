-- =============================================================
-- VIMED — Buscar una cuenta por correo para poder vincularla
-- =============================================================
-- PROBLEMA. Las políticas de SELECT sobre public.usuarios son
-- tres, y ninguna cubre la vinculación:
--
--   user_self_read                  → tu propia fila
--   cuidador_lee_perfil             → los pacientes que ya cuidás
--   cuidador: ver perfil del adulto → el adulto que ya te vinculó
--
-- O sea: para ver a alguien hay que estar vinculado, y para
-- vincularse hay que poder verlo. La búsqueda por correo devolvía
-- cero filas siempre, y la app lo mostraba como "no existe
-- ninguna cuenta con ese correo".
--
-- POR QUÉ NO SE ABRE LA TABLA. Agregar una política amplia de
-- lectura para authenticated arreglaría la búsqueda, pero dejaría
-- a cualquier persona logueada leer el nombre, el correo y el rol
-- de TODOS los usuarios. Son datos de salud de adultos mayores.
--
-- SOLUCIÓN. Una función SECURITY DEFINER que atraviesa el RLS
-- pero solo para lo mínimo: coincidencia EXACTA de un correo que
-- quien busca ya conoce, y devuelve nombre y rol para poder
-- mostrar "Rosa vinculada correctamente". No hay búsqueda
-- parcial, así que no se puede usar para listar cuentas.
--
-- Correr en: Supabase Dashboard → SQL Editor → New query
-- Es idempotente: se puede re-ejecutar sin romper nada.
-- =============================================================

CREATE OR REPLACE FUNCTION public.buscar_usuario_por_correo(p_correo TEXT)
RETURNS TABLE (id_usuario BIGINT, nombre TEXT, correo TEXT, rol TEXT)
LANGUAGE SQL STABLE SECURITY DEFINER
SET search_path = public, auth
AS $$
    SELECT u.id_usuario, u.nombre, u.correo, u.rol
    FROM   public.usuarios u
    -- lower() de los dos lados: el correo puede haberse guardado
    -- como "Rosa@Gmail.com" y escribirse en minúscula. btrim()
    -- saca el espacio que deja el autocompletado del teclado.
    WHERE  lower(btrim(u.correo)) = lower(btrim(p_correo))
    LIMIT  1;
$$;

COMMENT ON FUNCTION public.buscar_usuario_por_correo(TEXT) IS
    'Resuelve un correo exacto a id_usuario + nombre + rol, para la '
    'pantalla de vinculación familiar. SECURITY DEFINER a propósito: '
    'el RLS de usuarios no permite ver a alguien con quien todavía no '
    'estás vinculado. No acepta búsqueda parcial.';

-- Quién puede invocarla se controla ACÁ, con los permisos, y no con
-- un filtro por auth.uid() adentro de la consulta. Un filtro así
-- además la volvía imposible de probar: en el SQL Editor se corre
-- como postgres, sin JWT, así que auth.uid() es NULL y la función
-- no devolvía nada nunca.
REVOKE ALL ON FUNCTION public.buscar_usuario_por_correo(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.buscar_usuario_por_correo(TEXT) TO authenticated;

-- Sin este índice cada búsqueda recorre la tabla entera, porque
-- lower(btrim(...)) no puede usar el índice común de correo.
CREATE INDEX IF NOT EXISTS idx_usuarios_correo_lower
    ON public.usuarios (lower(btrim(correo)));


-- ── VERIFICACIÓN ─────────────────────────────────────────────
-- 1) Mirá qué correos hay realmente cargados. Sirve para descartar
--    que el correo esté escrito distinto de lo que uno recuerda.
SELECT id_usuario, nombre, correo, rol FROM public.usuarios ORDER BY id_usuario;

-- 2) Copiá uno de esos correos acá. Tiene que devolver una fila.
-- SELECT * FROM public.buscar_usuario_por_correo('pegá-un-correo-de-arriba');
