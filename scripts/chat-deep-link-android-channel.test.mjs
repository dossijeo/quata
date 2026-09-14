import test from "node:test";
import assert from "node:assert/strict";
import {mkdtemp,access,rm,mkdir} from "node:fs/promises";
import path from "node:path";
import {randomUUID} from 'node:crypto';
import {openAndroidDeepLinkSessionChannel,retireAndroidDeepLinkForward} from "./e2e-fixtures/chat-deep-link-android-session-step.mjs";
const root=path.resolve("build-reports/android-external-sender/channel-contracts");
const receipt=input=>({runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true});
async function withDirectory(run) {
  await mkdir(root,{recursive:true});const directory=await mkdtemp(path.join(root,"test-"));
  try {await run(directory);} finally {
    assert.equal(path.dirname(path.resolve(directory)),root);
    assert.ok(path.basename(directory).startsWith("test-"));
    await rm(directory,{recursive:true,force:true,maxRetries:3,retryDelay:20});
  }
}
for(const operation of ["step","close"])test(`abort during pending ${operation} permanently preserves uncertainty and lease`,async()=>withDirectory(async directory=>{
  const leasePath=path.join(directory,"device.lock");let calls=0,resume;
  const channel=await openAndroidDeepLinkSessionChannel({adb:"synthetic",serial:"emulator-5560",leasePath,
    evidenceDirectory:directory,stepImpl:async({input})=>{
      if(++calls===1)return receipt(input);
      return new Promise(resolve=>{resume=()=>resolve(receipt(input));});
    }});
  const pending=operation==="step"?channel.sessionStep({stage:"install",runId:"synthetic",stepId:"one"}):channel.close();
  channel.abort();resume();
  await assert.rejects(pending,/aborted/);
  assert.equal(channel.settled(),false);await access(leasePath);
  await assert.rejects(channel.close());
  await assert.rejects(channel.sessionStep({stage:"install"}));
}));

test("normal close removes only the owned lease after final empty probe",async()=>withDirectory(async directory=>{
  const leasePath=path.join(directory,"device.lock"),stages=[];
  const channel=await openAndroidDeepLinkSessionChannel({adb:"synthetic",serial:"emulator-5560",leasePath,
    evidenceDirectory:directory,stepImpl:async({input})=>{stages.push(input.stage);return receipt(input);}});
  await channel.close();assert.equal(channel.settled(),true);
  assert.deepEqual(stages,["probe-empty","probe-empty"]);
  await assert.rejects(access(leasePath),{code:"ENOENT"});
  channel.abort();assert.equal(channel.settled(),true);
}));

for(const outcome of ['complete','foreign-session','unchanged-refresh','read-lost','clear-lost','abort-read'])
test(`expired Android channel ${outcome} binds renewed snapshot and preserves uncertainty`,async()=>withDirectory(async directory=>{
  const leasePath=path.join(directory,'device.lock'),stages=[];
  const runId=randomUUID(),profileId=randomUUID(),authUserId=randomUUID(),authSessionId=randomUUID();
  const token=id=>'synthetic.'+Buffer.from(JSON.stringify({sub:authUserId,session_id:id,exp:2000000000})).toString('base64url')+'.synthetic';
  const input={runId,stepId:randomUUID(),stage:'install-expired',profileId,authUserId,authSessionId,
    accessToken:'synthetic-original',refreshToken:'synthetic-original-refresh',expiresAt:1,originalExpiresAt:2000000000,
    email:'fixture@example.invalid',displayName:'Synthetic',isOfficial:false};
  const snapshot={profileId,authUserId,authSessionId:outcome==='foreign-session'?randomUUID():authSessionId,
    accessToken:token(authSessionId),refreshToken:outcome==='unchanged-refresh'?input.refreshToken:'synthetic-rotated',
    expiresAt:2000000000,email:input.email,displayName:input.displayName,isOfficial:false};
  snapshot.accessToken=token(snapshot.authSessionId);
  let channel;
  channel=await openAndroidDeepLinkSessionChannel({adb:'synthetic',serial:'emulator-5560',leasePath,evidenceDirectory:directory,
    stepImpl:async({input:command})=>{
      stages.push(command.stage);
      if(command.stage==='read-owned') {
        if(outcome==='read-lost')throw Error('synthetic-uncertain');
        if(outcome==='abort-read')channel.abort();
        return {...receipt(command),privateSession:snapshot};
      }
      if(command.stage==='clear'&&outcome==='clear-lost')throw Error('synthetic-uncertain');
      return receipt(command);
    }});
  await channel.sessionStep(input);
  await assert.rejects(channel.close());
  await assert.rejects(channel.sessionStep({...input,stepId:randomUUID(),stage:'clear-expired'}));
  const read={runId,stepId:randomUUID(),stage:'read-owned',profileId,authUserId};
  await assert.rejects(channel.sessionStep({...read,profileId:randomUUID()}));
  assert.deepEqual(stages,['probe-empty','install-expired']);
  if(['foreign-session','unchanged-refresh','read-lost','abort-read'].includes(outcome)) {
    await assert.rejects(channel.sessionStep(read));
    await assert.rejects(channel.sessionStep({...read,stepId:randomUUID()}));
  } else {
    await channel.sessionStep(read);
    await assert.rejects(channel.close());
    await assert.rejects(channel.sessionStep({...input,stage:'clear',stepId:randomUUID()}));
    await assert.rejects(channel.sessionStep({runId,stage:'clear',stepId:read.stepId,...snapshot}));
    const clear={runId,stage:'clear',stepId:randomUUID(),...snapshot};
    if(outcome==='clear-lost')await assert.rejects(channel.sessionStep(clear));
    else {await channel.sessionStep(clear);await channel.close();assert.equal(channel.settled(),true);
      assert.deepEqual(stages,['probe-empty','install-expired','read-owned','clear','probe-empty']);
      await assert.rejects(access(leasePath),{code:'ENOENT'});return;}
  }
  await assert.rejects(channel.close());assert.equal(channel.settled(),false);await access(leasePath);channel.abort();
}));

