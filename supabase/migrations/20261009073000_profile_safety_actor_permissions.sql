begin;

revoke all on function public.quata_ugc_report(uuid,text,text,text,text) from public, anon, authenticated;
revoke all on function public.quata_profile_block(uuid,uuid) from public, anon, authenticated;
revoke all on function public.quata_profile_unblock(uuid,uuid) from public, anon, authenticated;

grant execute on function public.quata_ugc_report(uuid,text,text,text,text) to authenticated;
grant execute on function public.quata_profile_block(uuid,uuid) to authenticated;
grant execute on function public.quata_profile_unblock(uuid,uuid) to authenticated;

revoke insert, update, delete, truncate, references, trigger
on table public.ugc_reports, public.chat_profile_blocks
from anon, authenticated;

revoke select on table public.ugc_reports, public.chat_profile_blocks from anon;
grant select on table public.ugc_reports, public.chat_profile_blocks to authenticated;

revoke all on sequence public.ugc_reports_id_seq from anon, authenticated;

commit;
