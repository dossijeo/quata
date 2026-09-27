\set ON_ERROR_STOP on

create role anon nologin;
create role authenticated nologin;
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
    account_status text not null default 'active'
);

create table public.chat_threads (
    id bigint primary key
);

create table public.chat_participants (
    thread_id bigint not null references public.chat_threads(id) on delete cascade,
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    role text not null check (role in ('owner', 'moderator', 'member')),
    left_at timestamptz,
    is_hidden boolean not null default false,
    primary key (thread_id, profile_id)
);

create table public.chat_profile_blocks (
    id bigint generated always as identity primary key,
    thread_id bigint references public.chat_threads(id) on delete cascade,
    blocker_profile_id uuid not null references public.community_profiles(id) on delete cascade,
    blocked_profile_id uuid not null references public.community_profiles(id) on delete cascade,
    constraint chat_profile_blocks_no_self_check check (blocker_profile_id <> blocked_profile_id),
    unique (thread_id, blocker_profile_id, blocked_profile_id)
);

create function public.quata_chat_actor_profile_id(p_actor_profile_id uuid default null)
returns uuid
language plpgsql
stable
security definer
set search_path = public, auth
as $$
declare
    v_profile_id uuid;
begin
    select id into v_profile_id
      from public.community_profiles
     where auth_user_id = auth.uid()
       and account_status = 'active';
    if v_profile_id is null or (p_actor_profile_id is not null and p_actor_profile_id <> v_profile_id) then
        raise exception 'authenticated chat actor is required' using errcode = '42501';
    end if;
    return v_profile_id;
end;
$$;

create function public.quata_chat_can_moderate(p_thread_id bigint, p_profile_id uuid)
returns boolean
language sql
stable
as $$
    select exists (
        select 1 from public.chat_participants
         where thread_id = p_thread_id
           and profile_id = p_profile_id
           and left_at is null
           and role in ('owner', 'moderator')
    )
$$;

create function public.quata_chat_is_thread_participant(p_thread_id bigint, p_profile_id uuid)
returns boolean
language sql
stable
as $$
    select exists (
        select 1 from public.chat_participants
         where thread_id = p_thread_id
           and profile_id = p_profile_id
           and left_at is null
    )
$$;

create function public.quata_chat_get_thread(p_actor_profile_id uuid, p_thread_id bigint)
returns jsonb
language plpgsql
as $$
begin
    if current_setting('quata.test.force_get_thread_failure', true) = 'on' then
        raise exception 'forced post-mutation failure';
    end if;
    return jsonb_build_object('thread_id', p_thread_id, 'actor_profile_id', p_actor_profile_id);
end;
$$;

\ir ../../supabase/migrations/20260927123000_chat_group_participant_guards.sql

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

insert into public.community_profiles(id, auth_user_id) values
    ('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'),
    ('10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002'),
    ('10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000003'),
    ('10000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000004'),
    ('10000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000005');
insert into public.chat_threads(id) values (41);
insert into public.chat_participants(thread_id, profile_id, role) values
    (41, '10000000-0000-4000-8000-000000000001', 'owner'),
    (41, '10000000-0000-4000-8000-000000000002', 'moderator'),
    (41, '10000000-0000-4000-8000-000000000003', 'member'),
    (41, '10000000-0000-4000-8000-000000000005', 'member');

select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000003', false);
select pg_temp.expect_error(
    $$select public.quata_chat_promote_moderator('10000000-0000-4000-8000-000000000003', 41, '10000000-0000-4000-8000-000000000005')$$,
    '42501', 'profile cannot promote moderators'
);

select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000002', false);
select pg_temp.expect_error(
    $$select public.quata_chat_promote_moderator('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000001')$$,
    '42501', 'thread owner cannot be promoted'
);
select pg_temp.expect_error(
    $$select public.quata_chat_demote_moderator('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000001')$$,
    '42501', 'thread owner cannot be demoted'
);
select pg_temp.expect_error(
    $$select public.quata_chat_remove_participant('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000001')$$,
    '42501', 'thread owner cannot be removed'
);
select pg_temp.expect_error(
    $$select public.quata_chat_remove_participant('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000004')$$,
    '22023', 'target participant does not exist'
);

select pg_temp.expect_error(
    $$select public.quata_chat_block_participant('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000002')$$,
    '22023', 'profile cannot block itself'
);
select pg_temp.expect_error(
    $$select public.quata_chat_block_participant('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000004')$$,
    '22023', 'target participant does not exist'
);

select set_config('quata.test.force_get_thread_failure', 'on', false);
select pg_temp.expect_error(
    $$select public.quata_chat_promote_moderator('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000003')$$,
    'P0001', 'forced post-mutation failure'
);
select pg_temp.assert_true(
    (select role = 'member' from public.chat_participants where thread_id = 41 and profile_id = '10000000-0000-4000-8000-000000000003'),
    'failed promotion must roll back its role update'
);
select set_config('quata.test.force_get_thread_failure', 'off', false);

select public.quata_chat_promote_moderator('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000003');
select pg_temp.assert_true(
    (select role = 'moderator' from public.chat_participants where thread_id = 41 and profile_id = '10000000-0000-4000-8000-000000000003'),
    'valid promotion failed'
);
select public.quata_chat_demote_moderator('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000003');
select pg_temp.assert_true(
    (select role = 'member' from public.chat_participants where thread_id = 41 and profile_id = '10000000-0000-4000-8000-000000000003'),
    'valid demotion failed'
);
select public.quata_chat_block_participant('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000005');
select pg_temp.assert_true(
    (select count(*) = 1 from public.chat_profile_blocks where thread_id = 41 and blocker_profile_id = '10000000-0000-4000-8000-000000000002' and blocked_profile_id = '10000000-0000-4000-8000-000000000005'),
    'valid block failed'
);
select public.quata_chat_remove_participant('10000000-0000-4000-8000-000000000002', 41, '10000000-0000-4000-8000-000000000003');
select pg_temp.assert_true(
    (select left_at is not null and is_hidden from public.chat_participants where thread_id = 41 and profile_id = '10000000-0000-4000-8000-000000000003'),
    'valid removal failed'
);

select pg_temp.assert_true(
    (select role = 'owner' and left_at is null from public.chat_participants where thread_id = 41 and profile_id = '10000000-0000-4000-8000-000000000001'),
    'negative guards changed the owner'
);

\ir ../../supabase/rollbacks/20260927123000_chat_group_participant_guards.rollback.sql
select pg_temp.assert_true(
    position('for update' in lower(pg_get_functiondef('public.quata_chat_promote_moderator(uuid,bigint,uuid)'::regprocedure))) = 0,
    'rollback did not restore the prior promotion definition'
);
\ir ../../supabase/migrations/20260927123000_chat_group_participant_guards.sql
select pg_temp.assert_true(
    position('for update' in lower(pg_get_functiondef('public.quata_chat_promote_moderator(uuid,bigint,uuid)'::regprocedure))) > 0,
    'migration did not reapply after rollback'
);

select 'chat_group_participant_guards_passed' as result;
