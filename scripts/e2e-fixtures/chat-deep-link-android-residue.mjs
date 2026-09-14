import {androidDeepLinkCustodySettled} from "./chat-deep-link-ios-custody.mjs";

// Removes only native registration metadata of a newly-created, journaled Android
// fixture after its device session has been cleared. Never permits broad cascades.
export async function retireAndroidDeepLinkResidue({client,journal,record,operationsSettled}) {
  const saved=await journal.read();
  if(["runId","profileId","authUserId"].some(key=>saved[key]!==record[key])||
    saved.state.profileCreated!==true||!saved.state.sessions.some(s=>s.androidSession||s.androidNativeLogin||s.nativeSessionRenewal?.platform==='android')||
    !saved.state.sessions.every(s=>androidDeepLinkCustodySettled(s))||await operationsSettled()!==true)
    throw Error("deep_link_android_residue_not_ready");
  await client.query("begin");
  try {
    await client.query("set local lock_timeout='5s'");
    const owner=await client.query(`select p.id from public.community_profiles p join auth.users u on u.id=p.auth_user_id
      where p.id=$1::uuid and p.auth_user_id=$2::uuid and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
      and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$3 for update of p,u`,[record.profileId,record.authUserId,record.runId]);
    if(owner.rowCount!==1)throw Error();
    const push=await client.query(`select to_jsonb(t) as row from public.push_tokens t
      where user_id=$1::uuid or auth_user_id=$2::uuid for update`,[record.profileId,record.authUserId]);
    const release=await client.query(`select to_jsonb(t) as row from public.user_app_release_state t where user_id=$1::uuid for update`,[record.profileId]);
    const pushRows=push.rows.map(x=>x.row),releaseRows=release.rows.map(x=>x.row);
    if(pushRows.length>1||releaseRows.length>1||pushRows.some(r=>r.user_id!==record.profileId||r.auth_user_id!==record.authUserId||r.platform!=="android")||
      releaseRows.some(r=>r.user_id!==record.profileId||r.platform!=="android"))throw Error();
    // Fail closed if new referencing tables could cascade unrelated state.
    const refs=await client.query(`select conrelid::regclass::text as child,confrelid::regclass::text as parent,
      pg_get_constraintdef(oid) as definition from pg_constraint where contype='f' and confrelid in
      ('public.push_tokens'::regclass,'public.user_app_release_state'::regclass)`);
    if(refs.rows.length!==1||!['push_delivery_log','public.push_delivery_log'].includes(refs.rows[0].child)||
      !['push_tokens','public.push_tokens'].includes(refs.rows[0].parent)||
      refs.rows[0].definition!=="FOREIGN KEY (push_token_id) REFERENCES push_tokens(id) ON DELETE CASCADE")throw Error();
    const logs=await client.query("select count(*)::text as count from public.push_delivery_log where push_token_id=any($1::uuid[])",[pushRows.map(r=>r.id)]);
    if(logs.rows?.[0]?.count!=="0")throw Error();
    const previous=saved.state.androidResidueCleanup;
    if(previous) {
      // Only a committed removal with a lost local receipt can be reconciled.
      // Existing rows require diagnosis, never a blind second delete.
      if(previous.started!==true||pushRows.length||releaseRows.length)throw Error();
    } else {
      saved.state.androidResidueCleanup={started:true,verified:false,pushRows,releaseRows};
      // FCM token is private: snapshot only through the encrypted journal.
      await journal.checkpoint(saved.state);
      for(const row of pushRows) {
        const result=await client.query("delete from public.push_tokens where id=$1::uuid and user_id=$2::uuid and auth_user_id=$3::uuid and platform='android'",[row.id,record.profileId,record.authUserId]);
        if(result.rowCount!==1)throw Error();
      }
      for(const row of releaseRows) {
        const result=await client.query("delete from public.user_app_release_state where user_id=$1::uuid and platform='android'",[row.user_id]);
        if(result.rowCount!==1)throw Error();
      }
    }
    const absent=await client.query(`select
      not exists(select 1 from public.push_tokens where user_id=$1::uuid or auth_user_id=$2::uuid) as push,
      not exists(select 1 from public.user_app_release_state where user_id=$1::uuid) as release`,[record.profileId,record.authUserId]);
    if(absent.rows?.[0]?.push!==true||absent.rows?.[0]?.release!==true)throw Error();
    await client.query("commit");
  } catch {await client.query("rollback").catch(()=>{});throw Error("deep_link_android_residue_cleanup_unresolved");}
  const current=await journal.read();current.state.androidResidueCleanup.verified=true;await journal.checkpoint(current.state);
  return {removed:true};
}
