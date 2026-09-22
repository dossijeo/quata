-- Restore the versioned latest-page semantics for Chat thread reads.
-- The deployed function currently selects the oldest rows before LIMIT; callers merge
-- known IDs incrementally and require the newest bounded page in chronological order.

create or replace function public.quata_chat_get_thread(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_known_message_ids bigint[] default '{}',
    p_limit integer default 250
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_messages jsonb;
    v_profiles jsonb;
    v_limit integer := greatest(1, least(coalesce(p_limit, 250), 500));
    v_deleted_at timestamptz;
    v_first_visible_message_id bigint;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    perform public.quata_chat_ensure_conversation_user_state(p_thread_id, v_actor);

    select s.deleted_at, s.first_visible_message_id
    into v_deleted_at, v_first_visible_message_id
    from public.conversation_user_state s
    where s.conversation_id = p_thread_id
      and s.user_id = v_actor;

    select coalesce(jsonb_agg(public.quata_chat_message_json(q.id, v_actor) order by q.created_at, q.id), '[]'::jsonb)
    into v_messages
    from (
        select m.id, m.created_at
        from public.chat_messages m
        where m.thread_id = p_thread_id
          and v_deleted_at is null
          and (v_first_visible_message_id is null or m.id >= v_first_visible_message_id)
          and not (m.id = any(coalesce(p_known_message_ids, array[]::bigint[])))
        order by m.created_at desc, m.id desc
        limit v_limit
    ) q;

    select coalesce(jsonb_agg(public.quata_chat_profile_json(p.profile_id) order by p.joined_at), '[]'::jsonb)
    into v_profiles
    from public.chat_participants p
    where p.thread_id = p_thread_id
      and p.left_at is null;

    return jsonb_build_object(
        'threads', jsonb_build_array(public.quata_chat_thread_json(p_thread_id, v_actor)),
        'thread', public.quata_chat_thread_json(p_thread_id, v_actor),
        'profiles', v_profiles,
        'messages', coalesce(v_messages, '[]'::jsonb),
        'server_time_millis', public.quata_chat_epoch_millis(clock_timestamp())
    );
end;
$$;
