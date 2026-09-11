import test from "node:test";
import assert from "node:assert/strict";
import {createMissingMessageReadObserver,verifyMissingDeepLinkMessage} from "./e2e-fixtures/chat-deep-link-missing-message.mjs";
const target={threadId:"123",messageId:"999",visibleMessageId:"456"};
const sample=()=>({request:{p_actor_profile_id:"owned",p_thread_id:123,p_limit:250,p_known_message_ids:[]},status:200,
  body:{thread:{id:123},messages:[{id:456,thread_id:123}]}});
test("requires complete successful owned history, not an empty snapshot or timeout",()=>{
  const observer=createMissingMessageReadObserver({target,profileId:"owned"});
  assert.equal(observer.passed(),false);observer.observe(sample());assert.equal(observer.passed(),true);
  const incremental=sample();incremental.request.p_known_message_ids=[456];incremental.body.messages=[];
  observer.observe(incremental);assert.equal(observer.passed(),true);
  observer.fail();assert.equal(observer.passed(),false);observer.observe(sample());assert.equal(observer.passed(),false);
});
test("rejects HTTP failure, malformed payload, wrong actor/thread, present target and incomplete page",()=>{
  for(const change of [x=>x.status=503,x=>x.body=null,x=>x.request.p_actor_profile_id="other",x=>x.body.thread.id=124,
    x=>x.body.messages=[],x=>x.body.messages[0].id=999,x=>x.body.messages[0].thread_id=124,
    x=>x.request.p_limit=1,x=>x.request.p_known_message_ids=[888]]) {
    const observer=createMissingMessageReadObserver({target,profileId:"owned"});const value=sample();change(value);
    observer.observe(value);assert.equal(observer.passed(),false);assert.equal(observer.diagnostics().failed,true);
  }
});
test("DB proof requires owned thread, exactly the fixture message and independently absent ID",async()=>{
  const good={owned:true,absent:true,count:"1",visible:true};const plan={uniqueKey:"owned-key",ownerId:"owned",body:"fixture",messageKey:"message-key"};
  for(const row of [good,{...good,owned:false},{...good,absent:false},{...good,count:"2"},{...good,visible:false}]) {
    const client={query:async(sql,args)=>{assert.match(sql,/select/);assert.deepEqual(args,["123","999","456","owned-key","owned","fixture","message-key"]);return {rows:[row]};}};
    if(row===good)assert.equal(await verifyMissingDeepLinkMessage({client,target,plan}),true);
    else await assert.rejects(verifyMissingDeepLinkMessage({client,target,plan}),/fixture_changed/);
  }
});
