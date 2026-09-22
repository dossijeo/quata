with invalid_pairs as (
  select
    cpt.thread_id,
    count(p.profile_id)::int as active_count,
    bool_or(p.profile_id = cpt.profile_low_id) as has_low,
    bool_or(p.profile_id = cpt.profile_high_id) as has_high,
    bool_or(p.profile_id not in (cpt.profile_low_id, cpt.profile_high_id)) as has_other
  from public.chat_private_threads cpt
  left join public.chat_participants p
    on p.thread_id = cpt.thread_id
   and p.left_at is null
  group by cpt.thread_id, cpt.profile_low_id, cpt.profile_high_id
), selected as (
  select thread_id
  from invalid_pairs
  where not coalesce(active_count = 2 and has_low and has_high, false)
)
select jsonb_build_object(
  'function', (
    select jsonb_build_object(
      'args', pg_get_function_identity_arguments(p.oid),
      'md5', md5(replace(pg_get_functiondef(p.oid), E'\r\n', E'\n')),
      'securityDefiner', p.prosecdef,
      'volatility', p.provolatile::text,
      'config', coalesce(p.proconfig, array[]::text[]),
      'acl', p.proacl
    )
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'quata_chat_enforce_private_thread_membership'
  ),
  'trigger', (
    select jsonb_build_object(
      'name', t.tgname,
      'definition', pg_get_triggerdef(t.oid, true),
      'enabled', t.tgenabled::text,
      'deferrable', t.tgdeferrable,
      'initiallyDeferred', t.tginitdeferred,
      'function', p.proname
    )
    from pg_trigger t
    join pg_class c on c.oid = t.tgrelid
    join pg_namespace n on n.oid = c.relnamespace
    join pg_proc p on p.oid = t.tgfoid
    where n.nspname = 'public'
      and c.relname = 'chat_participants'
      and t.tgname = 'chat_participants_enforce_private_membership'
      and not t.tgisinternal
  ),
  'invalidCurrentMappings', (select count(*)::int from selected),
  'currentMappings', (select count(*)::int from public.chat_private_threads)
) observation;
