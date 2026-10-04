\set ON_ERROR_STOP on

create role anon nologin;
create role authenticated nologin;
create role authenticator noinherit login password 'quata';
grant anon, authenticated to authenticator;

create table public.community_posts (
    id uuid primary key,
    wall_id uuid not null,
    profile_id uuid not null,
    body text,
    image_url text,
    video_url text,
    created_at timestamptz not null,
    community_id uuid,
    author_id uuid,
    content text
);

alter table public.community_posts enable row level security;
create policy community_posts_public_read on public.community_posts
for select to anon, authenticated using (true);
grant select on public.community_posts to anon, authenticated;

\ir ../../supabase/migrations/20261004113000_community_feed_total_order.sql

insert into public.community_posts(id, wall_id, profile_id, body, created_at)
select
    ('10000000-0000-4000-8000-' || lpad(to_hex(sequence), 12, '0'))::uuid,
    '20000000-0000-4000-8000-000000000001'::uuid,
    '30000000-0000-4000-8000-000000000001'::uuid,
    'deep ' || sequence,
    '2026-10-04 10:00:00+00'::timestamptz
from generate_series(1, 101) as sequence;

set role anon;

do $$
declare
    first_cursor_id uuid;
    second_cursor_id uuid;
    first_count integer;
    second_count integer;
    final_count integer;
begin
    select count(*) into first_count
      from public.quata_community_feed_page(50, null, null);
    select id into first_cursor_id
      from public.quata_community_feed_page(50, null, null)
     order by id asc limit 1;
    if first_count <> 50 then raise exception 'unexpected first page size: %', first_count; end if;

    select count(*) into second_count
      from public.quata_community_feed_page(50, '2026-10-04 10:00:00+00', first_cursor_id);
    select id into second_cursor_id
      from public.quata_community_feed_page(50, '2026-10-04 10:00:00+00', first_cursor_id)
     order by id asc limit 1;
    if second_count <> 50 then raise exception 'unexpected second page size: %', second_count; end if;

    select count(*) into final_count
      from public.quata_community_feed_page(50, '2026-10-04 10:00:00+00', second_cursor_id);
    if final_count <> 1 then raise exception 'unexpected final page size: %', final_count; end if;
end;
$$;

do $$
begin
    perform public.quata_community_feed_page(2, now(), null);
    raise exception 'partial cursor was accepted';
exception when sqlstate '22023' then null;
end;
$$;

reset role;
select 'COMMUNITY_FEED_TOTAL_ORDER_SQL_OK';
