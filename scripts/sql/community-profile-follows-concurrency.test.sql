\set ON_ERROR_STOP on

create extension if not exists dblink;

create function public.test_concurrent_follow_insert(
    p_follower uuid,
    p_followed uuid,
    p_delay_seconds double precision
) returns text
language plpgsql
as $$
begin
    insert into public.community_profile_follows (
        follower_profile_id, followed_profile_id
    ) values (p_follower, p_followed);
    perform pg_sleep(p_delay_seconds);
    return 'inserted';
end;
$$;

create function public.test_concurrent_follow_delete(
    p_follower uuid,
    p_followed uuid,
    p_delay_seconds double precision
) returns text
language plpgsql
as $$
begin
    delete from public.community_profile_follows
    where follower_profile_id = p_follower
      and followed_profile_id = p_followed;
    perform pg_sleep(p_delay_seconds);
    return 'deleted';
end;
$$;

delete from public.community_profile_follows;

select dblink_connect(
    'follow_c1',
    'host=127.0.0.1 dbname=postgres user=postgres password=quata-test-only'
);
select dblink_connect(
    'follow_c2',
    'host=127.0.0.1 dbname=postgres user=postgres password=quata-test-only'
);

-- Reciprocal inserts touch the same two profile rows in opposite semantic
-- directions. Deterministic UUID locking must avoid a deadlock and lost count.
select dblink_send_query(
    'follow_c1',
    $$select public.test_concurrent_follow_insert(
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        0.75
    )$$
);
select dblink_send_query(
    'follow_c2',
    $$select public.test_concurrent_follow_insert(
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        0
    )$$
);
select * from dblink_get_result('follow_c1') as t(result text);
select * from dblink_get_result('follow_c2') as t(result text);
select * from dblink_get_result('follow_c1') as t(result text);
select * from dblink_get_result('follow_c2') as t(result text);

do $$
begin
    if (select count(*) from public.community_profile_follows) <> 2
       or exists (
           select 1
           from public.community_profiles
           where id in (
               'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
               'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
           )
             and (followers_count <> 1 or following_count <> 1)
       ) then
        raise exception 'FAIL reciprocal concurrent inserts did not converge';
    end if;
    raise notice 'PASS reciprocal concurrent inserts';
end;
$$;

select dblink_send_query(
    'follow_c1',
    $$select public.test_concurrent_follow_delete(
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        0.75
    )$$
);
select dblink_send_query(
    'follow_c2',
    $$select public.test_concurrent_follow_delete(
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        0
    )$$
);
select * from dblink_get_result('follow_c1') as t(result text);
select * from dblink_get_result('follow_c2') as t(result text);
select * from dblink_get_result('follow_c1') as t(result text);
select * from dblink_get_result('follow_c2') as t(result text);

do $$
begin
    if exists (select 1 from public.community_profile_follows)
       or exists (
           select 1
           from public.community_profiles
           where followers_count <> 0 or following_count <> 0
       ) then
        raise exception 'FAIL reciprocal concurrent deletes did not converge';
    end if;
    raise notice 'PASS reciprocal concurrent deletes';
end;
$$;

-- Two followers concurrently target the same profile.
select dblink_send_query(
    'follow_c1',
    $$select public.test_concurrent_follow_insert(
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        0.75
    )$$
);
select dblink_send_query(
    'follow_c2',
    $$select public.test_concurrent_follow_insert(
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        0
    )$$
);
select * from dblink_get_result('follow_c1') as t(result text);
select * from dblink_get_result('follow_c2') as t(result text);
select * from dblink_get_result('follow_c1') as t(result text);
select * from dblink_get_result('follow_c2') as t(result text);

do $$
begin
    if (select followers_count from public.community_profiles
        where id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc') <> 2
       or (select following_count from public.community_profiles
           where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa') <> 1
       or (select following_count from public.community_profiles
           where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb') <> 1 then
        raise exception 'FAIL shared-target concurrent inserts did not converge';
    end if;
    raise notice 'PASS shared-target concurrent inserts';
end;
$$;

select dblink_disconnect('follow_c1');
select dblink_disconnect('follow_c2');

delete from public.community_profile_follows;

do $$
begin
    if exists (
        select 1 from public.community_profiles
        where followers_count <> 0 or following_count <> 0
    ) then
        raise exception 'FAIL concurrency cleanup counters';
    end if;
end;
$$;

drop function public.test_concurrent_follow_insert(uuid, uuid, double precision);
drop function public.test_concurrent_follow_delete(uuid, uuid, double precision);

\echo COMMUNITY_PROFILE_FOLLOWS_CONCURRENCY_TEST_OK
