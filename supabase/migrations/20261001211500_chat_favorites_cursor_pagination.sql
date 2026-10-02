create or replace function public.quata_chat_get_favorites_page(
    p_actor_profile_id uuid,
    p_limit integer default 250,
    p_before_created_at timestamptz default null,
    p_before_message_id bigint default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_limit integer := greatest(1, least(coalesce(p_limit, 250), 250));
    v_candidate_ids bigint[];
    v_message_ids bigint[];
    v_thread_ids bigint[];
    v_messages jsonb;
    v_threads jsonb;
    v_profiles jsonb;
    v_has_more boolean := false;
    v_cursor_created_at timestamptz;
    v_cursor_message_id bigint;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if (p_before_created_at is null) <> (p_before_message_id is null) then
        raise exception 'favorites cursor is incomplete' using errcode = '22023';
    end if;

    select coalesce(array_agg(q.id order by q.created_at desc, q.id desc), array[]::bigint[])
    into v_candidate_ids
    from (
        select m.id, m.created_at
        from public.chat_message_favorites f
        join public.chat_messages m on m.id = f.message_id
        join public.chat_participants p
          on p.thread_id = m.thread_id
         and p.profile_id = v_actor
        where f.profile_id = v_actor
          and m.deleted_at is null
          and p.left_at is null
          and p.is_hidden = false
          and p.is_deleted = false
          and (
              p_before_message_id is null
              or m.created_at < p_before_created_at
              or (m.created_at = p_before_created_at and m.id < p_before_message_id)
          )
        order by m.created_at desc, m.id desc
        limit v_limit + 1
    ) q;

    v_has_more := cardinality(v_candidate_ids) > v_limit;
    v_message_ids := coalesce(v_candidate_ids[1:v_limit], array[]::bigint[]);

    if cardinality(v_message_ids) > 0 then
        v_cursor_message_id := v_message_ids[cardinality(v_message_ids)];
        select m.created_at
        into v_cursor_created_at
        from public.chat_messages m
        where m.id = v_cursor_message_id;
    end if;

    select coalesce(array_agg(distinct m.thread_id), array[]::bigint[])
    into v_thread_ids
    from public.chat_messages m
    where m.id = any(v_message_ids);

    select coalesce(
        jsonb_agg(public.quata_chat_message_json(m.id, v_actor) order by m.created_at desc, m.id desc),
        '[]'::jsonb
    )
    into v_messages
    from public.chat_messages m
    where m.id = any(v_message_ids);

    select coalesce(jsonb_agg(public.quata_chat_thread_json(tid, v_actor)), '[]'::jsonb)
    into v_threads
    from unnest(v_thread_ids) tid;

    select coalesce(jsonb_agg(distinct public.quata_chat_profile_json(p.profile_id)), '[]'::jsonb)
    into v_profiles
    from public.chat_participants p
    where p.thread_id = any(v_thread_ids)
      and p.left_at is null;

    return jsonb_build_object(
        'threads', coalesce(v_threads, '[]'::jsonb),
        'profiles', coalesce(v_profiles, '[]'::jsonb),
        'messages', coalesce(v_messages, '[]'::jsonb),
        'has_more', v_has_more,
        'next_cursor', case
            when v_has_more and v_cursor_message_id is not null then jsonb_build_object(
                'created_at', v_cursor_created_at,
                'message_id', v_cursor_message_id
            )
            else null
        end,
        'server_time_millis', public.quata_chat_epoch_millis(clock_timestamp())
    );
end;
$$;

revoke all on function public.quata_chat_get_favorites_page(
    uuid,
    integer,
    timestamptz,
    bigint
) from public, anon;

grant execute on function public.quata_chat_get_favorites_page(
    uuid,
    integer,
    timestamptz,
    bigint
) to authenticated;
