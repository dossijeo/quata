begin;

-- A user's first-visible boundary is monotonic. If its exact message is hard
-- deleted, advance to the next message in that conversation; never select an
-- older message that the user had already hidden by deleting/reopening Chat.
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
              and message.id > old.id
       )
     where state.conversation_id = old.thread_id
       and state.first_visible_message_id = old.id;

    return old;
end;
$$;

revoke all on function public.quata_chat_repoint_visibility_before_message_delete() from public;

commit;
