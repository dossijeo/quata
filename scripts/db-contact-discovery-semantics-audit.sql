-- Read-only exhaustive semantic audit for Contact Discovery.
-- The backfill check emits one boolean and never emits phone/profile values.
with relation_state as (
  select jsonb_build_object(
    'name', c.relname,
    'kind', c.relkind::text,
    'rls', c.relrowsecurity,
    'forceRls', c.relforcerowsecurity,
    'acl', c.relacl,
    'anonPrivileges', jsonb_build_object(
      'select', has_table_privilege('anon', c.oid, 'SELECT'),
      'insert', has_table_privilege('anon', c.oid, 'INSERT'),
      'update', has_table_privilege('anon', c.oid, 'UPDATE'),
      'delete', has_table_privilege('anon', c.oid, 'DELETE'),
      'truncate', has_table_privilege('anon', c.oid, 'TRUNCATE'),
      'references', has_table_privilege('anon', c.oid, 'REFERENCES'),
      'trigger', has_table_privilege('anon', c.oid, 'TRIGGER'),
      'maintain', has_table_privilege('anon', c.oid, 'MAINTAIN')
    ),
    'authenticatedPrivileges', jsonb_build_object(
      'select', has_table_privilege('authenticated', c.oid, 'SELECT'),
      'insert', has_table_privilege('authenticated', c.oid, 'INSERT'),
      'update', has_table_privilege('authenticated', c.oid, 'UPDATE'),
      'delete', has_table_privilege('authenticated', c.oid, 'DELETE'),
      'truncate', has_table_privilege('authenticated', c.oid, 'TRUNCATE'),
      'references', has_table_privilege('authenticated', c.oid, 'REFERENCES'),
      'trigger', has_table_privilege('authenticated', c.oid, 'TRIGGER'),
      'maintain', has_table_privilege('authenticated', c.oid, 'MAINTAIN')
    )
  ) as metadata
  from pg_class c join pg_namespace n on n.oid=c.relnamespace
  where n.nspname='public' and c.relname='quata_profile_phone_directory' and c.relkind='r'
), columns_state as (
  select jsonb_agg(jsonb_build_object(
    'ordinal', a.attnum,
    'name', a.attname,
    'type', format_type(a.atttypid,a.atttypmod),
    'notNull', a.attnotnull,
    'default', pg_get_expr(d.adbin,d.adrelid)
  ) order by a.attnum) as metadata
  from pg_attribute a
  join pg_class c on c.oid=a.attrelid
  join pg_namespace n on n.oid=c.relnamespace
  left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum
  where n.nspname='public' and c.relname='quata_profile_phone_directory'
    and a.attnum>0 and not a.attisdropped
), constraints_state as (
  select jsonb_agg(jsonb_build_object(
    'name', con.conname,
    'type', con.contype::text,
    'definition', pg_get_constraintdef(con.oid,true),
    'validated', con.convalidated
  ) order by con.conname) as metadata
  from pg_constraint con
  join pg_class c on c.oid=con.conrelid
  join pg_namespace n on n.oid=c.relnamespace
  where n.nspname='public' and c.relname='quata_profile_phone_directory'
), functions_state as (
  select jsonb_agg(jsonb_build_object(
    'name',p.proname,
    'args',pg_get_function_identity_arguments(p.oid),
    'result',pg_get_function_result(p.oid),
    'language',l.lanname,
    'securityDefiner',p.prosecdef,
    'volatility',p.provolatile::text,
    'parallel',p.proparallel::text,
    'config',coalesce(p.proconfig,array[]::text[]),
    'definitionMd5',md5(pg_get_functiondef(p.oid)),
    'normalizedDefinitionMd5',md5(replace(pg_get_functiondef(p.oid),E'\r\n',E'\n')),
    'publicExecute',has_function_privilege('public',p.oid,'EXECUTE'),
    'anonExecute',has_function_privilege('anon',p.oid,'EXECUTE'),
    'authenticatedExecute',has_function_privilege('authenticated',p.oid,'EXECUTE'),
    'serviceRoleExecute',has_function_privilege('service_role',p.oid,'EXECUTE'),
    'acl',p.proacl
  ) order by p.proname) as metadata
  from pg_proc p join pg_namespace n on n.oid=p.pronamespace join pg_language l on l.oid=p.prolang
  where n.nspname='public' and p.proname in (
    'quata_normalize_phone_key','quata_refresh_profile_phone_directory','quata_chat_match_registered_contacts'
  )
), trigger_state as (
  select jsonb_agg(jsonb_build_object(
    'name',t.tgname,
    'table',c.relname,
    'definition',pg_get_triggerdef(t.oid,true),
    'enabled',t.tgenabled::text,
    'function',p.proname
  ) order by t.tgname) as metadata
  from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace join pg_proc p on p.oid=t.tgfoid
  where n.nspname='public' and c.relname='community_profiles'
    and t.tgname='quata_refresh_profile_phone_directory_trigger' and not t.tgisinternal
), backfill_state as (
  select not exists (
    select cp.id, candidate.phone_key
    from public.community_profiles cp
    cross join lateral (
      select distinct public.quata_normalize_phone_key(value) as phone_key
      from unnest(array[
        cp.phone, cp.phone_normalized, cp.phone_local, cp.phone_e164, cp.telefono,
        concat(coalesce(cp.country_code,''),coalesce(cp.phone_local,'')),
        concat(coalesce(cp.code,''),coalesce(cp.telefono,''))
      ]) supplied(value)
    ) candidate
    where char_length(candidate.phone_key) between 6 and 20
    except
    select profile_id, phone_key from public.quata_profile_phone_directory
  ) as all_expected_keys_present
), analyze_state as (
  select coalesce(last_analyze is not null or last_autoanalyze is not null,false) as planner_statistics_observed
  from pg_stat_all_tables
  where schemaname='public' and relname='quata_profile_phone_directory'
)
select jsonb_build_object(
  'relation',relation_state.metadata,
  'columns',columns_state.metadata,
  'constraints',constraints_state.metadata,
  'functions',functions_state.metadata,
  'triggers',trigger_state.metadata,
  'digests',jsonb_build_object(
    'relationMd5',md5(relation_state.metadata::text),
    'columnsMd5',md5(columns_state.metadata::text),
    'constraintsMd5',md5(constraints_state.metadata::text),
    'functionsMd5',md5(functions_state.metadata::text),
    'triggersMd5',md5(trigger_state.metadata::text)
  ),
  'allExpectedKeysPresent',backfill_state.all_expected_keys_present,
  'plannerStatisticsObserved',analyze_state.planner_statistics_observed,
  'allEffectsExact',
    md5(relation_state.metadata::text)='02c2cd528e87b2b7f65b10e14c17f0f8'
    and md5(columns_state.metadata::text)='ed209d4e1c4b439aaf08203b1d332c59'
    and md5(constraints_state.metadata::text)='87a1856a7afd90584c4dbc70938a80fe'
    and md5(functions_state.metadata::text)='8c5fc97b33d6c4aec9a27d08d94388c0'
    and md5(trigger_state.metadata::text)='dd31603ebdbf8ce8f341f3c7ea4c1859'
    and backfill_state.all_expected_keys_present
    and analyze_state.planner_statistics_observed
)
from relation_state cross join columns_state cross join constraints_state cross join functions_state cross join trigger_state cross join backfill_state cross join analyze_state;
