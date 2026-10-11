-- Add the private v2 Chat attachment lane without breaking Android v32.
-- The legacy chat-attachments bucket remains public until v32 has no active clients.

insert into storage.buckets (id, name, public)
values ('chat-attachments-private', 'chat-attachments-private', false)
on conflict (id) do update set public = false;

create or replace function public.quata_chat_private_object_is_linked(
    p_bucket text,
    p_path text
)
returns boolean
language plpgsql
stable
security definer
set search_path = public, storage
as $$
begin
    -- The policy helper exposes status only for the caller's own private object path.
    if p_bucket is distinct from 'chat-attachments-private'
       or (storage.foldername(p_path))[1] is distinct from public.quata_chat_auth_profile_id()::text
    then
        return true;
    end if;
    return exists (
        select 1
        from public.chat_attachments linked
        where linked.storage_bucket = p_bucket
          and linked.storage_path = p_path
          and linked.message_id is not null
    );
end;
$$;

revoke all on function public.quata_chat_private_object_is_linked(text, text) from public, anon;
grant execute on function public.quata_chat_private_object_is_linked(text, text) to authenticated;

drop policy if exists chat_attachments_private_insert_own on storage.objects;
create policy chat_attachments_private_insert_own
on storage.objects for insert to authenticated
with check (
    bucket_id = 'chat-attachments-private'
    and (storage.foldername(name))[1] = public.quata_chat_auth_profile_id()::text
);

drop policy if exists chat_attachments_private_update_own on storage.objects;
create policy chat_attachments_private_update_own
on storage.objects for update to authenticated
using (
    bucket_id = 'chat-attachments-private'
    and (storage.foldername(name))[1] = public.quata_chat_auth_profile_id()::text
    and not public.quata_chat_private_object_is_linked(bucket_id, name)
)
with check (
    bucket_id = 'chat-attachments-private'
    and (storage.foldername(name))[1] = public.quata_chat_auth_profile_id()::text
    and not public.quata_chat_private_object_is_linked(bucket_id, name)
);

drop policy if exists chat_attachments_private_delete_own on storage.objects;
create policy chat_attachments_private_delete_own
on storage.objects for delete to authenticated
using (
    bucket_id = 'chat-attachments-private'
    and (storage.foldername(name))[1] = public.quata_chat_auth_profile_id()::text
    and not public.quata_chat_private_object_is_linked(bucket_id, name)
);

drop policy if exists chat_attachments_private_select_visible on storage.objects;
create policy chat_attachments_private_select_visible
on storage.objects for select to authenticated
using (
    bucket_id = 'chat-attachments-private'
    and (
        (
            (storage.foldername(name))[1] = public.quata_chat_auth_profile_id()::text
            and not public.quata_chat_private_object_is_linked(bucket_id, name)
        )
        or exists (
            select 1
            from public.chat_attachments attachment
            join public.chat_messages message
              on message.id = attachment.message_id
             and message.thread_id = attachment.thread_id
            join public.chat_participants participant
              on participant.thread_id = attachment.thread_id
             and participant.profile_id = public.quata_chat_auth_profile_id()
             and participant.left_at is null
             and participant.is_deleted = false
            join public.conversation_user_state user_state
              on user_state.conversation_id = attachment.thread_id
             and user_state.user_id = participant.profile_id
             and user_state.deleted_at is null
            where attachment.storage_bucket = bucket_id
              and attachment.storage_path = name
              and message.deleted_at is null
              and (
                  user_state.first_visible_message_id is null
                  or message.id >= user_state.first_visible_message_id
              )
        )
    )
);

create or replace function public.quata_chat_register_attachment_v2(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_file_url text,
    p_storage_bucket text,
    p_storage_path text,
    p_mime_type text default 'application/octet-stream',
    p_name text default null,
    p_size_bytes bigint default null,
    p_ext text default null,
    p_thumb jsonb default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, storage
as $$
declare
    v_actor uuid;
    v_attachment_id bigint;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not an active participant of this thread' using errcode = '42501';
    end if;

    if not exists (
        select 1
        from public.conversation_user_state state
        where state.conversation_id = p_thread_id
          and state.user_id = v_actor
          and state.deleted_at is null
    ) then
        raise exception 'conversation is not visible to this profile' using errcode = '42501';
    end if;

    if p_storage_bucket is distinct from 'chat-attachments-private' then
        raise exception 'private attachment bucket is required' using errcode = '22023';
    end if;
    if p_storage_path is null
       or p_storage_path !~ ('^' || v_actor::text || '/chat/[A-Za-z0-9._/-]+$')
       or p_storage_path like '%..%'
       or p_storage_path like '%//%'
    then
        raise exception 'invalid private attachment storage path' using errcode = '22023';
    end if;
    if nullif(trim(p_file_url), '') is null
       or right(
            p_file_url,
            length('/storage/v1/object/public/chat-attachments-private/' || p_storage_path)
       ) <> '/storage/v1/object/public/chat-attachments-private/' || p_storage_path
    then
        raise exception 'private attachment pointer does not match storage identity' using errcode = '22023';
    end if;
    if not exists (
        select 1
        from storage.objects object
        where object.bucket_id = p_storage_bucket
          and object.name = p_storage_path
          and (storage.foldername(object.name))[1] = v_actor::text
          and object.owner_id = auth.uid()::text
    ) then
        raise exception 'private attachment object is missing' using errcode = '22023';
    end if;

    insert into public.chat_attachments(
        thread_id, uploaded_by_profile_id, storage_bucket, storage_path,
        file_url, mime_type, file_name, size_bytes, ext, thumb
    ) values (
        p_thread_id, v_actor, p_storage_bucket, p_storage_path,
        p_file_url, p_mime_type, p_name, p_size_bytes, p_ext, p_thumb
    ) returning id into v_attachment_id;

    return jsonb_build_object(
        'result', true,
        'id', v_attachment_id,
        'file', public.quata_chat_attachment_json(v_attachment_id)
    );
end;
$$;

revoke all on function public.quata_chat_register_attachment_v2(uuid, bigint, text, text, text, text, text, bigint, text, jsonb) from public, anon;
grant execute on function public.quata_chat_register_attachment_v2(uuid, bigint, text, text, text, text, text, bigint, text, jsonb) to authenticated;

-- These serializers are implementation helpers for guarded RPCs, never public APIs.
revoke all on function public.quata_chat_attachment_json(bigint) from public, anon, authenticated;
revoke all on function public.quata_chat_message_json(bigint, uuid) from public, anon, authenticated;
revoke all on function public.quata_chat_thread_json(bigint, uuid) from public, anon, authenticated;
