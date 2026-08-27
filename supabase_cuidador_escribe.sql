-- =============================================================
-- VIMED — El cuidador también escribe (no solo mira)
--
-- La fase 7 le dio al cuidador acceso de SOLO LECTURA, y esa
-- decisión dejó rotas dos cosas que la app ya ofrece:
--
--   · "Cargando este medicamento para Rosa"  → INSERT rebota
--     contra med_owner, que exige id_usuario = current_id_usuario()
--   · "Cita agendada para Rosa"              → igual con cit_owner
--
-- Los dos carteles aparecen en pantalla y la operación falla. Y lo
-- que se pide ahora —corregir el stock desde el teléfono del
-- cuidador y marcar como tomada una dosis olvidada— choca con lo
-- mismo.
--
-- Todo lo de acá abajo pasa por es_mi_paciente(), que exige un
-- vínculo en estado 'aceptado'. Un vínculo pendiente no habilita
-- nada, igual que antes.
--
-- PRECONDICIÓN: fases 1, 6 y 7 ya corridas, más
-- supabase_vinculo_pendiente.sql.
-- Correr en: Supabase Dashboard → SQL Editor → New query
-- Idempotente.
-- =============================================================


-- ── 1. MEDICAMENTOS: cargar y corregir ───────────────────────
-- El UPDATE es lo que permite arreglar el stock desde el perfil
-- del cuidador. No se limita a la columna stock_actual: el
-- cuidador es quien suele tener la caja en la mano, así que
-- también corrige la dosis mal tipeada o el vencimiento.
--
-- El WITH CHECK repite la condición del USING para que no se pueda
-- MOVER un medicamento a otra persona: sin él, el cuidador podría
-- cambiarle el id_usuario a la fila y sacarla de su alcance —o
-- peor, metérsela a otro paciente.

DROP POLICY IF EXISTS cuidador_crea_medicamentos  ON public.medicamentos;
DROP POLICY IF EXISTS cuidador_edita_medicamentos ON public.medicamentos;

CREATE POLICY cuidador_crea_medicamentos ON public.medicamentos
    FOR INSERT TO authenticated
    WITH CHECK (public.es_mi_paciente(id_usuario));

CREATE POLICY cuidador_edita_medicamentos ON public.medicamentos
    FOR UPDATE TO authenticated
    USING      (public.es_mi_paciente(id_usuario))
    WITH CHECK (public.es_mi_paciente(id_usuario));

-- Sin DELETE a propósito: dar de baja el medicamento de otra
-- persona es una acción de la que no queda rastro en la pantalla
-- del paciente. La baja lógica (activo = false) ya la cubre el
-- UPDATE de arriba, y esa sí se puede revertir.


-- ── 2. HORARIOS ──────────────────────────────────────────────
-- Un medicamento sin horario no suena. Si el cuidador puede
-- cargarlo, tiene que poder cargarle las horas.

DROP POLICY IF EXISTS cuidador_crea_horarios  ON public.horarios;
DROP POLICY IF EXISTS cuidador_edita_horarios ON public.horarios;

CREATE POLICY cuidador_crea_horarios ON public.horarios
    FOR INSERT TO authenticated
    WITH CHECK (id_medicamento IN (
        SELECT m.id_medicamento FROM public.medicamentos m
        WHERE  public.es_mi_paciente(m.id_usuario)
    ));

CREATE POLICY cuidador_edita_horarios ON public.horarios
    FOR UPDATE TO authenticated
    USING (id_medicamento IN (
        SELECT m.id_medicamento FROM public.medicamentos m
        WHERE  public.es_mi_paciente(m.id_usuario)
    ))
    WITH CHECK (id_medicamento IN (
        SELECT m.id_medicamento FROM public.medicamentos m
        WHERE  public.es_mi_paciente(m.id_usuario)
    ));


-- ── 3. CITAS MÉDICAS ─────────────────────────────────────────
-- Acompañar a la consulta suele ser tarea del familiar, así que
-- agendarla también.

DROP POLICY IF EXISTS cuidador_crea_citas  ON public.citas_medicas;
DROP POLICY IF EXISTS cuidador_edita_citas ON public.citas_medicas;

CREATE POLICY cuidador_crea_citas ON public.citas_medicas
    FOR INSERT TO authenticated
    WITH CHECK (public.es_mi_paciente(id_usuario));

CREATE POLICY cuidador_edita_citas ON public.citas_medicas
    FOR UPDATE TO authenticated
    USING      (public.es_mi_paciente(id_usuario))
    WITH CHECK (public.es_mi_paciente(id_usuario));


-- ── 4. QUIÉN CONFIRMÓ LA TOMA ────────────────────────────────
--
-- Sin esta columna, una dosis que el cuidador da por tomada queda
-- idéntica a una que el paciente confirmó apretando el botón. Y no
-- son lo mismo: la primera es lo que alguien CREE que pasó, la
-- segunda es lo que la persona dijo que hizo. Si se mezclan, el
-- porcentaje de adherencia deja de significar algo y nadie se
-- entera de que dejó de significarlo.
--
-- NULL = la confirmó el propio paciente (y también los registros
-- viejos, anteriores a esta columna, que son todos suyos).

ALTER TABLE public.registro_tomas
    ADD COLUMN IF NOT EXISTS confirmado_por BIGINT
    REFERENCES public.usuarios(id_usuario) ON DELETE SET NULL;

COMMENT ON COLUMN public.registro_tomas.confirmado_por IS
    'Quién marcó la toma como confirmada, cuando NO fue el paciente. '
    'NULL = la confirmó él mismo. Sirve para que la adherencia distinga '
    'lo confirmado de lo corregido por un tercero.';


-- ── 5. REGISTRO DE TOMAS: corregir un olvido ─────────────────
-- Solo UPDATE. El cuidador corrige una fila que ya existe; crear
-- tomas de la nada es del teléfono del paciente, que es el que
-- sabe a qué hora sonó la alarma.

DROP POLICY IF EXISTS cuidador_corrige_tomas ON public.registro_tomas;

CREATE POLICY cuidador_corrige_tomas ON public.registro_tomas
    FOR UPDATE TO authenticated
    USING      (public.es_mi_paciente(id_usuario))
    WITH CHECK (public.es_mi_paciente(id_usuario));



-- ── REFRESCAR EL CACHE DE POSTGREST ──────────────────────────
-- PostgREST guarda en memoria una copia del esquema. Las columnas
-- nuevas no existen para él hasta que se refresca, y hasta entonces
-- la app falla con "PGRST204 — Could not find the ... column".
NOTIFY pgrst, 'reload schema';

-- ── 6. VERIFICACIÓN ──────────────────────────────────────────
SELECT tablename, policyname, cmd
FROM   pg_policies
WHERE  schemaname = 'public'
  AND  policyname LIKE 'cuidador%'
ORDER  BY tablename, policyname;
