\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned
select 'guard=' || md5(pg_get_functiondef('public.quata_guard_official_post_likes()'::regprocedure));
select 'guard_acl=' || md5(coalesce(proacl::text, ''))
from pg_proc where oid = 'public.quata_guard_official_post_likes()'::regprocedure;
select 'helper=' || md5(pg_get_functiondef('public.quata_official_like_delete_allowed(uuid)'::regprocedure));
select 'helper_acl=' || md5(coalesce(proacl::text, ''))
from pg_proc where oid = 'public.quata_official_like_delete_allowed(uuid)'::regprocedure;
select 'trigger=' || md5(pg_get_triggerdef(t.oid, true))
from pg_trigger t where t.tgrelid = 'public.official_post_likes'::regclass
and t.tgname = 'quata_guard_official_post_likes_trg' and not t.tgisinternal;
select 'acl=' || md5(coalesce(c.relacl::text, ''))
from pg_class c where c.oid = 'public.official_post_likes'::regclass;
select 'policies=' || md5(string_agg(
    p.policyname || '|' || p.cmd || '|' || coalesce(array_to_string(p.roles, ','), '') || '|'
    || coalesce(p.qual, '') || '|' || coalesce(p.with_check, ''), E'\n' order by p.policyname))
from pg_policies p
where p.schemaname = 'public' and p.tablename = 'official_post_likes';
