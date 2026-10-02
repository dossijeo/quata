\set ON_ERROR_STOP on

create role anon nologin;
create role authenticator noinherit login password 'quata';
grant anon to authenticator;

create table public.community_comments (
    id uuid primary key,
    post_id uuid not null,
    profile_id uuid not null,
    body text not null,
    created_at timestamptz not null
);

create table public.official_post_comments (
    id uuid primary key,
    official_post_id uuid not null,
    profile_id uuid not null,
    body text not null,
    created_at timestamptz not null,
    deleted_at timestamptz
);

grant select on public.community_comments, public.official_post_comments to anon;

insert into public.community_comments(id, post_id, profile_id, body, created_at)
select
    ('10000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid,
    'aaaaaaaa-0000-4000-8000-000000000001'::uuid,
    'aaaaaaaa-0000-4000-8000-000000000002'::uuid,
    'community ' || value,
    timestamptz '2026-10-02 00:00:00+00' + ((1206 - value) * interval '1 second')
from generate_series(1, 1205) value;

insert into public.official_post_comments(id, official_post_id, profile_id, body, created_at, deleted_at)
select
    ('20000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid,
    'bbbbbbbb-0000-4000-8000-000000000001'::uuid,
    'bbbbbbbb-0000-4000-8000-000000000002'::uuid,
    'official ' || value,
    timestamptz '2026-10-02 00:00:00+00' + ((1207 - value) * interval '1 second'),
    case when value = 1206 then timestamptz '2026-10-02 12:00:00+00' end
from generate_series(1, 1206) value;

select 'COMMENTS_DEEP_PAGINATION_SQL_OK';
