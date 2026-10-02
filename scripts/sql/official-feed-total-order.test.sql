\set ON_ERROR_STOP on

create role anon nologin;
create role authenticated nologin;
create role authenticator noinherit login password 'quata';
grant anon, authenticated to authenticator;
create schema auth;

create table public.official_posts (
    id uuid primary key,
    profile_id uuid not null,
    language text not null,
    translation_group_id uuid not null,
    title text not null,
    is_published boolean not null default true,
    published_at timestamptz not null,
    created_at timestamptz not null,
    deleted_at timestamptz
);

create or replace function public.quata_requested_official_post_language()
returns text
language sql
stable
as $$
    select case
        when lower(coalesce((nullif(current_setting('request.headers', true), '')::jsonb ->> 'x-quata-official-language'), 'es')) in ('es', 'en', 'fr')
            then lower(coalesce((nullif(current_setting('request.headers', true), '')::jsonb ->> 'x-quata-official-language'), 'es'))
        else 'es'
    end;
$$;

alter table public.official_posts enable row level security;
create policy official_posts_public_read_language on public.official_posts
for select to anon, authenticated
using (
    is_published = true
    and deleted_at is null
    and (language = 'es' or language = public.quata_requested_official_post_language())
);
grant select on public.official_posts to anon, authenticated;

\ir ../../supabase/migrations/20261002013000_official_feed_total_order.sql

insert into public.official_posts(id, profile_id, language, translation_group_id, title, published_at, created_at)
values
('00000000-0000-0000-0000-000000000101','00000000-0000-0000-0000-000000000001','es','00000000-0000-0000-0000-000000000201','grupo 1 es','2026-10-02 09:00:00+00','2026-10-02 08:00:00+00'),
('00000000-0000-0000-0000-000000000102','00000000-0000-0000-0000-000000000001','en','00000000-0000-0000-0000-000000000201','group 1 en','2026-10-02 09:00:00+00','2026-10-02 08:00:01+00'),
('00000000-0000-0000-0000-000000000103','00000000-0000-0000-0000-000000000001','es','00000000-0000-0000-0000-000000000202','grupo 2 es','2026-10-02 09:00:00+00','2026-10-02 07:59:59+00'),
('00000000-0000-0000-0000-000000000104','00000000-0000-0000-0000-000000000001','en','00000000-0000-0000-0000-000000000202','group 2 en','2026-10-02 09:00:00+00','2026-10-02 08:00:00+00'),
('00000000-0000-0000-0000-000000000105','00000000-0000-0000-0000-000000000001','es','00000000-0000-0000-0000-000000000203','grupo 3 es','2026-10-02 09:00:00+00','2026-10-02 08:00:00+00'),
('00000000-0000-0000-0000-000000000106','00000000-0000-0000-0000-000000000001','fr','00000000-0000-0000-0000-000000000203','groupe 3 fr','2026-10-02 09:00:00+00','2026-10-02 08:00:00+00'),
('00000000-0000-0000-0000-000000000107','00000000-0000-0000-0000-000000000001','es','00000000-0000-0000-0000-000000000204','deleted','2026-10-02 10:00:00+00','2026-10-02 10:00:00+00')
;
update public.official_posts set deleted_at=now() where id='00000000-0000-0000-0000-000000000107';

set role anon;
select set_config('request.headers', '{"x-quata-official-language":"en"}', false);

do $$
declare
    first_page uuid[];
    second_page uuid[];
begin
    select array_agg(id order by coalesce(published_at,created_at) desc, created_at desc, id desc)
      into first_page
      from public.quata_official_feed_page(2, null, null, null);
    if first_page is distinct from array[
        '00000000-0000-0000-0000-000000000102'::uuid,
        '00000000-0000-0000-0000-000000000105'::uuid
    ] then raise exception 'unexpected first page: %', first_page; end if;

    select array_agg(id order by coalesce(published_at,created_at) desc, created_at desc, id desc)
      into second_page
      from public.quata_official_feed_page(
          2,
          '2026-10-02 09:00:00+00',
          '2026-10-02 08:00:00+00',
          '00000000-0000-0000-0000-000000000105'
      );
    if second_page is distinct from array[
        '00000000-0000-0000-0000-000000000104'::uuid
    ] then raise exception 'unexpected second page: %', second_page; end if;

    if (select count(*) from public.quata_official_feed_page(10, null, null, null)) <> 3 then
        raise exception 'translation grouping or residue mismatch';
    end if;
end;
$$;

do $$
begin
    perform public.quata_official_feed_page(2, now(), null, null);
    raise exception 'partial cursor was accepted';
exception when sqlstate '22023' then null;
end;
$$;

reset role;
select 'OFFICIAL_FEED_TOTAL_ORDER_SQL_OK';
