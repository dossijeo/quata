\set ON_ERROR_STOP on

create extension if not exists pgcrypto;
create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;
create schema auth;

create function auth.uid()
returns uuid language sql stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    )::uuid
$$;

create function auth.role()
returns text language sql stable
as $$
    select coalesce(current_setting('request.jwt.claim.role', true), current_user)
$$;

create table public.community_profiles (
    id uuid primary key,
    auth_user_id uuid unique,
    display_name text not null default '',
    is_admin boolean not null default false,
    is_official boolean not null default false,
    account_status text not null default 'active'
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

create function public.quata_requested_official_post_language()
returns text language sql stable
as $$ select 'es'::text $$;

create table public.official_posts (
    id uuid primary key default gen_random_uuid(),
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    title text not null default '',
    summary text,
    post_type text not null default 'announcement',
    content_html text not null default '',
    media_url text,
    media_type text,
    link_url text,
    is_live boolean not null default false,
    is_published boolean not null default true,
    published_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    read_more_label text not null default 'read_more',
    language text not null default 'es',
    translation_group_id uuid not null default gen_random_uuid(),
    constraint official_posts_type_check check (post_type in ('announcement', 'news', 'event', 'urgent')),
    constraint official_posts_media_type_check check (media_type is null or media_type in ('image', 'video')),
    constraint official_posts_content_check check (char_length(trim(content_html)) > 0),
    constraint official_posts_language_check check (language in ('es', 'en', 'fr'))
);

create function public.quata_guard_official_posts()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_actor uuid := public.quata_current_profile_id();
    v_is_official boolean := false;
begin
    if public.quata_current_role_is_service() then
        if tg_op = 'INSERT' or tg_op = 'UPDATE' then
            new.updated_at = now();
            if tg_op = 'INSERT' and new.published_at is null then
                new.published_at = now();
            end if;
            return new;
        end if;
        return old;
    end if;

    if v_actor is null then
        raise exception 'Authentication required'
            using errcode = '42501';
    end if;

    if tg_op = 'INSERT' then
        if new.profile_id <> v_actor then
            raise exception 'Official posts must be created by the current profile'
                using errcode = '42501';
        end if;

        select cp.is_official into v_is_official
        from public.community_profiles cp
        where cp.id = v_actor;

        if not coalesce(v_is_official, false) then
            raise exception 'Only official accounts can publish official posts'
                using errcode = '42501';
        end if;

        new.updated_at = now();
        new.published_at = coalesce(new.published_at, now());
        return new;
    end if;

    if tg_op = 'UPDATE' then
        if new.profile_id is distinct from old.profile_id then
            raise exception 'Official post author cannot be changed'
                using errcode = '42501';
        end if;

        if old.profile_id <> v_actor and not public.quata_current_profile_is_admin() then
            raise exception 'Only the official author or an administrator can update this post'
                using errcode = '42501';
        end if;

        new.updated_at = now();
        return new;
    end if;

    if tg_op = 'DELETE' then
        if old.profile_id <> v_actor and not public.quata_current_profile_is_admin() then
            raise exception 'Only the official author or an administrator can delete this post'
                using errcode = '42501';
        end if;
        return old;
    end if;

    return new;
end;
$$;

create trigger quata_guard_official_posts_trg
before insert or update or delete on public.official_posts
for each row execute function public.quata_guard_official_posts();

alter table public.official_posts enable row level security;
create policy official_posts_public_read_language on public.official_posts
for select to anon, authenticated using (true);
create policy official_posts_authenticated_insert on public.official_posts
for insert to authenticated with check (true);
create policy official_posts_authenticated_update_guarded on public.official_posts
for update to authenticated using (true) with check (true);
create policy official_posts_authenticated_delete_guarded on public.official_posts
for delete to authenticated using (true);

grant usage on schema public, auth to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;
grant execute on function auth.role() to anon, authenticated;
grant execute on function public.quata_current_profile_id() to authenticated;
grant execute on function public.quata_current_profile_is_admin() to authenticated;
grant execute on function public.quata_current_role_is_service() to authenticated;
grant execute on function public.quata_requested_official_post_language() to anon, authenticated;
grant select on public.community_profiles to authenticated;
grant select on public.official_posts to anon, authenticated;
grant insert, update, delete on public.official_posts to authenticated;

insert into public.community_profiles(id, auth_user_id, display_name, is_admin, is_official) values
    ('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 'official', false, true),
    ('10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002', 'nonofficial', false, false),
    ('10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000003', 'admin', true, false);

\echo 'Baseline fixture ready.'

set role authenticated;
select set_config('request.jwt.claim.role', 'authenticated', false);
select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000002', false);

insert into public.official_posts(id, profile_id, title, content_html, post_type, translation_group_id)
values (
    '30000000-0000-4000-8000-000000000002',
    '10000000-0000-4000-8000-000000000002',
    'baseline spoof',
    '<p>baseline spoof</p>',
    'news',
    '40000000-0000-4000-8000-000000000002'
);

reset role;

\echo 'Baseline spoof reproduced.'

\ir ../supabase/migrations/20260808_0001_official_posts_actor_guard.sql

set role authenticated;
select set_config('request.jwt.claim.role', 'authenticated', false);
select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000002', false);

do $$
begin
    insert into public.official_posts(id, profile_id, title, content_html, post_type, translation_group_id)
    values (
        '30000000-0000-4000-8000-000000000012',
        '10000000-0000-4000-8000-000000000002',
        'secured denied',
        '<p>secured denied</p>',
        'news',
        '40000000-0000-4000-8000-000000000012'
    );
    raise exception 'nonofficial insert was not denied';
exception when insufficient_privilege then
    null;
end $$;

select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000001', false);
insert into public.official_posts(id, profile_id, title, content_html, post_type, translation_group_id)
values (
    '30000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000001',
    'secured official',
    '<p>secured official</p>',
    'news',
    '40000000-0000-4000-8000-000000000001'
);

do $$
begin
    update public.official_posts
    set title = 'cross update denied'
    where id = '30000000-0000-4000-8000-000000000002';
    raise exception 'cross update was not denied';
exception when insufficient_privilege then
    null;
end $$;

update public.official_posts
set title = 'own update allowed'
where id = '30000000-0000-4000-8000-000000000001';

select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000003', false);
delete from public.official_posts
where id = '30000000-0000-4000-8000-000000000001';

reset role;

do $$
declare
    v_security_invoker boolean;
begin
    select not p.prosecdef
    into v_security_invoker
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'quata_guard_official_posts';

    if not coalesce(v_security_invoker, false) then
        raise exception 'official posts trigger must be security invoker';
    end if;
end $$;

\echo 'OFFICIAL_POSTS_RLS_MIGRATION_TEST_OK'
