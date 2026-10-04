\set ON_ERROR_STOP on

\ir ../../supabase/rollbacks/20261004113000_community_feed_total_order.rollback.sql

do $$
begin
    if to_regprocedure('public.quata_community_feed_page(integer,timestamp with time zone,uuid)') is not null then
        raise exception 'community feed function survived rollback';
    end if;
    if to_regclass('public.community_posts_public_total_order_idx') is not null then
        raise exception 'community feed index survived rollback';
    end if;
end;
$$;

\ir ../../supabase/migrations/20261004113000_community_feed_total_order.sql

do $$
begin
    if to_regprocedure('public.quata_community_feed_page(integer,timestamp with time zone,uuid)') is null then
        raise exception 'community feed function missing after reapply';
    end if;
    if to_regclass('public.community_posts_public_total_order_idx') is null then
        raise exception 'community feed index missing after reapply';
    end if;
end;
$$;

set role anon;
do $$
declare
    visible_count integer;
begin
    select count(*) into visible_count
      from public.quata_community_feed_page(100, null, null);
    if visible_count <> 100 then
        raise exception 'unexpected reapply page size: %', visible_count;
    end if;
end;
$$;
reset role;

select 'COMMUNITY_FEED_TOTAL_ORDER_ROLLBACK_REAPPLY_OK';
