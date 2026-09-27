begin;

create or replace function public.quata_chat_block_participant(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_profile_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_target_role text;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;
    if p_profile_id = v_actor then
        raise exception 'profile cannot block itself' using errcode = '22023';
    end if;

    select role
      into v_target_role
      from public.chat_participants
     where thread_id = p_thread_id
       and profile_id = p_profile_id
       and left_at is null
     for update;

    if v_target_role is null then
        raise exception 'target participant does not exist' using errcode = '22023';
    end if;

    insert into public.chat_profile_blocks(thread_id, blocker_profile_id, blocked_profile_id)
    values (p_thread_id, v_actor, p_profile_id)
    on conflict do nothing;

    return jsonb_build_object('result', true, 'thread_id', p_thread_id, 'blocked_profile_id', p_profile_id);
end;
$$;

revoke all on function public.quata_chat_block_participant(uuid, bigint, uuid) from public;
grant execute on function public.quata_chat_block_participant(uuid, bigint, uuid) to anon, authenticated;

commit;
