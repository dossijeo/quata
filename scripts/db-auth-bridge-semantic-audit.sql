-- Read-only semantic audit for 20260628_0003_auth_bridge_support.sql.
-- Emits catalogue booleans only; it never reads application-row values.
with expected(column_name, index_name) as (
  values
    ('phone_local', 'community_profiles_phone_local_idx'),
    ('phone_normalized', 'community_profiles_phone_normalized_idx'),
    ('telefono', 'community_profiles_telefono_idx'),
    ('country_code', 'community_profiles_country_code_idx'),
    ('code', 'community_profiles_code_idx')
),
observed as (
  select
    e.column_name,
    exists (
      select 1
      from pg_attribute a
      join pg_class c on c.oid = a.attrelid
      join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public'
        and c.relname = 'community_profiles'
        and a.attname = e.column_name
        and a.attnum > 0
        and not a.attisdropped
    ) as column_exists,
    coalesce((
      select
        i.indisvalid
        and i.indisready
        and pg_get_indexdef(ic.oid) = format(
          'CREATE INDEX %I ON public.community_profiles USING btree (%I) WHERE (%I IS NOT NULL)',
          e.index_name,
          e.column_name,
          e.column_name
        )
      from pg_class ic
      join pg_namespace n on n.oid = ic.relnamespace
      join pg_index i on i.indexrelid = ic.oid
      where n.nspname = 'public'
        and ic.relname = e.index_name
    ), false) as exact_index_exists
  from expected e
)
select column_name, column_exists, exact_index_exists
from observed
order by column_name;
