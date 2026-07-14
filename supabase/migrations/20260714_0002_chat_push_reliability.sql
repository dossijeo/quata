-- Session-safe push token removal and optional authenticated dispatch.

create or replace function public.quata_unregister_push_token(
    p_profile_id uuid,
    p_token text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_count integer;
begin
    v_actor := public.quata_chat_actor_profile_id(p_profile_id);

    update public.push_tokens
    set disabled_at = now(),
        last_error_text = 'Disabled on user logout',
        updated_at = now()
    where user_id = v_actor
      and token = trim(coalesce(p_token, ''))
      and disabled_at is null;

    get diagnostics v_count = row_count;
    return jsonb_build_object('result', true, 'disabled', v_count);
end;
$$;

revoke all on function public.quata_unregister_push_token(uuid, text) from public;
revoke all on function public.quata_unregister_push_token(uuid, text) from anon;
grant execute on function public.quata_unregister_push_token(uuid, text) to authenticated;

-- Store quata_push_dispatch_secret in Supabase Vault and set the same value as
-- QUATA_PUSH_DISPATCH_SECRET in the Edge Function to enforce this header.
create or replace function public.quata_enqueue_chat_push()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_message_id bigint;
    v_secret text;
begin
    select nullif(ds.decrypted_secret, '')
    into v_secret
    from vault.decrypted_secrets ds
    where ds.name = 'quata_push_dispatch_secret'
    order by ds.created_at desc
    limit 1;

    if tg_table_name = 'chat_messages' then
        if new.deleted_at is not null or nullif(btrim(coalesce(new.body, '')), '') is null then
            return new;
        end if;
        v_message_id := new.id;
    elsif tg_table_name = 'chat_attachments' then
        if new.message_id is null or (tg_op = 'UPDATE' and old.message_id is not null and old.message_id = new.message_id) then
            return new;
        end if;
        v_message_id := new.message_id;
    else
        return new;
    end if;

    perform net.http_post(
        url := 'https://yrrlankpwmhluexshxnw.supabase.co/functions/v1/quata-push-dispatch',
        body := jsonb_build_object('message_id', v_message_id),
        headers := jsonb_strip_nulls(jsonb_build_object(
            'Content-Type', 'application/json',
            'x-quata-push-secret', v_secret
        )),
        timeout_milliseconds := 5000
    );
    return new;
exception
    when undefined_function or insufficient_privilege or invalid_schema_name then return new;
end;
$$;
