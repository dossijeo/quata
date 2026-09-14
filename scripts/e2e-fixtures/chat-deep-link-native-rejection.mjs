import {isDeepStrictEqual} from 'node:util';
import {classifyNativeDeepLinkExpirySnapshot} from './chat-deep-link-native-expiry.mjs';
import {prepareRevokedDeepLinkSession} from './chat-deep-link-revoked-session.mjs';

const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const failure=()=>Error('deep_link_native_rejection_preparation_unresolved');

// The passive install has ended, and the caller still holds exclusive device/run
// custody. This revokes only the original owned session; it never calls refresh.
// Neither this receipt nor remote absence proves that the native app rejected it.
export const prepareAndroidNativeDeepLinkRejection = args => prepareNativeDeepLinkRejection(args, 'android');
export const prepareIosNativeDeepLinkRejection = args => prepareNativeDeepLinkRejection(args, 'ios');

async function prepareNativeDeepLinkRejection(args, platform) {
  try {
    if(typeof args.operationsSettled!=='function'||await args.operationsSettled()!==true)throw failure();
    const saved=await args.journal.read(),{record,ticket,session}=args;
    if(['runId','profileId','authUserId'].some(k=>!uuid.test(record[k])||saved[k]!==record[k])||
      saved.state.sessions.length!==1)throw failure();
    const entry=saved.state.sessions[0],renewal=entry.nativeSessionRenewal;
    if(['runId','profileId','authUserId'].some(k=>entry[k]!==record[k])||
      ['authSessionId','webSessionId'].some(k=>!uuid.test(entry[k])||ticket[k]!==entry[k])||
      entry.purpose!=='deep_link'||entry.requestStarted!==true||
      ['nativeSessionRejection','refreshAttempt','revocation','iosSession','androidSession','iosNativeLogin','androidNativeLogin','noSession']
        .some(k=>entry[k]!==undefined)||
      renewal?.platform!==platform||renewal.phase!=='installed'||renewal.install?.started!==true||
      renewal.install.verified!==true||!uuid.test(renewal.install.input?.stepId)||
      ['snapshotRead','clear','remoteIdentity'].some(k=>renewal[k]!==undefined)||
      entry.authSessionId!==renewal.original?.authSessionId)throw failure();
    const stepId=renewal.install.input.stepId;
    const input={runId:record.runId,profileId:record.profileId,authUserId:record.authUserId,stage:'read-owned',stepId};
    if(classifyNativeDeepLinkExpirySnapshot({renewal,input,receipt:{runId:record.runId,stepId,
      stage:'read-owned',verified:true,privateSession:renewal.original}})!=='original_snapshot'||
      !isDeepStrictEqual(renewal.install.input,{runId:record.runId,stepId,stage:'install-expired',
        ...renewal.expired,originalExpiresAt:renewal.original.expiresAt}))throw failure();
    const login=entry.privateLoginResponse;
    if(login?.status!==200||login.body?.session?.access_token!==renewal.original.accessToken||
      login.body?.session?.refresh_token!==renewal.original.refreshToken||
      login.body?.session?.expires_at!==renewal.original.expiresAt||
      session?.accessToken!==renewal.original.accessToken||session?.refreshToken!==renewal.original.refreshToken||
      session?.expiresAt!==renewal.original.expiresAt)throw failure();
    entry.nativeSessionRejection={platform,phase:'revocation-started',authSessionId:entry.authSessionId,
      installStepId:stepId};
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
