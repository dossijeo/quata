begin;

lock table public.community_profiles in share row exclusive mode;

do $migration$
begin
  if exists (
    select 1
    from public.community_profiles
    where phone_local is not null
    group by phone_local
    having count(*) > 1
  ) then
    raise exception 'profile_phone_local_collision_precondition_failed';
  end if;

  if exists (
    select 1
    from public.community_profiles
    where phone_normalized is not null
    group by phone_normalized
    having count(*) > 1
  ) then
    raise exception 'profile_phone_normalized_collision_precondition_failed';
  end if;

  if to_regclass('public.community_profiles_phone_local_uidx') is null then
    create unique index community_profiles_phone_local_uidx
      on public.community_profiles (phone_local)
      where phone_local is not null;
  elsif not exists (
    select 1
    from pg_index index_state
    where index_state.indexrelid = 'public.community_profiles_phone_local_uidx'::regclass
      and index_state.indrelid = 'public.community_profiles'::regclass
      and index_state.indisunique
      and index_state.indisvalid
      and index_state.indnkeyatts = 1
      and index_state.indkey[0] = (
        select attribute.attnum
        from pg_attribute attribute
        where attribute.attrelid = 'public.community_profiles'::regclass
          and attribute.attname = 'phone_local'
      )
      and (
        index_state.indpred is null
        or pg_get_expr(index_state.indpred, index_state.indrelid) in (
          '(phone_local IS NOT NULL)',
          'phone_local IS NOT NULL'
        )
      )
  ) then
    raise exception 'profile_phone_local_unique_index_invalid';
  end if;

  if to_regclass('public.unique_phone_normalized') is null then
    create unique index unique_phone_normalized
      on public.community_profiles (phone_normalized);
  elsif not exists (
    select 1
    from pg_index index_state
    where index_state.indexrelid = 'public.unique_phone_normalized'::regclass
      and index_state.indrelid = 'public.community_profiles'::regclass
      and index_state.indisunique
      and index_state.indisvalid
      and index_state.indnkeyatts = 1
      and index_state.indkey[0] = (
        select attribute.attnum
        from pg_attribute attribute
        where attribute.attrelid = 'public.community_profiles'::regclass
          and attribute.attname = 'phone_normalized'
      )
      and index_state.indpred is null
  ) then
    raise exception 'profile_phone_normalized_unique_index_invalid';
  end if;
end
$migration$;

commit;
