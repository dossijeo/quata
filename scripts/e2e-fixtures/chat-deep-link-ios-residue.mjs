import {iosDeepLinkCustodySettled} from './chat-deep-link-ios-custody.mjs';

// Current iOS APNs has no upload sink; WhatsNew seen state is local. Unexpected
// remote registration rows therefore require diagnosis, not an Android delete.
export async function verifyIosDeepLinkResidueAbsent({client,journal,record,operationsSettled}) {
  const saved=await journal.read();
  if(['runId','profileId','authUserId'].some(key=>saved[key]!==record[key])||
    saved.state.profileCreated!==true||!saved.state.sessions.some(entry=>entry.iosNativeLogin)||
    !saved.state.sessions.every(entry=>iosDeepLinkCustodySettled(entry))||await operationsSettled()!==true)
    throw Error('deep_link_ios_residue_not_ready');
  const result=await client.query(`select
    (select count(*) from public.community_profiles p join auth.users u on u.id=p.auth_user_id
      where p.id=$1::uuid and u.id=$2::uuid and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
      and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$3)=1 as owned,
    not exists(select 1 from public.push_tokens where user_id=$1::uuid or auth_user_id=$2::uuid) as push,
    not exists(select 1 from public.user_app_release_state where user_id=$1::uuid) as release`,
    [record.profileId,record.authUserId,record.runId]);
  if(result.rows?.length!==1||['owned','push','release'].some(key=>result.rows[0][key]!==true))
    throw Error('deep_link_ios_residue_unresolved');
  return {verifiedAbsent:true};
}
