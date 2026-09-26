create or replace function public.quata_chat_get_inbox_page(
    p_actor_profile_id uuid,
    p_limit integer default 100,
    p_before_last_message_at timestamptz default null,
    p_before_updated_at timestamptz default null,
    p_before_thread_id bigint default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_limit integer := greatest(1, least(coalesce(p_limit, 100), 100));
    v_candidate_ids bigint[];
    v_thread_ids bigint[];
    v_threads jsonb;
    v_messages jsonb;
    v_profiles jsonb;
    v_unread_total integer;
    v_has_more boolean := false;
    v_cursor_last_message_at timestamptz;
    v_cursor_updated_at timestamptz;
    v_cursor_thread_id bigint;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if (p_before_thread_id is null) <> (p_before_updated_at is null) then
        raise exception 'inbox cursor is incomplete' using errcode = '22023';
    end if;

    insert into public.conversation_user_state(
        conversation_id,
        user_id,
        first_visible_message_id,
        muted_at,
        last_read_message_id
    )
    select
        p.thread_id,
        p.profile_id,
        public.quata_chat_first_message_id(p.thread_id),
        p.muted_at,
        p.last_read_message_id
    from public.chat_participants p
    where p.profile_id = v_actor
    on conflict (conversation_id, user_id)
    do nothing;

    select coalesce(array_agg(q.id order by q.ordinality), array[]::bigint[])
    into v_candidate_ids
    from (
        select
            t.id,
            row_number() over (
                order by t.last_message_at desc nulls last, t.updated_at desc, t.id desc
            ) as ordinality
        from public.chat_threads t
        join public.chat_participants p on p.thread_id = t.id
        join public.conversation_user_state s
          on s.conversation_id = t.id
         and s.user_id = v_actor
        where p.profile_id = v_actor
          and p.left_at is null
          and s.deleted_at is null
          and t.deleted_at is null
          and (
              p_before_thread_id is null
              or (
                  p_before_last_message_at is not null
                  and (
                      t.last_message_at < p_before_last_message_at
                      or t.last_message_at is null
                      or (
                          t.last_message_at = p_before_last_message_at
                          and (
                              t.updated_at < p_before_updated_at
                              or (t.updated_at = p_before_updated_at and t.id < p_before_thread_id)
                          )
                      )
                  )
              )
              or (
                  p_before_last_message_at is null
                  and t.last_message_at is null
                  and (
                      t.updated_at < p_before_updated_at
                      or (t.updated_at = p_before_updated_at and t.id < p_before_thread_id)
                  )
              )
          )
        order by t.last_message_at desc nulls last, t.updated_at desc, t.id desc
        limit v_limit + 1
    ) q;

    v_has_more := cardinality(v_candidate_ids) > v_limit;
    v_thread_ids := coalesce(v_candidate_ids[1:v_limit], array[]::bigint[]);

    if cardinality(v_thread_ids) > 0 then
        v_cursor_thread_id := v_thread_ids[cardinality(v_thread_ids)];
        select t.last_message_at, t.updated_at
        into v_cursor_last_message_at, v_cursor_updated_at
        from public.chat_threads t
        where t.id = v_cursor_thread_id;
    end if;

    select coalesce(
        jsonb_agg(
            public.quata_chat_thread_json(t.id, v_actor)
            order by t.last_message_at desc nulls last, t.updated_at desc, t.id desc
        ),
        '[]'::jsonb
    )
    into v_threads
    from public.chat_threads t
    where t.id = any(v_thread_ids);

    select coalesce(
        jsonb_agg(public.quata_chat_message_json(q.id, v_actor) order by q.created_at, q.id),
        '[]'::jsonb
    )
    into v_messages
    from (
        select distinct on (m.thread_id) m.id, m.thread_id, m.created_at
        from public.chat_messages m
        join public.conversation_user_state s
          on s.conversation_id = m.thread_id
         and s.user_id = v_actor
        where m.thread_id = any(v_thread_ids)
          and s.deleted_at is null
          and m.deleted_at is null
          and (s.first_visible_message_id is null or m.id >= s.first_visible_message_id)
        order by m.thread_id, m.created_at desc, m.id desc
    ) q;

    select coalesce(
        jsonb_agg(distinct public.quata_chat_profile_json(p.profile_id)),
        '[]'::jsonb
    )
    into v_profiles
    from public.chat_participants p
    where p.thread_id = any(v_thread_ids)
      and p.left_at is null;

    select coalesce(sum((public.quata_chat_thread_json(tid, v_actor)->>'unread')::int), 0)
    into v_unread_total
    from unnest(v_thread_ids) tid;

    return jsonb_build_object(
        'threads', coalesce(v_threads, '[]'::jsonb),
        'profiles', coalesce(v_profiles, '[]'::jsonb),
        'messages', coalesce(v_messages, '[]'::jsonb),
        'unread_total', coalesce(v_unread_total, 0),
        'has_more', v_has_more,
        'next_cursor', case
            when v_has_more and v_cursor_thread_id is not null then jsonb_build_object(
                'last_message_at', v_cursor_last_message_at,
                'updated_at', v_cursor_updated_at,
                'thread_id', v_cursor_thread_id
            )
            else null
        end,
        'current_time_millis', public.quata_chat_epoch_millis(clock_timestamp())
    );
end;
$$;

revoke all on function public.quata_chat_get_inbox_page(
    uuid,
    integer,
    timestamptz,
    timestamptz,
    bigint
) from public, anon;

grant execute on function public.quata_chat_get_inbox_page(
    uuid,
    integer,
    timestamptz,
    timestamptz,
    bigint
) to authenticated;
