select jsonb_build_object(
  'profileColumns', (
    select jsonb_agg(jsonb_build_object(
      'name', a.attname,
      'type', format_type(a.atttypid, a.atttypmod),
      'notNull', a.attnotnull,
      'default', pg_get_expr(ad.adbin, ad.adrelid)
    ) order by a.attnum)
    from pg_attribute a
    join pg_class c on c.oid = a.attrelid
    join pg_namespace n on n.oid = c.relnamespace
    left join pg_attrdef ad on ad.adrelid = a.attrelid and ad.adnum = a.attnum
    where n.nspname = 'public'
      and c.relname = 'community_profiles'
      and a.attname in ('account_status', 'deactivated_at', 'deactivated_auth_user_id')
      and not a.attisdropped
  ),
  'profileConstraint', (
    select jsonb_build_object('name', con.conname, 'definition', pg_get_constraintdef(con.oid, true), 'validated', con.convalidated)
    from pg_constraint con
    where con.conrelid = 'public.community_profiles'::regclass
      and con.conname = 'community_profiles_account_status_check'
  ),
  'profileIndex', (
    select jsonb_build_object(
      'name', indexes.indexname,
      'definition', indexes.indexdef,
      'valid', index_catalog.indisvalid,
      'ready', index_catalog.indisready
    )
    from pg_indexes indexes
    join pg_namespace namespace on namespace.nspname = indexes.schemaname
    join pg_class index_class on index_class.relnamespace = namespace.oid
      and index_class.relname = indexes.indexname
    join pg_index index_catalog on index_catalog.indexrelid = index_class.oid
    where indexes.schemaname = 'public'
      and indexes.tablename = 'community_profiles'
      and indexes.indexname = 'community_profiles_account_status_idx'
  ),
  'deletionTable', (
    select jsonb_build_object('rls', c.relrowsecurity, 'forceRls', c.relforcerowsecurity, 'acl', c.relacl)
    from pg_class c
    where c.oid = 'public.account_deletion_requests'::regclass
  ),
  'deletionColumns', (
    select jsonb_agg(jsonb_build_object(
      'name', a.attname,
      'type', format_type(a.atttypid, a.atttypmod),
      'notNull', a.attnotnull,
      'default', pg_get_expr(ad.adbin, ad.adrelid)
    ) order by a.attnum)
    from pg_attribute a
    left join pg_attrdef ad on ad.adrelid = a.attrelid and ad.adnum = a.attnum
    where a.attrelid = 'public.account_deletion_requests'::regclass
      and a.attnum > 0 and not a.attisdropped
  ),
  'deletionConstraints', (
    select jsonb_agg(jsonb_build_object('name', con.conname, 'type', con.contype::text, 'definition', pg_get_constraintdef(con.oid, true), 'validated', con.convalidated) order by con.conname)
    from pg_constraint con
    where con.conrelid = 'public.account_deletion_requests'::regclass
  ),
  'functions', (
    select jsonb_agg(jsonb_build_object(
      'name', p.proname,
      'args', pg_get_function_identity_arguments(p.oid),
      'md5', md5(replace(pg_get_functiondef(p.oid), E'\r\n', E'\n')),
      'securityDefiner', p.prosecdef,
      'volatility', p.provolatile::text,
      'config', coalesce(p.proconfig, array[]::text[]),
      'acl', p.proacl,
      'serviceRoleExecute', has_function_privilege('service_role', p.oid, 'execute'),
      'anonExecute', has_function_privilege('anon', p.oid, 'execute'),
      'authenticatedExecute', has_function_privilege('authenticated', p.oid, 'execute')
    ) order by p.proname)
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'quata_chat_auth_profile_id',
        'quata_chat_actor_profile_id',
        'quata_account_deactivate',
        'quata_account_collect_deletion_assets',
        'quata_account_delete_data'
      )
  )
) observation;
