import test,{mock} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readdir} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
if(!process.execArgv.includes('--experimental-test-module-mocks')) {
  test('iOS Reply provider boundaries in an isolated process',()=>{
    const env={...process.env};delete env.NODE_TEST_CONTEXT;
    const result=spawnSync(process.execPath,['--experimental-test-module-mocks','--test',fileURLToPath(import.meta.url)],
      {env,encoding:'utf8',timeout:60000,windowsHide:true});
    assert.ifError(result.error);assert.equal(result.status,0,result.stdout+'\n'+result.stderr);
  });
} else {
let state;
const update=async(journal,fn)=>{const record=await journal.read();fn(record.state);await journal.checkpoint(record.state);};
mock.module('./e2e-fixtures/recovery-private-journal.mjs',{namedExports:{createRecoveryJournal:async({record})=>{
  let saved=structuredClone(record);state.records.push(()=>saved);
  return {read:async()=>structuredClone(saved),checkpoint:async value=>{saved.state=structuredClone(value)},
    removeAfterVerification:async verify=>{assert.deepEqual(await verify(),{password:true,secret:true,sessions:true});state.events.push('remove-journal')}};
}}});
mock.module('./e2e-fixtures/chat-deep-link-profile.mjs',{namedExports:{
  createDeepLinkProfile:async({journal})=>update(journal,s=>{s.profileCreated=true;s.profileCreationStarted=true}),
  retireDeepLinkProfile:async({operationsSettled})=>{assert.equal(await operationsSettled(),true);state.events.push('retire')},
}});
mock.module('./e2e-fixtures/chat-deep-link-session.mjs',{namedExports:{loginDeepLinkSession:async()=>({mock:true})}});
mock.module('./e2e-fixtures/chat-deep-link-thread.mjs',{namedExports:{
  seedDeepLinkThread:async({journal,capturePushRequest})=>{
    assert.equal(capturePushRequest,true);await update(journal,s=>{s.threadStarted=true;s.threadReceipt={threadId:'123',messageId:'456'};
      s.webNotificationSeedPush={settledWithoutDestinations:false}});return {threadId:'123',messageId:'456'};
  },removeDeepLinkThread:async()=>{throw Error('unexpected_baseline_cleanup')},
}});
mock.module('./e2e-fixtures/web-notification-seed-push.mjs',{namedExports:{observeWebNotificationSeedPush:async({journal})=>{
  state.events.push('seed-observed');if(state.failure==='seed')throw Error('web_notification_seed_push_unverified');
  await update(journal,s=>{s.webNotificationSeedPush.settledWithoutDestinations=true});return {settled:true};
}}});
mock.module('./e2e-fixtures/chat-deep-link-ios-session.mjs',{namedExports:{prepareIosDeepLinkSession:async({record})=>
  ({runId:record.runId,profileId:record.profileId,stage:'install'})}});
mock.module('./e2e-fixtures/chat-deep-link-ios-custody.mjs',{namedExports:{
  iosDeepLinkCustodySettled:entry=>!entry.iosSession||entry.iosSession.clear===true,
  runIosDeepLinkSessionStep:async({journal,input,execute})=>{
    await update(journal,s=>{s.sessions[0].iosSession??={};s.sessions[0].iosSession[input.stage]=false});
    await execute(input);await update(journal,s=>{s.sessions[0].iosSession[input.stage]=true});
  },
}});
mock.module('./e2e-fixtures/notification-reply-destination-audit.mjs',{namedExports:{auditReplyDestinations:async(client,record,proof,replyId)=>{
  assert.equal(state.transaction,true);assert.equal(proof.peerId,state.records[1]().profileId);
  assert.deepEqual(state.records[1]().state.sessions,[]);state.events.push(replyId?'audit-cleanup':'audit-before');
  if(state.failure==='audit-cleanup'&&replyId)throw Error('notification_reply_destinations_unverified');
}}});
mock.module('./e2e-fixtures/notification-reply-attempt.mjs',{namedExports:{
  submitNotificationReplyAttempt:async({journal,stepId,execute})=>{
    const record=await journal.read(),input={runId:record.runId,stepId,profileId:record.profileId,threadId:'123'};
    await update(journal,s=>{s.notificationReply={input,started:true}});return execute(input);
  },observeNotificationReplyMessage:async({journal})=>{
    await update(journal,s=>{s.notificationReply.backendReceipt={messageId:'457'}});return {persisted:true};
  },removeNotificationReplyThread:async({client,operationsSettled})=>{
    assert.equal(await operationsSettled(),true);await client.query('begin');
    try {await client.query('delete from public.chat_threads where id=$1::bigint and unique_key=$2',['123','owned']);await client.query('commit')}
    catch(error){await client.query('rollback');throw error}
    return {removed:true,matchesVerifiedReceipt:true};
  },
}});
const {runIosNotificationReplyTrial}=await import('./notification-reply-ios-trial.mjs');
for(const failure of [undefined,'sender','seed','audit-cleanup','transport'])test(`iOS custody ${failure??'complete'}`,async()=>{
  state={failure,records:[],events:[],transaction:false};let closed=false;
  // Retain isolated mock journals under the OS temp directory; no real credentials.
  const directory=await mkdtemp(path.join(os.tmpdir(),'quata-ios-reply-boundary-'));
  const client={query:async sql=>{
    if(sql==='begin')state.transaction=true;
    if(sql==='commit'||sql==='rollback')state.transaction=false;
    if(sql.startsWith('delete from')){assert.equal(state.events.at(-1),'audit-cleanup');state.events.push('delete')}
    return {rowCount:1,rows:[{auth:true,profile:true,sessions:true,web_sessions:true}]};
  }};
  const channel={sessionStep:async input=>state.events.push(input.stage),
    submitNotificationReply:async()=>{assert.ok(state.events.indexOf('seed-observed')<state.events.indexOf('install'));
      assert.ok(state.events.includes('audit-before'));state.events.push('send');return {mock:true}},
    verifyNotificationReplyOutcome:async input=>({runId:input.runId,stepId:input.stepId,notificationRemoved:true}),
    close:async()=>{closed=true;state.events.push('close')},settled:()=>closed,abort:()=>{closed=false},
  };
  const report=await runIosNotificationReplyTrial({client,channel,privateDirectory:directory,backendUrl:'https://example.invalid',publicKey:'mock',
    adminRequest:async()=>{},transportSettled:async()=>failure!=='transport',
    preflight:async()=>({passed:true,senderExcluded:failure!=='sender',dispatcherFingerprint:'a'.repeat(64)})});
  const retained=['seed','audit-cleanup','transport'].includes(failure);
  assert.equal(report.cleanupComplete,!retained);assert.equal((await readdir(directory)).includes('flow-deep-links.lock'),retained);
  assert.equal(report.status,retained?'failed_cleanup_pending':failure?'failed':'passed');
  assert.ok(state.events.filter(x=>x==='send').length<=1);
  if(['sender','seed'].includes(failure))assert.equal(state.events.includes('send'),false);
  if(retained){assert.equal(state.events.includes('delete'),false);assert.equal(state.events.includes('remove-journal'),false)}
  if(!failure){assert.ok(state.events.indexOf('close')<state.events.indexOf('audit-cleanup'));assert.equal(report.replyTrigger.requestTerminal,null)}
});
}
