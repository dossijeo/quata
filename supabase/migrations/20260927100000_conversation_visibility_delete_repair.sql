begin;

-- A hard delete of the message referenced by first_visible_message_id invokes
-- the FK's ON DELETE SET NULL action. Repoint the boundary before that happens
-- so a conversation with remaining messages never becomes unbounded.
create or replace function public.quata_chat_repoint_visibility_before_message_delete()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.conversation_user_state state
       set first_visible_message_id = (
           select min(message.id)
             from public.chat_messages message
            where message.thread_id = old.thread_id
              and message.id <> old.id
       )
     where state.conversation_id = old.thread_id
       and state.first_visible_message_id = old.id;

    return old;
end;
$$;

revoke all on function public.quata_chat_repoint_visibility_before_message_delete() from public;

drop trigger if exists chat_messages_repoint_visibility_before_delete on public.chat_messages;
create trigger chat_messages_repoint_visibility_before_delete
before delete on public.chat_messages
for each row
execute function public.quata_chat_repoint_visibility_before_message_delete();

-- Repair rows left null by hard deletes that predate the trigger. This update
-- is bounded to conversations which still contain at least one message.
update public.conversation_user_state state
   set first_visible_message_id = (
       select min(message.id)
         from public.chat_messages message
        where message.thread_id = state.conversation_id
   )
 where state.first_visible_message_id is null
   and exists (
       select 1
         from public.chat_messages message
        where message.thread_id = state.conversation_id
   );

commit;
