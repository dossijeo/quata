alter table public.official_posts
    add column if not exists read_more_label text not null default 'Leer mas';

comment on column public.official_posts.read_more_label is
    'Custom label shown on official feed cards for opening the full rich-text story.';
