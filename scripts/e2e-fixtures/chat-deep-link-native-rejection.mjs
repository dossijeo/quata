import {isDeepStrictEqual} from 'node:util';
import {classifyNativeDeepLinkExpirySnapshot} from './chat-deep-link-native-expiry.mjs';
import {prepareRevokedDeepLinkSession} from './chat-deep-link-revoked-session.mjs';

const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const failure=()=>Error('deep_link_native_rejection_preparation_unresolved');
function validWarmPrelude(prelude,platform,runId,target) {
  const receipt=prelude?.receipt;
  if(prelude?.platform!==platform||prelude.mode!=='warm'||prelude.pidPreserved!==true||prelude.suspended!==true||
    !isDeepStrictEqual(prelude.target,{threadId:String(target?.threadId),messageId:String(target?.messageId)})||
    !['threadId','messageId'].every(key=>/^[1-9][0-9]{0,15}$/.test(prelude.target[key]))||
    receipt?.mode!=='cold'||receipt.passed!==true)return false;
  if(platform==='ios')return isDeepStrictEqual(receipt,{runId,stepId:receipt.stepId,mode:'cold',passed:true})&&uuid.test(receipt.stepId);
  return receipt.beforePid===null&&typeof receipt.afterPid==='string'&&receipt.afterPid.length>0&&
    typeof receipt.focusedMessageId==='string'&&typeof receipt.runId==='string'&&receipt.runId.startsWith('chat-cold-')&&uuid.test(receipt.runId.slice(10));
}
function exactRefreshTokenState(value) {
  const parsed=Object.fromEntries(['total','revoked','active'].map(key=>{
    const raw=value?.[key],count=typeof raw==='number'?raw:typeof raw==='string'&&/^\d+$/.test(raw)?Number(raw):Number.NaN;
    if(!Number.isSafeInteger(count)||count<0||count>=Number.MAX_SAFE_INTEGER)throw Error();return [key,count];
  }));
  if(parsed.total<1||parsed.active!==1||parsed.revoked!==parsed.total-1)throw Error();return parsed;
}

