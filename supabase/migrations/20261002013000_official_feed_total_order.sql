-- Page the public Official feed after choosing one language variant per group.
-- The cursor is the complete stable order tuple; partial cursors fail closed.

begin;

create index if not exists official_posts_public_total_order_idx
    on public.official_posts (
        language,
        (coalesce(published_at, created_at)) desc,
        created_at desc,
        id desc
    )
    include (translation_group_id)
    where is_published = true and deleted_at is null;

create or replace function public.quata_official_feed_page(
    p_limit integer default 50,
    p_before_sort_at timestamptz default null,
    p_before_created_at timestamptz default null,
    p_before_id uuid default null
)
returns setof public.official_posts
language plpgsql
stable
security invoker
set search_path = public, pg_temp
as $$
declare
    requested_language text := public.quata_requested_official_post_language();
    page_limit integer := greatest(1, least(coalesce(p_limit, 50), 100));
    cursor_values integer :=
        (p_before_sort_at is not null)::integer
        + (p_before_created_at is not null)::integer
        + (p_before_id is not null)::integer;
begin
    if cursor_values not in (0, 3) then
        raise exception using
            errcode = '22023',
            message = 'official_feed_cursor_incomplete';
    end if;

    return query
    with ranked as (
        select op as post,
               row_number() over (
                   partition by op.translation_group_id
                   order by
                       case
                           when op.language = requested_language then 0
                           when op.language = 'es' then 1
                           else 2
                       end,
                       coalesce(op.published_at, op.created_at) desc,
                       op.created_at desc,
                       op.id desc
               ) as language_rank
          from public.official_posts op
         where op.is_published = true
           and op.deleted_at is null
           and (
               (requested_language = 'es' and op.language = 'es')
               or
               (requested_language <> 'es' and op.language in (requested_language, 'es'))
           )
    ), chosen as (
        select (ranked.post).*
          from ranked
         where ranked.language_rank = 1
    )
    select chosen.*
      from chosen
     where cursor_values = 0
        or (
            coalesce(chosen.published_at, chosen.created_at),
            chosen.created_at,
            chosen.id
        ) < (
            p_before_sort_at,
            p_before_created_at,
            p_before_id
        )
     order by
        coalesce(chosen.published_at, chosen.created_at) desc,
        chosen.created_at desc,
        chosen.id desc
     limit page_limit;
end;
$$;

revoke all on function public.quata_official_feed_page(integer, timestamptz, timestamptz, uuid)
    from public, anon, authenticated;
grant execute on function public.quata_official_feed_page(integer, timestamptz, timestamptz, uuid)
    to anon, authenticated;

commit;
