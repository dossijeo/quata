\set ON_ERROR_STOP on

-- Minimal catalog fixture representing the deployed pre-RLS-002 shape. It is
-- intentionally usable through PostgREST as both anon and authenticated.
create extension if not exists pgcrypto;
create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;
create role authenticator login noinherit password 'isolated-postgrest-password';
grant anon, authenticated, service_role to authenticator;
create schema auth;

create function auth.uid()
returns uuid language sql stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    )::uuid
$$;

create table public.community_profiles (
    id uuid primary key,
    auth_user_id uuid unique,
    is_admin boolean not null default false
);

create function public.quata_current_profile_id()
returns uuid language sql stable security definer set search_path = public, auth
as $$
    select cp.id from public.community_profiles cp
    where cp.id = auth.uid() or cp.auth_user_id = auth.uid() limit 1
$$;

create function public.quata_current_profile_is_admin()
returns boolean language sql stable security definer set search_path = public, auth
as $$
    select coalesce((select cp.is_admin from public.community_profiles cp
        where cp.id = public.quata_current_profile_id() limit 1), false)
$$;

create function public.quata_current_role_is_service()
returns boolean language sql stable
as $$
    select coalesce(current_setting('request.jwt.claim.role', true), '') = 'service_role'
        or current_user in ('postgres', 'supabase_admin', 'service_role')
$$;

create table public.official_post_likes (
    id uuid primary key default gen_random_uuid(),
    official_post_id uuid not null,
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    unique (official_post_id, profile_id)
);

create function public.quata_guard_official_post_likes()
returns trigger language plpgsql security definer set search_path = public, auth
as $$
declare v_actor uuid := public.quata_current_profile_id();
begin
    if public.quata_current_role_is_service() then
        if tg_op = 'DELETE' then return old; end if;
        return new;
    end if;
    if v_actor is null then raise exception 'Authentication required' using errcode = '42501'; end if;
    if tg_op = 'INSERT' and new.profile_id <> v_actor then
        raise exception 'Likes must be created by the current profile' using errcode = '42501';
    end if;
    if tg_op = 'DELETE' and old.profile_id <> v_actor and not public.quata_current_profile_is_admin() then
        raise exception 'Only the like owner or an administrator can remove this like' using errcode = '42501';
    end if;
    if tg_op = 'DELETE' then return old; end if;
    return new;
end;
$$;

create trigger quata_guard_official_post_likes_trg
before insert or delete on public.official_post_likes
for each row execute function public.quata_guard_official_post_likes();

grant usage on schema public, auth to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;
grant execute on function public.quata_current_profile_id() to authenticated;
grant execute on function public.quata_current_profile_is_admin() to authenticated;
grant execute on function public.quata_current_role_is_service() to authenticated;
grant select on public.community_profiles to authenticated;
grant select on public.official_post_likes to anon, authenticated;
grant insert, delete on public.official_post_likes to authenticated;

insert into public.community_profiles(id, auth_user_id) values
    ('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'),
    ('10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002');
-- A pre-existing production-like row must survive apply, rollback and reapply.
insert into public.official_post_likes(id, official_post_id, profile_id) values
    ('40000000-0000-4000-8000-000000000009',
     '30000000-0000-4000-8000-000000000009',
     '10000000-0000-4000-8000-000000000002');

\echo 'Official likes pre-RLS-002 fixture ready.'
