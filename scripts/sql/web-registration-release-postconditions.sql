with untrusted_roles(role_name) as (
    values ('anon'), ('authenticated')
), registration_tables(table_name) as (
    values
        ('web_registration_requests'),
        ('web_registration_rate_limits'),
        ('web_registration_cleanup_events')
), table_privileges(privilege_name) as (
    values ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'),
           ('TRUNCATE'), ('REFERENCES'), ('TRIGGER')
), required_service_table_acl(table_name, privilege_name) as (
    values
        ('web_registration_requests', 'SELECT'),
        ('web_registration_requests', 'INSERT'),
        ('web_registration_requests', 'UPDATE'),
        ('web_registration_requests', 'DELETE'),
        ('web_registration_rate_limits', 'SELECT'),
        ('web_registration_rate_limits', 'INSERT'),
        ('web_registration_rate_limits', 'UPDATE'),
        ('web_registration_rate_limits', 'DELETE'),
        ('web_registration_cleanup_events', 'SELECT'),
        ('web_registration_cleanup_events', 'INSERT')
), registration_functions(function_signature) as (
    values
        ('public.quata_claim_web_registration(text,text,text,text,text)'),
        ('public.quata_web_registration_auth_user(text)'),
        ('public.quata_claim_web_registration_cleanup(uuid,text)'),
        ('public.quata_finish_web_registration_cleanup(uuid,uuid,text,boolean,jsonb)')
)
select
    (select name from supabase_migrations.schema_migrations
      where version::text = '20260726171004') as ledger_name,
    to_regclass('public.web_registration_requests') is not null as requests_table,
    to_regclass('public.web_registration_rate_limits') is not null as limits_table,
    to_regclass('public.web_registration_cleanup_events') is not null as cleanup_table,
    to_regprocedure('public.quata_claim_web_registration(text,text,text,text,text)') is not null as claim_function,
    to_regprocedure('public.quata_web_registration_auth_user(text)') is not null as auth_lookup_function,
    to_regprocedure('public.quata_claim_web_registration_cleanup(uuid,text)') is not null as cleanup_claim_function,
    to_regprocedure('public.quata_finish_web_registration_cleanup(uuid,uuid,text,boolean,jsonb)') is not null as cleanup_finish_function,
    exists(select 1 from information_schema.columns where table_schema='public'
      and table_name='community_profiles' and column_name='secret_answer_hash') as secret_answer_hash,
    (select bool_and(c.relrowsecurity) from pg_class c
      where c.oid in ('public.web_registration_requests'::regclass,
        'public.web_registration_rate_limits'::regclass,
        'public.web_registration_cleanup_events'::regclass)) as all_rls_enabled,
    not exists (
        select 1
        from required_service_table_acl acl
        where not has_table_privilege(
            'service_role',
            format('public.%I', acl.table_name),
            acl.privilege_name
        )
    ) as service_table_acl_complete,
    not exists (
        select 1
        from untrusted_roles roles
        cross join registration_tables tables
        cross join table_privileges privileges
        where has_table_privilege(
            roles.role_name,
            format('public.%I', tables.table_name),
            privileges.privilege_name
        )
    ) as untrusted_table_acl_denied,
    not exists (
        select 1 from registration_functions functions
        where not has_function_privilege('service_role', functions.function_signature, 'EXECUTE')
    ) as service_function_acl_complete,
    not exists (
        select 1
        from untrusted_roles roles
        cross join registration_functions functions
        where has_function_privilege(roles.role_name, functions.function_signature, 'EXECUTE')
    ) as untrusted_function_acl_denied,
    (select count(*)::int from public.web_registration_requests) as request_rows,
    (select count(*)::int from public.web_registration_rate_limits) as rate_limit_rows,
    (select count(*)::int from public.web_registration_cleanup_events) as cleanup_event_rows;
