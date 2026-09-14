import {isDeepStrictEqual} from 'node:util';
import {prepareIosDeepLinkSession} from './chat-deep-link-ios-session.mjs';
import {validateOwnedNativeSessionReceipt} from './chat-deep-link-owned-session.mjs';

const snapshotKeys = ['profileId','authUserId','authSessionId','accessToken','refreshToken',
  'expiresAt','email','displayName','isOfficial'];
const uuid = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;

// Preparation only: this function performs no native install, refresh, ACK or clear.
// The caller holds the run lock. All returned snapshots and journal fields are private.
export async function prepareNativeDeepLinkExpiry(args) {
  try {
    const platform = args.platform ?? 'ios';
    if (!['ios','android'].includes(platform)) throw Error();
    const before = await args.journal.read();
    if (before.state.sessions.length !== 1) throw Error();
    const entry = before.state.sessions[0];
    if (entry.nativeSessionRenewal !== undefined || entry.iosSession !== undefined ||
        entry.androidSession !== undefined || entry.iosNativeLogin !== undefined ||
        entry.androidNativeLogin !== undefined) throw Error();
    // Reuse all existing Auth/DB/receipt/lifetime checks without weakening install.
    const verified = await prepareIosDeepLinkSession({...args, platform});
    const original = Object.fromEntries(snapshotKeys.map(key => [key, verified[key]]));
    const preparedAt = (args.now ?? (() => Math.floor(Date.now()/1000)))();
    if (!Number.isSafeInteger(preparedAt) || preparedAt < 2 || original.expiresAt <= preparedAt + 900)
      throw Error();
    const expired = {...original, expiresAt: preparedAt - 1};
    const current = await args.journal.read();
    if (!isDeepStrictEqual(current, before)) throw Error();
    const renewal = {platform, phase:'prepared', preparedAt, original, expired};
    current.state.sessions[0].nativeSessionRenewal = renewal;
    await args.journal.checkpoint(current.state);
    // A checkpoint that cannot be read back exactly never authorizes native work.
    const saved = await args.journal.read();
    if (!isDeepStrictEqual(saved, current)) throw Error();
    return structuredClone(renewal);
  } catch { throw Error('deep_link_native_expiry_preparation_unverified'); }
}

// Structural classification only. Never authorizes ACK/clear or proves a refresh
// happened: transport ordering and remote Auth identity still need verification.
export function classifyNativeDeepLinkExpirySnapshot({renewal,input,receipt}) {
  try {
    if (!renewal || !['ios','android'].includes(renewal.platform) || !['prepared','installed','cleared'].includes(renewal.phase) ||
        !Number.isSafeInteger(renewal.preparedAt) || renewal.preparedAt < 2 ||
        !isDeepStrictEqual(renewal.expired, {...renewal.original, expiresAt:renewal.preparedAt-1})) throw Error();
    // Validate original through the existing strict JWT/owner schema, not the
    // deliberately modified local expiry. The original is never overwritten.
    validateOwnedNativeSessionReceipt({input, receipt:{...receipt,privateSession:renewal.original}});
    if (renewal.original.expiresAt <= renewal.preparedAt+900) throw Error();
    const actual = receipt.privateSession;
    if (isDeepStrictEqual(actual, renewal.expired)) return 'expired_snapshot_unchanged';
    validateOwnedNativeSessionReceipt({input,receipt});
    if (['profileId','authUserId','authSessionId','email','displayName','isOfficial']
        .some(key => actual[key] !== renewal.original[key])) throw Error();
    if (isDeepStrictEqual(actual, renewal.original)) return 'original_snapshot';
    // A different token is a candidate, never evidence of one real renewal.
    if (actual.accessToken === renewal.original.accessToken ||
        actual.refreshToken === renewal.original.refreshToken || actual.expiresAt <= renewal.preparedAt+900)
      throw Error();
    return 'renewed_snapshot_unverified';
  } catch { throw Error('deep_link_native_expiry_snapshot_unverified'); }
}

