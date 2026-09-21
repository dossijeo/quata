-- Restore the historical moderator override for message edit/delete.

begin;

create or replace function public.quata_chat_edit_message(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_message_id bigint,
    p_message text
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

    update public.chat_messages m
    set body = coalesce(p_message, ''),
        edited_at = now(),
        updated_at = now()
    where m.id = p_message_id
      and m.thread_id = p_thread_id
      and m.deleted_at is null
      and (
          m.sender_profile_id = v_actor
          or public.quata_chat_can_moderate(p_thread_id, v_actor)
      );

    if not found then
        raise exception 'message cannot be edited' using errcode = '42501';
    end if;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (p_thread_id, v_actor, 'message_edited', jsonb_build_object('message_id', p_message_id));

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

create or replace function public.quata_chat_delete_messages(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_message_ids bigint[]
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

    update public.chat_messages m
    set deleted_at = now(),
        deleted_by_profile_id = v_actor,
        updated_at = now()
    where m.thread_id = p_thread_id
      and m.id = any(coalesce(p_message_ids, array[]::bigint[]))
      and m.deleted_at is null
      and (
          m.sender_profile_id = v_actor
          or public.quata_chat_can_moderate(p_thread_id, v_actor)
      );

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (p_thread_id, v_actor, 'messages_deleted', jsonb_build_object('message_ids', to_jsonb(p_message_ids)));

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

grant execute on function public.quata_chat_edit_message(uuid, bigint, bigint, text) to anon, authenticated;
grant execute on function public.quata_chat_delete_messages(uuid, bigint, bigint[]) to anon, authenticated;

commit;
