begin;

drop function if exists public.quata_community_feed_page(integer, timestamptz, uuid);
drop index if exists public.community_posts_public_total_order_idx;

commit;
