\pset tuples_only on
\pset format unaligned
with policy_state as (
    select coalesce(string_agg(
        policyname || '|' || cmd || '|' || array_to_string(roles, ',') || '|'
        || coalesce(qual, '') || '|' || coalesce(with_check, ''),
        E'\n' order by policyname
    ), '') as value
    from pg_policies
    where schemaname = 'public' and tablename = 'official_post_comments'
),
guard_state as (
    select pg_get_functiondef(p.oid) || '|' || p.prosecdef || '|'
        || coalesce(p.proconfig::text, '') || '|' || coalesce(p.proacl::text, '') || '|'
        || pg_get_userbyid(p.proowner) as value
    from pg_proc p
    where p.oid = 'public.quata_guard_official_post_comments()'::regprocedure
),
helper_state as (
    select pg_get_functiondef(p.oid) || '|' || p.prosecdef || '|'
        || coalesce(p.proconfig::text, '') || '|' || coalesce(p.proacl::text, '') || '|'
        || pg_get_userbyid(p.proowner) as value
    from pg_proc p
    where p.oid = 'public.quata_official_comment_mutation_allowed(uuid)'::regprocedure
),
trigger_state as (
    select pg_get_triggerdef(t.oid, true) || '|' || t.tgenabled::text as value
    from pg_trigger t
    where t.tgrelid = 'public.official_post_comments'::regclass
      and t.tgname = 'quata_guard_official_post_comments_trg'
      and not t.tgisinternal
),
table_state as (
    select c.relrowsecurity || '|' || c.relforcerowsecurity || '|'
        || pg_get_userbyid(c.relowner) || '|' || coalesce(c.relacl::text, '') as value
    from pg_class c
    where c.oid = 'public.official_post_comments'::regclass
)
select md5(concat_ws(E'\n',
    (select value from table_state),
    (select value from policy_state),
    (select value from guard_state),
    (select value from helper_state),
    (select value from trigger_state)
));
