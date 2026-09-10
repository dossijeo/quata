import test from "node:test";
import assert from "node:assert/strict";
import {createIosDeepLinkUi} from "./e2e-fixtures/chat-deep-link-ios.mjs";
const body="Deep link 11111111-1111-4111-8111-111111111111";
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
