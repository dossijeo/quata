import {isDeepStrictEqual} from 'node:util';
import {classifyNativeDeepLinkExpirySnapshot} from './chat-deep-link-native-expiry.mjs';
import {selectAndroidRefreshRejection} from './chat-deep-link-android-rejection.mjs';
import {verifyRevokedDeepLinkSession} from './chat-deep-link-revoked-session.mjs';
import {validateIosNativeRejectionReceipt} from './chat-deep-link-ios-rejection.mjs';
const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const rejectionScope=(platform,mode)=>`${platform}_external_owned_message_${mode}_native_rejection_barrier_cancel_and_feed${platform==='android'?'; visual review pending':''}`;
const legacyColdRejection=rejection=>rejection?.mode===undefined&&rejection?.custodyKind===undefined&&rejection?.prelude===undefined;
function rejectionMode(rejection) {
  if(legacyColdRejection(rejection))return 'cold';
  if(!['cold','warm'].includes(rejection?.mode))throw Error();
  return rejection.mode;
}
function checkedEntry(entry,platform='android') {
  const renewal=entry.nativeSessionRenewal,rejection=entry.nativeSessionRejection;
  const mode=rejectionMode(rejection),custodyKind=legacyColdRejection(rejection)?'expired-metadata':rejection.custodyKind;
  const terminalPhase=platform==='android'||mode==='warm'?'absent':'cleared';
  if(['runId','profileId','authUserId','authSessionId','webSessionId'].some(k=>!uuid.test(entry[k]))||
    entry.purpose!=='deep_link'||entry.requestStarted!==true||
    ['iosNativeLogin','androidNativeLogin','refreshAttempt','noSession'].some(k=>entry[k]!==undefined)||
    rejection?.platform!==platform||rejection.authSessionId!==entry.authSessionId||
    !['revoked','observed',terminalPhase].includes(rejection.phase)||
    ((platform==='android'||mode==='warm')?rejection.clear!==undefined:rejection.absence!==undefined)||
    !isDeepStrictEqual(entry.revocation,{started:true,verified:true}))throw Error();
  let installedSession;
  if(mode==='cold'&&custodyKind==='expired-metadata') {
    if(entry.iosSession!==undefined||entry.androidSession!==undefined||renewal?.platform!==platform||renewal.phase!=='installed'||
      renewal.install?.started!==true||renewal.install.verified!==true||['snapshotRead','remoteIdentity','clear'].some(k=>renewal[k]!==undefined)||
      !uuid.test(renewal.install.input?.stepId)||rejection.installStepId!==renewal.install.input.stepId||entry.authSessionId!==renewal.original?.authSessionId||
      rejection.prelude!==undefined)throw Error();
    const stepId=renewal.install.input.stepId,input={runId:entry.runId,profileId:entry.profileId,authUserId:entry.authUserId,stage:'read-owned',stepId};
    if(classifyNativeDeepLinkExpirySnapshot({renewal,input,receipt:{runId:entry.runId,stepId,stage:'read-owned',verified:true,
      privateSession:renewal.original}})!=='original_snapshot'||
      !isDeepStrictEqual(renewal.install.input,{runId:entry.runId,stepId,stage:'install-expired',...renewal.expired,originalExpiresAt:renewal.original.expiresAt}))throw Error();
    installedSession=renewal.original;
  } else if(mode==='warm'&&custodyKind==='valid-session') {
    const custody=entry[platform==='android'?'androidSession':'iosSession'],other=entry[platform==='android'?'iosSession':'androidSession'];
    if(renewal!==undefined||other!==undefined||custody?.install?.started!==true||custody.install.verified!==true||custody.clear!==undefined||
      custody.install.input?.stage!=='install'||rejection.installStepId!==custody.install.input.stepId||
      rejection.prelude?.platform!==platform||rejection.prelude.mode!=='warm'||rejection.prelude.pidPreserved!==true||rejection.prelude.suspended!==true||
      !['threadId','messageId'].every(key=>/^[1-9][0-9]{0,15}$/.test(rejection.prelude.target?.[key]))||
      rejection.prelude.receipt?.mode!=='cold'||rejection.prelude.receipt.passed!==true||
      rejection.cryptographicExpiry?.verified!==true||rejection.cryptographicExpiry.preDeliveryRefreshCount!==0||
      !Number.isSafeInteger(rejection.cryptographicExpiry.checkedAt)||rejection.cryptographicExpiry.checkedAt<=custody.install.input.expiresAt)throw Error();
    installedSession=custody.install.input;
  } else throw Error();
  const login=entry.privateLoginResponse;
  if(login?.status!==200||login.body?.session?.access_token!==installedSession.accessToken||
    login.body?.session?.refresh_token!==installedSession.refreshToken||login.body?.session?.expires_at!==installedSession.expiresAt)throw Error();
  return rejection;
}
async function checkedJournal({journal,record},platform='android') {
  const saved=await journal.read();
  if(['runId','profileId','authUserId'].some(k=>saved[k]!==record[k])||saved.state.sessions.length!==1||
    ['runId','profileId','authUserId'].some(k=>saved.state.sessions[0][k]!==record[k]))throw Error();
  checkedEntry(saved.state.sessions[0],platform);return saved;
}
function checkedObservation(attempt,platform='android',runId,allowLegacyCold=false) {
  const result=attempt?.result,r=result?.receipts?.[0],target=attempt?.target,
    mode=attempt?.mode??(allowLegacyCold?'cold':undefined);
  if(platform==='ios') {
    if(attempt?.started!==true||!['cold','warm'].includes(mode)||r?.mode!==mode||result?.passed!==true||result.scope!==rejectionScope('ios',mode)||
      result.receipts?.length!==1)throw Error();
    validateIosNativeRejectionReceipt({input:{runId,stepId:r?.stepId,mode,threadId:target?.threadId,
      messageId:target?.messageId,body:`Deep link ${runId}`},receipt:r});
    return;
  }
  if(attempt?.started!==true||!target||!['threadId','messageId'].every(k=>/^[1-9][0-9]{0,15}$/.test(target[k]))||
    result?.passed!==true||!['cold','warm'].includes(mode)||r?.mode!==mode||result.scope!==rejectionScope('android',mode)||result.receipts?.length!==1||r?.passed!==true||
    typeof r.runId!=='string'||!r.runId.startsWith(`chat-${r.mode}-`)||!uuid.test(r.runId.slice(`chat-${r.mode}-`.length))||
    (r.mode==='cold'?r.beforePid!==null:!r.beforePid)||r.anonymousAction!=='cancel'||r.targetThreadId!==target.threadId||r.targetMessageId!==target.messageId||
    r.focusedMessageId!==undefined||r.rejection?.pid!==r.afterPid)throw Error();
  const w=r.rejection;
  const normalized=selectAndroidRefreshRejection({...w,text:`${w.timestamp} ${w.pid} 1 W SupabaseHttpClient: Supabase session refresh failed with status=${w.status}`});
  if(!isDeepStrictEqual(w,normalized))throw Error();
}
function checkedPrelude(rejection,target,runId) {
  if(rejectionMode(rejection)!=='warm')return;
  const receipt=rejection.prelude?.receipt;
  if(!isDeepStrictEqual(rejection.prelude.target,{threadId:String(target?.threadId),messageId:String(target?.messageId)}))throw Error();
  if(rejection.platform==='ios') {
    if(!isDeepStrictEqual(receipt,{runId,stepId:receipt?.stepId,mode:'cold',passed:true})||!uuid.test(receipt.stepId))throw Error();
    return;
  }
  if(receipt?.mode!=='cold'||receipt.passed!==true||receipt.beforePid!==null||!receipt.afterPid||
    receipt.focusedMessageId!==String(target?.messageId)||typeof receipt.runId!=='string'||!receipt.runId.startsWith('chat-cold-')||
    !uuid.test(receipt.runId.slice(10)))throw Error();
}
async function persist(journal,saved) {
  await journal.checkpoint(saved.state);
  if(!isDeepStrictEqual(await journal.read(),saved))throw Error();
}

