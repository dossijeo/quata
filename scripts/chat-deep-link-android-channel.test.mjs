import test from "node:test";
import assert from "node:assert/strict";
import {mkdtemp,access,rm,mkdir} from "node:fs/promises";
import path from "node:path";
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
