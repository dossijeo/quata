\set ON_ERROR_STOP on

create role anon nologin;
create role authenticated nologin;
grant anon, authenticated to current_user;

create extension if not exists pgcrypto;

create schema auth;
create function auth.uid()
returns uuid
language sql
stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'
    )::uuid
$$;

create table public.community_profiles (
    id uuid primary key,
    auth_user_id uuid unique,
    account_status text not null
);

insert into public.community_profiles (id, auth_user_id, account_status) values
    ('00000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 'active'),
    ('00000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002', 'active'),
    ('00000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000003', 'inactive');

create table public.community_post_likes (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null,
    profile_id uuid not null,
    created_at timestamptz not null default now(),
    unique (post_id, profile_id)
);

create function public.quata_chat_auth_profile_id()
returns uuid
language sql
stable
security definer
set search_path = public, auth
as $$
    select cp.id
      from public.community_profiles cp
     where auth.uid() is not null
       and cp.account_status = 'active'
       and (cp.id = auth.uid() or cp.auth_user_id = auth.uid())
     limit 1
$$;
grant execute on function public.quata_chat_auth_profile_id() to authenticated;

alter table public.community_post_likes enable row level security;
create policy "public delete likes" on public.community_post_likes
for delete to public using (true);
create policy "public insert likes" on public.community_post_likes
for insert to public with check (true);
create policy "public read likes" on public.community_post_likes
for select to public using (true);
grant delete, insert, references, select, trigger, truncate, update
on table public.community_post_likes to anon, authenticated;

\ir ../../supabase/migrations/20261002010000_community_post_likes_actor_guard.sql
