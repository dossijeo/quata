\set ON_ERROR_STOP on

create role anon nologin;
create role authenticated nologin;
grant anon, authenticated to current_user;

create extension if not exists pgcrypto;

create table public.community_post_likes (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null,
    profile_id uuid not null,
    created_at timestamptz not null default now(),
    unique (post_id, profile_id)
);

create function public.quata_chat_auth_profile_id()
returns uuid
language sql
stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.profile_id', true), ''),
        nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'profile_id'
    )::uuid
$$;
grant execute on function public.quata_chat_auth_profile_id() to authenticated;

alter table public.community_post_likes enable row level security;
create policy "public delete likes" on public.community_post_likes
for delete to public using (true);
create policy "public insert likes" on public.community_post_likes
for insert to public with check (true);
create policy "public read likes" on public.community_post_likes
for select to public using (true);
grant delete, insert, references, select, trigger, truncate, update
on table public.community_post_likes to anon, authenticated;

\ir ../../supabase/migrations/20261002010000_community_post_likes_actor_guard.sql

do $$
declare
    v_policies text[];
    v_anon text[];
    v_authenticated text[];
begin
    select array_agg(policyname || ':' || cmd || ':' || roles::text order by policyname)
      into v_policies
      from pg_policies
     where schemaname = 'public'
       and tablename = 'community_post_likes';
    if v_policies <> array[
        'community_post_likes_delete_own:DELETE:{authenticated}',
        'community_post_likes_insert_own:INSERT:{authenticated}',
        'community_post_likes_public_read:SELECT:{public}'
    ] then
        raise exception 'unexpected forward policies: %', v_policies;
    end if;

    select array_agg(privilege_type order by privilege_type)
      into v_anon
      from information_schema.role_table_grants
     where table_schema = 'public'
       and table_name = 'community_post_likes'
       and grantee = 'anon';
    if v_anon <> array['SELECT'] then
        raise exception 'unexpected anon grants: %', v_anon;
    end if;

    select array_agg(privilege_type order by privilege_type)
      into v_authenticated
      from information_schema.role_table_grants
     where table_schema = 'public'
       and table_name = 'community_post_likes'
       and grantee = 'authenticated';
    if v_authenticated <> array['DELETE', 'INSERT', 'SELECT'] then
        raise exception 'unexpected authenticated grants: %', v_authenticated;
    end if;
end $$;

set role authenticated;
select set_config('request.jwt.claim.profile_id', '00000000-0000-0000-0000-000000000001', false);
insert into public.community_post_likes(post_id, profile_id)
values (
    '10000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001'
);

do $$
declare
    v_rejected boolean := false;
begin
    begin
        insert into public.community_post_likes(post_id, profile_id)
        values (
            '10000000-0000-0000-0000-000000000002',
            '00000000-0000-0000-0000-000000000002'
        );
    exception when insufficient_privilege or check_violation then
        v_rejected := true;
    end;
    if not v_rejected then
        raise exception 'cross-actor insert unexpectedly succeeded';
    end if;
end $$;

reset role;
set role authenticated;
select set_config('request.jwt.claim.profile_id', '00000000-0000-0000-0000-000000000002', false);
insert into public.community_post_likes(post_id, profile_id)
values (
    '10000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000002'
);

reset role;
set role authenticated;
select set_config('request.jwt.claim.profile_id', '00000000-0000-0000-0000-000000000001', false);
delete from public.community_post_likes
where post_id = '10000000-0000-0000-0000-000000000002'
  and profile_id = '00000000-0000-0000-0000-000000000002';
reset role;

do $$
begin
    if not exists (
        select 1 from public.community_post_likes
        where post_id = '10000000-0000-0000-0000-000000000002'
          and profile_id = '00000000-0000-0000-0000-000000000002'
    ) then
        raise exception 'cross-actor delete removed the owner row';
    end if;
end $$;

set role anon;
do $$
declare
    v_insert_rejected boolean := false;
    v_delete_rejected boolean := false;
begin
    begin
        insert into public.community_post_likes(post_id, profile_id)
        values (
            '10000000-0000-0000-0000-000000000003',
            '00000000-0000-0000-0000-000000000003'
        );
    exception when insufficient_privilege then
        v_insert_rejected := true;
    end;
    begin
        delete from public.community_post_likes
        where post_id = '10000000-0000-0000-0000-000000000001';
    exception when insufficient_privilege then
        v_delete_rejected := true;
    end;
    if not v_insert_rejected or not v_delete_rejected then
        raise exception 'anon mutation was not rejected: insert %, delete %',
            v_insert_rejected, v_delete_rejected;
    end if;
    if (select count(*) from public.community_post_likes) <> 2 then
        raise exception 'public read did not preserve both visible rows';
    end if;
end $$;
reset role;

set role authenticated;
select set_config('request.jwt.claim.profile_id', '00000000-0000-0000-0000-000000000001', false);
delete from public.community_post_likes
where post_id = '10000000-0000-0000-0000-000000000001'
  and profile_id = '00000000-0000-0000-0000-000000000001';
reset role;

set role authenticated;
select set_config('request.jwt.claim.profile_id', '00000000-0000-0000-0000-000000000002', false);
delete from public.community_post_likes
where post_id = '10000000-0000-0000-0000-000000000002'
  and profile_id = '00000000-0000-0000-0000-000000000002';
reset role;

do $$
begin
    if exists (select 1 from public.community_post_likes) then
        raise exception 'own delete did not remove the row';
    end if;
end $$;

\ir ../../supabase/rollbacks/20261002010000_community_post_likes_actor_guard.rollback.sql

do $$
declare
    v_policies text[];
    v_anon text[];
    v_authenticated text[];
    v_expected text[] := array[
        'DELETE', 'INSERT', 'REFERENCES', 'SELECT', 'TRIGGER', 'TRUNCATE', 'UPDATE'
    ];
begin
    select array_agg(policyname || ':' || cmd || ':' || roles::text order by policyname)
      into v_policies
      from pg_policies
     where schemaname = 'public'
       and tablename = 'community_post_likes';
    if v_policies <> array[
        'public delete likes:DELETE:{public}',
        'public insert likes:INSERT:{public}',
        'public read likes:SELECT:{public}'
    ] then
        raise exception 'rollback did not restore prior policies: %', v_policies;
    end if;

    select array_agg(privilege_type order by privilege_type)
      into v_anon
      from information_schema.role_table_grants
     where table_schema = 'public'
       and table_name = 'community_post_likes'
       and grantee = 'anon';
    select array_agg(privilege_type order by privilege_type)
      into v_authenticated
      from information_schema.role_table_grants
     where table_schema = 'public'
       and table_name = 'community_post_likes'
       and grantee = 'authenticated';
    if v_anon <> v_expected or v_authenticated <> v_expected then
        raise exception 'rollback did not restore grants: anon %, authenticated %',
            v_anon, v_authenticated;
    end if;
end $$;

select 'COMMUNITY_POST_LIKES_ACTOR_GUARD_TEST_OK';
