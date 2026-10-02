begin;

create or replace function public.quata_chat_reactivate_user_state_for_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.conversation_user_state(
        conversation_id,
        user_id,
        first_visible_message_id,
        muted_at,
        last_read_message_id,
        created_at,
        updated_at
    )
    select
        participant.thread_id,
        participant.profile_id,
        coalesce(public.quata_chat_first_message_id(participant.thread_id), new.id),
        participant.muted_at,
        participant.last_read_message_id,
        now(),
        now()
    from public.chat_participants participant
    where participant.thread_id = new.thread_id
      and participant.left_at is null
    on conflict (conversation_id, user_id)
    do nothing;

    update public.conversation_user_state state
    set deleted_at = null,
        first_visible_message_id = case
            when state.deleted_at is not null then new.id
            else coalesce(state.first_visible_message_id, new.id)
        end
    where state.conversation_id = new.thread_id
      and state.user_id in (
          select participant.profile_id
          from public.chat_participants participant
          where participant.thread_id = new.thread_id
            and participant.left_at is null
            and (
                participant.profile_id = new.sender_profile_id
                or state.deleted_at is not null
                or state.first_visible_message_id is null
            )
      );

    update public.chat_participants participant
    set is_hidden = false,
        is_deleted = false
    where participant.thread_id = new.thread_id
      and participant.left_at is null
      and exists (
          select 1
          from public.conversation_user_state state
          where state.conversation_id = participant.thread_id
            and state.user_id = participant.profile_id
            and state.deleted_at is null
      );

    return new;
end;
$$;

with active_missing_boundary as (
    select
        state.conversation_id,
        state.user_id,
        state.updated_at,
        first_message.id as first_message_id,
        first_message.created_at as first_message_created_at
    from public.conversation_user_state state
    join lateral (
        select message.id, message.created_at
        from public.chat_messages message
        where message.thread_id = state.conversation_id
        order by message.created_at, message.id
        limit 1
    ) first_message on true
    where state.first_visible_message_id is null
      and state.deleted_at is null
)
update public.conversation_user_state state
set first_visible_message_id = case
        when missing.updated_at <= missing.first_message_created_at then missing.first_message_id
        else null
    end,
    deleted_at = case
        when missing.updated_at <= missing.first_message_created_at then null
        else now()
    end
from active_missing_boundary missing
where state.conversation_id = missing.conversation_id
  and state.user_id = missing.user_id;

commit;
