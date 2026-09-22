-- Read-only semantic audit for the stale-token columns, index and registration RPC.
-- Emits catalogue metadata only; it never reads application rows.
with target_columns as (
  select
    a.attname as column_name,
    format_type(a.atttypid, a.atttypmod) as data_type,
    a.attnotnull as not_null,
    pg_get_expr(d.adbin, d.adrelid) as default_expression,
    a.attgenerated::text as generated_kind,
    a.attidentity::text as identity_kind
  from pg_attribute a
  join pg_class c on c.oid = a.attrelid
  join pg_namespace n on n.oid = c.relnamespace
  left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
  where n.nspname = 'public'
    and c.relname = 'push_tokens'
    and a.attname in ('disabled_at', 'last_error_text')
    and a.attnum > 0
    and not a.attisdropped
), column_mismatches as (
  select count(*) as mismatch_count
  from target_columns
  where (column_name = 'disabled_at' and data_type is distinct from 'timestamp with time zone')
     or (column_name = 'last_error_text' and data_type is distinct from 'text')
     or not_null is distinct from false
     or default_expression is not null
     or generated_kind is distinct from ''
     or identity_kind is distinct from ''
), target_index as (
  select
    index_class.relname as index_name,
    pg_get_indexdef(i.indexrelid) as definition,
    i.indisvalid as is_valid,
    i.indisready as is_ready,
    i.indisunique as is_unique
  from pg_index i
  join pg_class index_class on index_class.oid = i.indexrelid
  join pg_class table_class on table_class.oid = i.indrelid
  join pg_namespace n on n.oid = table_class.relnamespace
  where n.nspname = 'public'
    and table_class.relname = 'push_tokens'
    and index_class.relname = 'push_tokens_active_user_idx'
), index_mismatches as (
  select count(*) as mismatch_count
  from target_index
  where definition is distinct from
        'CREATE INDEX push_tokens_active_user_idx ON public.push_tokens USING btree (user_id) WHERE (disabled_at IS NULL)'
     or is_valid is distinct from true
     or is_ready is distinct from true
     or is_unique is distinct from false
), target_function as (
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
    and p.proname = 'quata_register_push_token'
    and pg_get_function_identity_arguments(p.oid) =
      'p_profile_id uuid, p_token text, p_platform text'
), function_acl as (
  select
    case when acl.grantee = 0 then 'PUBLIC' else role.rolname end as grantee,
    acl.privilege_type,
    acl.is_grantable
  from target_function f
  join pg_proc p on p.oid = f.oid
  cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
  left join pg_roles role on role.oid = acl.grantee
), function_mismatches as (
  select count(*) as mismatch_count
  from target_function
  where function_name is distinct from 'quata_register_push_token'
     or identity_arguments is distinct from 'p_profile_id uuid, p_token text, p_platform text'
     or result_type is distinct from 'jsonb'
     or language_name is distinct from 'plpgsql'
     or security_definer is distinct from true
     or volatility is distinct from 'v'
     or configuration is distinct from array['search_path=public']::text[]
     or definition_md5 is distinct from '38e9ccbb64d83e82352f7ef381ef2c2a'
     or public_execute is distinct from false
     or authenticated_execute is distinct from true
), acl_mismatches as (
  select
    case when exists (
      select 1 from function_acl where grantee = 'PUBLIC' and privilege_type = 'EXECUTE'
    ) then 1 else 0 end
    + case when exists (
      select 1 from function_acl
      where grantee = 'authenticated' and privilege_type = 'EXECUTE' and not is_grantable
    ) then 0 else 1 end as mismatch_count
)
select jsonb_build_object(
  'columns', (select coalesce(jsonb_agg(to_jsonb(c) order by column_name), '[]'::jsonb) from target_columns c),
  'columnCount', (select count(*) from target_columns),
  'columnMismatchCount',
    (select mismatch_count from column_mismatches)
    + case when (select count(*) from target_columns) = 2 then 0 else 1 end,
  'indexes', (select coalesce(jsonb_agg(to_jsonb(i)), '[]'::jsonb) from target_index i),
  'indexCount', (select count(*) from target_index),
  'indexMismatchCount',
    (select mismatch_count from index_mismatches)
    + case when (select count(*) from target_index) = 1 then 0 else 1 end,
  'functions', (
    select coalesce(jsonb_agg(to_jsonb(f) - 'oid'), '[]'::jsonb) from target_function f
  ),
  'functionAcl', (select coalesce(jsonb_agg(to_jsonb(a) order by grantee), '[]'::jsonb) from function_acl a),
  'functionCount', (select count(*) from target_function),
  'functionMismatchCount',
    (select mismatch_count from function_mismatches)
    + (select mismatch_count from acl_mismatches)
    + case when (select count(*) from target_function) = 1 then 0 else 1 end,
  'allEffectsExact',
    (select count(*) from target_columns) = 2
    and (select mismatch_count from column_mismatches) = 0
    and (select count(*) from target_index) = 1
    and (select mismatch_count from index_mismatches) = 0
    and (select count(*) from target_function) = 1
    and (select mismatch_count from function_mismatches) = 0
    and (select mismatch_count from acl_mismatches) = 0
);