// One UI delivery, after durable intent. A lost response cannot be retried.
export const observeAndroidNativeDeepLinkRejection=args=>observeNativeDeepLinkRejection(args,'android');
export const observeIosNativeDeepLinkRejection=args=>observeNativeDeepLinkRejection(args,'ios');
async function observeNativeDeepLinkRejection(args,platform) {
  try {
    if(typeof args.execute!=='function')throw Error();
    const saved=await checkedJournal(args,platform),rejection=saved.state.sessions[0].nativeSessionRejection;
    if(rejection.phase!=='revoked'||rejection.observation!==undefined||rejection.absence!==undefined||rejection.clear!==undefined||
      !isDeepStrictEqual(saved.state.threadReceipt,args.target)||saved.state.threadStarted!==true||
      saved.state.threadPlan?.runId!==args.record.runId||saved.state.threadPlan?.ownerId!==args.record.profileId)throw Error();
    await verifyRevokedDeepLinkSession(args);
    if(!isDeepStrictEqual(await args.journal.read(),saved))throw Error();
    checkedPrelude(rejection,args.target,args.record.runId);
    rejection.observation={started:true,verified:false,mode:rejectionMode(rejection),target:{threadId:String(args.target.threadId),messageId:String(args.target.messageId)}};
    await persist(args.journal,saved);
    const result=await args.execute();
    if(!isDeepStrictEqual(await args.journal.read(),saved))throw Error();
    rejection.observation.result=structuredClone(result);
    await persist(args.journal,saved);
    checkedObservation(rejection.observation,platform,args.record.runId);
    if(platform==='android'&&rejectionMode(rejection)==='warm'&&
      rejection.observation.result.receipts[0].beforePid!==rejection.prelude.receipt.afterPid)throw Error();
    await verifyRevokedDeepLinkSession(args);
    if(!isDeepStrictEqual(await args.journal.read(),saved))throw Error();
    rejection.observation.verified=true;rejection.phase='observed';
    await persist(args.journal,saved);
    return result;
  }catch{throw Error('deep_link_native_rejection_observation_unresolved');}
}

