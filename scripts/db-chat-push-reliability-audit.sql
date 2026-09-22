-- Read-only semantic audit for the final Chat push reliability functions and ACL.
-- Emits catalogue metadata only; it never invokes either function.
with target_functions as (
  select
    p.oid,
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
    and (
      (p.proname = 'quata_unregister_push_token'
       and pg_get_function_identity_arguments(p.oid) = 'p_profile_id uuid, p_token text')
      or (p.proname = 'quata_enqueue_chat_push'
          and pg_get_function_identity_arguments(p.oid) = '')
    )
), function_mismatches as (
  select count(*) as mismatch_count
  from target_functions
  where result_type is distinct from case function_name
          when 'quata_unregister_push_token' then 'jsonb' else 'trigger' end
     or language_name is distinct from 'plpgsql'
     or security_definer is distinct from true
     or volatility is distinct from 'v'
     or configuration is distinct from case function_name
          when 'quata_unregister_push_token' then array['search_path=public']::text[]
          else array['search_path=public, extensions']::text[] end
     or definition_md5 is distinct from case function_name
          when 'quata_unregister_push_token' then 'ef544eb6333fed45fee8aa946cd1f600'
          else 'e169f79cff681fe701d7eec34cf16dd0' end
     or (
       function_name = 'quata_unregister_push_token'
       and (
         public_execute is distinct from false
         or anon_execute is distinct from false
         or authenticated_execute is distinct from true
       )
     )
), unregister_acl as (
  select
    case when acl.grantee = 0 then 'PUBLIC' else role.rolname end as grantee,
    acl.privilege_type,
    acl.is_grantable
  from target_functions f
  join pg_proc p on p.oid = f.oid
  cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
  left join pg_roles role on role.oid = acl.grantee
  where f.function_name = 'quata_unregister_push_token'
), acl_mismatches as (
  select
    case when exists (
      select 1 from unregister_acl where grantee in ('PUBLIC', 'anon') and privilege_type = 'EXECUTE'
    ) then 1 else 0 end
    + case when exists (
      select 1 from unregister_acl
      where grantee = 'authenticated' and privilege_type = 'EXECUTE' and not is_grantable
    ) then 0 else 1 end as mismatch_count
)
select jsonb_build_object(
  'functions', (select coalesce(jsonb_agg(to_jsonb(f) - 'oid' order by function_name), '[]'::jsonb) from target_functions f),
  'unregisterAcl', (select coalesce(jsonb_agg(to_jsonb(a) order by grantee), '[]'::jsonb) from unregister_acl a),
  'functionCount', (select count(*) from target_functions),
  'functionMismatchCount',
    (select mismatch_count from function_mismatches)
    + (select mismatch_count from acl_mismatches)
    + case when (select count(*) from target_functions) = 2 then 0 else 1 end,
  'allEffectsExact',
    (select count(*) from target_functions) = 2
    and (select mismatch_count from function_mismatches) = 0
    and (select mismatch_count from acl_mismatches) = 0
);
