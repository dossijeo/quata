-- Read-only semantic audit for Chat client-message idempotency.
-- Emits catalogue metadata only; it never reads application rows.
with target_column as (
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
    and c.relname = 'chat_messages'
    and a.attname = 'client_message_id'
    and a.attnum > 0
    and not a.attisdropped
), column_mismatches as (
  select count(*) as mismatch_count
  from target_column
  where data_type is distinct from 'text'
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
    and table_class.relname = 'chat_messages'
    and index_class.relname = 'chat_messages_client_message_unique_idx'
), index_mismatches as (
  select count(*) as mismatch_count
  from target_index
  where definition is distinct from
        'CREATE UNIQUE INDEX chat_messages_client_message_unique_idx ON public.chat_messages USING btree (thread_id, sender_profile_id, client_message_id) WHERE (client_message_id IS NOT NULL)'
     or is_valid is distinct from true
     or is_ready is distinct from true
     or is_unique is distinct from true
), expected_functions as (
  select * from (values
    (
      'quata_chat_message_json',
      'p_message_id bigint, p_actor_profile_id uuid',
      'jsonb', 'sql', true, 's', array['search_path=public']::text[],
      '302687376f6e54116e2be944a7e69899'
    ),
    (
      'quata_chat_send_files',
      'p_actor_profile_id uuid, p_thread_id bigint, p_file_ids bigint[], p_message text',
      'jsonb', 'sql', true, 'v', array['search_path=public']::text[],
      '0d898b67462756a822cb7f93dd628305'
    ),
    (
      'quata_chat_send_message',
      'p_actor_profile_id uuid, p_thread_id bigint, p_message text, p_file_ids bigint[], p_reply_to_message_id bigint, p_client_message_id text',
      'jsonb', 'plpgsql', true, 'v', array['search_path=public']::text[],
      '8c5a1bfa4dd69d350f3438e5936cf93b'
    )
  ) as expected(
    function_name, identity_arguments, result_type, language_name,
    security_definer, volatility, configuration, definition_md5
  )
), target_functions as (
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
  join expected_functions expected
    on expected.function_name = p.proname
   and expected.identity_arguments = pg_get_function_identity_arguments(p.oid)
  where n.nspname = 'public'
), function_mismatches as (
  select count(*) as mismatch_count
  from expected_functions expected
  left join target_functions actual
    on actual.function_name = expected.function_name
   and actual.identity_arguments = expected.identity_arguments
  where actual.function_name is null
     or actual.result_type is distinct from expected.result_type
     or actual.language_name is distinct from expected.language_name
     or actual.security_definer is distinct from expected.security_definer
     or actual.volatility is distinct from expected.volatility
     or actual.configuration is distinct from expected.configuration
     or actual.definition_md5 is distinct from expected.definition_md5
     or actual.anon_execute is distinct from true
     or actual.authenticated_execute is distinct from true
), old_signature as (
  select count(*) as function_count
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname = 'quata_chat_send_message'
    and pg_get_function_identity_arguments(p.oid) =
      'p_actor_profile_id uuid, p_thread_id bigint, p_message text, p_file_ids bigint[], p_reply_to_message_id bigint'
)
select jsonb_build_object(
  'columns', (select coalesce(jsonb_agg(to_jsonb(c)), '[]'::jsonb) from target_column c),
  'columnCount', (select count(*) from target_column),
  'columnMismatchCount',
    (select mismatch_count from column_mismatches)
    + case when (select count(*) from target_column) = 1 then 0 else 1 end,
  'indexes', (select coalesce(jsonb_agg(to_jsonb(i)), '[]'::jsonb) from target_index i),
  'indexCount', (select count(*) from target_index),
  'indexMismatchCount',
    (select mismatch_count from index_mismatches)
    + case when (select count(*) from target_index) = 1 then 0 else 1 end,
  'functions', (
    select coalesce(jsonb_agg(to_jsonb(f) order by function_name), '[]'::jsonb)
    from target_functions f
  ),
  'functionCount', (select count(*) from target_functions),
  'functionMismatchCount', (select mismatch_count from function_mismatches),
  'oldSignatureCount', (select function_count from old_signature),
  'allEffectsExact',
    (select count(*) from target_column) = 1
    and (select mismatch_count from column_mismatches) = 0
    and (select count(*) from target_index) = 1
    and (select mismatch_count from index_mismatches) = 0
    and (select count(*) from target_functions) = 3
    and (select mismatch_count from function_mismatches) = 0
    and (select function_count from old_signature) = 0
);