// One private install intent. Native adapters must explicitly implement this new
// command; it cannot be sent as an ordinary valid-session install. Holds no retry.
export async function installNativeDeepLinkExpiry({journal,record,stepId,execute,now=()=>Math.floor(Date.now()/1000)}) {
  try {
    if (!uuid.test(stepId) || typeof execute !== 'function' ||
        ['runId','profileId','authUserId'].some(key=>!uuid.test(record[key]))) throw Error();
    const saved=await journal.read();
    if (['runId','profileId','authUserId'].some(key=>saved[key]!==record[key]) || saved.state.sessions.length!==1) throw Error();
    const entry=saved.state.sessions[0], renewal=entry.nativeSessionRenewal;
    if (['runId','profileId','authUserId'].some(key=>entry[key]!==record[key]) ||
        entry.purpose!=='deep_link' || entry.requestStarted!==true || renewal?.phase!=='prepared' ||
        renewal.install!==undefined || entry.refreshAttempt!==undefined || entry.revocation!==undefined ||
        entry.iosSession!==undefined || entry.androidSession!==undefined ||
        entry.iosNativeLogin!==undefined || entry.androidNativeLogin!==undefined ||
        entry.authSessionId!==renewal.original?.authSessionId) throw Error();
    const readInput={runId:record.runId,profileId:record.profileId,authUserId:record.authUserId,
      stage:'read-owned',stepId};
    classifyNativeDeepLinkExpirySnapshot({renewal,input:readInput,receipt:{runId:record.runId,stepId,
      stage:'read-owned',verified:true,privateSession:renewal.original}});
    const body=entry.privateLoginResponse?.body;
    if (entry.privateLoginResponse?.status!==200 || body?.session?.access_token!==renewal.original.accessToken ||
        body?.session?.refresh_token!==renewal.original.refreshToken || body?.session?.expires_at!==renewal.original.expiresAt)
      throw Error();
    const timestamp=now();
    if (!Number.isSafeInteger(timestamp) || timestamp<renewal.preparedAt ||
        renewal.expired.expiresAt>=timestamp || renewal.original.expiresAt<=timestamp+900) throw Error();
    const input={runId:record.runId,stepId,stage:'install-expired',...renewal.expired,
      originalExpiresAt:renewal.original.expiresAt};
    renewal.install={input,started:true,verified:false};
    await journal.checkpoint(saved.state);
    const durable=await journal.read();
    if (!isDeepStrictEqual(durable,saved)) throw Error();
    const receipt=await execute(structuredClone(input));
    if (!isDeepStrictEqual(receipt,{runId:record.runId,stepId,stage:'install-expired',verified:true})) throw Error();
    const completed=await journal.read();
    if (!isDeepStrictEqual(completed,saved)) throw Error();
    completed.state.sessions[0].nativeSessionRenewal.install.verified=true;
    completed.state.sessions[0].nativeSessionRenewal.phase='installed';
    await journal.checkpoint(completed.state);
    if (!isDeepStrictEqual(await journal.read(),completed)) throw Error();
    return receipt;
  } catch { throw Error('deep_link_native_expiry_install_unresolved'); }
}

