\set ON_ERROR_STOP on

create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;
create schema auth;

create function auth.uid()
returns uuid
language sql
stable
as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;

create table public.community_profiles (
    id uuid primary key,
    auth_user_id uuid unique,
    is_admin boolean not null default false
);

create function public.quata_current_profile_id()
returns uuid
language sql
stable
security definer
set search_path = public, auth
as $$
    select cp.id
    from public.community_profiles cp
    where cp.id = auth.uid() or cp.auth_user_id = auth.uid()
    limit 1
$$;

create function public.quata_current_profile_is_admin()
returns boolean
language sql
stable
security definer
set search_path = public, auth
as $$
    select coalesce((
        select cp.is_admin
        from public.community_profiles cp
        where cp.id = public.quata_current_profile_id()
        limit 1
    ), false)
$$;

create function public.quata_current_role_is_service()
returns boolean
language sql
stable
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
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_actor uuid := public.quata_current_profile_id();
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

-- Pre-migration regression proof: SECURITY DEFINER makes current_user postgres,
-- so A can create a row attributed to B.
set role authenticated;
select set_config('request.jwt.claim.role', 'authenticated', false);
select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000001', false);
insert into public.official_post_likes(official_post_id, profile_id)
values ('30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002');
reset role;

do $$
begin
    if not exists (
        select 1 from public.official_post_likes
        where profile_id = '10000000-0000-4000-8000-000000000002'
    ) then
        raise exception 'pre_migration_spoof_was_not_reproduced';
    end if;
end;
$$;
truncate public.official_post_likes;

\ir ../supabase/migrations/20260726171002_official_post_likes_actor_guard.sql

set role authenticated;
select set_config('request.jwt.claim.role', 'authenticated', false);

-- A can create its own like.
select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000001', false);
insert into public.official_post_likes(official_post_id, profile_id)
values ('30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001');

-- A cannot create a like attributed to B.
do $$
begin
    begin
        insert into public.official_post_likes(official_post_id, profile_id)
        values ('30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002');
        raise exception 'spoof_insert_was_accepted';
    exception
        when insufficient_privilege then null;
    end;
end;
$$;

-- B can create its own like.
select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000002', false);
insert into public.official_post_likes(official_post_id, profile_id)
values ('30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002');

-- A cannot delete B's like and receives SQLSTATE 42501.
select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000001', false);
do $$
begin
    begin
        delete from public.official_post_likes
        where profile_id = '10000000-0000-4000-8000-000000000002';
        raise exception 'cross_profile_delete_was_accepted';
    exception
        when insufficient_privilege then null;
    end;
end;
$$;

do $$
begin
    if not exists (
        select 1 from public.official_post_likes
        where profile_id = '10000000-0000-4000-8000-000000000002'
    ) then
        raise exception 'cross_profile_delete_changed_row';
    end if;
end;
$$;

-- A and B can delete their own likes.
delete from public.official_post_likes
where profile_id = '10000000-0000-4000-8000-000000000001';
select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000002', false);
delete from public.official_post_likes
where profile_id = '10000000-0000-4000-8000-000000000002';
reset role;

-- Anonymous SELECT remains available and cleanup is complete.
set role anon;
select count(*) from public.official_post_likes;
reset role;

do $$
begin
    if exists (select 1 from public.official_post_likes) then
        raise exception 'test_cleanup_failed';
    end if;
end;
$$;

\echo 'Official likes RLS pre/post migration test passed.'
