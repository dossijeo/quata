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

create function pg_temp.expect_error(p_statement text, p_state text, p_fragment text)
returns void
language plpgsql
as $$
declare
    v_state text;
    v_message text;
begin
    begin
        execute p_statement;
    exception when others then
        get stacked diagnostics v_state = returned_sqlstate, v_message = message_text;
        if v_state = p_state and position(p_fragment in v_message) > 0 then
            return;
        end if;
        raise exception 'unexpected error: state=%, message=%', v_state, v_message;
    end;
    raise exception 'expected error was not raised: %', p_statement;
end;
$$;

create function pg_temp.force_conversation_create_failure()
returns trigger
language plpgsql
as $$
begin
    if current_setting('quata.test.force_conversation_create_failure', true) = new.event_type then
        raise exception 'forced conversation create post-mutation failure';
    end if;
    return new;
end;
$$;

create trigger force_conversation_create_failure
before insert on public.chat_events
for each row execute function pg_temp.force_conversation_create_failure();

begin;

insert into auth.users(id) values
    ('20000000-0000-4000-8000-000000000001'),
    ('20000000-0000-4000-8000-000000000002'),
    ('20000000-0000-4000-8000-000000000003');

insert into public.community_profiles(id, auth_user_id, display_name) values
    ('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 'Atomic Actor'),
    ('10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002', 'Atomic Peer'),
    ('10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000003', 'Atomic Member');

select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000001', false);

select set_config('quata.test.force_conversation_create_failure', 'private_thread_opened', false);
select pg_temp.expect_error(
    $statement$select public.quata_chat_get_or_create_private_thread(
        '10000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000002'
    )$statement$,
    'P0001',
    'forced conversation create post-mutation failure'
);
select pg_temp.assert_true(
    not exists (
        select 1 from public.chat_threads
        where unique_key = 'private:10000000-0000-4000-8000-000000000001:10000000-0000-4000-8000-000000000002'
    ),
    'failed private creation left a thread'
);
select pg_temp.assert_true((select count(*) = 0 from public.chat_private_threads), 'failed private creation left a pair');
select pg_temp.assert_true((select count(*) = 0 from public.chat_participants), 'failed private creation left participants');
select pg_temp.assert_true((select count(*) = 0 from public.chat_events), 'failed private creation left events');

select set_config('quata.test.force_conversation_create_failure', '', false);
select public.quata_chat_get_or_create_private_thread(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000002'
);
select pg_temp.assert_true((select count(*) = 1 from public.chat_private_threads), 'valid private creation did not create one pair');
select pg_temp.assert_true((select count(*) = 2 from public.chat_participants), 'valid private creation did not create two participants');
select pg_temp.assert_true((select count(*) = 1 from public.chat_events where event_type = 'private_thread_opened'), 'valid private creation did not create its event');

select set_config('quata.test.force_conversation_create_failure', 'thread_started', false);
select pg_temp.expect_error(
    $statement$select public.quata_chat_start_thread(
        '10000000-0000-4000-8000-000000000001',
        array['10000000-0000-4000-8000-000000000002'::uuid, '10000000-0000-4000-8000-000000000003'::uuid],
        'Atomic Group',
        'group',
        'Atomic first message',
        'atomicity-group',
        null
    )$statement$,
    'P0001',
    'forced conversation create post-mutation failure'
);
select pg_temp.assert_true(not exists (select 1 from public.chat_threads where unique_key = 'atomicity-group'), 'failed group creation left a thread');
select pg_temp.assert_true(not exists (select 1 from public.chat_messages where body = 'Atomic first message'), 'failed group creation left a message');
select pg_temp.assert_true(not exists (select 1 from public.chat_events where event_type = 'thread_started'), 'failed group creation left an event');

select set_config('quata.test.force_conversation_create_failure', '', false);
select public.quata_chat_start_thread(
    '10000000-0000-4000-8000-000000000001',
    array['10000000-0000-4000-8000-000000000002'::uuid, '10000000-0000-4000-8000-000000000003'::uuid],
    'Atomic Group',
    'group',
    'Atomic first message',
    'atomicity-group',
    null
);
select pg_temp.assert_true((select count(*) = 1 from public.chat_threads where unique_key = 'atomicity-group'), 'valid group creation did not create one thread');
select pg_temp.assert_true((select count(*) = 3 from public.chat_participants p join public.chat_threads t on t.id = p.thread_id where t.unique_key = 'atomicity-group'), 'valid group creation did not create three participants');
select pg_temp.assert_true((select count(*) = 1 from public.chat_messages where body = 'Atomic first message'), 'valid group creation did not create one message');
select pg_temp.assert_true((select count(*) = 1 from public.chat_events e join public.chat_threads t on t.id = e.thread_id where t.unique_key = 'atomicity-group' and e.event_type = 'thread_started'), 'valid group creation did not create one event');

select 'conversation_create_backend_atomicity_passed' as result;
rollback;
