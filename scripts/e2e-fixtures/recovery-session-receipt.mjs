import {createHash} from "node:crypto";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// Native login creates an Auth session, without a Web client/session identity.
export async function recordRecoveryNativeSession({client,journal,ticket,accessToken,backendUrl,publicKey,fetchImpl=fetch}) {
  try {
    if(ticket?.kind!=="native" || ![ticket.runId,ticket.profileId,ticket.authUserId,ticket.ticketId].every(id=>uuid.test(id)) ||
        ticket.clientInstanceId!==undefined || ticket.webSessionId!==undefined || typeof accessToken!=="string")throw Error();
    const url=new URL(backendUrl);
    if(url.protocol!=="https:" || url.username || url.password || url.pathname!=="/" || url.search || url.hash)throw Error();
    const response=await fetchImpl(new URL("/auth/v1/user",url),{headers:{apikey:publicKey,Authorization:`Bearer ${accessToken}`},signal:AbortSignal.timeout(10000)});
    if(!response.ok)throw Error();
    const user=await response.json();
    const claims=JSON.parse(Buffer.from(accessToken.split(".")[1],"base64url").toString("utf8"));
    if(user.id!==ticket.authUserId || claims.sub!==ticket.authUserId || !uuid.test(claims.session_id))throw Error();
    const found=await client.query(`select s.id as auth_session_id
      from auth.sessions s join public.community_profiles p on p.auth_user_id=s.user_id
      where s.id=$1::uuid and s.user_id=$2::uuid and p.id=$3::uuid`,
      [claims.session_id,ticket.authUserId,ticket.profileId]);
    if(found.rowCount!==1 || found.rows[0].auth_session_id!==claims.session_id)throw Error();
    const record=await journal.read();
    if(["runId","profileId","authUserId"].some(key=>record[key]!==ticket[key]))throw Error();
    const matches=record.state.sessions.filter(entry=>entry.kind==="native" && entry.ticketId===ticket.ticketId);
    if(record.state.sessions.some(entry=>entry.authSessionId===claims.session_id))throw Error();
    if(matches.length!==1 || ["runId","profileId","authUserId","purpose"].some(key=>matches[0][key]!==ticket[key]) ||
        matches[0].clientInstanceId!==undefined || matches[0].noSession!==undefined || matches[0].authSessionId || matches[0].webSessionId)throw Error();
    const receipt={authSessionId:claims.session_id};
    Object.assign(matches[0],receipt);
    await journal.checkpoint(record.state);
    Object.assign(ticket,receipt);
    return true;
  } catch {throw Error("recovery_native_session_receipt_unverified");}
}

export function createRecoveryWebActorVerifier({client,journal,backendUrl,publicKey,fetchImpl=fetch}) {
  return async(record,ticket,credentials)=>{
    if(credentials?.profileId!==record.profileId ||
        ["runId","profileId","authUserId"].some(key=>record[key]!==ticket[key])) throw Error("recovery_session_actor_mismatch");
    return recordRecoverySession({client,journal,ticket,backendUrl,publicKey,fetchImpl,
      accessToken:credentials.accessToken,webSessionToken:credentials.webSessionToken});
  };
}

// Tokens stay in memory. Only exact, verified session IDs enter the private journal.
export async function recordRecoverySession({client,journal,ticket,accessToken,webSessionToken,backendUrl,publicKey,fetchImpl=fetch}) {
  try {
    if(![ticket?.runId,ticket?.profileId,ticket?.authUserId].every(id=>uuid.test(id)) ||
        typeof ticket.clientInstanceId!=="string" || ticket.clientInstanceId.length<8 ||
        typeof accessToken!=="string" || typeof webSessionToken!=="string" || !webSessionToken)throw Error();
    const url=new URL(backendUrl);
    if(url.protocol!=="https:" || url.username || url.password || url.pathname!=="/" || url.search || url.hash)throw Error();
    // Decoding alone is not authentication: validate this very token at Auth first.
    const response=await fetchImpl(new URL("/auth/v1/user",url),{headers:{apikey:publicKey,Authorization:`Bearer ${accessToken}`},signal:AbortSignal.timeout(10000)});
    if(!response.ok)throw Error();
    const user=await response.json();
    const claims=JSON.parse(Buffer.from(accessToken.split(".")[1],"base64url").toString("utf8"));
    if(user.id!==ticket.authUserId || claims.sub!==ticket.authUserId || !uuid.test(claims.session_id))throw Error();
    const tokenHash=createHash("sha256").update(webSessionToken).digest("hex");
    const found=await client.query(`select s.id as auth_session_id,w.id as web_session_id
      from auth.sessions s join public.community_profiles p on p.auth_user_id=s.user_id
      join public.web_client_sessions w on w.profile_id=p.id and w.auth_user_id=s.user_id
      where s.id=$1::uuid and s.user_id=$2::uuid and p.id=$3::uuid
        and w.client_instance_id=$4 and w.token_hash=$5 and w.revoked_at is null`,
      [claims.session_id,ticket.authUserId,ticket.profileId,ticket.clientInstanceId,tokenHash]);
    if(found.rowCount!==1 || !uuid.test(found.rows[0].web_session_id))throw Error();
    const record=await journal.read();
    if(["runId","profileId","authUserId"].some(key=>record[key]!==ticket[key]))throw Error();
    const matches=record.state.sessions.filter(entry=>entry.clientInstanceId===ticket.clientInstanceId);
    if(matches.length!==1 || ["runId","profileId","authUserId","purpose"].some(key=>matches[0][key]!==ticket[key]) ||
        matches[0].noSession!==undefined || matches[0].authSessionId || matches[0].webSessionId)throw Error();
    const receipt={authSessionId:claims.session_id,webSessionId:found.rows[0].web_session_id};
    Object.assign(matches[0],receipt);
    await journal.checkpoint(record.state);
    // The core retains this same ticket object; update it only after durable receipt.
    Object.assign(ticket,receipt);
    return true;
  } catch {throw Error("recovery_session_receipt_unverified");}
}
