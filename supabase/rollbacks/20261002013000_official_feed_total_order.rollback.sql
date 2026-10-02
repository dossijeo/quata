begin;

drop function if exists public.quata_official_feed_page(integer, timestamptz, timestamptz, uuid);
drop index if exists public.official_posts_public_total_order_idx;

commit;
