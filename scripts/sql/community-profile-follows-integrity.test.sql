\set ON_ERROR_STOP on

create extension if not exists pgcrypto;
create schema auth;
create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;
grant anon, authenticated, service_role to postgres;
grant usage on schema public, auth to anon, authenticated, service_role;

create function auth.uid()
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
    id uuid primary key,
    auth_user_id uuid unique,
    display_name text not null,
    account_status text not null default 'active',
    deactivated_at timestamptz,
    is_admin boolean not null default false,
    followers_count integer not null default 0,
    following_count integer not null default 0
);

create table public.community_profile_follows (
    id uuid primary key default gen_random_uuid(),
    follower_profile_id uuid not null
        references public.community_profiles(id) on delete cascade,
    followed_profile_id uuid not null
        references public.community_profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint community_profile_follows_no_self
        check (follower_profile_id <> followed_profile_id),
    constraint community_profile_follows_unique
        unique (follower_profile_id, followed_profile_id)
);

alter table public.community_profile_follows enable row level security;
create policy "allow all"
on public.community_profile_follows for all using (true) with check (true);
create policy "public read profile follows"
on public.community_profile_follows for select using (true);
create policy "public insert profile follows"
on public.community_profile_follows for insert with check (true);
create policy "public delete profile follows"
on public.community_profile_follows for delete using (true);

grant all on public.community_profile_follows to anon, authenticated, service_role;
grant select on public.community_profiles to anon, authenticated, service_role;
grant update on public.community_profiles to authenticated, service_role;

create function public.quata_chat_auth_profile_id()
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
        where cp.id = public.quata_chat_auth_profile_id()
    ), false);
$$;

create function public.quata_current_role_is_service()
returns boolean
language sql
stable
as $$
    select coalesce(current_setting('request.jwt.claim.role', true), '') = 'service_role'
        or current_user in ('postgres', 'supabase_admin', 'service_role');
$$;

create function public.toggle_follow_profile(target_id uuid)
returns json
language sql
as $$
    select json_build_object('deprecated', true);
$$;

create function public.recalculate_profile_follow_counts(p_profile_id uuid)
returns void
language plpgsql
as $$
begin
    update public.community_profiles cp
    set followers_count = (
            select count(*)::integer
            from public.community_profile_follows f
            where f.followed_profile_id = cp.id
        ),
        following_count = (
            select count(*)::integer
            from public.community_profile_follows f
            where f.follower_profile_id = cp.id
        )
    where cp.id = p_profile_id;
end;
$$;

grant execute on function public.toggle_follow_profile(uuid)
to public, anon, authenticated, service_role;
grant execute on function public.recalculate_profile_follow_counts(uuid)
to public, anon, authenticated, service_role;

insert into public.community_profiles (
    id, auth_user_id, display_name, account_status, is_admin
) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
     '11111111-1111-4111-8111-111111111111', 'Actor A', 'active', false),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
     '22222222-2222-4222-8222-222222222222', 'Actor B', 'active', false),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc',
     '33333333-3333-4333-8333-333333333333', 'Admin', 'active', true),
    ('dddddddd-dddd-4ddd-8ddd-dddddddddddd',
     '44444444-4444-4444-8444-444444444444', 'Inactive', 'deactivated', false);

insert into public.community_profile_follows (
    follower_profile_id, followed_profile_id
) values (
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
);

