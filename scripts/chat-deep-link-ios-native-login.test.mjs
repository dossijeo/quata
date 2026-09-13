import test from 'node:test';
import assert from 'node:assert/strict';
import {createIosNativeLoginUi,validateIosNativeLoginInput} from './e2e-fixtures/chat-deep-link-ios-native-login.mjs';
const id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
const input=()=>({runId:id(1),stepId:id(2),ticketId:id(3),profileId:id(4),authUserId:id(5),countryCode:'240',phone:'799000000000',password:'Synthetic-only-password',messageId:'456'});
const delivery={runId:id(1),mode:'cold',target:{threadId:'123',messageId:'456'}};
function fixture(failure) {
 const calls=[];
 const ui=createIosNativeLoginUi({channel:{nativeGate:async request=>{
  calls.push('gate');if(failure==='gate')throw Error('synthetic');return {...request,passed:true};
 },nativeLogin:async request=>{
  calls.push('login');if(failure==='login')throw Error('synthetic');
  return {runId:request.runId,stepId:failure==='receipt'?id(9):request.stepId,passed:true};
 }}});return {ui,calls};
}
test('iOS observes gate before one Login and permits only completed or pre-login close',async()=>{
 const {ui,calls}=fixture();await assert.rejects(ui.login(input()));
 await ui.deliver(delivery);await ui.login(input());await ui.close();
 await assert.rejects(ui.login(input()));await assert.rejects(ui.deliver(delivery));assert.deepEqual(calls,['gate','login']);
 const unused=fixture();await unused.ui.close();assert.deepEqual(unused.calls,[]);
 const cancelled=fixture();await cancelled.ui.deliver(delivery);await cancelled.ui.close();assert.deepEqual(cancelled.calls,['gate']);
});
for(const failure of ['gate','login','receipt'])test(`uncertain ${failure} cannot replay or close`,async()=>{
 const {ui,calls}=fixture(failure);
 if(failure==='gate')await assert.rejects(ui.deliver(delivery));
 else {await ui.deliver(delivery);await assert.rejects(ui.login(input()));}
 await assert.rejects(ui.login(input()));await assert.rejects(ui.deliver(delivery));await assert.rejects(ui.close());
 assert.deepEqual(calls,failure==='gate'?['gate']:['gate','login']);
});
test('private iOS input rejects foreign target, invalid identity and extra fields before dispatch',async()=>{
 for(const mutate of [v=>v.messageId='999',v=>v.runId=id(9)]) {
  const {ui,calls}=fixture();await ui.deliver(delivery);const value=input();mutate(value);await assert.rejects(ui.login(value));assert.deepEqual(calls,['gate']);
 }
 for(const mutate of [v=>v.ticketId='invalid',v=>v.extra='unexpected',v=>v.password='short',v=>v.countryCode='34',
   v=>v.variant='resume-chat',v=>v.variant=null,v=>v.variant=false]) {
  const value=input();mutate(value);assert.throws(()=>validateIosNativeLoginInput(value));
 }
});

test('cancel then Feed requires its own receipt and cannot accept a resume receipt or replay',async()=>{
 for(const receiptVariant of ['cancel-then-feed',undefined,'wrong']) {
  let calls=0;
  const ui=createIosNativeLoginUi({channel:{
   nativeGate:async request=>({...request,passed:true}),
   nativeLogin:async request=>{calls++;assert.equal(request.variant,'cancel-then-feed');
    return {runId:request.runId,stepId:request.stepId,passed:true,...(receiptVariant?{variant:receiptVariant}:{})};}
  }});
  await ui.deliver(delivery);
  const request={...input(),variant:'cancel-then-feed'};
  if(receiptVariant==='cancel-then-feed') {
   assert.equal((await ui.login(request)).variant,receiptVariant);await ui.close();
  } else {
   await assert.rejects(ui.login(request));await assert.rejects(ui.close());
  }
  await assert.rejects(ui.login(request));assert.equal(calls,1);
 }
});
