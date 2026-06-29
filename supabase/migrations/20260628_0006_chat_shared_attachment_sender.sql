-- Adds sender metadata to the direct shared-attachment lookup used by Android profiles.

create or replace function public.quata_chat_list_shared_attachments(
    p_actor_profile_id uuid,
    p_peer_profile_id uuid default null,
    p_thread_id bigint default null,
    p_community_id uuid default null,
    p_limit integer default 100,
    p_offset integer default 0,
    p_kind text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_limit integer := greatest(1, least(coalesce(p_limit, 100), 250));
    v_offset integer := greatest(0, coalesce(p_offset, 0));
    v_thread_ids bigint[];
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if p_thread_id is not null then
        if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
            raise exception 'profile is not a participant of this thread' using errcode = '42501';
        end if;
        v_thread_ids := array[p_thread_id];
    elsif p_peer_profile_id is not null then
        select coalesce(array_agg(t.id order by t.updated_at desc), array[]::bigint[])
        into v_thread_ids
        from public.chat_threads t
        join public.chat_participants actor_p
          on actor_p.thread_id = t.id
         and actor_p.profile_id = v_actor
         and actor_p.left_at is null
         and actor_p.is_hidden = false
         and actor_p.is_deleted = false
        join public.chat_participants peer_p
          on peer_p.thread_id = t.id
         and peer_p.profile_id = p_peer_profile_id
         and peer_p.left_at is null
        where t.deleted_at is null;
    elsif p_community_id is not null then
        select coalesce(array_agg(t.id order by t.updated_at desc), array[]::bigint[])
        into v_thread_ids
        from public.chat_threads t
        join public.chat_participants actor_p
          on actor_p.thread_id = t.id
         and actor_p.profile_id = v_actor
         and actor_p.left_at is null
         and actor_p.is_hidden = false
         and actor_p.is_deleted = false
        where t.deleted_at is null
          and t.community_id = p_community_id;
    else
        select coalesce(array_agg(t.id order by t.updated_at desc), array[]::bigint[])
        into v_thread_ids
        from public.chat_threads t
        join public.chat_participants actor_p
          on actor_p.thread_id = t.id
         and actor_p.profile_id = v_actor
         and actor_p.left_at is null
         and actor_p.is_hidden = false
         and actor_p.is_deleted = false
        where t.deleted_at is null;
    end if;

    return (
        select jsonb_build_object(
            'files',
            coalesce(
                jsonb_agg(
                    public.quata_chat_attachment_json(q.id)
                    || jsonb_build_object(
                        'sender_profile_id', q.sender_profile_id,
                        'sender_name', coalesce(
                            nullif(cp.display_name, ''),
                            nullif(cp.nombre, ''),
                            nullif(cp.phone_local, ''),
                            'Usuario'
                        ),
                        'created_at_millis', public.quata_chat_epoch_millis(q.created_at)
                    )
                    order by q.created_at desc, q.id desc
                ),
                '[]'::jsonb
            )
        )
        from (
            select a.id, a.created_at, m.sender_profile_id
            from public.chat_attachments a
            left join public.chat_messages m on m.id = a.message_id
            where a.thread_id = any(v_thread_ids)
              and (
                  p_kind is null
                  or p_kind = ''
                  or (p_kind = 'image' and a.mime_type like 'image/%')
                  or (p_kind = 'video' and a.mime_type like 'video/%')
                  or (p_kind = 'document' and a.mime_type not like 'image/%' and a.mime_type not like 'video/%')
              )
            order by a.created_at desc, a.id desc
            limit v_limit
            offset v_offset
        ) q
        left join public.community_profiles cp on cp.id = q.sender_profile_id
    );
end;
$$;

grant execute on function public.quata_chat_list_shared_attachments(uuid, uuid, bigint, uuid, integer, integer, text) to anon, authenticated;
