-- Read-only semantic audit for 20260709_0003_official_post_soft_delete_policy.sql.
-- Emits catalogue metadata only; it never reads application-row values.
with target as (
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
    and p.polname = 'official_posts_public_read_language'
), expected as (
  select
    'official_posts_public_read_language'::text as policy_name,
    'r'::text as command,
    true as permissive,
    array['anon', 'authenticated']::name[] as roles,
    'is_published = true AND deleted_at IS NULL AND (language = ''es''::text OR language = quata_requested_official_post_language()) OR auth.role() = ''authenticated''::text AND (profile_id = quata_current_profile_id() OR quata_current_profile_is_admin())'::text as using_expression
)
select jsonb_build_object(
  'policyCount', (select count(*) from target),
  'policyName', t.policy_name,
  'command', t.command,
  'permissive', t.permissive,
  'roles', t.roles,
  'usingExpression', t.using_expression,
  'withCheckExpression', t.with_check_expression,
  'allEffectsExact',
    t.policy_name = e.policy_name
    and t.command = e.command
    and t.permissive = e.permissive
    and t.roles = e.roles
    and t.using_expression = e.using_expression
    and t.with_check_expression is null
)
from target t
cross join expected e;
