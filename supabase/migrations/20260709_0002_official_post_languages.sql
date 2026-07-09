-- Official post language variants.
-- Existing rows become the Spanish/default variant of their own translation group.

create extension if not exists pgcrypto;

alter table public.official_posts
    add column if not exists language text not null default 'es',
    add column if not exists translation_group_id uuid;

alter table public.official_posts
    alter column read_more_label set default 'read_more';

update public.official_posts
set language = coalesce(nullif(lower(language), ''), 'es')
where language is distinct from coalesce(nullif(lower(language), ''), 'es');

update public.official_posts
set translation_group_id = id
where translation_group_id is null;

alter table public.official_posts
    alter column translation_group_id set not null;

do $$
begin
    alter table public.official_posts
        add constraint official_posts_language_check
        check (language in ('es', 'en', 'fr'));
exception when duplicate_object then null;
end $$;

create index if not exists official_posts_language_published_idx
    on public.official_posts(language, published_at desc, created_at desc)
    where is_published = true and deleted_at is null;

create index if not exists official_posts_translation_group_idx
    on public.official_posts(translation_group_id);

create unique index if not exists official_posts_translation_group_language_live_idx
    on public.official_posts(translation_group_id, language)
    where deleted_at is null;

create or replace function public.quata_normalize_official_post_language()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    new.language = coalesce(nullif(lower(new.language), ''), 'es');
    new.translation_group_id = coalesce(new.translation_group_id, new.id);
    return new;
end;
$$;

drop trigger if exists quata_normalize_official_post_language_trg on public.official_posts;
create trigger quata_normalize_official_post_language_trg
before insert or update on public.official_posts
for each row execute function public.quata_normalize_official_post_language();

comment on column public.official_posts.language is
    'Language variant for official posts: es, en or fr. Clients should request the interface language and fallback to es.';

comment on column public.official_posts.translation_group_id is
    'Groups the language variants of the same official publication.';

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

drop policy if exists official_posts_public_read_language on public.official_posts;
create policy official_posts_public_read_language
on public.official_posts
for select
to anon, authenticated
using (
    (
        is_published = true
        and deleted_at is null
        and (
            language = 'es'
            or language = public.quata_requested_official_post_language()
        )
    )
    or (
        auth.role() = 'authenticated'
        and (
            profile_id = public.quata_current_profile_id()
            or public.quata_current_profile_is_admin()
        )
    )
);

drop policy if exists official_posts_authenticated_insert on public.official_posts;
create policy official_posts_authenticated_insert
on public.official_posts
for insert
to authenticated
with check (true);

drop policy if exists official_posts_authenticated_update_guarded on public.official_posts;
create policy official_posts_authenticated_update_guarded
on public.official_posts
for update
to authenticated
using (true)
with check (true);

drop policy if exists official_posts_authenticated_delete_guarded on public.official_posts;
create policy official_posts_authenticated_delete_guarded
on public.official_posts
for delete
to authenticated
using (true);
