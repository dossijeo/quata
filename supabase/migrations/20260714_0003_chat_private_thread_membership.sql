-- A direct thread is reusable only while its active participants are exactly
-- the two profiles registered in chat_private_threads. If a third member is
-- added, the existing thread is preserved as a group and the pair can create
-- a new direct conversation.

create or replace function public.quata_chat_enforce_private_thread_membership()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_thread_id bigint;
    v_low uuid;
    v_high uuid;
    v_active_count integer;
    v_is_exact_pair boolean;
    v_has_other_participant boolean;
begin
    v_thread_id := case when tg_op = 'DELETE' then old.thread_id else new.thread_id end;

    select cpt.profile_low_id, cpt.profile_high_id
    into v_low, v_high
    from public.chat_private_threads cpt
    where cpt.thread_id = v_thread_id;

    if not found then
        return null;
    end if;

    select
        count(*)::int,
        count(*) = 2
            and bool_or(p.profile_id = v_low)
            and bool_or(p.profile_id = v_high),
        bool_or(p.profile_id not in (v_low, v_high))
    into v_active_count, v_is_exact_pair, v_has_other_participant
    from public.chat_participants p
    where p.thread_id = v_thread_id
      and p.left_at is null;

    if coalesce(v_is_exact_pair, false) then
        return null;
    end if;

    update public.chat_threads
    set
        type = case
            when v_active_count > 2 or coalesce(v_has_other_participant, false) then 'group'
            else type
        end,
        unique_key = null
    where id = v_thread_id;

    delete from public.chat_private_threads
    where thread_id = v_thread_id;

    return null;
end;
$$;

drop trigger if exists chat_participants_enforce_private_membership on public.chat_participants;
create constraint trigger chat_participants_enforce_private_membership
after insert or delete or update of left_at on public.chat_participants
deferrable initially deferred
for each row
execute function public.quata_chat_enforce_private_thread_membership();

-- Repair pairs that became groups before the invariant above existed.
update public.chat_threads t
set
    type = case
        when (
            select count(*)
            from public.chat_participants p
            where p.thread_id = t.id
              and p.left_at is null
        ) > 2 or exists (
            select 1
            from public.chat_participants p
            join public.chat_private_threads cpt on cpt.thread_id = t.id
            where p.thread_id = t.id
              and p.left_at is null
              and p.profile_id not in (cpt.profile_low_id, cpt.profile_high_id)
        ) then 'group'
        else t.type
    end,
    unique_key = null
where exists (
    select 1
    from public.chat_private_threads cpt
    where cpt.thread_id = t.id
      and not coalesce((
          select count(*) = 2
              and bool_or(p.profile_id = cpt.profile_low_id)
              and bool_or(p.profile_id = cpt.profile_high_id)
          from public.chat_participants p
          where p.thread_id = cpt.thread_id
            and p.left_at is null
      ), false)
);

delete from public.chat_private_threads cpt
where not coalesce((
    select count(*) = 2
        and bool_or(p.profile_id = cpt.profile_low_id)
        and bool_or(p.profile_id = cpt.profile_high_id)
    from public.chat_participants p
    where p.thread_id = cpt.thread_id
      and p.left_at is null
), false);
