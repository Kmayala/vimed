-- =============================================================
-- VIMED — Que el cuidador cargue el peso y la edad de su paciente
--
-- El problema. ConfiguracionActivity editaba siempre el perfil de
-- quien está logueado. Al cuidador le preguntaba "¿cuánto pesás?"
-- y guardaba SUS kilos en SU fila, que no la lee nadie: el chequeo
-- de dosis usa los del paciente. Y como son dos filas distintas,
-- lo que cargaba uno no aparecía nunca del otro lado.
--
-- Lo que falta es que el cuidador pueda ESCRIBIR esos dos campos
-- en la fila de su paciente. Leerlos ya podía (cuidador_lee_perfil).
--
-- Por qué una función y no una política de UPDATE. Los permisos de
-- columna en Postgres son del ROL, no de la política: si se le
-- diera UPDATE sobre usuarios al cuidador, el mismo permiso que le
-- deja tocar peso_kg le dejaría tocar el `rol` de su paciente y
-- dejarlo sin poder usar la app. Una función SECURITY DEFINER
-- escribe exactamente dos columnas y ninguna más, y decide ella
-- misma sobre qué filas.
--
-- Idempotente. Supabase Dashboard → SQL Editor → New query.
-- PRECONDICIÓN: supabase_perfil_clinico.sql (ya corrido).
-- =============================================================

CREATE OR REPLACE FUNCTION public.guardar_datos_clinicos(
    p_id_usuario      BIGINT,
    p_peso_kg         REAL,
    p_anio_nacimiento INTEGER
)
RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    yo BIGINT := public.current_id_usuario();
BEGIN
    IF yo IS NULL THEN
        RAISE EXCEPTION 'Sesión no válida';
    END IF;

    -- Los propios, o los de un paciente que YA aceptó el vínculo.
    -- es_mi_paciente() exige estado = 'aceptado', así que una
    -- solicitud pendiente no alcanza para escribirle nada a nadie.
    IF p_id_usuario <> yo AND NOT public.es_mi_paciente(p_id_usuario) THEN
        RAISE EXCEPTION 'No podés editar los datos de esa persona';
    END IF;

    -- NULL borra el dato, que es lo que la app manda cuando alguien
    -- deja el campo vacío. Por eso se escriben siempre las dos
    -- columnas y no se saltea la que viene en NULL.
    UPDATE public.usuarios
       SET peso_kg         = p_peso_kg,
           anio_nacimiento = p_anio_nacimiento
     WHERE id_usuario = p_id_usuario;
END;
$$;

COMMENT ON FUNCTION public.guardar_datos_clinicos(BIGINT, REAL, INTEGER) IS
    'Escribe peso_kg y anio_nacimiento propios o de un paciente vinculado. '
    'SECURITY DEFINER para poder tocar la fila del paciente sin darle al '
    'cuidador UPDATE sobre toda la tabla usuarios, que le permitiría '
    'cambiarle también el rol.';

REVOKE ALL ON FUNCTION public.guardar_datos_clinicos(BIGINT, REAL, INTEGER)
    FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.guardar_datos_clinicos(BIGINT, REAL, INTEGER)
    TO authenticated;


NOTIFY pgrst, 'reload schema';


-- ── VERIFICACIÓN ─────────────────────────────────────────────
-- Quién tiene permiso de ejecutarla. Tiene que aparecer
-- authenticated, y NO anon.
SELECT grantee, privilege_type
FROM   information_schema.routine_privileges
WHERE  routine_schema = 'public'
  AND  routine_name   = 'guardar_datos_clinicos';

-- Peso y edad de cada usuario, para mirar a ojo que se compartan.
SELECT id_usuario, nombre, rol, peso_kg, anio_nacimiento
FROM   public.usuarios
ORDER  BY id_usuario;
