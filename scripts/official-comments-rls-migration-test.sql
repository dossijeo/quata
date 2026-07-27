\set ON_ERROR_STOP on
create extension if not exists pgcrypto;
create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;
create schema auth;

create function auth.uid() returns uuid language sql stable as $$
 select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
create table public.community_profiles (id uuid primary key, auth_user_id uuid unique, is_admin boolean not null default false);
create function public.quata_current_profile_id() returns uuid language sql stable security definer set search_path = public, auth as $$
 select id from public.community_profiles where id = auth.uid() or auth_user_id = auth.uid() limit 1
$$;
create function public.quata_current_profile_is_admin() returns boolean language sql stable security definer set search_path = public, auth as $$
 select coalesce((select is_admin from public.community_profiles where id = public.quata_current_profile_id()), false)
$$;
create function public.quata_current_role_is_service() returns boolean language sql stable as $$
 select current_user in ('postgres', 'service_role')
$$;
create table public.official_post_comments (
 id uuid primary key default gen_random_uuid(), official_post_id uuid not null, profile_id uuid not null references public.community_profiles(id),
 body text not null default '', created_at timestamptz not null default now(), updated_at timestamptz, deleted_at timestamptz
);
create function public.quata_guard_official_post_comments() returns trigger language plpgsql security definer set search_path = public, auth as $$
begin return case when tg_op = 'DELETE' then old else new end; end;
$$;
create trigger quata_guard_official_post_comments_trg before insert or update or delete on public.official_post_comments for each row execute function public.quata_guard_official_post_comments();
grant usage on schema public, auth to anon, authenticated;
grant execute on function auth.uid(), public.quata_current_profile_id(), public.quata_current_profile_is_admin(), public.quata_current_role_is_service() to authenticated;
grant select on public.community_profiles to authenticated;
grant select on public.official_post_comments to anon, authenticated;
grant insert, update, delete on public.official_post_comments to authenticated;
insert into public.community_profiles(id, auth_user_id, is_admin) values
 ('10000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001',false),
 ('10000000-0000-4000-8000-000000000002','20000000-0000-4000-8000-000000000002',false),
 ('10000000-0000-4000-8000-000000000003','20000000-0000-4000-8000-000000000003',true);
insert into public.official_post_comments(id, official_post_id, profile_id, body) values
 ('40000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','original'),
 ('40000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','moderation target');
\ir ../supabase/migrations/20260727120001_official_post_comments_actor_guard.sql

-- Matrix: actor legitimate edits; outsider denied; admin may moderate content;
-- inactive authenticated identity is denied; profile_id never changes.
set role authenticated;
set request.jwt.claim.sub = '20000000-0000-4000-8000-000000000001';
update public.official_post_comments set body = 'actor edit' where id = '40000000-0000-4000-8000-000000000001';
insert into public.official_post_comments(id, official_post_id, profile_id, body) values
 ('40000000-0000-4000-8000-000000000003','30000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','actor insert');
delete from public.official_post_comments where id = '40000000-0000-4000-8000-000000000003';
do $$ begin
 begin update public.official_post_comments set profile_id = '10000000-0000-4000-8000-000000000002' where id = '40000000-0000-4000-8000-000000000001'; raise exception 'profile_id mutation accepted';
 exception when insufficient_privilege then null; end;
end $$;
set request.jwt.claim.sub = '20000000-0000-4000-8000-000000000002';
do $$ begin
 begin update public.official_post_comments set body = 'outsider edit' where id = '40000000-0000-4000-8000-000000000001'; raise exception 'outsider mutation accepted';
 exception when insufficient_privilege then null; end;
 begin delete from public.official_post_comments where id = '40000000-0000-4000-8000-000000000001'; raise exception 'outsider delete accepted';
 exception when insufficient_privilege then null; end;
end $$;
set request.jwt.claim.sub = '20000000-0000-4000-8000-000000000003';
update public.official_post_comments set body = 'admin edit' where id = '40000000-0000-4000-8000-000000000001';
delete from public.official_post_comments where id = '40000000-0000-4000-8000-000000000002';
set request.jwt.claim.sub = '20000000-0000-4000-8000-000000000009';
do $$ begin
 begin update public.official_post_comments set body = 'inactive edit' where id = '40000000-0000-4000-8000-000000000001'; raise exception 'inactive mutation accepted';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
do $$ begin
 if (select profile_id from public.official_post_comments where id = '40000000-0000-4000-8000-000000000001') <> '10000000-0000-4000-8000-000000000001'::uuid then raise exception 'profile_id changed'; end if;
 if (select body from public.official_post_comments where id = '40000000-0000-4000-8000-000000000001') <> 'admin edit' then raise exception 'legitimate admin edit missing'; end if;
 if exists (select 1 from public.official_post_comments where id in ('40000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000003')) then raise exception 'legitimate delete missing'; end if;
end $$;
\ir ../supabase/rollbacks/20260727120001_official_post_comments_actor_guard.rollback.sql
do $$ begin
 if (select relrowsecurity from pg_class where oid = 'public.official_post_comments'::regclass) then raise exception 'rollback left RLS enabled'; end if;
 if to_regprocedure('public.quata_official_comment_mutation_allowed(uuid)') is not null then raise exception 'rollback left helper'; end if;
end $$;
\ir ../supabase/migrations/20260727120001_official_post_comments_actor_guard.sql
\echo 'Official comments actor/outsider/admin/inactive and rollback/reapply contract passed.'
