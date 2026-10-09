\set ON_ERROR_STOP on

create role anon nologin;
create role authenticated nologin;
create schema auth;
create schema storage;

create table auth.users (
    id uuid primary key
);

create function auth.uid()
returns uuid
language sql
stable
as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;

create table storage.buckets (
    id text primary key,
    name text not null,
    public boolean not null default false
);

create table public.community_walls (
    id uuid primary key
);

create table public.community_profiles (
    id uuid primary key,
    display_name text,
    nombre text,
    avatar_url text,
    avatar text,
    neighborhood text,
    barrio text,
    phone_local text,
    country_code text
);

create table public.community_emergency_contacts (
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    contact_profile_id uuid not null references public.community_profiles(id) on delete cascade,
    position integer not null default 0,
    created_at timestamptz not null default now(),
    primary key (profile_id, contact_profile_id)
);

\ir ../../supabase/migrations/20260628_0001_chat_schema.sql
\ir ../../supabase/migrations/20260628_0002_chat_rpc.sql
\ir ../../supabase/migrations/20260926171500_chat_private_thread_concurrency.sql
\ir ../../supabase/migrations/20261009070000_chat_private_thread_open_idempotency.sql

begin;

insert into auth.users(id) values
    ('20000000-0000-4000-8000-000000000001'),
    ('20000000-0000-4000-8000-000000000002'),
    ('20000000-0000-4000-8000-000000000003');

insert into public.community_profiles(id, auth_user_id, display_name) values
    ('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 'Uncertain Actor'),
    ('10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002', 'Uncertain Peer'),
    ('10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000003', 'Uncertain Member');

select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000001', false);

-- The first committed result is deliberately ignored, as if transport failed after commit.
do $$
begin
    perform public.quata_chat_get_or_create_private_thread(
        '10000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000002'
    );
end;
$$;

do $$
begin
    perform public.quata_chat_start_thread(
        '10000000-0000-4000-8000-000000000001',
        array['10000000-0000-4000-8000-000000000002'::uuid, '10000000-0000-4000-8000-000000000003'::uuid],
        'Uncertain Group',
        'group',
        'Uncertain first message',
        'uncertain-response-group',
        null
    );
end;
$$;

commit;

-- Retry from a new PostgreSQL backend so the first transaction cannot be observed
-- through local transaction state or a reused session snapshot.
\connect postgres postgres
select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000001', false);

create function pg_temp.assert_true(p_condition boolean, p_message text)
returns void
language plpgsql
as $$
begin
    if not coalesce(p_condition, false) then
        raise exception 'assertion failed: %', p_message;
    end if;
end;
$$;

create temporary table private_retry_result as
select public.quata_chat_get_or_create_private_thread(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000002'
) as payload;

select pg_temp.assert_true((select count(*) = 1 from public.chat_private_threads), 'private retry created a second pair');
select pg_temp.assert_true((select count(*) = 1 from public.chat_threads where type = 'private'), 'private retry created a second thread');
select pg_temp.assert_true(
    (select count(*) = 2 from public.chat_participants p join public.chat_threads t on t.id = p.thread_id where t.type = 'private'),
    'private retry changed participant cardinality'
);
select pg_temp.assert_true((select count(*) = 1 from public.chat_events where event_type = 'private_thread_opened'), 'private retry duplicated its open event');
select pg_temp.assert_true(
    (select (payload #>> '{thread,id}')::bigint from private_retry_result) = (select thread_id from public.chat_private_threads),
    'private retry did not return the committed thread'
);

create temporary table group_retry_result as
select public.quata_chat_start_thread(
    '10000000-0000-4000-8000-000000000001',
    array['10000000-0000-4000-8000-000000000002'::uuid, '10000000-0000-4000-8000-000000000003'::uuid],
    'Uncertain Group',
    'group',
    'Uncertain first message',
    'uncertain-response-group',
    null
) as payload;

select pg_temp.assert_true((select count(*) = 1 from public.chat_threads where unique_key = 'uncertain-response-group'), 'group retry created a second thread');
select pg_temp.assert_true((select count(*) = 3 from public.chat_participants p join public.chat_threads t on t.id = p.thread_id where t.unique_key = 'uncertain-response-group'), 'group retry changed participant cardinality');
select pg_temp.assert_true((select count(*) = 1 from public.chat_messages where body = 'Uncertain first message'), 'group retry duplicated its first message');
select pg_temp.assert_true((select count(*) = 1 from public.chat_events e join public.chat_threads t on t.id = e.thread_id where t.unique_key = 'uncertain-response-group' and e.event_type = 'thread_started'), 'group retry duplicated its start event');
select pg_temp.assert_true(
    (select (payload #>> '{thread,id}')::bigint from group_retry_result) = (select id from public.chat_threads where unique_key = 'uncertain-response-group'),
    'group retry did not return the committed thread'
);

-- Prove that rollback restores the previous repeat-event behavior, then reapply.
begin;
\ir ../../supabase/rollbacks/20261009070000_chat_private_thread_open_idempotency.rollback.sql
select public.quata_chat_get_or_create_private_thread(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000002'
);
select pg_temp.assert_true((select count(*) = 2 from public.chat_events where event_type = 'private_thread_opened'), 'rollback did not restore the previous behavior');

\ir ../../supabase/migrations/20261009070000_chat_private_thread_open_idempotency.sql
select public.quata_chat_get_or_create_private_thread(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000002'
);
select pg_temp.assert_true((select count(*) = 2 from public.chat_events where event_type = 'private_thread_opened'), 'reapply did not restore idempotent open effects');

select 'conversation_create_uncertain_response_passed' as result;
rollback;
