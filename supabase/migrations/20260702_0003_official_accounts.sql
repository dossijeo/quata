-- Official accounts and official wall.
-- Public read remains available for anonymous clients. Mutations are guarded by
-- Supabase Auth identity mapped to public.community_profiles.auth_user_id.

create extension if not exists pgcrypto;

alter table public.community_profiles
    add column if not exists is_admin boolean not null default false,
    add column if not exists is_official boolean not null default false;

create index if not exists community_profiles_is_admin_idx
    on public.community_profiles(is_admin)
    where is_admin = true;

create index if not exists community_profiles_is_official_idx
    on public.community_profiles(is_official)
    where is_official = true;

-- Initial administrators requested for the first official-accounts phase.
update public.community_profiles
set is_admin = true
where id in (
        '757edf3f-8b7f-40cf-b775-cd3ef3b07f4c'::uuid,
        'f99a8051-4ef8-4b93-984c-7a011937919f'::uuid
    )
   or lower(coalesce(display_name, nombre, '')) in ('gabriel', 'juan')
   or regexp_replace(coalesce(phone_e164, phone, telefono, concat(coalesce(country_code, ''), coalesce(phone_local, '')), ''), '\D', '', 'g')
        in ('34680242606', '240555352016');

-- Make Gabriel official too so the existing test user can exercise the flow.
update public.community_profiles
set is_official = true
where id = '757edf3f-8b7f-40cf-b775-cd3ef3b07f4c'::uuid
   or lower(coalesce(display_name, nombre, '')) = 'gabriel'
   or regexp_replace(coalesce(phone_e164, phone, telefono, concat(coalesce(country_code, ''), coalesce(phone_local, '')), ''), '\D', '', 'g')
        = '34680242606';

create table if not exists public.official_posts (
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
    constraint official_posts_type_check
        check (post_type in ('announcement', 'news', 'event', 'urgent')),
    constraint official_posts_media_type_check
        check (media_type is null or media_type in ('image', 'video')),
    constraint official_posts_content_check
        check (char_length(trim(content_html)) > 0)
);

create index if not exists official_posts_published_idx
    on public.official_posts(published_at desc, created_at desc)
    where is_published = true and deleted_at is null;

create index if not exists official_posts_profile_idx
    on public.official_posts(profile_id, published_at desc);

create table if not exists public.official_post_likes (
    id uuid primary key default gen_random_uuid(),
    official_post_id uuid not null references public.official_posts(id) on delete cascade,
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint official_post_likes_unique unique (official_post_id, profile_id)
);

create index if not exists official_post_likes_post_idx
    on public.official_post_likes(official_post_id);

create table if not exists public.official_post_comments (
    id uuid primary key default gen_random_uuid(),
    official_post_id uuid not null references public.official_posts(id) on delete cascade,
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    body text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    deleted_at timestamptz,
    constraint official_post_comments_body_check
        check (char_length(trim(body)) > 0)
);

create index if not exists official_post_comments_post_idx
    on public.official_post_comments(official_post_id, created_at);

create or replace function public.quata_current_profile_id()
returns uuid
language plpgsql
stable
security definer
set search_path = public, auth
as $$
declare
    v_auth_uid uuid := auth.uid();
    v_profile_id uuid;
begin
    if v_auth_uid is null then
        return null;
    end if;

    select cp.id
    into v_profile_id
    from public.community_profiles cp
    where cp.id = v_auth_uid
       or cp.auth_user_id = v_auth_uid
    limit 1;

    return v_profile_id;
end;
$$;

create or replace function public.quata_current_profile_is_admin()
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
    ), false);
$$;

create or replace function public.quata_current_role_is_service()
returns boolean
language sql
stable
as $$
    select coalesce(current_setting('request.jwt.claim.role', true), '') = 'service_role'
        or current_user in ('postgres', 'supabase_admin', 'service_role');
$$;

