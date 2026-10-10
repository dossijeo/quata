begin;

lock table public.community_profiles in share row exclusive mode;

do $migration$
begin
  if exists (
    select 1
    from public.community_profiles
    where country_code is not null and phone_local is not null
    group by country_code, phone_local
    having count(*) > 1
  ) then
    raise exception 'profile_country_phone_local_collision_precondition_failed';
  end if;

  if exists (
    select 1
    from public.community_profiles
    where phone_e164 is not null
    group by phone_e164
    having count(*) > 1
  ) then
    raise exception 'profile_phone_e164_collision_precondition_failed';
  end if;

  drop index if exists public.community_profiles_phone_local_key;
  drop index if exists public.community_profiles_phone_local_uidx;
  drop index if exists public.phone_unique;
  drop index if exists public.unique_phone_normalized;

  if to_regclass('public.community_profiles_country_phone_local_uidx') is null then
    create unique index community_profiles_country_phone_local_uidx
      on public.community_profiles (country_code, phone_local)
      where country_code is not null and phone_local is not null;
  elsif not exists (
    select 1
    from pg_index index_state
    where index_state.indexrelid = 'public.community_profiles_country_phone_local_uidx'::regclass
      and index_state.indrelid = 'public.community_profiles'::regclass
      and index_state.indisunique
      and index_state.indisvalid
      and index_state.indnkeyatts = 2
      and index_state.indkey[0] = (
        select attribute.attnum
        from pg_attribute attribute
        where attribute.attrelid = 'public.community_profiles'::regclass
          and attribute.attname = 'country_code'
      )
      and index_state.indkey[1] = (
        select attribute.attnum
        from pg_attribute attribute
        where attribute.attrelid = 'public.community_profiles'::regclass
          and attribute.attname = 'phone_local'
      )
      and pg_get_expr(index_state.indpred, index_state.indrelid) in (
        '((country_code IS NOT NULL) AND (phone_local IS NOT NULL))',
        '(country_code IS NOT NULL AND phone_local IS NOT NULL)'
      )
  ) then
    raise exception 'profile_country_phone_local_unique_index_invalid';
  end if;

  if to_regclass('public.community_profiles_phone_e164_uidx') is null then
    create unique index community_profiles_phone_e164_uidx
      on public.community_profiles (phone_e164)
      where phone_e164 is not null;
  elsif not exists (
    select 1
    from pg_index index_state
    where index_state.indexrelid = 'public.community_profiles_phone_e164_uidx'::regclass
      and index_state.indrelid = 'public.community_profiles'::regclass
      and index_state.indisunique
      and index_state.indisvalid
      and index_state.indnkeyatts = 1
      and index_state.indkey[0] = (
        select attribute.attnum
        from pg_attribute attribute
        where attribute.attrelid = 'public.community_profiles'::regclass
          and attribute.attname = 'phone_e164'
      )
      and pg_get_expr(index_state.indpred, index_state.indrelid) in (
        '(phone_e164 IS NOT NULL)',
        'phone_e164 IS NOT NULL'
      )
  ) then
    raise exception 'profile_phone_e164_unique_index_invalid';
  end if;
end
$migration$;

commit;
