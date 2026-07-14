-- Include the reply context in every message payload so paginated and
-- offline clients do not need the replied-to message to be present in the page.
create or replace function public.quata_chat_message_json(
    p_message_id bigint,
    p_actor_profile_id uuid
)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
    with message_row as (
        select *
        from public.chat_messages
        where id = p_message_id
    ),
    recipient_state as (
        select count(*)::int as recipient_count
        from message_row m
        join public.chat_participants p
          on p.thread_id = m.thread_id
         and p.left_at is null
         and p.profile_id <> m.sender_profile_id
    ),
    status_state as (
        select
            count(distinct s.profile_id) filter (where s.status in ('DELIVERED', 'READ'))::int as delivered_count,
            count(distinct s.profile_id) filter (where s.status = 'READ')::int as read_count
        from public.chat_message_states s
        where s.message_id = p_message_id
    )
    select jsonb_build_object(
        'id', m.id,
        'thread_id', m.thread_id,
        'sender_profile_id', m.sender_profile_id,
        'sender', public.quata_chat_profile_json(m.sender_profile_id),
        'body', case when m.deleted_at is null then m.body else '' end,
        'created_at', m.created_at,
        'created_at_millis', public.quata_chat_epoch_millis(m.created_at),
        'updated_at', coalesce(m.edited_at, m.updated_at),
        'updated_at_millis', public.quata_chat_epoch_millis(coalesce(m.edited_at, m.updated_at, m.created_at)),
        'edited_at', m.edited_at,
        'deleted_at', m.deleted_at,
        'is_deleted', m.deleted_at is not null,
        'is_edited', m.edited_at is not null,
        'favorited', exists (
            select 1
            from public.chat_message_favorites f
            where f.message_id = m.id
              and f.profile_id = p_actor_profile_id
        ),
        'reply_to_message_id', m.reply_to_message_id,
        'reply_to_sender_profile_id', r.sender_profile_id,
        'reply_to_sender', case
            when r.id is null then null
            else public.quata_chat_profile_json(r.sender_profile_id)
        end,
        'reply_to_body', case
            when r.id is null then null
            when r.deleted_at is null then r.body
            else ''
        end,
        'forwarded_from_message_id', m.forwarded_from_message_id,
        'forwarded_from_profile_id', m.forwarded_from_profile_id,
        'client_message_id', m.client_message_id,
        'delivery_recipient_count', coalesce(recipient_state.recipient_count, 0),
        'delivered_count', coalesce(status_state.delivered_count, 0),
        'read_count', coalesce(status_state.read_count, 0),
        'delivery_state', case
            when m.sender_profile_id <> p_actor_profile_id then null
            when coalesce(recipient_state.recipient_count, 0) = 0 then 'SENT'
            when coalesce(status_state.read_count, 0) >= recipient_state.recipient_count then 'READ'
            when coalesce(status_state.delivered_count, 0) >= recipient_state.recipient_count then 'DELIVERED'
            else 'SENT'
        end,
        'metadata', m.metadata,
        'attachments', coalesce(
            (
                select jsonb_agg(public.quata_chat_attachment_json(a.id) order by a.id)
                from public.chat_attachments a
                where a.message_id = m.id
            ),
            '[]'::jsonb
        )
    )
    from message_row m
    cross join recipient_state
    cross join status_state
    left join public.chat_messages r on r.id = m.reply_to_message_id
$$;