create or replace function public.quata_guard_profile_roles()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
begin
    if tg_op = 'UPDATE'
       and (new.is_admin is distinct from old.is_admin
            or new.is_official is distinct from old.is_official) then
        if public.quata_current_role_is_service() then
            return new;
        end if;

        if not public.quata_current_profile_is_admin() then
            raise exception 'Only administrators can change official roles'
                using errcode = '42501';
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists quata_guard_profile_roles_trg on public.community_profiles;
create trigger quata_guard_profile_roles_trg
before update on public.community_profiles
for each row execute function public.quata_guard_profile_roles();

create or replace function public.quata_guard_official_posts()
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

drop trigger if exists quata_guard_official_posts_trg on public.official_posts;
create trigger quata_guard_official_posts_trg
before insert or update or delete on public.official_posts
for each row execute function public.quata_guard_official_posts();

create or replace function public.quata_guard_official_post_likes()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_actor uuid := public.quata_current_profile_id();
begin
    if public.quata_current_role_is_service() then
        if tg_op = 'DELETE' then
            return old;
        end if;
        return new;
    end if;

    if v_actor is null then
        raise exception 'Authentication required'
            using errcode = '42501';
    end if;

    if tg_op = 'INSERT' and new.profile_id <> v_actor then
        raise exception 'Likes must be created by the current profile'
            using errcode = '42501';
    end if;

    if tg_op = 'DELETE' and old.profile_id <> v_actor and not public.quata_current_profile_is_admin() then
        raise exception 'Only the like owner or an administrator can remove this like'
            using errcode = '42501';
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

drop trigger if exists quata_guard_official_post_likes_trg on public.official_post_likes;
create trigger quata_guard_official_post_likes_trg
before insert or delete on public.official_post_likes
for each row execute function public.quata_guard_official_post_likes();

create or replace function public.quata_guard_official_post_comments()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_actor uuid := public.quata_current_profile_id();
begin
    if public.quata_current_role_is_service() then
        if tg_op = 'UPDATE' then
            new.updated_at = now();
        end if;
        if tg_op = 'DELETE' then
            return old;
        end if;
        return new;
    end if;

    if v_actor is null then
        raise exception 'Authentication required'
            using errcode = '42501';
    end if;

    if tg_op = 'INSERT' and new.profile_id <> v_actor then
        raise exception 'Comments must be created by the current profile'
            using errcode = '42501';
    end if;

    if tg_op in ('UPDATE', 'DELETE')
       and old.profile_id <> v_actor
       and not public.quata_current_profile_is_admin() then
        raise exception 'Only the comment owner or an administrator can change this comment'
            using errcode = '42501';
    end if;

    if tg_op = 'UPDATE' then
        new.updated_at = now();
        return new;
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

drop trigger if exists quata_guard_official_post_comments_trg on public.official_post_comments;
create trigger quata_guard_official_post_comments_trg
before insert or update or delete on public.official_post_comments
for each row execute function public.quata_guard_official_post_comments();

revoke all privileges on public.official_posts from anon;
revoke all privileges on public.official_post_likes from anon;
revoke all privileges on public.official_post_comments from anon;

revoke truncate, references, trigger on public.official_posts from authenticated;
revoke truncate, references, trigger on public.official_post_likes from authenticated;
revoke update, truncate, references, trigger on public.official_post_likes from authenticated;
revoke truncate, references, trigger on public.official_post_comments from authenticated;

grant select on public.official_posts to anon, authenticated;
grant select on public.official_post_likes to anon, authenticated;
grant select on public.official_post_comments to anon, authenticated;

grant insert, update, delete on public.official_posts to authenticated;
grant insert, delete on public.official_post_likes to authenticated;
grant insert, update, delete on public.official_post_comments to authenticated;
grant update (is_admin, is_official) on public.community_profiles to authenticated;

do $$
begin
    alter publication supabase_realtime add table public.official_posts;
exception when duplicate_object then null;
end $$;

do $$
begin
    alter publication supabase_realtime add table public.official_post_likes;
exception when duplicate_object then null;
end $$;

do $$
begin
    alter publication supabase_realtime add table public.official_post_comments;
exception when duplicate_object then null;
end $$;
