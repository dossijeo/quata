-- Read-only audit for the retired single-active-token rule.
-- Emits function catalogue metadata and a boolean successor postcondition only;
-- it never emits token rows or invokes the registration function/provider.
with function_state as (
  select jsonb_build_object(
    'name', p.proname,
    'identityArguments', pg_get_function_identity_arguments(p.oid),
    'result', pg_get_function_result(p.oid),
    'language', l.lanname,
    'securityDefiner', p.prosecdef,
    'volatility', p.provolatile::text,
    'configuration', coalesce(p.proconfig, array[]::text[]),
    'definitionMd5', md5(pg_get_functiondef(p.oid)),
    'publicExecute', has_function_privilege('public', p.oid, 'EXECUTE'),
    'anonExecute', has_function_privilege('anon', p.oid, 'EXECUTE'),
    'authenticatedExecute', has_function_privilege('authenticated', p.oid, 'EXECUTE'),
    'serviceRoleExecute', has_function_privilege('service_role', p.oid, 'EXECUTE'),
    'acl', p.proacl
  ) as metadata
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  join pg_language l on l.oid = p.prolang
  where n.nspname = 'public'
    and p.oid = to_regprocedure('public.quata_register_push_token(uuid,text,text)')
), successor_state as (
  select not exists (
    select 1
    from public.push_tokens
    where disabled_at is not null
      and last_error_text = 'Superseded by a newer active push token for this profile'
  ) as legacy_single_active_marker_absent
)
select jsonb_build_object(
  'function', function_state.metadata,
  'legacySingleActiveMarkerAbsent', successor_state.legacy_single_active_marker_absent,
  'allEffectsSuperseded',
    function_state.metadata->>'definitionMd5' = '38e9ccbb64d83e82352f7ef381ef2c2a'
    and (function_state.metadata->>'publicExecute')::boolean = false
    and (function_state.metadata->>'authenticatedExecute')::boolean = true
    and successor_state.legacy_single_active_marker_absent
)
from function_state cross join successor_state;
