import test from "node:test";
import assert from "node:assert/strict";
import {createIosDeepLinkUi} from "./e2e-fixtures/chat-deep-link-ios.mjs";
const body="Deep link 11111111-1111-4111-8111-111111111111";

test('native renewal cold observes only cold and never claims renewed warm acceptance',async()=>{
  const requests=[];
  const ui=createIosDeepLinkUi({nativeRenewalMode:'cold',channel:{observeChat:async input=>{requests.push(input);return {passed:true};}}});
  const result=await ui.run({target:{threadId:'123',messageId:'456'},body});
  assert.equal(ui.nativeExpiryMode,'cold');assert.deepEqual(requests.map(r=>r.mode),['cold']);
  assert.equal(result.scope,'ios_external_owned_message_cold_with_expired_metadata_and_back');
  await ui.close();
  for(const options of [{nativeRenewalMode:'warm'},{nativeRenewalMode:'cold',targetMode:'missing-message'}])
    assert.throws(()=>createIosDeepLinkUi({...options,channel:{observeChat:async()=>{}}}));
});
test("observes owned target cold then warm with different step IDs and allows closure",async()=>{
  const requests=[];
  const channel={observeChat:async input=>{requests.push(input);return {passed:true,mode:input.mode};}};
  const ui=createIosDeepLinkUi({channel});
  const result=await ui.run({target:{threadId:"123",messageId:"456"},body});
  assert.equal(result.passed,true);assert.deepEqual(requests.map(item=>item.mode),["cold","warm"]);
  assert.notEqual(requests[0].stepId,requests[1].stepId);
  assert.ok(requests.every(item=>item.threadId==="123"&&item.messageId==="456"&&item.body===body));
  await ui.close();await assert.rejects(ui.run({target:{threadId:"123",messageId:"456"},body}));
});
test("failed observation prevents UI closure, hence coordinator clear/retirement",async()=>{
  let calls=0;
  const ui=createIosDeepLinkUi({channel:{observeChat:async()=>{calls++;throw Error("unresolved");}}});
  await assert.rejects(ui.run({target:{threadId:"123",messageId:"456"},body}));
  await assert.rejects(ui.close(),{message:"deep_link_ios_ui_unresolved"});assert.equal(calls,1);
});
test("missing-target modes cannot be silently treated as valid-message acceptance",async()=>{
  for(const extra of [{visibleMessageId:"789"},{ownedThreadId:"789"}]){
    const ui=createIosDeepLinkUi({channel:{observeChat:async()=>{assert.fail("must not run");}}});
    await assert.rejects(ui.run({target:{threadId:"123",messageId:"456",...extra},body}));await ui.close();
  }
});

test("missing thread uses explicit mode and distinct cold/warm receipts",async()=>{
  const requests=[];
  const ui=createIosDeepLinkUi({targetMode:"missing-thread",channel:{observeChat:async input=>{
    requests.push(input);return {passed:true,mode:input.mode,targetMode:input.targetMode};
  }}});
  const result=await ui.run({target:{threadId:"123",messageId:"456",ownedThreadId:"789"},body});
  assert.equal(result.scope,"ios_external_missing_thread_cold_warm_and_back");
  assert.deepEqual(requests.map(r=>r.mode),["cold","warm"]);
  assert.ok(requests.every(r=>r.targetMode==="missing-thread"));
  await ui.close();
});
test("missing-thread mode refuses owned or unspecified targets before delivery",async()=>{
  for(const target of [{threadId:"123",messageId:"456"},{threadId:"123",messageId:"456",ownedThreadId:"123"}]){
    const ui=createIosDeepLinkUi({targetMode:"missing-thread",channel:{observeChat:()=>assert.fail("no delivery")}});
    await assert.rejects(ui.run({target,body}));await ui.close();
  }
  assert.throws(()=>createIosDeepLinkUi({targetMode:"unknown",channel:{observeChat(){}}}));
});

test("missing message carries its distinct control through both deliveries",async()=>{
  const requests=[];
  const ui=createIosDeepLinkUi({targetMode:"missing-message",channel:{observeChat:async input=>{
    requests.push(input);return {passed:true,mode:input.mode,targetMode:input.targetMode};
  }}});
  const result=await ui.run({target:{threadId:"123",messageId:"456",visibleMessageId:"789"},body});
  assert.equal(result.scope,"ios_external_missing_message_cold_warm_and_back");
  assert.deepEqual(requests.map(r=>r.mode),["cold","warm"]);
  assert.ok(requests.every(r=>r.targetMode==="missing-message"&&r.visibleMessageId==="789"));
  await ui.close();
});

test("missing message refuses absent, identical or foreign control shapes before delivery",async()=>{
  for(const extra of [{},{visibleMessageId:"456"},{visibleMessageId:"0"},{visibleMessageId:"789",ownedThreadId:"123"}]){
    const ui=createIosDeepLinkUi({targetMode:"missing-message",channel:{observeChat:()=>assert.fail("no delivery")}});
    await assert.rejects(ui.run({target:{threadId:"123",messageId:"456",...extra},body}));await ui.close();
  }
});
