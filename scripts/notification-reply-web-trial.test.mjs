import test,{mock} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readdir,rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
if(!process.execArgv.includes('--experimental-test-module-mocks')) {
  test('Web notification coordinator in isolated boundary-mock process',()=>{
    const env={...process.env};delete env.NODE_TEST_CONTEXT;
    const result=spawnSync(process.execPath,['--experimental-test-module-mocks','--test',fileURLToPath(import.meta.url)],{env,encoding:'utf8',timeout:60000,windowsHide:true});
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
  createDeepLinkProfile:async({journal})=>{scenario.events.push('create');await change(journal,state=>{state.profileCreationStarted=true;state.profileCreated=true;});if(scenario.failure==='second_actor'&&scenario.records.length===2)throw Error('deep_link_profile_creation_failed');},
  retireDeepLinkProfile:async({operationsSettled})=>{assert.equal(await operationsSettled(),true);scenario.events.push('retire');},
}});
mock.module('./e2e-fixtures/chat-deep-link-session.mjs',{namedExports:{loginDeepLinkSession:async({journal})=>{
  scenario.events.push('login');await change(journal,state=>{state.sessions[0].requestStarted=true;});
  if(scenario.failure==='login')throw Error('deep_link_login_response_uncertain');
  await change(journal,state=>{state.sessions[0].authSessionId='synthetic-auth';state.sessions[0].webSessionId='synthetic-web';});
}}});
mock.module('./e2e-fixtures/chat-deep-link-thread.mjs',{namedExports:{seedDeepLinkThread:async({journal,capturePushRequest})=>{
  assert.equal(capturePushRequest,true);scenario.events.push('seed');scenario.ownerJournal=journal;
  await change(journal,state=>{state.threadStarted=true;state.threadReceipt={threadId:'123',messageId:'456'};state.webNotificationSeedPush={settledWithoutDestinations:false};});
  return {threadId:'123',messageId:'456'};
}}});
mock.module('./e2e-fixtures/web-notification-seed-push.mjs',{namedExports:{observeWebNotificationSeedPush:async({journal})=>{
  scenario.events.push('seed-settled');await change(journal,state=>{state.webNotificationSeedPush.settledWithoutDestinations=true;});return {settled:true};
}}});
mock.module('./e2e-fixtures/web-notification-subscription.mjs',{namedExports:{
  captureWebNotificationSubscription:async({journal})=>{scenario.events.push('subscribe-capture');await change(journal,state=>{state.webNotificationSubscription={receipt:{subscriptionId:'synthetic-subscription'}};});return {captured:true};},
  observeWebNotificationSubscription:async()=>({persisted:true}),
  removeWebNotificationSubscription:async({cleanupDisposition,operationsSettled})=>{
    scenario.events.push('cleanup-subscription');
    if(scenario.failure==='cleanup')throw Error('web_notification_subscription_unverified');
    if(cleanupDisposition){assert.equal(operationsSettled,undefined);assert.equal(cleanupDisposition.replyTrigger.requestTerminal,null);}
    else assert.equal(await operationsSettled(),true);
    return {removed:true};
  },
}});
mock.module('./e2e-fixtures/web-notification-dispatch.mjs',{namedExports:{dispatchWebNotificationSeed:async({journal})=>{
  scenario.events.push('dispatch');await change(journal,state=>{state.webNotificationDispatch={started:true,settled:false};});
  if(scenario.failure==='dispatch')throw Error('web_notification_dispatch_unverified');
  await change(journal,state=>{state.webNotificationDispatch.settled=true;});return {completed:true,webSent:1};
}}});
mock.module('./e2e-fixtures/web-notification-cleanup-disposition.mjs',{namedExports:{prepareWebReplyDestinationInvariant:async({journal,peerJournal})=>{
  assert.deepEqual((await peerJournal.read()).state.sessions,[]);scenario.events.push('invariant');
  await change(journal,state=>{assert.equal(state.webNotificationMessage,undefined);state.webReplyDestinationInvariant={preparedBeforeSend:true};});
}}});
mock.module('./e2e-fixtures/notification-reply-attempt.mjs',{namedExports:{
  observeWebNotificationReplyMessage:async({journal})=>{scenario.events.push('observe-message');await change(journal,state=>{state.webNotificationMessage.backendReceipt={messageId:'457'};});return {persisted:true,messageId:'457'};},
  removeWebNotificationReplyThread:async({cleanupDisposition,operationsSettled})=>{
    scenario.events.push('cleanup-thread');if(cleanupDisposition){assert.equal(operationsSettled,undefined);assert.equal(cleanupDisposition.replyTrigger.requestTerminal,null);}
    else assert.equal(await operationsSettled(),true);return {removed:true,matchesVerifiedReceipt:true};
  },
}});
const {runWebNotificationReplyTrial}=await import('./notification-reply-web-trial.mjs');
for(const failure of [undefined,'initial','startup','second_actor','login','dispatch','click','send','close','cleanup','fingerprint_subscription','fingerprint_thread'])test(`Web coordinator ${failure??'complete'} retains appropriate custody`,async()=>{
  scenario={failure,events:[],records:[]};
  const directory=await mkdtemp(path.join(os.tmpdir(),'quata-web-trial-'));
  let runId;
  const ui={start:async input=>{runId=input.runId;scenario.events.push('startup');if(failure==='startup')throw Error('web_notification_startup_failed');return {runId,publicReady:true,anonymous:true};},
    requestLogin:async()=>{throw Error('not_used_by_mock');},
    enablePush:async({capture})=>{scenario.events.push('enable-push');await capture({});return {runId,productSubscribed:true};},
    clickNotification:async target=>{scenario.events.push('click');if(failure==='click')throw Error('web_notification_click_unverified');return {...target,clickedViaSystemUi:true,chatVisible:true};},
    sendReply:async({marker,capture})=>{
      scenario.events.push('send');assert.ok(scenario.events.includes('invariant'));
      await capture({p_actor_profile_id:scenario.records[0]().profileId,p_thread_id:123,p_message:marker,p_client_message_id:'1789412345678--7abc',p_file_ids:[],p_reply_to_message_id:null});
      if(failure==='send')throw Error('web_notification_send_ui_unverified');return {runId,sentViaChatUi:true};
    },
    closeOwnedState:async input=>{scenario.events.push('close');return {runId:input.runId,browserClosed:failure!=='close',serverClosed:true,transportSettled:true,ownedNotificationsRemoved:true,browserSubscriptionRemoved:true};},
  };
  try {
    const report=await runWebNotificationReplyTrial({privateDirectory:directory,ui,backendUrl:'https://example.invalid',publicKey:'synthetic',
      client:{query:async sql=>sql.includes('select status')?{rowCount:1,rows:[{status:'sent'}]}:{rowCount:1,rows:[{auth:true,profile:true,sessions:true,web_sessions:true}]}},
      adminRequest:async()=>{throw Error('not_used_by_mock');},executeDispatch:async()=>{throw Error('not_used_by_mock');},transportSettled:async()=>true,
      preflight:async({phase})=>{scenario.events.push(`preflight:${phase}`);return {passed:failure!=='initial',dispatcherFingerprint:(failure===`fingerprint_${phase.replace('cleanup_','')}`?'b':'a').repeat(64)};}});
    const retained=['login','dispatch','send','close','cleanup','fingerprint_subscription','fingerprint_thread'].includes(failure);
    assert.equal(report.cleanupComplete,!retained);
    assert.equal(report.status,failure?(retained?'failed_cleanup_pending':'failed'):'passed');
    assert.equal((await readdir(directory)).includes('flow-deep-links.lock'),retained);
    if(retained)assert.equal(scenario.events.includes('remove-journal'),false);
    if(failure==='fingerprint_subscription')assert.equal(scenario.events.includes('cleanup-subscription'),false);
    if(failure==='fingerprint_thread') {
      assert.equal(scenario.events.includes('cleanup-subscription'),true);
      assert.equal(scenario.events.includes('cleanup-thread'),false);
    }
    if(['send','dispatch'].includes(failure))assert.equal(scenario.events.includes('cleanup-subscription'),false);
    if(failure==='second_actor')assert.equal(scenario.events.filter(event=>event==='retire').length,2);
    if(!failure) {
      assert.ok(scenario.events.indexOf('seed-settled')<scenario.events.indexOf('enable-push'));
      assert.ok(scenario.events.indexOf('close')<scenario.events.indexOf('cleanup-subscription'));
      assert.ok(scenario.events.indexOf('cleanup-subscription')<scenario.events.indexOf('cleanup-thread'));
      assert.ok(scenario.events.indexOf('cleanup-thread')<scenario.events.indexOf('retire'));
      assert.equal(report.replyTrigger.requestTerminal,null);
    }
    if(['initial','startup'].includes(failure))assert.equal(scenario.records.length,0);
  } finally {await rm(directory,{recursive:true,force:true});}
});
}
