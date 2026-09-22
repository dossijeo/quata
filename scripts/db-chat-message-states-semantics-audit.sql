-- Read-only exhaustive catalogue audit for the chat-message-states package.
-- Emits schema metadata only; it never reads chat rows or executes functions.
with observation as (
select jsonb_build_object(
  'table',(select jsonb_agg(jsonb_build_object('name',c.relname,'rls',c.relrowsecurity,'forceRls',c.relforcerowsecurity,'acl',c.relacl,'authenticatedSelect',has_table_privilege('authenticated',c.oid,'SELECT')) order by c.relname) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname='chat_message_states' and c.relkind='r'),
  'columns',(select jsonb_agg(jsonb_build_object('ordinal',a.attnum,'name',a.attname,'type',format_type(a.atttypid,a.atttypmod),'notNull',a.attnotnull,'default',pg_get_expr(d.adbin,d.adrelid),'identity',a.attidentity,'generated',a.attgenerated) order by a.attnum) from pg_attribute a join pg_class c on c.oid=a.attrelid join pg_namespace n on n.oid=c.relnamespace left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum where n.nspname='public' and c.relname='chat_message_states' and a.attnum>0 and not a.attisdropped),
  'constraints',(select jsonb_agg(jsonb_build_object('name',con.conname,'type',con.contype,'definition',pg_get_constraintdef(con.oid,true),'validated',con.convalidated) order by con.conname) from pg_constraint con join pg_class c on c.oid=con.conrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname='chat_message_states'),
  'indexes',(select jsonb_agg(jsonb_build_object('name',ic.relname,'definition',pg_get_indexdef(i.indexrelid),'valid',i.indisvalid,'ready',i.indisready,'unique',i.indisunique,'primary',i.indisprimary) order by ic.relname) from pg_index i join pg_class ic on ic.oid=i.indexrelid join pg_class tc on tc.oid=i.indrelid join pg_namespace n on n.oid=tc.relnamespace where n.nspname='public' and tc.relname='chat_message_states'),
  'policies',(select jsonb_agg(jsonb_build_object('name',policyname,'permissive',permissive,'roles',roles,'cmd',cmd,'qual',qual,'withCheck',with_check) order by policyname) from pg_policies where schemaname='public' and tablename='chat_message_states'),
  'triggers',(select jsonb_agg(jsonb_build_object('name',t.tgname,'definition',pg_get_triggerdef(t.oid,true),'enabled',t.tgenabled::text,'function',p.proname) order by t.tgname) from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace join pg_proc p on p.oid=t.tgfoid where n.nspname='public' and c.relname='chat_message_states' and not t.tgisinternal),
  'functions',(select jsonb_agg(jsonb_build_object('name',p.proname,'args',pg_get_function_identity_arguments(p.oid),'result',pg_get_function_result(p.oid),'language',l.lanname,'securityDefiner',p.prosecdef,'volatility',p.provolatile::text,'config',coalesce(p.proconfig,array[]::text[]),'md5',md5(pg_get_functiondef(p.oid)),'publicExecute',has_function_privilege('public',p.oid,'EXECUTE'),'anonExecute',has_function_privilege('anon',p.oid,'EXECUTE'),'authenticatedExecute',has_function_privilege('authenticated',p.oid,'EXECUTE'),'serviceRoleExecute',has_function_privilege('service_role',p.oid,'EXECUTE'),'acl',p.proacl) order by p.proname) from pg_proc p join pg_namespace n on n.oid=p.pronamespace join pg_language l on l.oid=p.prolang where n.nspname='public' and p.proname in ('quata_chat_touch_message_state_updated_at','quata_chat_mark_messages_state','quata_chat_mark_message_state','quata_chat_message_json','quata_chat_mark_thread_read')),
  'publication',(select jsonb_agg(jsonb_build_object('publication',pubname,'schema',schemaname,'table',tablename) order by pubname,schemaname,tablename) from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='chat_message_states')
) as metadata
)
select jsonb_build_object(
  'metadata',metadata,
  'digests',jsonb_build_object(
    'tableMd5',md5((metadata->'table')::text),
    'columnsMd5',md5((metadata->'columns')::text),
    'constraintsMd5',md5((metadata->'constraints')::text),
    'indexesMd5',md5((metadata->'indexes')::text),
    'policiesMd5',md5((metadata->'policies')::text),
    'triggersMd5',md5((metadata->'triggers')::text),
    'functionsMd5',md5((metadata->'functions')::text),
    'publicationMd5',md5((metadata->'publication')::text)
  ),
  'allEffectsExact',
    md5((metadata->'table')::text) = '859f1ba425a396e24efa4738bf071783'
    and md5((metadata->'columns')::text) = '0c2b4b8bdca6aebc301b4f142ba4ec47'
    and md5((metadata->'constraints')::text) = 'b357aee237a292ca753fb04b72d87b1e'
    and md5((metadata->'indexes')::text) = '6e74a6bb5f3e91f5a7deda32c1274740'
    and md5((metadata->'policies')::text) = '16f0845157ebf13b1b29a9268308bf1e'
    and md5((metadata->'triggers')::text) = 'd9452ead80b1cd0e1176a2e6c2d9952b'
    and md5((metadata->'functions')::text) = '4f8c9a5bb3536c4dc6d17c180a09daf7'
    and md5((metadata->'publication')::text) = '1a53d5f51abad7451cff20eefbf4e4f6'
)
from observation;
