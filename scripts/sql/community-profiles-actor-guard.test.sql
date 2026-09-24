\set ON_ERROR_STOP on

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
create schema auth;

create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;

grant anon, authenticated, service_role to postgres;
grant usage on schema public, auth to anon, authenticated, service_role;

create or replace function auth.uid()
returns uuid
language sql
stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    )::uuid;
$$;

create table public.community_profiles (
    id uuid primary key default gen_random_uuid(),
    display_name text not null,
    phone text not null,
    pass_hash text not null,
    created_at timestamptz not null default now(),
    last_login_at timestamptz,
    phone_normalized text not null,
    country_code text,
    phone_local text not null,
    phone_e164 text,
    barrio text,
    barrio_normalized text,
    home_community_id bigint,
    neighborhood text,
    code text,
    telefono text,
    nombre text,
    avatar_url text,
    secret_question text,
    secret_answer text,
    pass_plain text,
    avatar text,
    followers_count integer not null default 0,
    following_count integer not null default 0,
    auth_user_id uuid,
    is_admin boolean not null default false,
    is_official boolean not null default false,
    account_status text not null default 'active'
        check (account_status in ('active', 'deactivated')),
    deactivated_at timestamptz,
    deactivated_auth_user_id uuid
);

create table public.community_profile_follows (
    id uuid primary key default gen_random_uuid(),
    follower_profile_id uuid not null
        references public.community_profiles(id) on delete cascade,
    followed_profile_id uuid not null
        references public.community_profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    unique (follower_profile_id, followed_profile_id)
);

grant all on public.community_profiles to anon, authenticated, service_role;

create or replace function public.quata_current_profile_id()
returns uuid
language sql
stable
security definer
set search_path = public, auth
as $$
    select cp.id
    from public.community_profiles cp
    where cp.id = auth.uid()
       or cp.auth_user_id = auth.uid()
    limit 1;
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

create or replace function public.quata_chat_auth_profile_id()
returns uuid
language sql
stable
security definer
set search_path = public, auth
as $$
    select cp.id
    from public.community_profiles cp
    where auth.uid() is not null
      and cp.account_status = 'active'
      and (cp.id = auth.uid() or cp.auth_user_id = auth.uid())
    limit 1;
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
    return new;
end;
$$;

create trigger quata_guard_profile_roles_trg
before update on public.community_profiles
for each row execute function public.quata_guard_profile_roles();

create policy "public read profiles"
on public.community_profiles for select using (true);
create policy "public insert profiles"
on public.community_profiles for insert with check (true);
create policy "public update profiles"
on public.community_profiles for update using (true) with check (true);
alter table public.community_profiles enable row level security;

\i /workspace/supabase/migrations/20260726171003_community_profiles_actor_guard.sql
\i /workspace/supabase/migrations/20260924153500_android_v32_publishable_key_compatibility.sql
\i /workspace/supabase/migrations/20260924154500_android_v32_gateway_header_compatibility.sql

insert into public.community_profiles (
    id, display_name, phone, pass_hash, phone_normalized, phone_local,
    auth_user_id, is_admin
) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Actor A', '+34111', 'hash-a', '111', '111',
     '11111111-1111-4111-8111-111111111111', false),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Actor B', '+34222', 'hash-b', '222', '222',
     '22222222-2222-4222-8222-222222222222', false),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'Admin', '+34333', 'hash-c', '333', '333',
     '33333333-3333-4333-8333-333333333333', true),
    ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', 'Inactive owner', '+34777', 'hash-e', '777', '777',
     '44444444-4444-4444-8444-444444444444', false),
    ('ffffffff-ffff-4fff-8fff-ffffffffffff', 'Inactive admin', '+34888', 'hash-f', '888', '888',
     '55555555-5555-4555-8555-555555555555', true);

update public.community_profiles
set account_status = 'deactivated',
    deactivated_at = now()
where id in (
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    'ffffffff-ffff-4fff-8fff-ffffffffffff'
);

\set EXPECTED_ADMIN_SHA256 d2c931fdc9988046080e8d006024273b6c6111ae47852a4617d061ec1b7c16a9
\set EXPECTED_OFFICIAL_SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
\i /workspace/scripts/sql/community-profiles-rollout-preflight.sql
\unset EXPECTED_ADMIN_SHA256
\unset EXPECTED_OFFICIAL_SHA256

create or replace function public.test_exec_as(
    p_role text,
    p_sub uuid,
    p_sql text
) returns bigint
language plpgsql
as $$
declare
    v_rows bigint;
