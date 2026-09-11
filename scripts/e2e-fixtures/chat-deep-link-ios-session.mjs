import {randomUUID,createHash} from "node:crypto";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// Private return value: send through stdin to the exclusive native coordinator,
// never stdout, argv, environment or the public report. This performs no login.
// Importing a verified bridge session does not certify the native login UI.
export async function prepareIosDeepLinkSession({client,journal,record,ticket,session,
  backendUrl,publicKey,fetchImpl=fetch,now=()=>Math.floor(Date.now()/1000)}) {
  try {
    const root=new URL(backendUrl);
    if(root.protocol!=="https:"||root.username||root.password||root.pathname!=="/"||root.search||root.hash)throw Error();
    const saved=await journal.read();
    if(["runId","profileId","authUserId"].some(key=>!uuid.test(record[key])||saved[key]!==record[key]||ticket[key]!==record[key])||
       ticket.purpose!=="deep_link"||ticket.kind!==undefined||ticket.ticketId!==undefined||typeof ticket.clientInstanceId!=="string"||ticket.clientInstanceId.length<8||
       !uuid.test(ticket.authSessionId)||!uuid.test(ticket.webSessionId))throw Error();
    const matches=saved.state.sessions.filter(entry=>entry.clientInstanceId===ticket.clientInstanceId);
    if(matches.length!==1)throw Error();
    const entry=matches[0];
    if(["runId","profileId","authUserId","purpose","authSessionId","webSessionId"].some(key=>entry[key]!==ticket[key])||
       entry.requestStarted!==true||entry.noSession!==undefined||entry.refreshAttempt!==undefined||entry.revocation!==undefined||
       entry.kind!==undefined||entry.ticketId!==undefined||entry.privateLoginResponse?.status!==200)throw Error();
    const body=entry.privateLoginResponse.body;
    if(body.profile?.id!==record.profileId||body.profile?.auth_user_id!==record.authUserId||
       body.user?.id!==record.authUserId||session.profileId!==record.profileId||
       typeof session.accessToken!=="string"||!session.accessToken||typeof session.refreshToken!=="string"||!session.refreshToken||
       typeof session.webSessionToken!=="string"||!session.webSessionToken||
       body.session?.access_token!==session.accessToken||body.session?.refresh_token!==session.refreshToken||
       body.session?.expires_at!==session.expiresAt||body.web_session?.token!==session.webSessionToken)throw Error();
    if(session.accessToken.split(".").length!==3)throw Error();
    const claims=JSON.parse(Buffer.from(session.accessToken.split(".")[1],"base64url").toString("utf8"));
    if(claims.sub!==record.authUserId||claims.session_id!==ticket.authSessionId||claims.exp!==session.expiresAt||
       !Number.isSafeInteger(session.expiresAt)||session.expiresAt<=now()+900)throw Error();
    const verified=await fetchImpl(new URL("/auth/v1/user",root),{headers:{apikey:publicKey,
      Authorization:`Bearer ${session.accessToken}`},signal:AbortSignal.timeout(10000)});
    if(!verified.ok||(await verified.json()).id!==record.authUserId)throw Error();
    const owner=await client.query(`select s.id as auth_session_id, w.id as web_session_id
      from auth.sessions s join auth.users u on u.id=s.user_id
      join public.community_profiles p on p.auth_user_id=u.id
      join public.web_client_sessions w on w.profile_id=p.id and w.auth_user_id=u.id
      where s.id=$1::uuid and u.id=$2::uuid and p.id=$3::uuid
        and w.id=$4::uuid and w.client_instance_id=$5 and w.token_hash=$6 and w.revoked_at is null
        and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
        and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$7 and p.account_status='active'`,
      [ticket.authSessionId,record.authUserId,record.profileId,ticket.webSessionId,ticket.clientInstanceId,
       createHash("sha256").update(session.webSessionToken).digest("hex"),record.runId]);
    if(owner.rowCount!==1||owner.rows[0].auth_session_id!==ticket.authSessionId||owner.rows[0].web_session_id!==ticket.webSessionId)throw Error();
    // Same metadata mapping as toIosAuthSession, with strict bridge response
    // fields for this disposable-fixture lane rather than fabricated fallbacks.
    if(typeof body.user.email!=="string"||!body.user.email||typeof body.profile.display_name!=="string"||!body.profile.display_name||session.expiresAt<=now()+900)throw Error();
    return {runId:record.runId,stepId:randomUUID(),stage:"install",profileId:record.profileId,
      authUserId:record.authUserId,authSessionId:ticket.authSessionId,accessToken:session.accessToken,
      refreshToken:session.refreshToken,expiresAt:session.expiresAt,email:body.user.email,
      displayName:body.profile.display_name,isOfficial:body.profile.is_official===true};
  } catch {throw Error("deep_link_ios_session_unverified");}
}