create function public.test_exec_as(
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
    perform set_config('request.jwt.claim.role', p_role, true);
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

create function public.test_expect_42501(
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

\i /workspace/supabase/templates/community_profile_follows_actor_guard.sql.template

do $$
declare
    v_rows bigint;
begin
    v_rows := public.test_exec_as(
        'anon', null,
        'select id from public.community_profile_follows'
    );
    if v_rows <> 1 then
        raise exception 'FAIL anon read expected 1 row, got %', v_rows;
    end if;
    raise notice 'PASS anonymous read';

    perform public.test_exec_as(
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$insert into public.community_profile_follows (
               follower_profile_id, followed_profile_id
           ) values (
               'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
               'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
           )$q$
    );
    raise notice 'PASS own insert';

    perform public.test_expect_42501(
        'spoof insert',
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$insert into public.community_profile_follows (
               follower_profile_id, followed_profile_id
           ) values (
               'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
               'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
           )$q$
    );
    perform public.test_expect_42501(
        'anonymous insert',
        'anon', null,
        $q$insert into public.community_profile_follows (
               follower_profile_id, followed_profile_id
           ) values (
               'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
               'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
           )$q$
    );
    perform public.test_expect_42501(
        'inactive actor insert',
        'authenticated',
        '44444444-4444-4444-8444-444444444444',
        $q$insert into public.community_profile_follows (
               follower_profile_id, followed_profile_id
           ) values (
               'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
               'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
           )$q$
    );

    perform public.test_exec_as(
        'authenticated',
        '22222222-2222-4222-8222-222222222222',
        $q$insert into public.community_profile_follows (
               follower_profile_id, followed_profile_id
           ) values (
               'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
               'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
           )$q$
    );
    perform public.test_expect_42501(
        'foreign delete',
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$delete from public.community_profile_follows
           where follower_profile_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
             and followed_profile_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );

    v_rows := public.test_exec_as(
        'authenticated',
        '33333333-3333-4333-8333-333333333333',
        $q$delete from public.community_profile_follows
           where follower_profile_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
             and followed_profile_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$q$
    );
    if v_rows <> 1 then
        raise exception 'FAIL admin delete expected 1 row, got %', v_rows;
    end if;
    raise notice 'PASS admin delete';

    perform public.test_exec_as(
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$delete from public.community_profile_follows
           where follower_profile_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
             and followed_profile_id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'$q$
    );
    raise notice 'PASS own delete';
end;
$$;

\i /workspace/supabase/templates/community_profile_follow_counter_reconciliation.sql.template

do $$
declare
    v_batch public.quata_follow_count_reconciliation_batches%rowtype;
begin
    select * into strict v_batch
    from public.quata_follow_count_reconciliation_batches
    where migration_marker = '__MIGRATION_VERSION__';

    if v_batch.profile_count <> 4
       or v_batch.mismatch_count <> 2
       or v_batch.edge_count <> 1 then
        raise exception 'FAIL reconciliation snapshot counts: %', row_to_json(v_batch);
    end if;

    if (select following_count from public.community_profiles
        where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa') <> 1
       or (select followers_count from public.community_profiles
           where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb') <> 1 then
        raise exception 'FAIL initial counter backfill';
    end if;
    raise notice 'PASS snapshot and counter backfill';
end;
$$;

do $$
begin
    perform public.test_exec_as(
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$insert into public.community_profile_follows (
               follower_profile_id, followed_profile_id
           ) values (
               'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
               'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
           )$q$
    );
    if (select following_count from public.community_profiles
        where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa') <> 2
       or (select followers_count from public.community_profiles
           where id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc') <> 1 then
        raise exception 'FAIL insert trigger counters';
    end if;

    perform public.test_exec_as(
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$delete from public.community_profile_follows
           where follower_profile_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
             and followed_profile_id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'$q$
    );
    if (select following_count from public.community_profiles
        where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa') <> 1
       or (select followers_count from public.community_profiles
           where id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc') <> 0 then
        raise exception 'FAIL delete trigger counters';
    end if;
    raise notice 'PASS trigger maintains both counter sides';

    perform public.test_expect_42501(
        'client recalculate RPC',
        'authenticated',
        '11111111-1111-4111-8111-111111111111',
        $q$select public.recalculate_profile_follow_counts(
               'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
           )$q$
    );
    perform public.test_exec_as(
        'service_role', null,
        $q$select public.recalculate_profile_follow_counts(
               'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
           )$q$
    );
    raise notice 'PASS producer RPC restricted to service';
end;
$$;

\i /workspace/supabase/templates/community_profile_follow_counter_reconciliation.rollback.sql.template

do $$
begin
    if exists (
        select 1 from public.community_profiles
        where followers_count <> 0 or following_count <> 0
    ) then
        raise exception 'FAIL counter rollback did not restore snapshot';
    end if;
    if to_regclass('public.quata_follow_count_reconciliation_batches') is not null
       or to_regprocedure('public.quata_sync_profile_follow_counts()') is not null then
        raise exception 'FAIL counter rollback catalog cleanup';
    end if;
    raise notice 'PASS counter rollback';
end;
$$;

\i /workspace/supabase/templates/community_profile_follows_actor_guard.rollback.sql.template

select public.test_exec_as(
    'anon', null,
    $q$insert into public.community_profile_follows (
           follower_profile_id, followed_profile_id
       ) values (
           'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
           'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
       )$q$
);

do $$
begin
    if not exists (
        select 1 from public.community_profile_follows
        where follower_profile_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
          and followed_profile_id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
    ) then
        raise exception 'FAIL actor rollback did not restore historical exposure';
    end if;
    raise notice 'PASS actor rollback reproduces historical exposure';
end;
$$;

delete from public.community_profile_follows
where follower_profile_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
  and followed_profile_id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';

\i /workspace/supabase/templates/community_profile_follows_actor_guard.sql.template

select public.test_expect_42501(
    'spoof after secure reapply',
    'authenticated',
    '11111111-1111-4111-8111-111111111111',
    $q$insert into public.community_profile_follows (
           follower_profile_id, followed_profile_id
       ) values (
           'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
           'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
       )$q$
);

\i /workspace/supabase/templates/community_profile_follow_counter_reconciliation.sql.template

do $$
begin
    if (select count(*) from public.quata_follow_count_reconciliation_batches) <> 1
       or exists (
           select 1 from public.community_profiles cp
           where cp.followers_count <> (
                     select count(*) from public.community_profile_follows f
                     where f.followed_profile_id = cp.id
                 )
              or cp.following_count <> (
                     select count(*) from public.community_profile_follows f
                     where f.follower_profile_id = cp.id
                 )
       ) then
        raise exception 'FAIL secure reconciliation reapply';
    end if;
    raise notice 'PASS secure reconciliation reapply';
end;
$$;

\i /workspace/supabase/templates/community_profile_follow_counter_producer_decommission.sql.template

do $$
begin
    if exists (
        select 1 from pg_trigger
        where tgrelid = 'public.community_profile_follows'::regclass
          and tgname = 'quata_sync_profile_follow_counts_trg'
          and not tgisinternal
    ) then
        raise exception 'FAIL producer decommission retained trigger';
    end if;
    if to_regprocedure('public.quata_sync_profile_follow_counts()') is null then
        raise exception 'FAIL producer decommission removed reusable function';
    end if;
    raise notice 'PASS forward-safe producer decommission';
end;
$$;

\i /workspace/supabase/templates/community_profile_follow_counter_producer_decommission.rollback.sql.template

do $$
begin
    if not exists (
        select 1 from pg_trigger
        where tgrelid = 'public.community_profile_follows'::regclass
          and tgname = 'quata_sync_profile_follow_counts_trg'
          and not tgisinternal
    ) then
        raise exception 'FAIL producer decommission rollback';
    end if;
    raise notice 'PASS producer decommission rollback';
end;
$$;

\if :{?KEEP_FIXTURES}
\echo COMMUNITY_PROFILE_FOLLOWS_INTEGRITY_FIXTURES_READY
\else
\i /workspace/supabase/templates/community_profile_follow_counter_reconciliation.rollback.sql.template

delete from public.community_profile_follows;
delete from public.community_profiles;
\endif

\echo COMMUNITY_PROFILE_FOLLOWS_INTEGRITY_TEST_OK
