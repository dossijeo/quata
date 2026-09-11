import test from "node:test";
import assert from "node:assert/strict";
import {createMissingThreadReadObserver,verifyMissingDeepLinkThread} from "./e2e-fixtures/chat-deep-link-missing-thread.mjs";
const target={threadId:"999",ownedThreadId:"123"};
const rejected=()=>({functionName:"quata_chat_get_thread",request:{p_actor_profile_id:"owned",p_thread_id:999},status:403,
  body:{code:"42501",message:"profile is not a participant of this thread"}});
test("only audited scoped rejections settle the missing-thread observer",()=>{
  const observer=createMissingThreadReadObserver({target,profileId:"owned"});assert.equal(observer.passed(),false);
  assert.equal(observer.targets({p_thread_id:999}),true);
  assert.equal(observer.targets({p_thread_id:123}),false);
  assert.equal(observer.targets({p_thread_id:999,p_actor_profile_id:"other"}),true);
  assert.equal(observer.observe({...rejected(),functionName:"quata_chat_mark_thread_read"}),true);assert.equal(observer.passed(),false);
  assert.equal(observer.observe(rejected()),true);assert.equal(observer.passed(),true);
  assert.equal(observer.observe({functionName:"quata_chat_cleanup_empty_private_thread",request:{p_actor_profile_id:"owned",p_thread_id:999},status:200,
    body:{deleted:false,thread_id:999,reason:"not_participant"}}),true);
  assert.equal(observer.passed(),true);
});
test("network/unknown failures, mixed identity and unexpected successful reads never pass",()=>{
  for(const change of [x=>x.status=500,x=>x.status=200,x=>x.body.code="other",x=>x.body.message="other",
    x=>x.request.p_thread_id=123,x=>x.request.p_actor_profile_id="other",x=>x.functionName="other",x=>x.body=null]) {
    const observer=createMissingThreadReadObserver({target,profileId:"owned"});const value=rejected();change(value);
    assert.equal(observer.observe(value),false);observer.observe(rejected());assert.equal(observer.passed(),false);
  }
  const observer=createMissingThreadReadObserver({target,profileId:"owned"});observer.observe(rejected());observer.fail();assert.equal(observer.passed(),false);
});
test("absence proof rejects an existing thread or any residue and requires owned fixture",async()=>{
  const good={owned:true,absent:true,messages_absent:true,participants_absent:true,state_absent:true};
  for(const key of [null,...Object.keys(good)]) {
    const row={...good,...(key?{[key]:false}:{})};const client={query:async()=>({rows:[row]})};
    const operation=verifyMissingDeepLinkThread({client,target,plan:{uniqueKey:"fixture",ownerId:"owned"}});
    if(key)await assert.rejects(operation,/changed/);else assert.equal(await operation,true);
  }
});
