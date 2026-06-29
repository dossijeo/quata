create or replace function public.quata_enqueue_chat_push()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
    if new.deleted_at is not null then
        return new;
    end if;

    perform net.http_post(
        url := 'https://yrrlankpwmhluexshxnw.supabase.co/functions/v1/quata-push-dispatch',
        body := jsonb_build_object('message_id', new.id),
        headers := jsonb_build_object('Content-Type', 'application/json'),
        timeout_milliseconds := 5000
    );

    return new;
exception
    when undefined_function or insufficient_privilege or invalid_schema_name then
        return new;
end;
$$;
