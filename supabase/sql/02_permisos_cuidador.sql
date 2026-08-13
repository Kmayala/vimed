-- ═══════════════════════════════════════════════════════════════
--  Permisos del CUIDADOR sobre los datos del adulto mayor
--
--  Sin esto, cuando el cuidador intenta cargar un medicamento o
--  cambiar un stock, Supabase responde 403 y la app muestra
--  "Sin permisos para esta operación (RLS)".
--
--  Las políticas de RLS son PERMISIVAS: se suman con OR a las que ya
--  tenés. Agregar estas NO le saca permisos a nadie — solo habilita
--  al familiar vinculado.
--
--  Corré esto en Supabase ▸ SQL Editor ▸ New query ▸ Run.
-- ═══════════════════════════════════════════════════════════════

-- ── Helper: ¿soy cuidador de esta persona? ─────────────────────
-- Centraliza la pregunta para no repetir el subselect en cada política.
create or replace function public.es_cuidador_de(p_id_adulto bigint)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.vinculacion_familiar v
        join public.usuarios u on u.id_usuario = v.id_familiar
        where v.id_adulto = p_id_adulto
          and u.auth_user_id = auth.uid()
    );
$$;

-- ── MEDICAMENTOS ───────────────────────────────────────────────
drop policy if exists "cuidador: ver medicamentos" on public.medicamentos;
create policy "cuidador: ver medicamentos"
    on public.medicamentos for select
    using (public.es_cuidador_de(id_usuario));

drop policy if exists "cuidador: cargar medicamentos" on public.medicamentos;
create policy "cuidador: cargar medicamentos"
    on public.medicamentos for insert
    with check (public.es_cuidador_de(id_usuario));

drop policy if exists "cuidador: editar medicamentos" on public.medicamentos;
create policy "cuidador: editar medicamentos"
    on public.medicamentos for update
    using (public.es_cuidador_de(id_usuario))
    with check (public.es_cuidador_de(id_usuario));

-- ── HORARIOS (cuelgan del medicamento) ─────────────────────────
drop policy if exists "cuidador: ver horarios" on public.horarios;
create policy "cuidador: ver horarios"
    on public.horarios for select
    using (
        exists (
            select 1 from public.medicamentos m
            where m.id_medicamento = horarios.id_medicamento
              and public.es_cuidador_de(m.id_usuario)
        )
    );

drop policy if exists "cuidador: cargar horarios" on public.horarios;
create policy "cuidador: cargar horarios"
    on public.horarios for insert
    with check (
        exists (
            select 1 from public.medicamentos m
            where m.id_medicamento = horarios.id_medicamento
              and public.es_cuidador_de(m.id_usuario)
        )
    );

-- ── CITAS MÉDICAS ──────────────────────────────────────────────
drop policy if exists "cuidador: ver citas" on public.citas_medicas;
create policy "cuidador: ver citas"
    on public.citas_medicas for select
    using (public.es_cuidador_de(id_usuario));

drop policy if exists "cuidador: cargar citas" on public.citas_medicas;
create policy "cuidador: cargar citas"
    on public.citas_medicas for insert
    with check (public.es_cuidador_de(id_usuario));

-- ── REGISTRO DE TOMAS (solo lectura: monitoreo) ────────────────
-- El cuidador ve si tomó o no, pero no confirma tomas por él.
drop policy if exists "cuidador: ver tomas" on public.registro_tomas;
create policy "cuidador: ver tomas"
    on public.registro_tomas for select
    using (public.es_cuidador_de(id_usuario));

-- ── NOTIFICACIONES (historial que ve en el panel) ──────────────
drop policy if exists "cuidador: ver notificaciones" on public.notificaciones;
create policy "cuidador: ver notificaciones"
    on public.notificaciones for select
    using (public.es_cuidador_de(id_destinatario));

-- ── PERFIL del adulto (nombre y correo en la tarjeta) ──────────
drop policy if exists "cuidador: ver perfil del adulto" on public.usuarios;
create policy "cuidador: ver perfil del adulto"
    on public.usuarios for select
    using (public.es_cuidador_de(id_usuario));
