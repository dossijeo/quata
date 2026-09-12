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
 for(const mutate of [v=>v.ticketId='invalid',v=>v.extra='unexpected',v=>v.password='short',v=>v.countryCode='34']) {
  const value=input();mutate(value);assert.throws(()=>validateIosNativeLoginInput(value));
 }
});
