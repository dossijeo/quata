import test from 'node:test';
import assert from 'node:assert/strict';
import {validateAndroidOwnedSessionReceipt,runAndroidDeepLinkSessionStep} from './e2e-fixtures/chat-deep-link-android-session-step.mjs';
const id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
const input={runId:id(1),stepId:id(2),stage:'read-owned',profileId:id(3),authUserId:id(4)};
const original=()=>({runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true,
  privateSession:{profileId:id(3),authUserId:id(4),authSessionId:id(5),
    accessToken:'synthetic.'+Buffer.from(JSON.stringify({sub:id(4),session_id:id(5),exp:2000000000})).toString('base64url')+'.synthetic',
    refreshToken:'synthetic-only-refresh',expiresAt:2000000000,email:'fixture@example.invalid',displayName:'Fixture',isOfficial:false}});

test('native session read binds owner, step, receipt and stored expiry without authenticating the token',()=>{
  assert.equal(validateAndroidOwnedSessionReceipt({input,receipt:original()}),true);
  for(const [section,key,value] of [
    ['receipt','runId',id(9)],['receipt','stepId',id(9)],['receipt','verified',false],
    ['session','profileId',id(9)],['session','authUserId',id(9)],['session','authSessionId',id(9)],
    ['session','expiresAt',2000000001],['session','refreshToken',''],['session','isOfficial','false'],
    ['session','accessToken','private-secret-marker'],['session','unexpected','private-secret-marker']]) {
    const receipt=original();(section==='receipt'?receipt:receipt.privateSession)[key]=value;
    assert.throws(()=>validateAndroidOwnedSessionReceipt({input,receipt}),{message:'deep_link_android_owned_session_receipt_invalid'});
  }
});

test('read-owned rejects malformed or extended ownership input before starting adb',async()=>{
  for(const value of [{...input,profileId:'wrong'},{...input,password:'private-secret-marker'},{...input,authUserId:undefined}]) {
    await assert.rejects(runAndroidDeepLinkSessionStep({adb:'must-not-execute',serial:'emulator-5560',input:value,logPath:'unused'}),
      {message:'deep_link_android_step_configuration_invalid'});
  }
});
