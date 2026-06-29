-- Open or create a single community chat and add the current user as participant.

create or replace function public.quata_chat_open_community_thread(
    p_actor_profile_id uuid,
    p_community_id uuid,
    p_title text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_thread_id bigint;
    v_unique_key text := 'quata-community:' || p_community_id::text;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    select id
    into v_thread_id
    from public.chat_threads
    where unique_key = v_unique_key
      and deleted_at is null
    limit 1;

    if v_thread_id is null then
        insert into public.chat_threads(
            type,
            subject,
            title,
            community_id,
            created_by_profile_id,
            unique_key,
            allow_invite
        )
        values (
            'wall',
            nullif(p_title, ''),
            nullif(p_title, ''),
            p_community_id,
            v_actor,
            v_unique_key,
            true
        )
        returning id into v_thread_id;
    end if;

    insert into public.chat_participants(thread_id, profile_id, role)
    values (v_thread_id, v_actor, 'member')
    on conflict (thread_id, profile_id)
    do update set left_at = null, is_hidden = false, is_deleted = false;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (
        v_thread_id,
        v_actor,
        'community_thread_opened',
        jsonb_build_object('community_id', p_community_id)
    );

    return public.quata_chat_get_thread(v_actor, v_thread_id)
        || jsonb_build_object('result', true, 'thread_id', v_thread_id);
end;
$$;

grant execute on function public.quata_chat_open_community_thread(uuid, uuid, text) to anon, authenticated;
