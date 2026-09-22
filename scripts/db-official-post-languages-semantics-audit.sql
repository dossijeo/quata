-- Read-only exhaustive catalogue audit for the Official language-variants package.
-- Emits schema metadata only; it never reads Official rows or executes functions.
with observation as (
select jsonb_build_object(
  'extension',(select jsonb_agg(jsonb_build_object('name',e.extname,'schema',n.nspname,'version',e.extversion,'relocatable',e.extrelocatable) order by e.extname) from pg_extension e join pg_namespace n on n.oid=e.extnamespace where e.extname='pgcrypto'),
  'table',(select jsonb_agg(jsonb_build_object('name',c.relname,'rls',c.relrowsecurity,'forceRls',c.relforcerowsecurity) order by c.relname) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname='official_posts' and c.relkind='r'),
  'columns',(select jsonb_agg(jsonb_build_object('ordinal',a.attnum,'name',a.attname,'type',format_type(a.atttypid,a.atttypmod),'notNull',a.attnotnull,'default',pg_get_expr(d.adbin,d.adrelid),'identity',a.attidentity,'generated',a.attgenerated,'comment',col_description(c.oid,a.attnum)) order by a.attnum) from pg_attribute a join pg_class c on c.oid=a.attrelid join pg_namespace n on n.oid=c.relnamespace left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum where n.nspname='public' and c.relname='official_posts' and a.attname in ('read_more_label','language','translation_group_id') and not a.attisdropped),
  'constraints',(select jsonb_agg(jsonb_build_object('name',con.conname,'type',con.contype,'definition',pg_get_constraintdef(con.oid,true),'validated',con.convalidated) order by con.conname) from pg_constraint con join pg_class c on c.oid=con.conrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname='official_posts' and con.conname='official_posts_language_check'),
  'indexes',(select jsonb_agg(jsonb_build_object('name',ic.relname,'definition',pg_get_indexdef(i.indexrelid),'valid',i.indisvalid,'ready',i.indisready,'unique',i.indisunique,'primary',i.indisprimary) order by ic.relname) from pg_index i join pg_class ic on ic.oid=i.indexrelid join pg_class tc on tc.oid=i.indrelid join pg_namespace n on n.oid=tc.relnamespace where n.nspname='public' and tc.relname='official_posts' and ic.relname in ('official_posts_language_published_idx','official_posts_translation_group_idx','official_posts_translation_group_language_live_idx')),
  'triggers',(select jsonb_agg(jsonb_build_object('name',t.tgname,'definition',pg_get_triggerdef(t.oid,true),'enabled',t.tgenabled::text,'function',p.proname) order by t.tgname) from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace join pg_proc p on p.oid=t.tgfoid where n.nspname='public' and c.relname='official_posts' and t.tgname='quata_normalize_official_post_language_trg' and not t.tgisinternal),
  'functions',(select jsonb_agg(jsonb_build_object('name',p.proname,'args',pg_get_function_identity_arguments(p.oid),'result',pg_get_function_result(p.oid),'language',l.lanname,'securityDefiner',p.prosecdef,'volatility',p.provolatile::text,'config',coalesce(p.proconfig,array[]::text[]),'md5',md5(pg_get_functiondef(p.oid)),'acl',p.proacl) order by p.proname) from pg_proc p join pg_namespace n on n.oid=p.pronamespace join pg_language l on l.oid=p.prolang where n.nspname='public' and p.proname in ('quata_normalize_official_post_language','quata_requested_official_post_language')),
  'policies',(select jsonb_agg(jsonb_build_object('name',policyname,'permissive',permissive,'roles',roles,'cmd',cmd,'qual',qual,'withCheck',with_check) order by policyname) from pg_policies where schemaname='public' and tablename='official_posts' and policyname in ('official_posts_public_read_language','official_posts_authenticated_insert','official_posts_authenticated_update_guarded','official_posts_authenticated_delete_guarded','official_posts_authenticated_insert_official_own','official_posts_authenticated_update_author_or_admin','official_posts_authenticated_delete_author_or_admin'))
) as metadata
)
select jsonb_build_object(
  'metadata',metadata,
  'digests',jsonb_build_object(
    'extensionMd5',md5((metadata->'extension')::text),
    'tableMd5',md5((metadata->'table')::text),
    'columnsMd5',md5((metadata->'columns')::text),
    'constraintsMd5',md5((metadata->'constraints')::text),
    'indexesMd5',md5((metadata->'indexes')::text),
    'triggersMd5',md5((metadata->'triggers')::text),
    'functionsMd5',md5((metadata->'functions')::text),
    'policiesMd5',md5((metadata->'policies')::text)
  ),
  'allEffectsExact',
    md5((metadata->'extension')::text) = '04b815c392d8e44b2a1d82787b5ad06a'
    and md5((metadata->'table')::text) = 'ca52b31d4a66146ed7354c48cb508347'
    and md5((metadata->'columns')::text) = '6a256fc055a06db393d0fe36a1e6a723'
    and md5((metadata->'constraints')::text) = '007fa4d71b0238258d5f4609bca7f5ba'
    and md5((metadata->'indexes')::text) = 'afc925abdfaf1e5a375565b1087653db'
    and md5((metadata->'triggers')::text) = 'd0fda67631e0a3c01db2bf4fb00d3b15'
    and md5((metadata->'functions')::text) = 'f52f51541ed72c06b39add1e590d68ec'
    and md5((metadata->'policies')::text) = '514cbc699267aa9416ab7a05cc646b98'
)
from observation;