// No clear or refresh: the passive native probe must prove all session keys absent.
export const confirmAndroidNativeDeepLinkRejectionAbsence=args=>confirmNativeDeepLinkRejectionAbsence(args,'android');
export const confirmIosNativeDeepLinkRejectionAbsence=args=>confirmNativeDeepLinkRejectionAbsence(args,'ios');
async function confirmNativeDeepLinkRejectionAbsence(args,platform) {
  try {
    if(typeof args.execute!=='function'||typeof args.operationsSettled!=='function'||await args.operationsSettled()!==true||!uuid.test(args.stepId))throw Error();
    const saved=await checkedJournal(args,platform),rejection=saved.state.sessions[0].nativeSessionRejection;
    if(platform==='ios'&&rejectionMode(rejection)!=='warm')throw Error();
    if(rejection.phase!=='observed'||rejection.observation?.verified!==true||rejection.absence!==undefined||args.stepId===rejection.installStepId)throw Error();
    checkedObservation(rejection.observation,platform,args.record.runId,legacyColdRejection(rejection));
    const input={runId:args.record.runId,stepId:args.stepId,stage:'probe-empty'};
    rejection.absence={started:true,verified:false,input};await persist(args.journal,saved);
    const receipt=await args.execute(structuredClone(input));
    const expected=platform==='ios'?{runId:input.runId,stepId:input.stepId,probe:true,verified:true}:{...input,verified:true};
    if(!isDeepStrictEqual(receipt,expected)||!isDeepStrictEqual(await args.journal.read(),saved))throw Error();
    rejection.absence.verified=true;rejection.phase='absent';await persist(args.journal,saved);
    return {absent:true};
  }catch{throw Error('deep_link_native_rejection_absence_unresolved');}
}

