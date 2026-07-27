\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

select md5(concat_ws(E'\n',
  (select coalesce(relrowsecurity::text, '') || '|' || coalesce(relforcerowsecurity::text, '') || '|' || coalesce(relacl::text, '')
   from pg_class where oid = 'public.official_post_likes'::regclass),
  (select coalesce(string_agg(policyname || '|' || cmd || '|' || coalesce(array_to_string(roles, ','), '') || '|'
     || coalesce(qual, '') || '|' || coalesce(with_check, ''), E'\n' order by policyname), '')
   from pg_policies where schemaname = 'public' and tablename = 'official_post_likes'),
  (select coalesce(pg_get_functiondef('public.quata_guard_official_post_likes()'::regprocedure), '')),
  (select coalesce(pg_get_functiondef('public.quata_official_like_delete_allowed(uuid)'::regprocedure), '')),
  (select coalesce(pg_get_triggerdef(oid, true), '') from pg_trigger
   where tgrelid = 'public.official_post_likes'::regclass and tgname = 'quata_guard_official_post_likes_trg' and not tgisinternal)
));
