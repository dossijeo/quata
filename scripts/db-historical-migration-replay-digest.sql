CREATE OR REPLACE FUNCTION pg_temp.quata_catalog_digest()
RETURNS text
LANGUAGE sql
AS $$
WITH target_namespaces AS (
  SELECT oid, nspname FROM pg_namespace WHERE nspname IN ('public','storage','security_citizen')
), objects AS (
  SELECT 'REL|' || n.nspname || '|' || c.relname || '|' || c.relkind::text || '|' || c.relpersistence::text || '|' || c.relowner || '|' || coalesce(c.relacl::text,'') || '|' || coalesce(c.reloptions::text,'') || '|' || c.relrowsecurity || '|' || c.relforcerowsecurity AS value
  FROM pg_class c JOIN target_namespaces n ON n.oid=c.relnamespace
  WHERE c.relkind IN ('r','p','v','m','S','f')
  UNION ALL
  SELECT 'COL|' || n.nspname || '|' || c.relname || '|' || a.attnum || '|' || a.attname || '|' || format_type(a.atttypid,a.atttypmod) || '|' || a.attnotnull || '|' || a.attidentity::text || '|' || a.attgenerated::text || '|' || coalesce(a.attcollation::regcollation::text,'') || '|' || coalesce(pg_get_expr(d.adbin,d.adrelid,true),'')
  FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN target_namespaces n ON n.oid=c.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
  WHERE a.attnum>0 AND NOT a.attisdropped AND c.relkind IN ('r','p','v','m','f')
  UNION ALL
  SELECT 'CON|' || n.nspname || '|' || c.relname || '|' || con.conname || '|' || con.contype::text || '|' || con.convalidated || '|' || pg_get_constraintdef(con.oid,true)
  FROM pg_constraint con JOIN pg_class c ON c.oid=con.conrelid JOIN target_namespaces n ON n.oid=c.relnamespace
  UNION ALL
  SELECT 'IDX|' || n.nspname || '|' || c.relname || '|' || i.indexrelid::regclass::text || '|' || i.indisvalid || '|' || i.indisready || '|' || pg_get_indexdef(i.indexrelid)
  FROM pg_index i JOIN pg_class c ON c.oid=i.indrelid JOIN target_namespaces n ON n.oid=c.relnamespace
  UNION ALL
  SELECT 'TRG|' || n.nspname || '|' || c.relname || '|' || t.tgname || '|' || t.tgenabled::text || '|' || pg_get_triggerdef(t.oid,true)
  FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN target_namespaces n ON n.oid=c.relnamespace WHERE NOT t.tgisinternal
  UNION ALL
  SELECT 'POL|' || n.nspname || '|' || c.relname || '|' || p.polname || '|' || p.polcmd::text || '|' || p.polpermissive || '|' || p.polroles::text || '|' || coalesce(pg_get_expr(p.polqual,p.polrelid,true),'') || '|' || coalesce(pg_get_expr(p.polwithcheck,p.polrelid,true),'')
  FROM pg_policy p JOIN pg_class c ON c.oid=p.polrelid JOIN target_namespaces n ON n.oid=c.relnamespace
  UNION ALL
  SELECT 'FUN|' || n.nspname || '|' || p.proname || '|' || pg_get_function_identity_arguments(p.oid) || '|' || pg_get_function_result(p.oid) || '|' || l.lanname || '|' || p.provolatile::text || '|' || p.proparallel::text || '|' || p.proisstrict || '|' || p.prosecdef || '|' || p.proleakproof || '|' || coalesce(p.proconfig::text,'') || '|' || coalesce(p.proacl::text,'') || '|' || pg_get_functiondef(p.oid)
  FROM pg_proc p JOIN target_namespaces n ON n.oid=p.pronamespace JOIN pg_language l ON l.oid=p.prolang
  UNION ALL
  SELECT 'TYPE|' || n.nspname || '|' || t.typname || '|' || t.typtype::text || '|' || t.typcategory::text || '|' || t.typnotnull || '|' || coalesce(t.typdefault,'') || '|' || coalesce(t.typacl::text,'')
  FROM pg_type t JOIN target_namespaces n ON n.oid=t.typnamespace WHERE t.typtype IN ('e','d')
  UNION ALL
  SELECT 'ENUM|' || n.nspname || '|' || t.typname || '|' || e.enumsortorder || '|' || e.enumlabel
  FROM pg_enum e JOIN pg_type t ON t.oid=e.enumtypid JOIN target_namespaces n ON n.oid=t.typnamespace
)
SELECT md5(coalesce(string_agg(value,E'\n' ORDER BY value),'')) FROM objects;
$$;

CREATE TEMP TABLE IF NOT EXISTS quata_digest_rows (
  object_name text PRIMARY KEY,
  row_count bigint NOT NULL,
  content_digest text NOT NULL
);

CREATE OR REPLACE FUNCTION pg_temp.quata_data_digest()
RETURNS text
LANGUAGE plpgsql
AS $$
DECLARE
  r record;
  v_count bigint;
  v_digest text;
  v_result text;
BEGIN
  TRUNCATE quata_digest_rows;
  FOR r IN
    SELECT n.nspname, c.relname
    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname IN ('public','security_citizen') AND c.relkind IN ('r','p')
    ORDER BY n.nspname,c.relname
  LOOP
    EXECUTE format('SELECT count(*)::bigint, md5(coalesce(string_agg(h, '''' ORDER BY h),'''')) FROM (SELECT md5(row_to_json(t)::text) AS h FROM %I.%I t) q',r.nspname,r.relname)
      INTO v_count,v_digest;
    INSERT INTO quata_digest_rows VALUES (r.nspname||'.'||r.relname,v_count,v_digest);
  END LOOP;
  FOR r IN
    SELECT schemaname, sequencename FROM pg_sequences WHERE schemaname IN ('public','security_citizen') ORDER BY schemaname,sequencename
  LOOP
    EXECUTE format('SELECT last_value::bigint, md5(last_value::text||''|''||is_called::text) FROM %I.%I',r.schemaname,r.sequencename)
      INTO v_count,v_digest;
    INSERT INTO quata_digest_rows VALUES ('sequence:'||r.schemaname||'.'||r.sequencename,v_count,v_digest);
  END LOOP;
  SELECT md5(coalesce(string_agg(object_name||'|'||row_count||'|'||content_digest,E'\n' ORDER BY object_name),'')) INTO v_result FROM quata_digest_rows;
  RETURN v_result;
END;
$$;