export function androidNativeDeepLinkRejectionCustodySettled(entry) {
  try {
    const rejection=checkedEntry(entry),absence=rejection.absence;
    checkedObservation(rejection.observation,'android',entry.runId,legacyColdRejection(rejection));
    return rejection.phase==='absent'&&rejection.observation.verified===true&&absence?.started===true&&absence.verified===true&&
      uuid.test(absence.input?.stepId)&&absence.input.stepId!==rejection.installStepId&&
      isDeepStrictEqual(absence.input,{runId:entry.runId,stepId:absence.input.stepId,stage:'probe-empty'});
  }catch{return false;}
}

// The cold iOS fixture retains its deliberately expired metadata snapshot. The
// native command compares it exactly before removing it; warm terminal rejection
// instead uses the passive absence proof above because product owns the removal.
export async function clearIosNativeDeepLinkRejection(args) {
  try {
    if(typeof args.execute!=='function'||typeof args.operationsSettled!=='function'||await args.operationsSettled()!==true||!uuid.test(args.stepId))throw Error();
    const saved=await checkedJournal(args,'ios'),entry=saved.state.sessions[0],rejection=entry.nativeSessionRejection;
    if(rejection.phase!=='observed'||rejection.observation?.verified!==true||rejection.clear!==undefined||
      [rejection.installStepId,rejection.observation.result?.receipts?.[0]?.stepId].includes(args.stepId))throw Error();
    checkedObservation(rejection.observation,'ios',args.record.runId,legacyColdRejection(rejection));
    const input=rejectionMode(rejection)==='cold'?{...entry.nativeSessionRenewal.install.input,stage:'clear-expired',stepId:args.stepId}:
      {...entry.iosSession.install.input,stage:'clear',stepId:args.stepId};
    rejection.clear={started:true,verified:false,input};await persist(args.journal,saved);
    const receipt=await args.execute(structuredClone(input));
    if(!isDeepStrictEqual(receipt,{runId:args.record.runId,stepId:args.stepId,stage:input.stage,verified:true})||
      !isDeepStrictEqual(await args.journal.read(),saved))throw Error();
    rejection.clear.verified=true;rejection.phase='cleared';await persist(args.journal,saved);
    return {cleared:true};
  }catch{throw Error('deep_link_native_rejection_clear_unresolved');}
}

export function iosNativeDeepLinkRejectionCustodySettled(entry) {
  try {
    const rejection=checkedEntry(entry,'ios');
    checkedObservation(rejection.observation,'ios',entry.runId,legacyColdRejection(rejection));
    if(rejectionMode(rejection)==='warm') {
      const absence=rejection.absence;
      return rejection.phase==='absent'&&rejection.observation.verified===true&&absence?.started===true&&absence.verified===true&&
        uuid.test(absence.input?.stepId)&&![rejection.installStepId,rejection.observation.result.receipts[0].stepId].includes(absence.input.stepId)&&
        isDeepStrictEqual(absence.input,{runId:entry.runId,stepId:absence.input.stepId,stage:'probe-empty'});
    }
    const clear=rejection.clear;
    return rejection.phase==='cleared'&&rejection.observation.verified===true&&clear?.started===true&&clear.verified===true&&
      uuid.test(clear.input?.stepId)&&![rejection.installStepId,rejection.observation.result.receipts[0].stepId].includes(clear.input.stepId)&&
      isDeepStrictEqual(clear.input,rejectionMode(rejection)==='cold'?
        {...entry.nativeSessionRenewal.install.input,stage:'clear-expired',stepId:clear.input.stepId}:
        {...entry.iosSession.install.input,stage:'clear',stepId:clear.input.stepId});
  }catch{return false;}
}