// Read after the UI observation. This stores private native output before any
// structural validation, and never ACKs, clears or declares a refresh verified.
export async function readNativeDeepLinkExpiry({journal,record,stepId,execute}) {
  try {
    if (!uuid.test(stepId) || typeof execute!=='function' ||
        ['runId','profileId','authUserId'].some(key=>!uuid.test(record[key]))) throw Error();
    const saved=await journal.read();
    if (['runId','profileId','authUserId'].some(key=>saved[key]!==record[key]) || saved.state.sessions.length!==1) throw Error();
    const entry=saved.state.sessions[0], renewal=entry.nativeSessionRenewal;
    if (['runId','profileId','authUserId'].some(key=>entry[key]!==record[key]) || renewal?.phase!=='installed' ||
        renewal.install?.started!==true || renewal.install.verified!==true || renewal.snapshotRead!==undefined ||
        stepId===renewal.install.input?.stepId || entry.authSessionId!==renewal.original?.authSessionId) throw Error();
    const input={runId:record.runId,profileId:record.profileId,authUserId:record.authUserId,stage:'read-owned',stepId};
    classifyNativeDeepLinkExpirySnapshot({renewal,input,receipt:{runId:record.runId,stepId,stage:'read-owned',
      verified:true,privateSession:renewal.original}});
    if (!isDeepStrictEqual(renewal.install.input,{runId:record.runId,stepId:renewal.install.input.stepId,
      stage:'install-expired',...renewal.expired,originalExpiresAt:renewal.original.expiresAt})) throw Error();
    renewal.snapshotRead={input,started:true,structurallyVerified:false};
    await journal.checkpoint(saved.state);
    if (!isDeepStrictEqual(await journal.read(),saved)) throw Error();
    const receipt=await execute(structuredClone(input));
    const current=await journal.read();
    if (!isDeepStrictEqual(current,saved)) throw Error();
    current.state.sessions[0].nativeSessionRenewal.snapshotRead.privateReceipt=structuredClone(receipt);
    await journal.checkpoint(current.state);
    if (!isDeepStrictEqual(await journal.read(),current)) throw Error();
    const classification=classifyNativeDeepLinkExpirySnapshot({renewal,input,receipt});
    const result=current.state.sessions[0].nativeSessionRenewal.snapshotRead;
    result.structurallyVerified=true;
    result.classification=classification;
    await journal.checkpoint(current.state);
    if (!isDeepStrictEqual(await journal.read(),current)) throw Error();
    return {classification,remoteVerified:false};
  } catch { throw Error('deep_link_native_expiry_read_unresolved'); }
}

// Auth/DB identity verification of the already persisted snapshot. This is a
// read-only check, not observation of the product's refresh transport or its count.
export async function verifyNativeDeepLinkExpiryIdentity({journal,record,client,backendUrl,publicKey,fetchImpl=fetch,
  now=()=>Math.floor(Date.now()/1000)}) {
  try {
    const root=new URL(backendUrl);
    if(root.protocol!=='https:'||root.username||root.password||root.pathname!=='/'||root.search||root.hash||
      typeof publicKey!=='string'||!publicKey||['runId','profileId','authUserId'].some(key=>!uuid.test(record[key])))throw Error();
    const saved=await journal.read();
    if(['runId','profileId','authUserId'].some(key=>saved[key]!==record[key])||saved.state.sessions.length!==1)throw Error();
    const entry=saved.state.sessions[0],renewal=entry.nativeSessionRenewal,read=renewal?.snapshotRead;
    if(['runId','profileId','authUserId'].some(key=>entry[key]!==record[key])||entry.purpose!=='deep_link'||
      entry.requestStarted!==true||renewal?.phase!=='installed'||renewal.install?.verified!==true||
      renewal.remoteIdentity!==undefined||read?.started!==true||read.structurallyVerified!==true||
      read.classification!=='renewed_snapshot_unverified'||entry.authSessionId!==renewal.original?.authSessionId)throw Error();
    const input=read.input,receipt=read.privateReceipt;
    if(['runId','profileId','authUserId'].some(key=>input?.[key]!==record[key])||
      classifyNativeDeepLinkExpirySnapshot({renewal,input,receipt})!=='renewed_snapshot_unverified')throw Error();
    const snapshot=receipt.privateSession;
    const checkedAt=now();
    if(!Number.isSafeInteger(checkedAt)||checkedAt<renewal.preparedAt||snapshot.expiresAt<=checkedAt+120)throw Error();
    renewal.remoteIdentity={started:true,verified:false,stepId:input.stepId};
    await journal.checkpoint(saved.state);
    if(!isDeepStrictEqual(await journal.read(),saved))throw Error();
    const response=await fetchImpl(new URL('/auth/v1/user',root),{method:'GET',redirect:'error',
      headers:{apikey:publicKey,Authorization:`Bearer ${snapshot.accessToken}`},signal:AbortSignal.timeout(10000)});
    if(!response.ok||(await response.json()).id!==record.authUserId)throw Error();
    const found=await client.query(`select s.id as auth_session_id,
      (select count(*)::int from auth.sessions where user_id=$2::uuid) as auth_count
      from auth.sessions s join auth.users u on u.id=s.user_id
      join public.community_profiles p on p.auth_user_id=u.id
      where s.id=$1::uuid and u.id=$2::uuid and p.id=$3::uuid and p.account_status='active'
        and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
        and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$4`,
      [entry.authSessionId,record.authUserId,record.profileId,record.runId]);
    if(found.rowCount!==1||found.rows[0].auth_session_id!==entry.authSessionId||found.rows[0].auth_count!==1)throw Error();
    const current=await journal.read();
    if(!isDeepStrictEqual(current,saved))throw Error();
    current.state.sessions[0].nativeSessionRenewal.remoteIdentity.verified=true;
    await journal.checkpoint(current.state);
    if(!isDeepStrictEqual(await journal.read(),current))throw Error();
    return {identityVerified:true,refreshObserved:false};
  }catch{throw Error('deep_link_native_expiry_identity_unverified');}
}

