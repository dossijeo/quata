-- Restore the database contract that existed before APNs token registration.
-- The Edge Function must first be rolled back to a bundle that does not query
-- apns_environment. Refuse to discard registrations or an unexpected schema.
begin;

lock table public.push_tokens in share row exclusive mode;
lock table public.push_delivery_log in share row exclusive mode;

do $$
declare
    v_register regprocedure := to_regprocedure(
        'public.quata_register_apns_token(uuid,text,text)'
    );
    v_reserve regprocedure := to_regprocedure(
        'public.quata_reserve_apns_delivery(bigint,uuid,uuid,text,timestamp with time zone)'
    );
    v_register_definition_md5 text;
    v_reserve_definition_md5 text;
    v_dependency_rows integer;
    v_dependency_objects integer;
    v_expected_dependency_rows integer;
begin
    if v_register is null or v_reserve is null then
        raise exception 'APNs rollback anchor missing';
    end if;

    if not exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'push_tokens'
          and column_name = 'apns_environment'
          and data_type = 'text'
          and is_nullable = 'YES'
    ) then
        raise exception 'APNs rollback column anchor mismatch';
    end if;

    select
        count(*),
        count(distinct (d.classid::text || ':' || d.objid::text || ':' || d.objsubid::text)),
        count(*) filter (
            where d.classid = 'pg_constraint'::regclass
              and c.conname = 'push_tokens_apns_environment_check'
              and c.contype = 'c'
              and pg_get_constraintdef(c.oid) =
                  'CHECK ((apns_environment = ANY (ARRAY[''sandbox''::text, ''production''::text])))'
              and d.deptype in ('a', 'n')
        )
    into v_dependency_rows, v_dependency_objects, v_expected_dependency_rows
    from pg_attribute a
    join pg_depend d
      on d.refobjid = a.attrelid
     and d.refobjsubid = a.attnum
    left join pg_constraint c
      on d.classid = 'pg_constraint'::regclass
     and c.oid = d.objid
    where a.attrelid = 'public.push_tokens'::regclass
      and a.attname = 'apns_environment';

    if v_dependency_rows <> 2
       or v_dependency_objects <> 1
       or v_expected_dependency_rows <> 2 then
        raise exception 'APNs rollback dependency anchor mismatch';
    end if;

    select md5(pg_get_functiondef(v_register)), md5(pg_get_functiondef(v_reserve))
    into v_register_definition_md5, v_reserve_definition_md5;

    if v_register_definition_md5 <> '3376277da104ee0a44192ffb63f7a168'
       or v_reserve_definition_md5 <> '418391a784b91560b94bad6a3286f37f' then
        raise exception 'APNs rollback function fingerprint mismatch';
    end if;

    if (select pg_get_userbyid(proowner) from pg_proc where oid = v_register)
           <> (select pg_get_userbyid(relowner) from pg_class where oid = 'public.push_tokens'::regclass)
       or (select pg_get_userbyid(proowner) from pg_proc where oid = v_reserve)
           <> (select pg_get_userbyid(relowner) from pg_class where oid = 'public.push_tokens'::regclass) then
        raise exception 'APNs rollback owner anchor mismatch';
    end if;

    if not has_function_privilege('authenticated', v_register, 'EXECUTE')
       or has_function_privilege('anon', v_register, 'EXECUTE')
       or has_function_privilege('service_role', v_register, 'EXECUTE')
       or not has_function_privilege('service_role', v_reserve, 'EXECUTE')
       or has_function_privilege('anon', v_reserve, 'EXECUTE')
       or has_function_privilege('authenticated', v_reserve, 'EXECUTE') then
        raise exception 'APNs rollback privilege anchor mismatch';
    end if;

    if exists (
        select 1
        from public.push_tokens
        where platform = 'ios'
           or apns_environment is not null
    ) then
        raise exception 'APNs rollback refused: iOS registrations exist';
    end if;
end;
$$;

drop function public.quata_reserve_apns_delivery(
    bigint, uuid, uuid, text, timestamptz
);
drop function public.quata_register_apns_token(uuid, text, text);
alter table public.push_tokens drop column apns_environment;

commit;
