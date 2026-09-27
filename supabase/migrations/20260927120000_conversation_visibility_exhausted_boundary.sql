begin;

-- If the deleted boundary has no later message, NULL cannot mean "unbounded":
-- every remaining message is older and must stay hidden. Use the existing
-- deleted-at tombstone until the normal after-insert trigger reopens the thread
-- at the next new message.
create or replace function public.quata_chat_repoint_visibility_before_message_delete()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_next_message_id bigint;
begin
    select min(message.id)
      into v_next_message_id
      from public.chat_messages message
     where message.thread_id = old.thread_id
       and message.id > old.id;

    update public.conversation_user_state state
       set first_visible_message_id = v_next_message_id,
           deleted_at = case
               when v_next_message_id is null then coalesce(state.deleted_at, now())
               else state.deleted_at
           end
     where state.conversation_id = old.thread_id
       and state.first_visible_message_id = old.id;

    return old;
end;
$$;

revoke all on function public.quata_chat_repoint_visibility_before_message_delete() from public;

-- Repair any active, unbounded state produced after the previous trigger was
-- deployed. The earlier rollout already backfilled pre-existing NULL states,
-- so an active NULL with remaining messages now denotes an exhausted boundary.
update public.conversation_user_state state
   set deleted_at = now()
 where state.first_visible_message_id is null
   and state.deleted_at is null
   and exists (
       select 1
         from public.chat_messages message
        where message.thread_id = state.conversation_id
   );

commit;