function verifiedExpiryRead(entry) {
  const renewal=entry.nativeSessionRenewal,read=renewal?.snapshotRead;
  if(['runId','profileId','authUserId','authSessionId','webSessionId'].some(key=>!uuid.test(entry[key]))||
    entry.kind!==undefined||entry.purpose!=='deep_link'||entry.requestStarted!==true||
    typeof entry.clientInstanceId!=='string'||entry.clientInstanceId.length<8||
    !['ios','android'].includes(renewal?.platform)||!['installed','cleared'].includes(renewal.phase)||
    renewal.install?.started!==true||renewal.install.verified!==true||
    read?.started!==true||read.structurallyVerified!==true||read.classification!=='renewed_snapshot_unverified'||
    renewal.remoteIdentity?.started!==true||renewal.remoteIdentity.verified!==true||
    renewal.remoteIdentity.stepId!==read.input?.stepId||entry.authSessionId!==renewal.original?.authSessionId)throw Error();
  const input=read.input,install=renewal.install.input;
  if(!uuid.test(install?.stepId)||install.stepId===input.stepId||
    !isDeepStrictEqual(input,{runId:entry.runId,profileId:entry.profileId,authUserId:entry.authUserId,
      stage:'read-owned',stepId:input.stepId})||
    !isDeepStrictEqual(install,{runId:entry.runId,stepId:install.stepId,stage:'install-expired',
      ...renewal.expired,originalExpiresAt:renewal.original.expiresAt})||
    classifyNativeDeepLinkExpirySnapshot({renewal,input,receipt:read.privateReceipt})!=='renewed_snapshot_unverified')throw Error();
  const original=entry.privateLoginResponse?.body?.session;
  if(entry.privateLoginResponse?.status!==200||original?.access_token!==renewal.original.accessToken||
    original?.refresh_token!==renewal.original.refreshToken||original?.expires_at!==renewal.original.expiresAt)throw Error();
  if(renewal.platform==='ios') {
    const ack=read.acknowledgment;
    if(ack?.started!==true||ack.verified!==true||
      !isDeepStrictEqual(ack.input,{runId:entry.runId,stepId:input.stepId}))throw Error();
  } else if(read.acknowledgment!==undefined)throw Error();
  return read.privateReceipt.privateSession;
}

