import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,readFile,rm,access} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {openAndroidNotificationReplyChannel} from './e2e-fixtures/notification-reply-android-channel.mjs';
const hash=value=>createHash('sha256').update(value).digest('hex');
const id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
const owner={runId:id(1),profileId:id(2),authUserId:id(3)};
const submission={...owner,stepId:id(4),threadId:'123'};
const observation={runId:owner.runId,profileId:owner.profileId,attemptStepId:submission.stepId,stepId:id(5),threadId:'123'};
const absent=()=>Object.assign(Error('absent'),{code:1,stdout:'',stderr:''});
async function harness(t,options={}) {
  const root=await mkdtemp(path.join(os.tmpdir(),'quata-reply-channel-'));
  t.after(async()=>{assert.equal(path.dirname(root),os.tmpdir());await rm(root,{recursive:true,force:true});});
  const product=Buffer.from('product-test-apk'),custody=Buffer.from('custody-test-apk'),app=Buffer.from('app-apk');
  const productPath=path.join(root,'product.apk'),custodyPath=path.join(root,'custody.apk');
  await writeFile(productPath,product);await writeFile(custodyPath,custody);
  const events=[],state={live:false,avd:'QuataReplyPr339Api35',test:custody,app,intent:true,...options};
  const execute=async(_adb,argv,execOptions)=>{
    if(_adb.endsWith('aapt2.exe')) {
      events.push('manifest');return {stdout:`package: name='${state.badPackage?'com.quata':'com.quata.test'}' versionCode='1'\n`,stderr:''};
    }
    assert.equal(argv[1],'emulator-5562');const args=argv.slice(2);events.push(args.join(' '));
    if(args[0]==='shell'&&args[1]==='pidof') {
      if(state.live)return {stdout:'1234\n',stderr:''};throw absent();
    }
    // Windows adb console output captured from the owned API35 candidate.
    if(args[0]==='emu')return {stdout:`${state.avd}\r\r\nOK\r\r\n`,stderr:''};
    if(args[0]==='shell'&&args[1]==='pm')return {stdout:`package:/data/app/~~owned-apk==/${args[3]}-install==/base.apk\n`,stderr:''};
    if(args[0]==='exec-out') {
      assert.equal(execOptions.encoding,'buffer');return {stdout:args[2].includes('com.quata.test')?state.test:state.app,stderr:Buffer.alloc(0)};
    }
    if(args[0]==='install') {
      if(state.installFailure)throw Error('install lost');
      assert.deepEqual(args.slice(0,3),['install','-r','-t']);state.test=await readFile(args[3]);
      return {stdout:'Performing Streamed Install\nSuccess\n',stderr:''};
    }
    if(args[0]==='shell'&&args[1]==='run-as') {
      if(state.intentError)throw Object.assign(Error('uncertain'),{code:1,stdout:'',stderr:'run-as failed'});
      if(!state.intent)throw absent();return {stdout:'',stderr:''};
    }
    throw Error(`unexpected command ${args.join(' ')}`);
  };
  const leasePath=path.join(root,'lease.json');
  const config={adb:path.join(root,'adb.exe'),aapt2:path.join(root,'aapt2.exe'),serial:'emulator-5562',avdName:'QuataReplyPr339Api35',leasePath,
    evidenceDirectory:path.join(root,'evidence'),appSha256:hash(app),
    productApk:{path:productPath,sha256:hash(product)},custodyApk:{path:custodyPath,sha256:hash(custody)},
    identity:{pr:339,base:'a'.repeat(40),head:'b'.repeat(40),merge:'c'.repeat(40)},execute,
    sessionStepImpl:async({input})=>{events.push(`session:${input.stage}`);if(state.liveAfterProbe&&input.stage==='probe-empty')state.live=true;
      return {runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true};},
    replyStepImpl:async({input,mode})=>{
      events.push(`reply:${mode}`);if(state.nativeFailure&&mode==='submit')throw Error('native lost');
      return {mode,stepId:input.stepId};
    }};
  return {root,config,state,events,leasePath};
}
test('channel completes one native submission and preserves app data through test APK swaps',async t=>{
  const h=await harness(t),channel=await openAndroidNotificationReplyChannel(h.config);
  await channel.sessionStep({...owner,stepId:id(6),stage:'install'});
  await channel.submitNotificationReply(submission);
  await assert.rejects(channel.submitNotificationReply(submission));
  await channel.verifyNotificationReplyOutcome(observation);
  await channel.reconcileNotification({...observation,stepId:id(7)});
  await channel.sessionStep({...owner,stepId:id(8),stage:'clear'});
  assert.deepEqual(await channel.close({runId:owner.runId}),{runId:owner.runId,processClosed:true});
  assert.equal(channel.settled(),true);await assert.rejects(access(h.leasePath));
  assert.deepEqual(h.events.filter(event=>event.startsWith('reply:')),['reply:submit','reply:observe','reply:cleanup']);
  assert.equal(h.events.some(event=>/uninstall|force-stop|pm clear/.test(event)),false);
  assert.ok(h.events.indexOf('reply:cleanup')<h.events.indexOf('session:clear'));
});
for(const scenario of ['live','avd','app','installFailure'])test(`channel opening ${scenario} retains the acquired lease`,async t=>{
  const options=scenario==='live'?{live:true}:scenario==='avd'?{avd:'Stable'}:scenario==='app'?{app:Buffer.from('different')}:{installFailure:true};
  const h=await harness(t,options);await assert.rejects(openAndroidNotificationReplyChannel(h.config));
  await access(h.leasePath);assert.equal(h.events.some(event=>event.startsWith('session:')),false);
});
test('uncertain native submission is never replayed; empty recovery still requires its passive test',async t=>{
  const h=await harness(t),channel=await openAndroidNotificationReplyChannel(h.config);
  await channel.sessionStep({...owner,stepId:id(6),stage:'install'});
  h.state.nativeFailure=true;h.state.intent=false;
  await assert.rejects(channel.submitNotificationReply(submission));
  await assert.rejects(channel.submitNotificationReply({...submission,stepId:id(10)}));
  await assert.rejects(channel.sessionStep({...owner,stepId:id(8),stage:'clear'}));
  assert.deepEqual(await channel.reconcileNotification(observation),{mode:'cleanup-empty',stepId:observation.stepId});
  await channel.sessionStep({...owner,stepId:id(8),stage:'clear'});await channel.close({runId:owner.runId});
  assert.equal(h.events.filter(event=>event==='reply:submit').length,1);
});
test('a live product process prevents recovery, swap and lease release',async t=>{
  const h=await harness(t),channel=await openAndroidNotificationReplyChannel(h.config);
  await channel.sessionStep({...owner,stepId:id(6),stage:'install'});await channel.submitNotificationReply(submission);
  h.state.live=true;const before=h.events.filter(event=>event.startsWith('install ')).length;
  await assert.rejects(channel.reconcileNotification(observation));await assert.rejects(channel.close({runId:owner.runId}));
  assert.equal(h.events.filter(event=>event.startsWith('install ')).length,before);
  channel.abort();assert.equal(channel.settled(),false);await access(h.leasePath);
});
test('an uncertain intent probe cannot select empty recovery',async t=>{
  const h=await harness(t),channel=await openAndroidNotificationReplyChannel(h.config);
  await channel.sessionStep({...owner,stepId:id(6),stage:'install'});await channel.submitNotificationReply(submission);
  h.state.intentError=true;await assert.rejects(channel.reconcileNotification(observation));
  assert.equal(h.events.includes('reply:cleanup-empty'),false);channel.abort();await access(h.leasePath);
});
test('foreign actors and protected emulator are rejected before mutation',async t=>{
  const h=await harness(t);await assert.rejects(openAndroidNotificationReplyChannel({...h.config,serial:'emulator-5560'}));
  assert.equal(h.events.length,0);const channel=await openAndroidNotificationReplyChannel(h.config);
  await channel.sessionStep({...owner,stepId:id(6),stage:'install'});
  await assert.rejects(channel.submitNotificationReply({...submission,profileId:id(9)}));
  assert.equal(h.events.includes('reply:submit'),false);channel.abort();await access(h.leasePath);
});
test('wrong local APK package is rejected before installation',async t=>{
  const h=await harness(t,{badPackage:true});await assert.rejects(openAndroidNotificationReplyChannel(h.config));
  assert.equal(h.events.some(event=>event.startsWith('install ')),false);await access(h.leasePath);
});
test('a live host after the final probe prevents release and a closure receipt',async t=>{
  const h=await harness(t),channel=await openAndroidNotificationReplyChannel(h.config);
  h.state.liveAfterProbe=true;await assert.rejects(channel.close({runId:owner.runId}));
  assert.equal(channel.settled(),false);await access(h.leasePath);channel.abort();
});
