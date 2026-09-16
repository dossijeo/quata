import test,{mock} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readdir,rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
if(!process.execArgv.includes('--experimental-test-module-mocks')) {
  test('Android Reply coordinator in an isolated boundary-mock process',()=>{
    const env={...process.env};delete env.NODE_TEST_CONTEXT;
    const result=spawnSync(process.execPath,['--experimental-test-module-mocks','--test',fileURLToPath(import.meta.url)],
      {env,encoding:'utf8',timeout:60000,windowsHide:true});
    assert.ifError(result.error);assert.equal(result.status,0,result.stdout+'\n'+result.stderr);
  });
} else {
let scenario;
const change=async(journal,fn)=>{const record=await journal.read();fn(record.state);await journal.checkpoint(record.state);};
mock.module('./e2e-fixtures/recovery-private-journal.mjs',{namedExports:{createRecoveryJournal:async({record})=>{
  let saved=structuredClone(record);scenario.records.push(()=>saved);
  return {read:async()=>structuredClone(saved),checkpoint:async state=>{saved.state=structuredClone(state);},
    removeAfterVerification:async verify=>{assert.deepEqual(await verify(),{password:true,secret:true,sessions:true});scenario.events.push('remove-journal');}};
}}});
mock.module('./e2e-fixtures/chat-deep-link-profile.mjs',{namedExports:{
  createDeepLinkProfile:async({journal})=>{scenario.events.push('create');await change(journal,state=>{state.profileCreationStarted=true;state.profileCreated=true;});
    if(scenario.failure==='second_actor'&&scenario.records.length===2)throw Error('deep_link_profile_creation_failed');},
  retireDeepLinkProfile:async({operationsSettled})=>{assert.equal(await operationsSettled(),true);scenario.events.push('retire');},
}});
mock.module('./e2e-fixtures/chat-deep-link-session.mjs',{namedExports:{loginDeepLinkSession:async({journal})=>{
  scenario.events.push('login');await change(journal,state=>{state.sessions[0].requestStarted=true;});
  if(scenario.failure==='login')throw Error('deep_link_login_response_uncertain');
  await change(journal,state=>{state.sessions[0].authSessionId='mock-auth';state.sessions[0].webSessionId='mock-web';});
  return {privateSession:true};
}}});
mock.module('./e2e-fixtures/chat-deep-link-thread.mjs',{namedExports:{
  seedDeepLinkThread:async({journal,capturePushRequest})=>{
    assert.equal(capturePushRequest,true);scenario.events.push('seed');scenario.ownerJournal=journal;
    await change(journal,state=>{state.threadStarted=true;state.threadReceipt={threadId:'123',messageId:'456'};
      state.webNotificationSeedPush={settledWithoutDestinations:false};});return {threadId:'123',messageId:'456'};
  },
  removeDeepLinkThread:async({operationsSettled})=>{assert.equal(await operationsSettled(),true);scenario.events.push('cleanup-baseline');},
}});
mock.module('./e2e-fixtures/web-notification-seed-push.mjs',{namedExports:{observeWebNotificationSeedPush:async({journal})=>{
  scenario.events.push('seed-settled');if(scenario.failure==='seed')throw Error('web_notification_seed_push_unverified');
  await change(journal,state=>{state.webNotificationSeedPush.settledWithoutDestinations=true;});return {settled:true};
}}});
mock.module('./e2e-fixtures/chat-deep-link-ios-session.mjs',{namedExports:{prepareAndroidDeepLinkSession:async({record})=>
  ({runId:record.runId,profileId:record.profileId,authUserId:record.authUserId,stage:'install',stepId:'mock-install'})}});
mock.module('./e2e-fixtures/chat-deep-link-ios-custody.mjs',{namedExports:{
  androidDeepLinkCustodySettled:entry=>!entry.androidSession||(entry.androidSession.install?.verified===true&&entry.androidSession.clear?.verified===true),
  runAndroidDeepLinkCustodyStep:async({journal,input,execute})=>{
    if(input.stage==='clear'&&(await journal.read()).state.sessions[0].androidSession?.install?.verified!==true)throw Error('deep_link_android_custody_unresolved');
    await change(journal,state=>{const entry=state.sessions[0];entry.androidSession??={};entry.androidSession[input.stage]={started:true,verified:false};});
    await execute(input);await change(journal,state=>{state.sessions[0].androidSession[input.stage].verified=true;});
  },
}});
mock.module('./e2e-fixtures/chat-deep-link-android-residue.mjs',{namedExports:{retireAndroidDeepLinkResidue:async({operationsSettled})=>{
  assert.equal(await operationsSettled(),true);scenario.events.push('residue');if(scenario.failure==='residue')throw Error('deep_link_android_residue_cleanup_unresolved');
}}});
mock.module('./e2e-fixtures/android-notification-cleanup-disposition.mjs',{namedExports:{prepareAndroidReplyDestinationInvariant:async({journal,peerJournal})=>{
  assert.deepEqual((await peerJournal.read()).state.sessions,[]);scenario.events.push('invariant');
  await change(journal,state=>{assert.equal(state.notificationReply,undefined);state.androidReplyDestinationInvariant={preparedBeforeSend:true};});
}}});
mock.module('./e2e-fixtures/notification-reply-attempt.mjs',{namedExports:{
  submitAndroidNotificationReplyAttempt:async({journal,stepId,execute})=>{
    const saved=await journal.read(),input={runId:saved.runId,stepId,profileId:saved.profileId,threadId:'123'};
    await change(journal,state=>{state.notificationReply={input,started:true,uiVerified:false};});
    const receipt=await execute(input);await change(journal,state=>{state.notificationReply.uiVerified=true;});return receipt;
  },
  observeNotificationReplyMessage:async({journal})=>{scenario.events.push('observe-message');
    await change(journal,state=>{state.notificationReply.backendReceipt={messageId:'457'};});return {persisted:true,messageId:'457'};},
  removeAndroidNotificationReplyThread:async({journal,cleanupDisposition})=>{
    scenario.events.push('cleanup-thread');assert.equal(cleanupDisposition.replyTrigger.requestTerminal,null);
    assert.equal((await journal.read()).state.androidReplyClosure.processClosed,true);
    if(scenario.failure==='thread')throw Error('notification_reply_cleanup_unresolved');
    return {removed:true,matchesVerifiedReceipt:scenario.failure!=='late-duplicate'};
  },
}});
const {runAndroidNotificationReplyTrial}=await import('./notification-reply-android-trial.mjs');
for(const failure of [undefined,'initial','sender','second_actor','login','seed','install','send','outcome','reconcile','session-rotation',
  'close','transport','fingerprint','thread','residue','late-duplicate'])test(`Android coordinator ${failure??'complete'} preserves custody`,async()=>{
  scenario={failure,events:[],records:[]};let closed=false;
  const directory=await mkdtemp(path.join(os.tmpdir(),'quata-android-trial-'));
  const channel={
    sessionStep:async input=>{scenario.events.push(input.stage);if(failure===input.stage||(failure==='session-rotation'&&input.stage==='clear'))throw Error('deep_link_android_custody_unresolved');},
    submitNotificationReply:async input=>{
      scenario.events.push('send');assert.ok(scenario.events.indexOf('seed-settled')<scenario.events.indexOf('install'));
      assert.ok(scenario.events.includes('invariant'));assert.equal(input.authUserId,scenario.records[0]().authUserId);
      if(failure==='send')throw Error('notification_reply_android_step_unresolved');
      return {runId:input.runId,stepId:input.stepId,submittedBySystemUi:true,backendVerified:false,
        replyMarker:`qadata-reply-text-${input.stepId}`,notificationMarker:`qadata-reply-alert-${input.stepId}`};
    },
    verifyNotificationReplyOutcome:async input=>{scenario.events.push('outcome');return {runId:input.runId,stepId:input.stepId,
      attemptStepId:input.attemptStepId,notificationRemoved:failure!=='outcome',backendVerified:false,reconciled:false};},
    reconcileNotification:async input=>{scenario.events.push('reconcile');if(failure==='reconcile')throw Error('notification_reply_android_step_unresolved');
      return {runId:input.runId,stepId:input.stepId,attemptStepId:input.attemptStepId,notificationRemoved:true,backendVerified:false,reconciled:true};},
    close:async({runId})=>{scenario.events.push('close');closed=failure!=='close';return {runId,processClosed:closed};},
    settled:()=>closed,abort:()=>{closed=false;scenario.events.push('abort');},
  };
  try {
    const report=await runAndroidNotificationReplyTrial({privateDirectory:directory,channel,backendUrl:'https://example.invalid',publicKey:'mock',
      client:{query:async()=>({rowCount:1,rows:[{auth:true,profile:true,sessions:true,web_sessions:true}]})},
      adminRequest:async()=>{throw Error('not_used_by_mock');},transportSettled:async()=>failure!=='transport',
      preflight:async({phase})=>{scenario.events.push(`preflight:${phase}`);return {passed:failure!=='initial',senderExcluded:failure!=='sender',
        dispatcherFingerprint:(failure==='fingerprint'&&phase==='cleanup_thread'?'b':'a').repeat(64)};}});
    const retained=['login','seed','install','send','reconcile','session-rotation','close','transport','fingerprint','thread','residue'].includes(failure);
    assert.equal(report.cleanupComplete,!retained);assert.equal(report.status,failure?(retained?'failed_cleanup_pending':'failed'):'passed');
    assert.equal((await readdir(directory)).includes('flow-deep-links.lock'),retained);
    assert.ok(scenario.events.filter(event=>event==='send').length<=1);
    if(retained)assert.equal(scenario.events.includes('remove-journal'),false);
    if(['login','seed','install','send','reconcile','session-rotation','close','transport','fingerprint'].includes(failure))
      assert.equal(scenario.events.includes('cleanup-thread'),false);
    if(failure==='seed')assert.equal(scenario.events.includes('install'),false);
    if(!failure) {
      for(const [before,after] of [['send','observe-message'],['outcome','reconcile'],['reconcile','clear'],['clear','close'],
        ['close','cleanup-thread'],['cleanup-thread','residue'],['residue','retire'],['retire','remove-journal']])
        assert.ok(scenario.events.indexOf(before)<scenario.events.indexOf(after),`${before} before ${after}`);
      assert.equal(report.replyTrigger.requestTerminal,null);assert.equal(report.pushDeliveryCertified,false);
    }
  } finally {
    assert.equal(path.dirname(path.resolve(directory)),path.resolve(os.tmpdir()));
    assert.ok(path.basename(directory).startsWith('quata-android-trial-'));await rm(directory,{recursive:true,force:true});
  }
});
}
