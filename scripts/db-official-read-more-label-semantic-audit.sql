-- Read-only semantic audit for 20260702_0004_official_read_more_label.sql.
-- Its original default is intentionally superseded by the exact ALTER in
-- 20260709_0002_official_post_languages.sql; every other source effect remains.
-- Emits catalogue metadata only; it never reads application rows.
with target_column as (
  select
    n.nspname as schema_name,
    c.relname as table_name,
    a.attname as column_name,
    format_type(a.atttypid, a.atttypmod) as data_type,
    a.attnotnull as not_null,
    pg_get_expr(d.adbin, d.adrelid, true) as default_expression,
    col_description(a.attrelid, a.attnum) as column_comment,
    a.attidentity::text as identity_kind,
    a.attgenerated::text as generated_kind,
    a.attislocal as is_local,
    a.attinhcount as inheritance_count,
    a.attstorage::text as storage_kind,
    a.attcompression::text as compression_kind,
    a.attstattarget as statistics_target,
    a.attndims as array_dimensions,
    a.attacl is null as has_no_column_acl,
    a.attcollation = t.typcollation as uses_type_default_collation
  from pg_attribute a
  join pg_class c on c.oid = a.attrelid
  join pg_namespace n on n.oid = c.relnamespace
  join pg_type t on t.oid = a.atttypid
  left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
  where n.nspname = 'public'
    and c.relname = 'official_posts'
    and c.relkind in ('r', 'p')
    and a.attname = 'read_more_label'
    and a.attnum > 0
    and not a.attisdropped
), mismatches as (
  select count(*) as mismatch_count
  from target_column
  where schema_name is distinct from 'public'
     or table_name is distinct from 'official_posts'
     or column_name is distinct from 'read_more_label'
     or data_type is distinct from 'text'
     or not_null is distinct from true
     or default_expression is distinct from '''read_more''::text'
     or column_comment is distinct from
        'Custom label shown on official feed cards for opening the full rich-text story.'
     or identity_kind is distinct from ''
     or generated_kind is distinct from ''
     or is_local is distinct from true
     or inheritance_count is distinct from 0
     or storage_kind is distinct from 'x'
     or compression_kind is distinct from ''
     or statistics_target is not null
     or array_dimensions is distinct from 0
     or has_no_column_acl is distinct from true
     or uses_type_default_collation is distinct from true
)
select jsonb_build_object(
  'columns', (select coalesce(jsonb_agg(to_jsonb(c) order by column_name), '[]'::jsonb) from target_column c),
  'columnCount', (select count(*) from target_column),
  'columnMismatchCount',
    (select mismatch_count from mismatches)
    + case when (select count(*) from target_column) = 1 then 0 else 1 end,
  'allEffectsExact',
    (select count(*) from target_column) = 1
    and (select mismatch_count from mismatches) = 0
);