// The exact authenticated product process is suspended by the platform adapter
// after its Chat/Back prelude. Waiting therefore cannot rotate or clear its session.
export async function awaitNativeWarmDeepLinkRejectionExpiry({journal,record,ticket,session,platform,prelude,target,client,
  backendUrl,publicKey,fetchImpl=fetch,now=()=>Math.floor(Date.now()/1000),
  sleep=milliseconds=>new Promise(resolve=>setTimeout(resolve,milliseconds)),maxWaitSeconds=7200}) {
  try {
    if(!['android','ios'].includes(platform)||!validWarmPrelude(prelude,platform,record.runId,target)||typeof sleep!=='function'||
      !Number.isSafeInteger(maxWaitSeconds)||maxWaitSeconds<1)throw failure();
    const root=new URL(backendUrl);
    if(root.protocol!=='https:'||root.username||root.password||root.pathname!=='/'||root.search||root.hash||!publicKey)throw failure();
    const saved=await journal.read(),entry=saved.state?.sessions?.[0],custody=entry?.[platform==='android'?'androidSession':'iosSession'];
    if(saved.state.sessions.length!==1||['runId','profileId','authUserId'].some(k=>saved[k]!==record[k]||entry[k]!==record[k])||
      entry.authSessionId!==ticket.authSessionId||entry.webSessionId!==ticket.webSessionId||entry.nativeSessionRejection!==undefined||
      entry.revocation!==undefined||entry.nativeSessionRenewal!==undefined||custody?.install?.started!==true||custody.install.verified!==true||
      custody.clear!==undefined||custody.install.input.stage!=='install'||session.accessToken!==custody.install.input.accessToken||
      session.refreshToken!==custody.install.input.refreshToken||session.expiresAt!==custody.install.input.expiresAt)throw failure();
    const tokenResult=await client.query(`select count(*)::int as total,
      count(*) filter(where revoked is true)::int as revoked,
      count(*) filter(where revoked is not true)::int as active
      from auth.refresh_tokens where session_id=$1::uuid`,[entry.authSessionId]);
    if(tokenResult.rowCount!==1)throw failure();
    const refreshTokenBaseline=exactRefreshTokenState(tokenResult.rows[0]),startedAt=now(),waitSeconds=session.expiresAt-startedAt+2;
    if(!Number.isSafeInteger(startedAt)||waitSeconds<1||waitSeconds>maxWaitSeconds)throw failure();
    entry.nativeSessionRejection={platform,mode:'warm',custodyKind:'valid-session',phase:'expiry-wait',authSessionId:entry.authSessionId,
      installStepId:custody.install.input.stepId,prelude:structuredClone(prelude),refreshTokenBaseline,
      cryptographicExpiry:{started:true,verified:false,startedAt,waitSeconds}};
    await journal.checkpoint(saved.state);if(!isDeepStrictEqual(await journal.read(),saved))throw failure();
    await sleep(waitSeconds*1000);if(!isDeepStrictEqual(await journal.read(),saved))throw failure();
    const checkedAt=now();if(!Number.isSafeInteger(checkedAt)||checkedAt<=session.expiresAt)throw failure();
    const response=await fetchImpl(new URL('/auth/v1/user',root),{method:'GET',redirect:'error',
      headers:{apikey:publicKey,Authorization:`Bearer ${session.accessToken}`},signal:AbortSignal.timeout(10000)});
    if(response.ok||![401,403].includes(response.status))throw failure();
    const after=await client.query(`select count(*)::int as total,
      count(*) filter(where revoked is true)::int as revoked,
      count(*) filter(where revoked is not true)::int as active
      from auth.refresh_tokens where session_id=$1::uuid`,[entry.authSessionId]);
    if(after.rowCount!==1||!isDeepStrictEqual(exactRefreshTokenState(after.rows[0]),refreshTokenBaseline))throw failure();
    const current=await journal.read();if(!isDeepStrictEqual(current,saved))throw failure();
    Object.assign(current.state.sessions[0].nativeSessionRejection.cryptographicExpiry,
      {verified:true,checkedAt,authRejectedStatus:response.status,preDeliveryRefreshCount:0});
    current.state.sessions[0].nativeSessionRejection.phase='expiry-verified';
    await journal.checkpoint(current.state);if(!isDeepStrictEqual(await journal.read(),current))throw failure();
    return {jwtExpired:true,authRejected:true,preDeliveryRefreshCount:0,processSuspended:true};
  }catch{throw failure();}
}

// The passive install has ended, and the caller still holds exclusive device/run
// custody. This revokes only the original owned session; it never calls refresh.
// Neither this receipt nor remote absence proves that the native app rejected it.
export const prepareAndroidNativeDeepLinkRejection = args => prepareNativeDeepLinkRejection(args, 'android');
export const prepareIosNativeDeepLinkRejection = args => prepareNativeDeepLinkRejection(args, 'ios');

