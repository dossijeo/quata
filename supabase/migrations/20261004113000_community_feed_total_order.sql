-- Page the public community feed with a complete, stable order tuple.
-- Partial cursors fail closed; row visibility continues to be decided by RLS.

begin;

create index if not exists community_posts_public_total_order_idx
    on public.community_posts (created_at desc, id desc);

create or replace function public.quata_community_feed_page(
    p_limit integer default 50,
    p_before_created_at timestamptz default null,
    p_before_id uuid default null
)
returns setof public.community_posts
language plpgsql
stable
security invoker
set search_path = public, pg_temp
as $$
declare
    page_limit integer := greatest(1, least(coalesce(p_limit, 50), 100));
    cursor_values integer :=
        (p_before_created_at is not null)::integer
        + (p_before_id is not null)::integer;
begin
    if cursor_values not in (0, 2) then
        raise exception using
            errcode = '22023',
            message = 'community_feed_cursor_incomplete';
    end if;

    return query
    select post.*
      from public.community_posts post
     where cursor_values = 0
        or (post.created_at, post.id) < (p_before_created_at, p_before_id)
     order by post.created_at desc, post.id desc
     limit page_limit;
end;
$$;

revoke all on function public.quata_community_feed_page(integer, timestamptz, uuid)
    from public, anon, authenticated;
grant execute on function public.quata_community_feed_page(integer, timestamptz, uuid)
    to anon, authenticated;

commit;
