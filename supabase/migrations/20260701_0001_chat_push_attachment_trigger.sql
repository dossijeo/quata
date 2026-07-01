-- Dispatch text chat pushes from chat_messages and attachment-only pushes
-- after the attachment is linked, so file/audio notifications can be typed.

create or replace function public.quata_enqueue_chat_push()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_message_id bigint;
begin
    if tg_table_name = 'chat_messages' then
        if new.deleted_at is not null then
            return new;
        end if;

        if nullif(btrim(coalesce(new.body, '')), '') is null then
            return new;
        end if;

        v_message_id := new.id;
    elsif tg_table_name = 'chat_attachments' then
        if new.message_id is null then
            return new;
        end if;

        if tg_op = 'UPDATE' and old.message_id is not null and old.message_id = new.message_id then
            return new;
        end if;

        v_message_id := new.message_id;
    else
        return new;
    end if;

    perform net.http_post(
        url := 'https://yrrlankpwmhluexshxnw.supabase.co/functions/v1/quata-push-dispatch',
        body := jsonb_build_object('message_id', v_message_id),
        headers := jsonb_build_object('Content-Type', 'application/json'),
        timeout_milliseconds := 5000
    );

    return new;
exception
    when undefined_function or insufficient_privilege or invalid_schema_name then
        return new;
end;
$$;

drop trigger if exists chat_messages_after_insert_push on public.chat_messages;
create trigger chat_messages_after_insert_push
after insert on public.chat_messages
for each row
when (nullif(btrim(coalesce(new.body, '')), '') is not null)
execute function public.quata_enqueue_chat_push();

drop trigger if exists chat_attachments_after_link_push on public.chat_attachments;
create trigger chat_attachments_after_link_push
after insert or update of message_id on public.chat_attachments
for each row
when (new.message_id is not null)
execute function public.quata_enqueue_chat_push();