for(const outcome of ['complete','response-lost','wrong-receipt','abort-probe','final-probe-lost'])
test(`expired Android absence ${outcome} requires exact receipt and final probe`,async()=>withDirectory(async directory=>{
  const leasePath=path.join(directory,'device.lock'),stages=[];
  const runId=randomUUID(),installStep=randomUUID();let channel;
  channel=await openAndroidDeepLinkSessionChannel({adb:'synthetic',serial:'emulator-5560',leasePath,evidenceDirectory:directory,
    stepImpl:async({input})=>{
      stages.push(input.stage);
      if(stages.length===3) {
        if(outcome==='response-lost')throw Error('synthetic-uncertain');
        if(outcome==='wrong-receipt')return {...receipt(input),runId:randomUUID()};
        if(outcome==='abort-probe')channel.abort();
      }
      if(stages.length===4&&outcome==='final-probe-lost')throw Error('synthetic-uncertain');
      return receipt(input);
    }});
  await channel.sessionStep({runId,stepId:installStep,stage:'install-expired'});
  const input={runId,stepId:randomUUID(),stage:'probe-empty'};
  for(const invalid of [{...input,runId:randomUUID()},{...input,stepId:installStep},{...input,extra:true}])
    await assert.rejects(channel.sessionStep(invalid),/order_invalid/);
  assert.deepEqual(stages,['probe-empty','install-expired']);
  if(['response-lost','wrong-receipt','abort-probe'].includes(outcome)) {
    await assert.rejects(channel.sessionStep(input));
    await assert.rejects(channel.sessionStep({...input,stepId:randomUUID()}));
    await assert.rejects(channel.close());
  } else {
    await channel.sessionStep(input);
    assert.equal(channel.settled(),false);await access(leasePath);
    await assert.rejects(channel.sessionStep({...input,stepId:randomUUID()}));
    if(outcome==='final-probe-lost')await assert.rejects(channel.close());
    else {
      await channel.close();assert.equal(channel.settled(),true);
      assert.deepEqual(stages,['probe-empty','install-expired','probe-empty','probe-empty']);
      await assert.rejects(access(leasePath),{code:'ENOENT'});return;
    }
  }
  assert.equal(channel.settled(),false);await access(leasePath);channel.abort();
}));

test("forward retirement fails closed on removal failure or surviving owned mapping",async()=>{
  for(const mode of ["remove-failed","still-live","list-failed"]) {
    await assert.rejects(retireAndroidDeepLinkForward({adb:"synthetic",serial:"emulator-5560",port:"41234",
      execute:async(_file,args)=>{
        if(args.includes("--remove")&&mode==="remove-failed")throw Error("timeout");
        if(args.includes("--list")&&mode==="list-failed")throw Error("timeout");
        return {stdout:mode==="still-live"?"emulator-5560 tcp:41234 localabstract:owned\n":""};
      }}),{message:"deep_link_android_forward_cleanup_unresolved"});
  }
});

test("forward retirement verifies absence without removing other mappings",async()=>{
  const calls=[];
  await retireAndroidDeepLinkForward({adb:"synthetic",serial:"emulator-5560",port:"41234",
    execute:async(_file,args)=>{calls.push(args);return {stdout:"emulator-5562 tcp:41234 localabstract:other\nemulator-5560 tcp:41235 localabstract:other\n"};}});
  assert.deepEqual(calls,[["-s","emulator-5560","forward","--remove","tcp:41234"],["forward","--list"]]);
});