export async function clearNativeDeepLinkExpiry({journal,record,stepId,execute,operationsSettled}) {
  try {
    if(!uuid.test(stepId)||typeof execute!=='function'||typeof operationsSettled!=='function')throw Error();
    const saved=await journal.read();
    if(['runId','profileId','authUserId'].some(key=>saved[key]!==record[key])||saved.state.sessions.length!==1)throw Error();
    const entry=saved.state.sessions[0],snapshot=verifiedExpiryRead(entry),renewal=entry.nativeSessionRenewal;
    if(['runId','profileId','authUserId'].some(key=>entry[key]!==record[key])||renewal.phase!=='installed'||
      renewal.clear!==undefined||[renewal.install.input.stepId,renewal.snapshotRead.input.stepId].includes(stepId)||
      await operationsSettled()!==true)throw Error();
    if(!isDeepStrictEqual(await journal.read(),saved))throw Error();
    const input={runId:record.runId,stepId,stage:'clear',...snapshot};
    renewal.clear={input,started:true,verified:false};
    await journal.checkpoint(saved.state);
    if(!isDeepStrictEqual(await journal.read(),saved))throw Error();
    const receipt=await execute(structuredClone(input));
    if(!isDeepStrictEqual(receipt,{runId:record.runId,stepId,stage:'clear',verified:true}))throw Error();
    const current=await journal.read();
    if(!isDeepStrictEqual(current,saved))throw Error();
    current.state.sessions[0].nativeSessionRenewal.clear.verified=true;
    current.state.sessions[0].nativeSessionRenewal.phase='cleared';
    await journal.checkpoint(current.state);
    if(!isDeepStrictEqual(await journal.read(),current))throw Error();
    return {cleared:true};
  }catch{throw Error('deep_link_native_expiry_clear_unresolved');}
}

// Device custody only: channel shutdown, remote effects and UI remain separate.
export function nativeDeepLinkExpiryCustodySettled(entry) {
  try {
    const snapshot=verifiedExpiryRead(entry),renewal=entry.nativeSessionRenewal,clear=renewal.clear;
    return renewal.phase==='cleared'&&clear?.started===true&&clear.verified===true&&uuid.test(clear.input?.stepId)&&
      ![renewal.install.input.stepId,renewal.snapshotRead.input.stepId].includes(clear.input.stepId)&&
      isDeepStrictEqual(clear.input,{runId:entry.runId,stepId:clear.input.stepId,stage:'clear',...snapshot});
  }catch{return false;}
}

// ACK retires only the worker's private exchange, not the stored device session.
export async function acknowledgeNativeDeepLinkExpiryRead({journal,record,acknowledge}) {
  try {
    if(typeof acknowledge!=='function'||['runId','profileId','authUserId'].some(key=>!uuid.test(record[key])))throw Error();
    const saved=await journal.read();
    if(['runId','profileId','authUserId'].some(key=>saved[key]!==record[key])||saved.state.sessions.length!==1)throw Error();
    const entry=saved.state.sessions[0],renewal=entry.nativeSessionRenewal,read=renewal?.snapshotRead;
    if(['runId','profileId','authUserId'].some(key=>entry[key]!==record[key])||renewal?.platform!=='ios'||
      renewal.phase!=='installed'||renewal.install?.verified!==true||renewal.remoteIdentity?.started!==true||
      renewal.remoteIdentity.verified!==true||read?.started!==true||read.structurallyVerified!==true||
      read.acknowledgment!==undefined||renewal.remoteIdentity.stepId!==read.input?.stepId||
      entry.authSessionId!==renewal.original?.authSessionId)throw Error();
    if(['runId','profileId','authUserId'].some(key=>read.input[key]!==record[key])||
      classifyNativeDeepLinkExpirySnapshot({renewal,input:read.input,receipt:read.privateReceipt})!=='renewed_snapshot_unverified')throw Error();
    const input={runId:record.runId,stepId:read.input.stepId};
    read.acknowledgment={input,started:true,verified:false};
    await journal.checkpoint(saved.state);
    if(!isDeepStrictEqual(await journal.read(),saved))throw Error();
    const receipt=await acknowledge(structuredClone(input));
    if(!isDeepStrictEqual(receipt,{...input,acknowledged:true}))throw Error();
    const current=await journal.read();
    if(!isDeepStrictEqual(current,saved))throw Error();
    current.state.sessions[0].nativeSessionRenewal.snapshotRead.acknowledgment.verified=true;
    await journal.checkpoint(current.state);
    if(!isDeepStrictEqual(await journal.read(),current))throw Error();
    return {acknowledged:true};
  }catch{throw Error('deep_link_native_expiry_ack_unresolved');}
}