begin
    perform set_config('request.jwt.claim.sub', coalesce(p_sub::text, ''), true);
    perform set_config(
        'request.jwt.claim.role',
        case when p_role = 'service_role' then 'service_role' else p_role end,
        true
    );
    execute format('set local role %I', p_role);
    execute p_sql;
    get diagnostics v_rows = row_count;
    reset role;
    return v_rows;
exception
    when others then
        reset role;
        raise;
end;
$$;

create or replace function public.test_expect_42501(
    p_label text,
    p_role text,
    p_sub uuid,
    p_sql text
) returns void
language plpgsql
as $$
begin
    begin
        perform public.test_exec_as(p_role, p_sub, p_sql);
    exception
        when sqlstate '42501' then
            raise notice 'PASS % (42501)', p_label;
            return;
        when others then
            raise exception 'FAIL %: expected 42501, got % (%)',
                p_label, sqlstate, sqlerrm;
    end;
    raise exception 'FAIL %: statement unexpectedly succeeded', p_label;
end;
$$;

do $$
declare
    v_rows bigint;
    v_generated_id uuid;
begin
    v_rows := public.test_exec_as(
        'anon', null,
        $q$select id from public.community_profiles where display_name = 'Actor A'$q$
    );
    if v_rows <> 1 then
        raise exception 'FAIL anonymous public read: expected 1 row, got %', v_rows;
    end if;
    raise notice 'PASS anonymous public read';

    perform set_config('request.method', 'POST', true);
    perform set_config('request.path', '/community_profiles', true);
    perform set_config(
        'request.headers',
        '{"user-agent":"okhttp/4.12.0","apikey":"legacy-public-key","authorization":"Bearer legacy-public-key","content-profile":"public","prefer":"return=representation"}',
        true
    );
    perform public.test_exec_as(
        'anon', null,
        $q$insert into public.community_profiles (
               display_name, phone, pass_hash, phone_normalized, phone_local
           ) values (
               'Legacy registration', '+34444', 'hash-d', '444', '444'
           )$q$
    );
    select id into v_generated_id
    from public.community_profiles
    where display_name = 'Legacy registration';
    if v_generated_id is null then
        raise exception 'FAIL anonymous registration did not receive a server id';
    end if;
    raise notice 'PASS legacy anonymous registration with server id';

    perform set_config('request.method', '', true);
    perform set_config('request.path', '', true);
    perform set_config('request.headers', '{}', true);

    perform public.test_expect_42501(
        'anonymous id injection on insert',
        'anon', null,
        $q$insert into public.community_profiles (
               id, display_name, phone, pass_hash, phone_normalized, phone_local
           ) values (
               'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
               'Chosen identity', '+34994', 'hash-x', '994', '994'
           )$q$
    );

    perform public.test_expect_42501(
        'anonymous admin escalation on insert',
        'anon', null,
        $q$insert into public.community_profiles (
               display_name, phone, pass_hash, phone_normalized, phone_local, is_admin
           ) values ('Mallory', '+34999', 'hash-x', '999', '999', true)$q$
    );
    perform public.test_expect_42501(
        'anonymous auth identity injection on insert',
        'anon', null,
        $q$insert into public.community_profiles (
               display_name, phone, pass_hash, phone_normalized, phone_local,
               auth_user_id
           ) values (
               'Mallory identity', '+34998', 'hash-x', '998', '998',
               '99999999-9999-4999-8999-999999999999'
           )$q$
    );
    perform public.test_expect_42501(
        'anonymous lifecycle injection on insert',
        'anon', null,
        $q$insert into public.community_profiles (
               display_name, phone, pass_hash, phone_normalized, phone_local,
               account_status, deactivated_at
           ) values (
               'Mallory lifecycle', '+34997', 'hash-x', '997', '997',
               'deactivated', now()
           )$q$
    );
    perform public.test_expect_42501(
        'anonymous official role injection on insert',
        'anon', null,
        $q$insert into public.community_profiles (
               display_name, phone, pass_hash, phone_normalized, phone_local,
               is_official
           ) values (
               'Mallory official', '+34996', 'hash-x', '996', '996', true
           )$q$
    );
    perform public.test_expect_42501(
        'anonymous counter injection on insert',
        'anon', null,
        $q$insert into public.community_profiles (
               display_name, phone, pass_hash, phone_normalized, phone_local,
               followers_count
           ) values (
               'Mallory counters', '+34995', 'hash-x', '995', '995', 1000000
           )$q$
    );

    v_rows := public.test_exec_as(
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$update public.community_profiles
           set display_name = 'Actor A edited',
               phone_local = '1119',
               secret_question = 'new question',
               secret_answer = 'new answer'
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );
    if v_rows <> 1 then
        raise exception 'FAIL own profile update: expected 1 row, got %', v_rows;
    end if;
    raise notice 'PASS legitimate own profile update';

    v_rows := public.test_exec_as(
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$update public.community_profiles
           set display_name = 'Impersonated'
           where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'$q$
    );
    if v_rows <> 0
       or (select display_name from public.community_profiles
           where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb') <> 'Actor B' then
        raise exception 'FAIL outsider profile update was not blocked';
    end if;
    raise notice 'PASS outsider impersonation update';

    perform public.test_expect_42501(
        'own auth_user_id rewrite',
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$update public.community_profiles
           set auth_user_id = '99999999-9999-4999-8999-999999999999'
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );
    perform public.test_expect_42501(
        'own admin escalation',
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$update public.community_profiles
           set is_admin = true
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );
    perform public.test_expect_42501(
        'own lifecycle escalation',
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$update public.community_profiles
           set account_status = 'deactivated'
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );
    perform public.test_expect_42501(
        'anonymous profile update',
        'anon', null,
        $q$update public.community_profiles
           set display_name = 'Anonymous rewrite'
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );

    v_rows := public.test_exec_as(
        'authenticated',
        '33333333-3333-4333-8333-333333333333',
        $q$update public.community_profiles
           set is_official = true
           where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'$q$
    );
    if v_rows <> 1 then
        raise exception 'FAIL admin role assignment: expected 1 row, got %', v_rows;
    end if;
    raise notice 'PASS admin role assignment';

    perform public.test_expect_42501(
        'admin editing another profile data',
        'authenticated',
        '33333333-3333-4333-8333-333333333333',
        $q$update public.community_profiles
           set display_name = 'Admin rewrite'
           where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'$q$
    );

    v_rows := public.test_exec_as(
        'authenticated',
        '44444444-4444-4444-8444-444444444444',
        $q$update public.community_profiles
           set display_name = 'Inactive owner rewrite'
           where id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'$q$
    );
    if v_rows <> 0 then
        raise exception 'FAIL inactive owner update was not blocked';
    end if;
    raise notice 'PASS inactive owner update';

    v_rows := public.test_exec_as(
        'authenticated',
        '55555555-5555-4555-8555-555555555555',
        $q$update public.community_profiles
           set is_official = true
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );
    if v_rows <> 0 then
        raise exception 'FAIL inactive admin role update was not blocked';
    end if;
    raise notice 'PASS inactive admin role update';

    v_rows := public.test_exec_as(
        'service_role', null,
        $q$update public.community_profiles
           set account_status = 'deactivated',
               deactivated_at = now()
           where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'$q$
    );
    if v_rows <> 1 then
        raise exception 'FAIL service lifecycle update: expected 1 row, got %', v_rows;
    end if;
    raise notice 'PASS service lifecycle update';

    perform set_config('request.method', 'PATCH', true);
    perform set_config('request.path', '/community_profiles', true);
    perform set_config(
        'request.headers',
        '{"user-agent":"okhttp/4.12.0","apikey":"legacy-public-key","authorization":"Bearer legacy-public-key","content-profile":"public","prefer":"return=representation"}',
        true
    );
    v_rows := public.test_exec_as(
        'anon', null,
        $q$update public.community_profiles
           set pass_plain = 'LegacyPass9',
               pass_hash = encode(sha256(convert_to('LegacyPass9', 'UTF8')), 'hex')
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );
    if v_rows <> 1
       or (select request_count from public.quata_legacy_android_v32_compatibility
           where singleton) <> 2 then
        raise exception 'FAIL exact Android v32 password rotation was not recorded';
    end if;
    raise notice 'PASS exact Android v32 password rotation';

    perform set_config('request.jwt.claim.role', 'anon', true);
    perform set_config('request.jwt.claims', '', true);
    perform set_config(
        'request.headers',
        '{"user-agent":"okhttp/4.12.0","content-profile":"public","prefer":"return=representation"}',
        true
    );
    execute 'set local role anon';
    update public.community_profiles
    set pass_plain = 'PublishablePass9',
        pass_hash = encode(sha256(convert_to('PublishablePass9', 'UTF8')), 'hex')
    where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
    get diagnostics v_rows = row_count;
    reset role;
    perform set_config('request.jwt.claim.role', 'anon', true);
    if v_rows <> 1
       or (select request_count from public.quata_legacy_android_v32_compatibility
           where singleton) <> 3 then
        raise exception 'FAIL Android v32 gateway-stripped request was not recorded';
    end if;
    raise notice 'PASS Android v32 gateway-stripped request';

    perform public.test_expect_42501(
        'legacy Android v32 mismatched password pair',
        'anon', null,
        $q$update public.community_profiles
           set pass_plain = 'DifferentPass8',
               pass_hash = repeat('0', 64)
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );

    perform set_config(
        'request.headers',
        '{"user-agent":"okhttp/4.12.0","apikey":"legacy-public-key","authorization":"Bearer legacy-public-key","content-profile":"public","prefer":"return=representation","x-quata-client-generation":"android-auth-boundary-v1"}',
        true
    );
    v_rows := public.test_exec_as(
        'anon', null,
        $q$update public.community_profiles
           set pass_plain = 'ModernMustUseBridge9',
               pass_hash = encode(sha256(convert_to('ModernMustUseBridge9', 'UTF8')), 'hex')
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );
    if v_rows <> 0 then
        raise exception 'FAIL modern Android flag entered the v32 compatibility branch';
    end if;
    raise notice 'PASS modern Android flag bypasses legacy branch';

    perform set_config(
        'request.headers',
        '{"user-agent":"spoofed-client","apikey":"legacy-public-key","authorization":"Bearer legacy-public-key","content-profile":"public","prefer":"return=representation"}',
        true
    );
    v_rows := public.test_exec_as(
        'anon', null,
        $q$update public.community_profiles
           set pass_plain = 'WrongOrigin9',
               pass_hash = encode(sha256(convert_to('WrongOrigin9', 'UTF8')), 'hex')
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );
    if v_rows <> 0 then
        raise exception 'FAIL non-v32 request signature entered compatibility branch';
    end if;
    raise notice 'PASS non-v32 request signature rejected';

    update public.quata_legacy_android_v32_compatibility
    set enabled = false, disabled_at = clock_timestamp()
    where singleton;
    perform set_config(
        'request.headers',
        '{"user-agent":"okhttp/4.12.0","apikey":"legacy-public-key","authorization":"Bearer legacy-public-key","content-profile":"public","prefer":"return=representation"}',
        true
    );
    v_rows := public.test_exec_as(
        'anon', null,
        $q$update public.community_profiles
           set pass_plain = 'DisabledBranch9',
               pass_hash = encode(sha256(convert_to('DisabledBranch9', 'UTF8')), 'hex')
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );
    if v_rows <> 0 then
        raise exception 'FAIL disabled v32 compatibility branch accepted a request';
    end if;
    update public.quata_legacy_android_v32_compatibility
    set enabled = true, disabled_at = null
    where singleton;
    raise notice 'PASS v32 compatibility kill switch';
end;
$$;

\if :{?KEEP_FIXTURES}
\echo COMMUNITY_PROFILES_ACTOR_GUARD_FIXTURES_READY
\else
drop function public.test_expect_42501(text, text, uuid, text);
drop function public.test_exec_as(text, uuid, text);
delete from public.community_profiles;

\i /workspace/supabase/rollbacks/20260924154500_android_v32_gateway_header_compatibility.rollback.sql
\i /workspace/supabase/rollbacks/20260924153500_android_v32_publishable_key_compatibility.rollback.sql
\i /workspace/supabase/rollbacks/20260726171003_community_profiles_actor_guard.rollback.sql

do $$
declare
    v_policy_count integer;
    v_security_definer boolean;
begin
    select count(*) into v_policy_count
    from pg_policy
    where polrelid = 'public.community_profiles'::regclass;
    if v_policy_count <> 6 then
        raise exception 'FAIL rollback policy count: expected 6, got %', v_policy_count;
    end if;

    select p.prosecdef into v_security_definer
    from pg_trigger t
    join pg_proc p on p.oid = t.tgfoid
    where t.tgrelid = 'public.community_profiles'::regclass
      and t.tgname = 'quata_guard_profile_roles_trg';
    if v_security_definer is distinct from true then
        raise exception 'FAIL rollback did not restore the previous trigger mode';
    end if;

    if not has_table_privilege('anon', 'public.community_profiles', 'UPDATE') then
        raise exception 'FAIL rollback did not restore the previous anon UPDATE grant';
    end if;
    if to_regclass('public.quata_legacy_android_v32_compatibility') is not null
       or to_regprocedure('public.quata_legacy_android_v32_request_allowed()') is not null
       or to_regprocedure('public.quata_record_legacy_android_v32_request()') is not null then
        raise exception 'FAIL rollback left legacy Android v32 compatibility objects';
    end if;
    raise notice 'PASS reviewed rollback restores the previous catalog contract';
end;
$$;
\endif

\echo COMMUNITY_PROFILES_ACTOR_GUARD_TEST_OK
