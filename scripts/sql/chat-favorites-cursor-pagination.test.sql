create role anon;
create role authenticated;

create table public.community_profiles(id uuid primary key);
create table public.chat_threads(id bigint primary key);
create table public.chat_participants(
    thread_id bigint not null,
    profile_id uuid not null,
    left_at timestamptz,
    is_hidden boolean not null default false,
    is_deleted boolean not null default false
);
create table public.chat_messages(
    id bigint primary key,
    thread_id bigint not null,
    sender_profile_id uuid not null,
    body text not null,
    created_at timestamptz not null,
    deleted_at timestamptz
);
create table public.chat_message_favorites(
    profile_id uuid not null,
    message_id bigint not null,
    primary key(profile_id, message_id)
);

create function public.quata_chat_actor_profile_id(p_actor_profile_id uuid default null)
returns uuid language sql stable as $$ select p_actor_profile_id $$;
create function public.quata_chat_message_json(p_message_id bigint, p_actor uuid)
returns jsonb language sql stable as $$
    select jsonb_build_object(
        'id', m.id,
        'thread_id', m.thread_id,
        'sender_profile_id', m.sender_profile_id,
        'body', m.body,
        'created_at', m.created_at,
        'created_at_millis', floor(extract(epoch from m.created_at) * 1000)::bigint,
        'favorited', exists(
            select 1 from public.chat_message_favorites f
            where f.profile_id = p_actor and f.message_id = m.id
        )
    )
    from public.chat_messages m where m.id = p_message_id
$$;
create function public.quata_chat_thread_json(p_thread_id bigint, p_actor uuid)
returns jsonb language sql stable as $$ select jsonb_build_object('id', p_thread_id) $$;
create function public.quata_chat_profile_json(p_profile_id uuid)
returns jsonb language sql stable as $$ select jsonb_build_object('id', p_profile_id) $$;
create function public.quata_chat_epoch_millis(p_value timestamptz)
returns bigint language sql immutable as $$ select floor(extract(epoch from p_value) * 1000)::bigint $$;

create function public.quata_chat_get_favorites(p_actor_profile_id uuid, p_limit integer default 250)
returns jsonb language sql stable as $$ select jsonb_build_object('legacy', true) $$;
create table public.legacy_function_fingerprint(value text not null);
insert into public.legacy_function_fingerprint(value)
select md5(pg_get_functiondef('public.quata_chat_get_favorites(uuid,integer)'::regprocedure));

insert into public.community_profiles values ('00000000-0000-0000-0000-000000000001');
insert into public.chat_threads values (77);
insert into public.chat_participants(thread_id, profile_id)
values (77, '00000000-0000-0000-0000-000000000001');
insert into public.chat_messages(id, thread_id, sender_profile_id, body, created_at)
select id, 77, '00000000-0000-0000-0000-000000000001', 'favorite-' || id, '2026-10-01T00:00:00Z'
from generate_series(1, 601) id;
insert into public.chat_message_favorites(profile_id, message_id)
select '00000000-0000-0000-0000-000000000001', id from generate_series(1, 601) id;

do $$
declare
    v_actor constant uuid := '00000000-0000-0000-0000-000000000001';
    v_first jsonb;
    v_second jsonb;
    v_third jsonb;
    v_ids bigint[];
    v_unique_count integer;
begin
    if not has_function_privilege('authenticated', 'public.quata_chat_get_favorites_page(uuid,integer,timestamptz,bigint)', 'execute')
       or has_function_privilege('anon', 'public.quata_chat_get_favorites_page(uuid,integer,timestamptz,bigint)', 'execute')
       or has_function_privilege('public', 'public.quata_chat_get_favorites_page(uuid,integer,timestamptz,bigint)', 'execute') then
        raise exception 'favorites pagination ACL mismatch';
    end if;

    if (select value from public.legacy_function_fingerprint) <>
       md5(pg_get_functiondef('public.quata_chat_get_favorites(uuid,integer)'::regprocedure)) then
        raise exception 'legacy favorites function changed';
    end if;

    begin
        perform public.quata_chat_get_favorites_page(v_actor, 250, '2026-10-01T00:00:00Z', null);
        raise exception 'partial cursor accepted';
    exception when sqlstate '22023' then
        null;
    end;

    v_first := public.quata_chat_get_favorites_page(v_actor, 250, null, null);
    if jsonb_array_length(v_first->'messages') <> 250
       or (v_first->>'has_more')::boolean is not true
       or (v_first#>>'{next_cursor,message_id}')::bigint <> 352 then
        raise exception 'first page mismatch';
    end if;

    v_second := public.quata_chat_get_favorites_page(
        v_actor,
        250,
        (v_first#>>'{next_cursor,created_at}')::timestamptz,
        (v_first#>>'{next_cursor,message_id}')::bigint
    );
    if jsonb_array_length(v_second->'messages') <> 250
       or (v_second->>'has_more')::boolean is not true
       or (v_second#>>'{next_cursor,message_id}')::bigint <> 102 then
        raise exception 'second page mismatch';
    end if;

    v_third := public.quata_chat_get_favorites_page(
        v_actor,
        250,
        (v_second#>>'{next_cursor,created_at}')::timestamptz,
        (v_second#>>'{next_cursor,message_id}')::bigint
    );
    if jsonb_array_length(v_third->'messages') <> 101
       or (v_third->>'has_more')::boolean is not false
       or v_third->'next_cursor' <> 'null'::jsonb then
        raise exception 'third page mismatch';
    end if;

    select array_agg((row->>'id')::bigint), count(distinct (row->>'id')::bigint)
    into v_ids, v_unique_count
    from jsonb_array_elements(
        (v_first->'messages') || (v_second->'messages') || (v_third->'messages')
    ) row;
    if cardinality(v_ids) <> 601 or v_unique_count <> 601
       or (select min(id) from unnest(v_ids) id) <> 1
       or (select max(id) from unnest(v_ids) id) <> 601 then
        raise exception 'page coverage or uniqueness mismatch';
    end if;
end;
$$;
