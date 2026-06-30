-- Remove draft private chat threads that were opened but never received a message.

create or replace function public.quata_chat_cleanup_empty_private_thread(
    p_actor_profile_id uuid,
    p_thread_id bigint
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_deleted boolean := false;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        return jsonb_build_object(
            'deleted', false,
            'thread_id', p_thread_id,
            'reason', 'not_participant'
        );
    end if;

    delete from public.chat_threads t
    where t.id = p_thread_id
      and t.type = 'private'
      and t.deleted_at is null
      and exists (
          select 1
          from public.chat_participants p
          where p.thread_id = t.id
            and p.profile_id = v_actor
            and p.left_at is null
      )
      and not exists (
          select 1
          from public.chat_messages m
          where m.thread_id = t.id
      )
    returning true into v_deleted;

    return jsonb_build_object(
        'deleted', coalesce(v_deleted, false),
        'thread_id', p_thread_id
    );
end;
$$;

grant execute on function public.quata_chat_cleanup_empty_private_thread(uuid, bigint) to anon, authenticated;
