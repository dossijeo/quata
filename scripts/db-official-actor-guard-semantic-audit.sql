-- Read-only semantic audit for 20260808_0001_official_posts_actor_guard.sql.
-- Emits catalogue and privilege metadata only; it never reads application rows.
with target_functions as (
  select
    p.proname as function_name,
    pg_get_function_identity_arguments(p.oid) as identity_arguments,
    p.prosecdef as security_definer,
    p.provolatile::text as volatility,
    coalesce(p.proconfig, array[]::text[]) as configuration,
    md5(pg_get_functiondef(p.oid)) as definition_md5,
    has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
    has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute,
    has_function_privilege('service_role', p.oid, 'EXECUTE') as service_role_execute
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname in (
      'quata_guard_official_posts',
      'quata_official_post_insert_allowed',
      'quata_official_post_owner_or_admin_allowed'
    )
), expected_functions(
  function_name, identity_arguments, security_definer, volatility,
  configuration, definition_md5, anon_execute, authenticated_execute,
  service_role_execute
) as (
  values
    ('quata_guard_official_posts', '', false, 'v', array['search_path=public, auth']::text[], 'fe5d2b0913c8dc550d0a08cd2cc4dcdc', false, false, false),
    ('quata_official_post_insert_allowed', 'p_profile_id uuid', true, 's', array['search_path=public, auth']::text[], '41dbc5966ec8762705712082134d3573', false, true, null),
    ('quata_official_post_owner_or_admin_allowed', 'p_profile_id uuid', true, 's', array['search_path=public, auth']::text[], 'd3194ab802647e74da17dbc430a3f248', false, true, null)
), target_policies as (
  select
    p.polname as policy_name,
    p.polcmd::text as command,
    p.polpermissive as permissive,
    array(
      select r.rolname
      from unnest(p.polroles) role_oid
      join pg_roles r on r.oid = role_oid
      order by r.rolname
    ) as roles,
    pg_get_expr(p.polqual, p.polrelid, true) as using_expression,
    pg_get_expr(p.polwithcheck, p.polrelid, true) as with_check_expression
  from pg_policy p
  join pg_class c on c.oid = p.polrelid
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public'
    and c.relname = 'official_posts'
), expected_policies(
  policy_name, command, permissive, roles, using_expression, with_check_expression
) as (
  values
    ('official_posts_authenticated_delete_author_or_admin', 'd', true, array['authenticated']::name[], 'quata_official_post_owner_or_admin_allowed(profile_id)', null),
    ('official_posts_authenticated_insert_official_own', 'a', true, array['authenticated']::name[], null, 'quata_official_post_insert_allowed(profile_id)'),
    ('official_posts_authenticated_update_author_or_admin', 'w', true, array['authenticated']::name[], 'quata_official_post_owner_or_admin_allowed(profile_id)', 'quata_official_post_owner_or_admin_allowed(profile_id)'),
    ('official_posts_public_read_language', 'r', true, array['anon','authenticated']::name[], 'is_published = true AND deleted_at IS NULL AND (language = ''es''::text OR language = quata_requested_official_post_language()) OR auth.role() = ''authenticated''::text AND (profile_id = quata_current_profile_id() OR quata_current_profile_is_admin())', null)
), table_state as (
  select
    c.relrowsecurity as row_security,
    c.relforcerowsecurity as force_row_security,
    has_table_privilege('anon', c.oid, 'SELECT') as anon_select,
    has_table_privilege('anon', c.oid, 'INSERT') as anon_insert,
    has_table_privilege('anon', c.oid, 'UPDATE') as anon_update,
    has_table_privilege('anon', c.oid, 'DELETE') as anon_delete,
    has_table_privilege('anon', c.oid, 'TRUNCATE') as anon_truncate,
    has_table_privilege('anon', c.oid, 'REFERENCES') as anon_references,
    has_table_privilege('anon', c.oid, 'TRIGGER') as anon_trigger,
    has_table_privilege('anon', c.oid, 'MAINTAIN') as anon_maintain,
    has_table_privilege('authenticated', c.oid, 'SELECT') as authenticated_select,
    has_table_privilege('authenticated', c.oid, 'INSERT') as authenticated_insert,
    has_table_privilege('authenticated', c.oid, 'UPDATE') as authenticated_update,
    has_table_privilege('authenticated', c.oid, 'DELETE') as authenticated_delete
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'official_posts'
)
select jsonb_build_object(
  'functions', (select jsonb_agg(to_jsonb(f) order by function_name) from target_functions f),
  'policies', (select jsonb_agg(to_jsonb(p) order by policy_name) from target_policies p),
  'tableState', (select to_jsonb(t) from table_state t),
  'oldPolicyCount', (
    select count(*) from target_policies
    where policy_name in (
      'official_posts_authenticated_insert',
      'official_posts_authenticated_update_guarded',
      'official_posts_authenticated_delete_guarded',
      'official_posts_admin_update',
      'official_posts_admin_delete'
    )
  ),
  'functionMismatchCount', (
    select count(*)
    from expected_functions e
    full join target_functions t using (function_name)
    where t.function_name is null
       or e.function_name is null
       or t.identity_arguments is distinct from e.identity_arguments
       or t.security_definer is distinct from e.security_definer
       or t.volatility is distinct from e.volatility
       or t.configuration is distinct from e.configuration
       or t.definition_md5 is distinct from e.definition_md5
       or t.anon_execute is distinct from e.anon_execute
       or t.authenticated_execute is distinct from e.authenticated_execute
       or (e.service_role_execute is not null and t.service_role_execute is distinct from e.service_role_execute)
  ),
  'policyMismatchCount', (
    select count(*)
    from expected_policies e
    full join target_policies t using (policy_name)
    where t.policy_name is null
       or e.policy_name is null
       or t.command is distinct from e.command
       or t.permissive is distinct from e.permissive
       or t.roles is distinct from e.roles
       or t.using_expression is distinct from e.using_expression
       or t.with_check_expression is distinct from e.with_check_expression
  ),
  'allEffectsExact',
    (select count(*) from expected_functions e full join target_functions t using (function_name)
      where t.function_name is null or e.function_name is null
         or t.identity_arguments is distinct from e.identity_arguments
         or t.security_definer is distinct from e.security_definer
         or t.volatility is distinct from e.volatility
         or t.configuration is distinct from e.configuration
         or t.definition_md5 is distinct from e.definition_md5
         or t.anon_execute is distinct from e.anon_execute
         or t.authenticated_execute is distinct from e.authenticated_execute
         or (e.service_role_execute is not null and t.service_role_execute is distinct from e.service_role_execute)) = 0
    and (select count(*) from expected_policies e full join target_policies t using (policy_name)
      where t.policy_name is null or e.policy_name is null
         or t.command is distinct from e.command
         or t.permissive is distinct from e.permissive
         or t.roles is distinct from e.roles
         or t.using_expression is distinct from e.using_expression
         or t.with_check_expression is distinct from e.with_check_expression) = 0
    and (select row_security and not force_row_security
         and anon_select and not anon_insert and not anon_update and not anon_delete
         and not anon_truncate and not anon_references and not anon_trigger and not anon_maintain
         and authenticated_select and authenticated_insert and authenticated_update and authenticated_delete
         from table_state)
    and not exists (
      select 1 from target_policies
      where policy_name in (
        'official_posts_authenticated_insert',
        'official_posts_authenticated_update_guarded',
        'official_posts_authenticated_delete_guarded',
        'official_posts_admin_update',
        'official_posts_admin_delete'
      )
    )
);
