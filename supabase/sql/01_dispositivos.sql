-- ═══════════════════════════════════════════════════════════════
--  Tabla de dispositivos (tokens de push FCM)
--
--  Corré esto en Supabase ▸ SQL Editor ▸ New query ▸ Run.
--  Es idempotente: se puede correr más de una vez sin romper nada.
-- ═══════════════════════════════════════════════════════════════

create table if not exists public.dispositivos (
    id_dispositivo bigint generated always as identity primary key,
    id_usuario     bigint not null
                   references public.usuarios (id_usuario) on delete cascade,
    -- El token identifica al CELULAR, no a la persona. Es único: si en el
    -- mismo aparato inicia sesión otra persona, la fila se reasigna en vez
    -- de duplicarse (por eso la app hace upsert sobre esta constraint).
    token          text not null unique,
    plataforma     text not null default 'android',
    actualizado    timestamptz not null default now()
);

create index if not exists dispositivos_id_usuario_idx
    on public.dispositivos (id_usuario);

-- ── Seguridad ──────────────────────────────────────────────────
alter table public.dispositivos enable row level security;

-- Cada quien administra únicamente los tokens de sus propios aparatos.
-- La Edge Function los lee con la service_role key, que saltea RLS.

drop policy if exists "dispositivos propios: ver" on public.dispositivos;
create policy "dispositivos propios: ver"
    on public.dispositivos for select
    using (
        id_usuario in (
            select id_usuario from public.usuarios
            where auth_user_id = auth.uid()
        )
    );

drop policy if exists "dispositivos propios: registrar" on public.dispositivos;
create policy "dispositivos propios: registrar"
    on public.dispositivos for insert
    with check (
        id_usuario in (
            select id_usuario from public.usuarios
            where auth_user_id = auth.uid()
        )
    );

drop policy if exists "dispositivos propios: actualizar" on public.dispositivos;
create policy "dispositivos propios: actualizar"
    on public.dispositivos for update
    using (
        id_usuario in (
            select id_usuario from public.usuarios
            where auth_user_id = auth.uid()
        )
    );

drop policy if exists "dispositivos propios: borrar" on public.dispositivos;
create policy "dispositivos propios: borrar"
    on public.dispositivos for delete
    using (
        id_usuario in (
            select id_usuario from public.usuarios
            where auth_user_id = auth.uid()
        )
    );
