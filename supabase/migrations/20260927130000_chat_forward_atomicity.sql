-- Forwarding to several destinations is one user action. Validate every target
-- before the first insert and let any later error abort the whole RPC statement.
create or replace function public.quata_chat_forward_message(
    p_actor_profile_id uuid,
    p_message_id bigint,
    p_thread_ids bigint[]
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_source public.chat_messages%rowtype;
    v_target_thread_ids bigint[];
    v_target_thread_id bigint;
    v_new_message_id bigint;
    v_sent jsonb := '{}'::jsonb;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    select *
    into v_source
    from public.chat_messages
    where id = p_message_id
      and deleted_at is null;

    if v_source.id is null then
        raise exception 'source message does not exist' using errcode = '22023';
    end if;

    if not public.quata_chat_is_thread_participant(v_source.thread_id, v_actor) then
        raise exception 'profile cannot read source message' using errcode = '42501';
    end if;

    select coalesce(array_agg(target_thread_id order by target_thread_id), array[]::bigint[])
    into v_target_thread_ids
    from (
        select distinct unnest(coalesce(p_thread_ids, array[]::bigint[])) as target_thread_id
    ) requested_targets;

    -- Complete authorization before the first write. If membership changed
    -- after the picker was shown, no destination receives a partial copy.
    foreach v_target_thread_id in array v_target_thread_ids
    loop
        if not public.quata_chat_is_thread_participant(v_target_thread_id, v_actor) then
            raise exception 'profile cannot forward to target thread' using errcode = '42501';
        end if;
    end loop;

    foreach v_target_thread_id in array v_target_thread_ids
    loop
        insert into public.chat_messages(
            thread_id,
            sender_profile_id,
            body,
            forwarded_from_message_id,
            forwarded_from_profile_id
        )
        values (
            v_target_thread_id,
            v_actor,
            v_source.body,
            v_source.id,
            v_source.sender_profile_id
        )
        returning id into v_new_message_id;

        insert into public.chat_attachments(
            thread_id,
            message_id,
            uploaded_by_profile_id,
            storage_bucket,
            storage_path,
            file_url,
            thumb,
            mime_type,
            file_name,
            size_bytes,
            ext,
            attached_at
        )
        select
            v_target_thread_id,
            v_new_message_id,
            v_actor,
            a.storage_bucket,
            a.storage_path,
            a.file_url,
            a.thumb,
            a.mime_type,
            a.file_name,
            a.size_bytes,
            a.ext,
            now()
        from public.chat_attachments a
        where a.message_id = v_source.id;

        v_sent := v_sent || jsonb_build_object(v_target_thread_id::text, v_new_message_id);
    end loop;

    return jsonb_build_object(
        'result', cardinality(v_target_thread_ids) > 0,
        'sent', v_sent,
        'errors', '[]'::jsonb
    );
end;
$$;

