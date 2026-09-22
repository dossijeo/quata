-- Repair the deployed Community key transliteration and re-run the original idempotent
-- participant synchronization for active wall threads.

create or replace function public.quata_chat_community_key(p_value text)
returns text
language sql
immutable
as $$
    select nullif(
        regexp_replace(
            translate(
                lower(coalesce(p_value, '')),
                'áàäâãåéèëêíìïîóòöôõúùüûñç',
                'aaaaaaeeeeiiiiooooouuuunc'
            ),
            '[^a-z0-9]+',
            '',
            'g'
        ),
        ''
    )
$$;

insert into public.chat_participants(thread_id, profile_id, role)
select t.id, t.created_by_profile_id, 'owner'
from public.chat_threads t
where t.type = 'wall'
  and t.deleted_at is null
  and t.created_by_profile_id is not null
on conflict (thread_id, profile_id)
do update set
    left_at = null,
    is_hidden = false,
    is_deleted = false,
    role = case
        when public.chat_participants.role = 'member' then 'owner'
        else public.chat_participants.role
    end;

insert into public.chat_participants(thread_id, profile_id, role)
select distinct t.id, cp.id, 'member'
from public.chat_threads t
join public.community_walls w on w.id = t.community_id
join public.community_profiles cp on (
    public.quata_chat_community_key(cp.neighborhood) in (
        public.quata_chat_community_key(w.normalized_name),
        public.quata_chat_community_key(w.name),
        public.quata_chat_community_key(w.slug),
        public.quata_chat_community_key(t.subject),
        public.quata_chat_community_key(t.title)
    )
    or public.quata_chat_community_key(cp.barrio) in (
        public.quata_chat_community_key(w.normalized_name),
        public.quata_chat_community_key(w.name),
        public.quata_chat_community_key(w.slug),
        public.quata_chat_community_key(t.subject),
        public.quata_chat_community_key(t.title)
    )
    or public.quata_chat_community_key(cp.barrio_normalized) in (
        public.quata_chat_community_key(w.normalized_name),
        public.quata_chat_community_key(w.name),
        public.quata_chat_community_key(w.slug),
        public.quata_chat_community_key(t.subject),
        public.quata_chat_community_key(t.title)
    )
)
where t.type = 'wall'
  and t.deleted_at is null
on conflict (thread_id, profile_id)
do update set
    left_at = null,
    is_hidden = false,
    is_deleted = false;