async function prepareNativeDeepLinkRejection(args, platform) {
  try {
    const mode=args.mode??'cold';
    if(!['cold','warm'].includes(mode))throw failure();
    if(typeof args.operationsSettled!=='function'||await args.operationsSettled()!==true)throw failure();
    const saved=await args.journal.read(),{record,ticket,session}=args;
    if(['runId','profileId','authUserId'].some(k=>!uuid.test(record[k])||saved[k]!==record[k])||
      saved.state.sessions.length!==1)throw failure();
    const entry=saved.state.sessions[0],renewal=entry.nativeSessionRenewal,custodyKey=platform==='android'?'androidSession':'iosSession';
    if(['runId','profileId','authUserId'].some(k=>entry[k]!==record[k])||
      ['authSessionId','webSessionId'].some(k=>!uuid.test(entry[k])||ticket[k]!==entry[k])||
      entry.purpose!=='deep_link'||entry.requestStarted!==true||
      ['refreshAttempt','revocation','iosNativeLogin','androidNativeLogin','noSession'].some(k=>entry[k]!==undefined))throw failure();
    let stepId,installedSession,custodyKind;
    if(mode==='cold') {
      if(entry.nativeSessionRejection!==undefined||entry.iosSession!==undefined||entry.androidSession!==undefined||renewal?.platform!==platform||renewal.phase!=='installed'||
        renewal.install?.started!==true||renewal.install.verified!==true||!uuid.test(renewal.install.input?.stepId)||
        ['snapshotRead','clear','remoteIdentity'].some(k=>renewal[k]!==undefined)||entry.authSessionId!==renewal.original?.authSessionId||args.prelude!==undefined)throw failure();
      stepId=renewal.install.input.stepId;installedSession=renewal.original;custodyKind='expired-metadata';
      const input={runId:record.runId,profileId:record.profileId,authUserId:record.authUserId,stage:'read-owned',stepId};
      if(classifyNativeDeepLinkExpirySnapshot({renewal,input,receipt:{runId:record.runId,stepId,
        stage:'read-owned',verified:true,privateSession:renewal.original}})!=='original_snapshot'||
        !isDeepStrictEqual(renewal.install.input,{runId:record.runId,stepId,stage:'install-expired',
          ...renewal.expired,originalExpiresAt:renewal.original.expiresAt}))throw failure();
    } else {
      const installed=entry[custodyKey]?.install;
      if(renewal!==undefined||entry[platform==='android'?'iosSession':'androidSession']!==undefined||installed?.started!==true||
        installed.verified!==true||entry[custodyKey].clear!==undefined||installed.input?.stage!=='install'||
        !uuid.test(installed.input.stepId)||entry.authSessionId!==installed.input.authSessionId||!validWarmPrelude(args.prelude,platform,record.runId,args.target)||
        entry.nativeSessionRejection?.phase!=='expiry-verified'||entry.nativeSessionRejection.mode!=='warm'||
        !isDeepStrictEqual(entry.nativeSessionRejection.prelude,args.prelude)||
        entry.nativeSessionRejection.cryptographicExpiry?.verified!==true||
        entry.nativeSessionRejection.cryptographicExpiry.preDeliveryRefreshCount!==0)throw failure();
      stepId=installed.input.stepId;installedSession=installed.input;custodyKind='valid-session';
    }
    const login=entry.privateLoginResponse;
    if(login?.status!==200||login.body?.session?.access_token!==installedSession.accessToken||
      login.body?.session?.refresh_token!==installedSession.refreshToken||
      login.body?.session?.expires_at!==installedSession.expiresAt||
      session?.accessToken!==installedSession.accessToken||session?.refreshToken!==installedSession.refreshToken||
      session?.expiresAt!==installedSession.expiresAt)throw failure();
    if(mode==='cold')entry.nativeSessionRejection={platform,mode,custodyKind,phase:'revocation-started',authSessionId:entry.authSessionId,
      installStepId:stepId};
    else entry.nativeSessionRejection.phase='revocation-started';
    await args.journal.checkpoint(saved.state);
    let expected=structuredClone(saved);
    if(!isDeepStrictEqual(await args.journal.read(),expected))throw failure();
    // Reuse the exact SQL and ownership audit. Every read and each revocation
    // checkpoint must match this lifecycle, including durable read-back BEFORE SQL.
    const guardedJournal={
      read:async()=>{
        const actual=await args.journal.read();
        if(!isDeepStrictEqual(actual,expected))throw failure();
        return actual;
      },
      checkpoint:async state=>{
        if(!isDeepStrictEqual(await args.journal.read(),expected))throw failure();
        const next=structuredClone(expected),prior=next.state.sessions[0].revocation;
        next.state.sessions[0].revocation=prior===undefined?{started:true}:{started:true,verified:true};
        if(prior!==undefined&&!isDeepStrictEqual(prior,{started:true}))throw failure();
        if(!isDeepStrictEqual(state,next.state))throw failure();
        await args.journal.checkpoint(state);
        if(!isDeepStrictEqual(await args.journal.read(),next))throw failure();
        expected=next;
      },
    };
    await prepareRevokedDeepLinkSession({...args,journal:guardedJournal});
    const completed=await guardedJournal.read();
    if(!isDeepStrictEqual(completed.state.sessions[0].revocation,{started:true,verified:true}))throw failure();
    completed.state.sessions[0].nativeSessionRejection.phase='revoked';
    await args.journal.checkpoint(completed.state);
    if(!isDeepStrictEqual(await args.journal.read(),completed))throw failure();
    return {revoked:true,nativeRejectionObserved:false};
  }catch{throw failure();}
}
