begin;

create or replace function public.quata_chat_promote_moderator(
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

    if not public.quata_chat_can_moderate(p_thread_id, v_actor) then
        raise exception 'profile cannot promote moderators' using errcode = '42501';
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
    if v_target_role = 'owner' then
        raise exception 'thread owner cannot be promoted' using errcode = '42501';
    end if;

    update public.chat_participants
       set role = 'moderator'
     where thread_id = p_thread_id
       and profile_id = p_profile_id;

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

create or replace function public.quata_chat_demote_moderator(
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

    if not public.quata_chat_can_moderate(p_thread_id, v_actor) then
        raise exception 'profile cannot demote moderators' using errcode = '42501';
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
    if v_target_role = 'owner' then
        raise exception 'thread owner cannot be demoted' using errcode = '42501';
    end if;

    update public.chat_participants
       set role = 'member'
     where thread_id = p_thread_id
       and profile_id = p_profile_id
       and role = 'moderator';

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

create or replace function public.quata_chat_remove_participant(
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

    if not public.quata_chat_can_moderate(p_thread_id, v_actor) then
        raise exception 'profile cannot remove participants' using errcode = '42501';
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
    if v_target_role = 'owner' then
        raise exception 'thread owner cannot be removed' using errcode = '42501';
    end if;

    update public.chat_participants
       set left_at = now(),
           is_hidden = true
     where thread_id = p_thread_id
       and profile_id = p_profile_id;

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

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
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;
    if p_profile_id = v_actor then
        raise exception 'profile cannot block itself' using errcode = '22023';
    end if;
    if not public.quata_chat_is_thread_participant(p_thread_id, p_profile_id) then
        raise exception 'target participant does not exist' using errcode = '22023';
    end if;

    insert into public.chat_profile_blocks(thread_id, blocker_profile_id, blocked_profile_id)
    values (p_thread_id, v_actor, p_profile_id)
    on conflict do nothing;

    return jsonb_build_object('result', true, 'thread_id', p_thread_id, 'blocked_profile_id', p_profile_id);
end;
$$;

revoke all on function public.quata_chat_promote_moderator(uuid, bigint, uuid) from public;
revoke all on function public.quata_chat_demote_moderator(uuid, bigint, uuid) from public;
revoke all on function public.quata_chat_remove_participant(uuid, bigint, uuid) from public;
revoke all on function public.quata_chat_block_participant(uuid, bigint, uuid) from public;

grant execute on function public.quata_chat_promote_moderator(uuid, bigint, uuid) to anon, authenticated;
grant execute on function public.quata_chat_demote_moderator(uuid, bigint, uuid) to anon, authenticated;
grant execute on function public.quata_chat_remove_participant(uuid, bigint, uuid) to anon, authenticated;
grant execute on function public.quata_chat_block_participant(uuid, bigint, uuid) to anon, authenticated;

commit;
