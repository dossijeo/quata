// Real coordinator with isolated journal/backend/device boundaries; no credentials or remote effects.
import test,{mock} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,access} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

if(!process.execArgv.includes('--experimental-test-module-mocks')) {
 test('native login variant custody in an isolated module-mock process',()=>{
  const env={...process.env};delete env.NODE_TEST_CONTEXT;
  const child=spawnSync(process.execPath,['--experimental-test-module-mocks','--test',fileURLToPath(import.meta.url)],
   {env,encoding:'utf8',timeout:120000});
  assert.ifError(child.error);assert.equal(child.status,0,child.stdout+'\n'+child.stderr);
 });
} else {
 let journals,events;
 mock.module('./e2e-fixtures/recovery-private-journal.mjs',{namedExports:{createRecoveryJournal:async({record})=>{
  let saved=structuredClone(record);
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{saved.state=structuredClone(state);},
   removeAfterVerification:async()=>{events.push('retire-journal');}};
  journals.push(journal);return journal;
 }}});
 mock.module('./e2e-fixtures/chat-deep-link-profile.mjs',{namedExports:{
  createDeepLinkProfile:async({journal})=>{const r=await journal.read();r.state.profileCreationStarted=true;await journal.checkpoint(r.state);},
  retireDeepLinkProfile:async()=>{events.push('retire-profile');}
 }});
 mock.module('./e2e-fixtures/chat-deep-link-thread.mjs',{namedExports:{
  seedDeepLinkThread:async({journal})=>{const r=await journal.read();r.state.threadStarted=true;await journal.checkpoint(r.state);return {threadId:'123',messageId:'456'};},
  removeDeepLinkThread:async()=>{events.push('retire-thread');}
 }});
 const {runNativeDeepLinkChatTrial}=await import('./flow-deep-links-native-chat-trial.mjs');
 for(const platform of ['android','ios'])for(const receiptVariant of [undefined,'wrong','cancel-then-feed']) {
  test(`${platform} binds the durable requested variant before accepting ${String(receiptVariant)}`,async()=>{
   journals=[];events=[];
   const directory=await mkdtemp(path.join(os.tmpdir(),'quata-native-login-variant-'));
   try {
    const nativeKey=platform==='ios'?'iosNativeLogin':'androidNativeLogin';
    const report=await runNativeDeepLinkChatTrial({platform,variant:'cancel-then-feed',mode:'cold',privateDirectory:directory,
     client:{query:async()=>({rowCount:1,rows:[{owned:true,unique_active:true,phone_matches:true,no_sessions:true}]})},
     preflight:async()=>true,adminRequest:async()=>{throw Error('unexpected');},
     channel:{acknowledgeOwnedRead:async()=>{},close:async()=>{events.push('channel-close');},settled:()=>false,abort:()=>{events.push('abort');}},
     transportSettled:async()=>true,retireNativeResidue:async()=>{events.push('retire-native');},
     ui:{deliver:async()=>({passed:true}),close:async()=>{events.push('ui-close');},login:async input=>{
      const owner=await journals[0].read();const ticket=owner.state.sessions[0];
      assert.equal(input.variant,'cancel-then-feed');assert.equal(ticket[nativeKey].variant,input.variant);
      assert.equal(ticket.requestStarted,true);assert.equal(ticket[nativeKey].observationVerified,false);
      events.push('login');return {passed:true,...(receiptVariant?{variant:receiptVariant}:{})};
     }},
     sessionStep:async()=>{events.push('read');throw Error('synthetic read stops this test');}
    });
    assert.equal(report.status,'failed_cleanup_pending');assert.equal(report.cleanupComplete,false);
    assert.equal(report.variant,'cancel-then-feed');
    const owner=await journals[0].read();
    if(receiptVariant==='cancel-then-feed') {
     assert.equal(owner.state.sessions[0][nativeKey].observationVerified,true);
     assert.deepEqual(events,['login','ui-close','read','abort']);
    } else {
     assert.equal(report.failureCode,'deep_link_native_observation_failed');
     assert.equal(owner.state.sessions[0][nativeKey].observationVerified,false);
     assert.deepEqual(events,['login','abort']);
    }
    await access(path.join(directory,'flow-deep-links.lock'));
   } finally {await rm(directory,{recursive:true,force:true});}
  });
 }
}
