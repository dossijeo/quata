import {isDeepStrictEqual} from 'node:util';
import {classifyNativeDeepLinkExpirySnapshot} from './chat-deep-link-native-expiry.mjs';
import {selectAndroidRefreshRejection} from './chat-deep-link-android-rejection.mjs';
import {verifyRevokedDeepLinkSession} from './chat-deep-link-revoked-session.mjs';
const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const scope='android_external_owned_message_cold_native_rejection_barrier_cancel_and_feed; visual review pending';
function checkedEntry(entry) {
  const renewal=entry.nativeSessionRenewal,rejection=entry.nativeSessionRejection;
  if(['runId','profileId','authUserId','authSessionId','webSessionId'].some(k=>!uuid.test(entry[k]))||
    entry.purpose!=='deep_link'||entry.requestStarted!==true||
    ['iosSession','androidSession','iosNativeLogin','androidNativeLogin','refreshAttempt','noSession'].some(k=>entry[k]!==undefined)||
    renewal?.platform!=='android'||renewal.phase!=='installed'||renewal.install?.started!==true||renewal.install.verified!==true||
    ['snapshotRead','remoteIdentity','clear'].some(k=>renewal[k]!==undefined)||
    !uuid.test(renewal.install.input?.stepId)||entry.authSessionId!==renewal.original?.authSessionId||
    rejection?.platform!=='android'||rejection.authSessionId!==entry.authSessionId||rejection.installStepId!==renewal.install.input.stepId||
    !['revoked','observed','absent'].includes(rejection.phase)||!isDeepStrictEqual(entry.revocation,{started:true,verified:true}))throw Error();
  const stepId=renewal.install.input.stepId;
  const input={runId:entry.runId,profileId:entry.profileId,authUserId:entry.authUserId,stage:'read-owned',stepId};
  if(classifyNativeDeepLinkExpirySnapshot({renewal,input,receipt:{runId:entry.runId,stepId,stage:'read-owned',verified:true,
    privateSession:renewal.original}})!=='original_snapshot'||
    !isDeepStrictEqual(renewal.install.input,{runId:entry.runId,stepId,stage:'install-expired',...renewal.expired,originalExpiresAt:renewal.original.expiresAt}))throw Error();
  const login=entry.privateLoginResponse;
  if(login?.status!==200||login.body?.session?.access_token!==renewal.original.accessToken||
    login.body?.session?.refresh_token!==renewal.original.refreshToken||login.body?.session?.expires_at!==renewal.original.expiresAt)throw Error();
  return rejection;
}
async function checkedJournal({journal,record}) {
  const saved=await journal.read();
  if(['runId','profileId','authUserId'].some(k=>saved[k]!==record[k])||saved.state.sessions.length!==1||
    ['runId','profileId','authUserId'].some(k=>saved.state.sessions[0][k]!==record[k]))throw Error();
  checkedEntry(saved.state.sessions[0]);return saved;
}
function checkedObservation(attempt) {
  const result=attempt?.result,r=result?.receipts?.[0],target=attempt?.target;
  if(attempt?.started!==true||!target||!['threadId','messageId'].every(k=>/^[1-9][0-9]{0,15}$/.test(target[k]))||
    result?.passed!==true||result.scope!==scope||result.receipts?.length!==1||r?.passed!==true||r.mode!=='cold'||
    typeof r.runId!=='string'||!r.runId.startsWith('chat-cold-')||!uuid.test(r.runId.slice(10))||
    r.beforePid!==null||r.anonymousAction!=='cancel'||r.targetThreadId!==target.threadId||r.targetMessageId!==target.messageId||
    r.focusedMessageId!==undefined||r.rejection?.pid!==r.afterPid)throw Error();
  const w=r.rejection;
  const normalized=selectAndroidRefreshRejection({...w,text:`${w.timestamp} ${w.pid} 1 W SupabaseHttpClient: Supabase session refresh failed with status=${w.status}`});
  if(!isDeepStrictEqual(w,normalized))throw Error();
}
async function persist(journal,saved) {
  await journal.checkpoint(saved.state);
  if(!isDeepStrictEqual(await journal.read(),saved))throw Error();
}

// One UI delivery, after durable intent. A lost response cannot be retried.
export async function observeAndroidNativeDeepLinkRejection(args) {
  try {
    if(typeof args.execute!=='function')throw Error();
    const saved=await checkedJournal(args),rejection=saved.state.sessions[0].nativeSessionRejection;
    if(rejection.phase!=='revoked'||rejection.observation!==undefined||rejection.absence!==undefined||
      !isDeepStrictEqual(saved.state.threadReceipt,args.target)||saved.state.threadStarted!==true||
      saved.state.threadPlan?.runId!==args.record.runId||saved.state.threadPlan?.ownerId!==args.record.profileId)throw Error();
    await verifyRevokedDeepLinkSession(args);
    if(!isDeepStrictEqual(await args.journal.read(),saved))throw Error();
    rejection.observation={started:true,verified:false,target:{threadId:String(args.target.threadId),messageId:String(args.target.messageId)}};
    await persist(args.journal,saved);
    const result=await args.execute();
    if(!isDeepStrictEqual(await args.journal.read(),saved))throw Error();
    rejection.observation.result=structuredClone(result);
    await persist(args.journal,saved);
    checkedObservation(rejection.observation);
    await verifyRevokedDeepLinkSession(args);
    if(!isDeepStrictEqual(await args.journal.read(),saved))throw Error();
    rejection.observation.verified=true;rejection.phase='observed';
    await persist(args.journal,saved);
    return result;
  }catch{throw Error('deep_link_native_rejection_observation_unresolved');}
}

// No clear or refresh: the passive native probe must prove all session keys absent.
export async function confirmAndroidNativeDeepLinkRejectionAbsence(args) {
  try {
    if(typeof args.execute!=='function'||typeof args.operationsSettled!=='function'||await args.operationsSettled()!==true||!uuid.test(args.stepId))throw Error();
    const saved=await checkedJournal(args),rejection=saved.state.sessions[0].nativeSessionRejection;
    if(rejection.phase!=='observed'||rejection.observation?.verified!==true||rejection.absence!==undefined||args.stepId===rejection.installStepId)throw Error();
    checkedObservation(rejection.observation);
    const input={runId:args.record.runId,stepId:args.stepId,stage:'probe-empty'};
    rejection.absence={started:true,verified:false,input};await persist(args.journal,saved);
    const receipt=await args.execute(structuredClone(input));
    if(!isDeepStrictEqual(receipt,{...input,verified:true})||!isDeepStrictEqual(await args.journal.read(),saved))throw Error();
    rejection.absence.verified=true;rejection.phase='absent';await persist(args.journal,saved);
    return {absent:true};
  }catch{throw Error('deep_link_native_rejection_absence_unresolved');}
}

export function androidNativeDeepLinkRejectionCustodySettled(entry) {
  try {
    const rejection=checkedEntry(entry),absence=rejection.absence;
    checkedObservation(rejection.observation);
    return rejection.phase==='absent'&&rejection.observation.verified===true&&absence?.started===true&&absence.verified===true&&
      uuid.test(absence.input?.stepId)&&absence.input.stepId!==rejection.installStepId&&
      isDeepStrictEqual(absence.input,{runId:entry.runId,stepId:absence.input.stepId,stage:'probe-empty'});
  }catch{return false;}
}
