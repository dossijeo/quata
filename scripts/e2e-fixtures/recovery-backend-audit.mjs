const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// Read-only guard before login/reset. Owned IDs must come from authenticated
// response receipts, never from a time-window difference of all actor sessions.
export async function auditRecoveryActor({client,profileId,authUserId,ownedAuthSessionIds=[]}) {
  if (!uuid.test(profileId) || !uuid.test(authUserId) || !Array.isArray(ownedAuthSessionIds) ||
      ownedAuthSessionIds.some(id=>!uuid.test(id))) throw Error("recovery_audit_identity_required");
  let result;
  try {
    result=await client.query(`select p.account_status,
      (select count(*)::int from public.community_profiles linked where linked.auth_user_id=p.auth_user_id) as linked_profiles,
      (select count(*)::int from auth.sessions s where s.user_id=p.auth_user_id and not (s.id=any($3::uuid[]))) as unowned_auth_sessions,
      (select count(*)::int from public.web_client_sessions w where w.profile_id=p.id and w.revoked_at is null) as unrevoked_web_sessions
      from public.community_profiles p where p.id=$1::uuid and p.auth_user_id=$2::uuid`,[profileId,authUserId,ownedAuthSessionIds]);
  } catch {throw Error("recovery_actor_audit_failed");}
  if(result.rowCount!==1)return {eligible:false,reason:"actor_identity_mismatch"};
  const row=result.rows[0];
  if(row.account_status!=="active" || row.linked_profiles!==1)return {eligible:false,reason:"actor_not_active_or_unique"};
  if(!Number.isSafeInteger(row.unowned_auth_sessions) || row.unowned_auth_sessions<0 ||
      !Number.isSafeInteger(row.unrevoked_web_sessions) || row.unrevoked_web_sessions<0)throw Error("recovery_actor_audit_invalid");
  return {eligible:row.unowned_auth_sessions===0,reason:row.unowned_auth_sessions===0?"no_unowned_auth_sessions":"existing_auth_sessions",
    unownedAuthSessions:row.unowned_auth_sessions,unrevokedWebSessions:row.unrevoked_web_sessions};
}
