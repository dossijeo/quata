-- Read-only semantic audit for 20260703_0001_admin_delete_posts.sql.
-- The two Official policies are intentionally superseded by the actor-guard
-- migration; the Community policy remains exact. No application rows are read.
with target_policies as (
  select
    n.nspname as schema_name,
    c.relname as table_name,
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
    and (
      (c.relname = 'community_posts' and p.polname = 'community_posts_admin_delete')
      or (c.relname = 'official_posts' and p.polname in (
        'official_posts_authenticated_update_author_or_admin',
        'official_posts_authenticated_delete_author_or_admin'
      ))
    )
), expected_policies(
  schema_name, table_name, policy_name, command, permissive, roles,
  using_expression, with_check_expression
) as (
  values
    ('public', 'community_posts', 'community_posts_admin_delete', 'd', true,
      array['authenticated']::name[], 'quata_current_profile_is_admin()', null),
    ('public', 'official_posts', 'official_posts_authenticated_update_author_or_admin', 'w', true,
      array['authenticated']::name[], 'quata_official_post_owner_or_admin_allowed(profile_id)',
      'quata_official_post_owner_or_admin_allowed(profile_id)'),
    ('public', 'official_posts', 'official_posts_authenticated_delete_author_or_admin', 'd', true,
      array['authenticated']::name[], 'quata_official_post_owner_or_admin_allowed(profile_id)', null)
), policy_mismatches as (
  select count(*) as mismatch_count
  from expected_policies e
  full join target_policies t using (schema_name, table_name, policy_name)
  where t.policy_name is null
     or e.policy_name is null
     or t.command is distinct from e.command
     or t.permissive is distinct from e.permissive
     or t.roles is distinct from e.roles
     or t.using_expression is distinct from e.using_expression
     or t.with_check_expression is distinct from e.with_check_expression
), superseded_policy_count as (
  select count(*) as policy_count
  from pg_policy p
  join pg_class c on c.oid = p.polrelid
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public'
    and c.relname = 'official_posts'
    and p.polname in ('official_posts_admin_update', 'official_posts_admin_delete')
), community_delete_surface as (
  select
    p.polname as policy_name,
    p.polroles = array[0::oid] as applies_to_public,
    array(
      select r.rolname
      from unnest(p.polroles) role_oid
      join pg_roles r on r.oid = role_oid
      order by r.rolname
    ) as roles,
    pg_get_expr(p.polqual, p.polrelid, true) as using_expression
  from pg_policy p
  join pg_class c on c.oid = p.polrelid
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public'
    and c.relname = 'community_posts'
    and p.polcmd = 'd'
), community_table_state as (
  select
    has_table_privilege('anon', c.oid, 'DELETE') as anon_delete,
    has_table_privilege('authenticated', c.oid, 'DELETE') as authenticated_delete
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relname = 'community_posts'
)
select jsonb_build_object(
  'policies', (select jsonb_agg(to_jsonb(p) order by table_name, policy_name) from target_policies p),
  'policyCount', (select count(*) from target_policies),
  'policyMismatchCount', (select mismatch_count from policy_mismatches),
  'supersededPolicyCount', (select policy_count from superseded_policy_count),
  'communityDeletePolicyCount', (select count(*) from community_delete_surface),
  'additionalCommunityDeletePolicies', (
    select jsonb_agg(to_jsonb(p) order by policy_name)
    from community_delete_surface p
    where policy_name <> 'community_posts_admin_delete'
  ),
  'communityTableState', (select to_jsonb(t) from community_table_state t),
  'allEffectsExact',
    (select count(*) from target_policies) = 3
    and (select mismatch_count from policy_mismatches) = 0
    and (select policy_count from superseded_policy_count) = 0
);
