-- Scalable, privacy-preserving contact discovery for the Android invite flow.
-- The directory is private: clients can only submit phone keys and receive the
-- subset of those same keys that belongs to a registered profile.

create or replace function public.quata_normalize_phone_key(p_value text)
returns text
language sql
immutable
parallel safe
as $$
    select regexp_replace(coalesce(p_value, ''), '[^0-9]', '', 'g');
$$;

create table if not exists public.quata_profile_phone_directory (
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    phone_key text not null,
    primary key (phone_key, profile_id),
    constraint quata_profile_phone_directory_key_length
        check (char_length(phone_key) between 6 and 20)
);

revoke all on table public.quata_profile_phone_directory from public, anon, authenticated;

create or replace function public.quata_refresh_profile_phone_directory()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    delete from public.quata_profile_phone_directory
    where profile_id = new.id;

    insert into public.quata_profile_phone_directory(profile_id, phone_key)
    select new.id, candidate.phone_key
    from (
        select distinct public.quata_normalize_phone_key(value) as phone_key
        from unnest(array[
            new.phone,
            new.phone_normalized,
            new.phone_local,
            new.phone_e164,
            new.telefono,
            concat(coalesce(new.country_code, ''), coalesce(new.phone_local, '')),
            concat(coalesce(new.code, ''), coalesce(new.telefono, ''))
        ]) as supplied(value)
    ) candidate
    where char_length(candidate.phone_key) between 6 and 20
    on conflict do nothing;

    return new;
end;
$$;

drop trigger if exists quata_refresh_profile_phone_directory_trigger
on public.community_profiles;

create trigger quata_refresh_profile_phone_directory_trigger
after insert or update of
    phone,
    phone_normalized,
    phone_local,
    phone_e164,
    telefono,
    country_code,
    code
on public.community_profiles
for each row
execute function public.quata_refresh_profile_phone_directory();

insert into public.quata_profile_phone_directory(profile_id, phone_key)
select cp.id, candidate.phone_key
from public.community_profiles cp
cross join lateral (
    select distinct public.quata_normalize_phone_key(value) as phone_key
    from unnest(array[
        cp.phone,
        cp.phone_normalized,
        cp.phone_local,
        cp.phone_e164,
        cp.telefono,
        concat(coalesce(cp.country_code, ''), coalesce(cp.phone_local, '')),
        concat(coalesce(cp.code, ''), coalesce(cp.telefono, ''))
    ]) as supplied(value)
) candidate
where char_length(candidate.phone_key) between 6 and 20
on conflict do nothing;

create or replace function public.quata_chat_match_registered_contacts(
    p_actor_profile_id uuid,
    p_phone_candidates text[]
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_candidate_count integer;
    v_matches jsonb;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);
    v_candidate_count := cardinality(coalesce(p_phone_candidates, array[]::text[]));

    if v_candidate_count > 500 then
        raise exception 'A maximum of 500 phone candidates is allowed per request'
            using errcode = '22023';
    end if;

    with requested as (
        select distinct public.quata_normalize_phone_key(value) as phone_key
        from unnest(coalesce(p_phone_candidates, array[]::text[])) supplied(value)
    ), valid_requested as (
        select phone_key
        from requested
        where char_length(phone_key) between 6 and 20
    )
    select coalesce(jsonb_agg(vr.phone_key order by vr.phone_key), '[]'::jsonb)
    into v_matches
    from valid_requested vr
    where exists (
        select 1
        from public.quata_profile_phone_directory directory
        where directory.phone_key = vr.phone_key
    );

    return jsonb_build_object('matched_phones', coalesce(v_matches, '[]'::jsonb));
end;
$$;

revoke all on function public.quata_chat_match_registered_contacts(uuid, text[]) from public;
grant execute on function public.quata_chat_match_registered_contacts(uuid, text[]) to anon, authenticated;

analyze public.quata_profile_phone_directory;
