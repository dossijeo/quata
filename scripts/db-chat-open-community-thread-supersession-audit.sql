-- Read-only semantic audit for the versioned open-community-thread function.
-- Emits function catalogue metadata only; it never reads application rows.
with target as (
  select
    p.proname as function_name,
    pg_get_function_identity_arguments(p.oid) as identity_arguments,
    pg_get_function_result(p.oid) as result_type,
    l.lanname as language_name,
    p.prosecdef as security_definer,
    p.provolatile::text as volatility,
    coalesce(p.proconfig, array[]::text[]) as configuration,
    md5(pg_get_functiondef(p.oid)) as definition_md5,
    has_function_privilege('public', p.oid, 'EXECUTE') as public_execute,
    has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
    has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute,
    has_function_privilege('service_role', p.oid, 'EXECUTE') as service_role_execute
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  join pg_language l on l.oid = p.prolang
  where n.nspname = 'public'
    and p.proname = 'quata_chat_open_community_thread'
), mismatches as (
  select count(*) as mismatch_count
  from target
  where function_name is distinct from 'quata_chat_open_community_thread'
     or identity_arguments is distinct from
        'p_actor_profile_id uuid, p_community_id uuid, p_title text'
     or result_type is distinct from 'jsonb'
     or language_name is distinct from 'plpgsql'
     or security_definer is distinct from true
     or volatility is distinct from 'v'
     or configuration is distinct from array['search_path=public']::text[]
     or definition_md5 is distinct from 'd76cbcb8d471aa87344bef1a9ed55616'
     or anon_execute is distinct from true
     or authenticated_execute is distinct from true
)
select jsonb_build_object(
  'functions', (select coalesce(jsonb_agg(to_jsonb(f)), '[]'::jsonb) from target f),
  'functionCount', (select count(*) from target),
  'functionMismatchCount',
    (select mismatch_count from mismatches)
    + case when (select count(*) from target) = 1 then 0 else 1 end,
  'allEffectsExact',
    (select count(*) from target) = 1
    and (select mismatch_count from mismatches) = 0
);
