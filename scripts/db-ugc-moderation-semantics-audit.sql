-- Read-only exhaustive catalogue audit for the UGC moderation package.
-- Emits schema metadata only; it never reads UGC rows or executes functions.
with observation as (
select jsonb_build_object(
 'tables',(select jsonb_agg(jsonb_build_object('name',c.relname,'rls',c.relrowsecurity,'forceRls',c.relforcerowsecurity) order by c.relname) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname in ('ugc_reports','ugc_terms_acceptances') and c.relkind='r'),
 'columns',(select jsonb_agg(jsonb_build_object('table',c.relname,'ordinal',a.attnum,'name',a.attname,'type',format_type(a.atttypid,a.atttypmod),'notNull',a.attnotnull,'default',pg_get_expr(d.adbin,d.adrelid),'identity',a.attidentity,'generated',a.attgenerated) order by c.relname,a.attnum) from pg_attribute a join pg_class c on c.oid=a.attrelid join pg_namespace n on n.oid=c.relnamespace left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum where n.nspname='public' and c.relname in ('ugc_reports','ugc_terms_acceptances') and a.attnum>0 and not a.attisdropped),
 'identitySequences',(select jsonb_agg(jsonb_build_object('table',tc.relname,'column',a.attname,'sequenceSchema',sn.nspname,'sequenceName',sc.relname,'dependencyType',dep.deptype::text,'type',format_type(s.seqtypid,null),'start',s.seqstart::text,'increment',s.seqincrement::text,'minimum',s.seqmin::text,'maximum',s.seqmax::text,'cache',s.seqcache::text,'cycle',s.seqcycle) order by tc.relname,a.attname) from pg_attribute a join pg_class tc on tc.oid=a.attrelid join pg_namespace tn on tn.oid=tc.relnamespace join pg_depend dep on dep.refclassid='pg_class'::regclass and dep.refobjid=tc.oid and dep.refobjsubid=a.attnum and dep.classid='pg_class'::regclass and dep.deptype='i' join pg_class sc on sc.oid=dep.objid and sc.relkind='S' join pg_namespace sn on sn.oid=sc.relnamespace join pg_sequence s on s.seqrelid=sc.oid where tn.nspname='public' and tc.relname='ugc_reports' and a.attname='id' and a.attidentity='d'),
 'constraints',(select jsonb_agg(jsonb_build_object('table',c.relname,'name',con.conname,'type',con.contype,'definition',pg_get_constraintdef(con.oid,true)) order by c.relname,con.conname) from pg_constraint con join pg_class c on c.oid=con.conrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname in ('ugc_reports','ugc_terms_acceptances')),
 'indexes',(select jsonb_agg(jsonb_build_object('table',tc.relname,'name',ic.relname,'definition',pg_get_indexdef(i.indexrelid),'valid',i.indisvalid,'ready',i.indisready,'unique',i.indisunique,'primary',i.indisprimary) order by tc.relname,ic.relname) from pg_index i join pg_class ic on ic.oid=i.indexrelid join pg_class tc on tc.oid=i.indrelid join pg_namespace n on n.oid=tc.relnamespace where n.nspname='public' and tc.relname in ('ugc_reports','ugc_terms_acceptances')),
 'policies',(select jsonb_agg(jsonb_build_object('table',tablename,'name',policyname,'permissive',permissive,'roles',roles,'cmd',cmd,'qual',qual,'withCheck',with_check) order by tablename,policyname) from pg_policies where schemaname='public' and tablename in ('ugc_reports','ugc_terms_acceptances')),
 'functions',(select jsonb_agg(jsonb_build_object('name',p.proname,'args',pg_get_function_identity_arguments(p.oid),'result',pg_get_function_result(p.oid),'language',l.lanname,'securityDefiner',p.prosecdef,'volatility',p.provolatile::text,'config',coalesce(p.proconfig,array[]::text[]),'md5',md5(pg_get_functiondef(p.oid)),'publicExecute',has_function_privilege('public',p.oid,'EXECUTE'),'anonExecute',has_function_privilege('anon',p.oid,'EXECUTE'),'authenticatedExecute',has_function_privilege('authenticated',p.oid,'EXECUTE'),'serviceRoleExecute',has_function_privilege('service_role',p.oid,'EXECUTE'),'acl',p.proacl) order by p.proname) from pg_proc p join pg_namespace n on n.oid=p.pronamespace join pg_language l on l.oid=p.prolang where n.nspname='public' and p.proname in ('quata_ugc_report','quata_profile_block','quata_profile_unblock','quata_has_accepted_ugc_terms','quata_accept_ugc_terms'))
)
)
select jsonb_build_object(
  'metadata', jsonb_build_object,
  'digests', jsonb_build_object(
    'tablesMd5', md5((jsonb_build_object->'tables')::text),
    'columnsMd5', md5((jsonb_build_object->'columns')::text),
    'identitySequencesMd5', md5((jsonb_build_object->'identitySequences')::text),
    'constraintsMd5', md5((jsonb_build_object->'constraints')::text),
    'indexesMd5', md5((jsonb_build_object->'indexes')::text),
    'policiesMd5', md5((jsonb_build_object->'policies')::text),
    'functionsMd5', md5((jsonb_build_object->'functions')::text)
  ),
  'allEffectsExact',
    md5((jsonb_build_object->'tables')::text) = '4e5d3c6ab9bcb19075524601ae672f3b'
    and md5((jsonb_build_object->'columns')::text) = 'e00aa07fd80d6317fd2caec25eb2933d'
    and md5((jsonb_build_object->'identitySequences')::text) = 'f32a76fd9c780d8eee944e77d9e91e57'
    and md5((jsonb_build_object->'constraints')::text) = 'c3b15887bf1d3800dd2c406ba498a6b3'
    and md5((jsonb_build_object->'indexes')::text) = '608e5333c6709f2b9861fa93d8378845'
    and md5((jsonb_build_object->'policies')::text) = '88455a5794bcdab780cfc5834b201c19'
    and md5((jsonb_build_object->'functions')::text) = '5c8cd393f255364f508282051630d553'
)
from observation;
