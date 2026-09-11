import test from "node:test";
import assert from "node:assert/strict";
import {seedDeepLinkThread,removeDeepLinkThread} from "./e2e-fixtures/chat-deep-link-thread.mjs";
function fixture() {
  const runId="11111111-1111-4111-8111-111111111111";
  const plan={runId,ownerId:"22222222-2222-4222-8222-222222222222",peerId:"33333333-3333-4333-8333-333333333333",
    uniqueKey:`quata-deep-link-${runId}`,messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`};
  let saved={runId,profileId:plan.ownerId,state:{threadPlan:plan}};
  let exists=false;
  const events=[];const rows=rows=>({rowCount:rows.length,rows});
  const client={query:async(sql,args)=>{
    events.push(sql);
    if(sql.includes("select p.id"))return rows([{id:plan.ownerId},{id:plan.peerId}]);
    if(sql.includes("as native_absent"))return rows([{native_absent:true,web_absent:true}]);
    if(sql.startsWith("select id from public.chat_threads"))return rows([]);
    if(sql.startsWith("insert into public.chat_threads")){exists=true;return rows([{id:"9007199254740993"}]);}
    if(sql.startsWith("insert into public.chat_messages"))return rows([{id:"9007199254740994"}]);
    if(sql.includes("select id::text,type"))return rows(exists?[{id:"9007199254740993",type:"group",created_by_profile_id:plan.ownerId}]:[]);
    if(sql.startsWith("select profile_id"))return rows([{profile_id:plan.ownerId},{profile_id:plan.peerId}]);
    if(sql.includes("select id::text,sender_profile_id"))return rows([{id:"9007199254740994",sender_profile_id:plan.peerId,body:plan.body,client_message_id:plan.messageKey}]);
    if(sql.includes("as count"))return rows([{count:"0"}]);
    if(sql.includes("with owned_messages"))return rows([{external_messages:"0",external_conversation_state:"0",sos_events:"0",sos_recipients:"0"}]);
    if(sql.startsWith("delete from public.chat_threads"))exists=false;
    if(sql.includes("as conversation_state"))return rows([{thread:true,messages:true,participants:true,events:true,conversation_state:true}]);
    return rows([]);
  }};
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{events.push("checkpoint");saved={...saved,state:structuredClone(state)};}};
  return {args:{client,journal,plan,operationsSettled:async()=>true},events,state:()=>saved};
}
test("seeds exact text-only fixture once and preserves bigint IDs",async()=>{
  const f=fixture();assert.deepEqual(await seedDeepLinkThread(f.args),{threadId:"9007199254740993",messageId:"9007199254740994"});
  assert.equal(f.events[0],"checkpoint");
  await assert.rejects(seedDeepLinkThread(f.args),/already_started/);
});
test("push registrations or unexpected actors roll back before inserting",async()=>{
  for(const check of ["push","actors"]) {
    const f=fixture();const query=f.args.client.query;f.args.client.query=async(sql,args)=>{
      if(check==="push"&&sql.includes("as native_absent"))return {rows:[{native_absent:false,web_absent:true}]};
      if(check==="actors"&&sql.includes("select p.id"))return {rowCount:1,rows:[]};
      return query(sql,args);
    };
    await assert.rejects(seedDeepLinkThread(f.args),/seed_unresolved/);
    assert.ok(f.events.includes("rollback"));assert.ok(!f.events.some(sql=>sql.startsWith("insert ")));
  }
});
test("cleanup checks owners, participants and messages before deletion",async()=>{
  for(const check of ["owner","participants","message"]) {
    const f=fixture();await seedDeepLinkThread(f.args);f.events.length=0;const query=f.args.client.query;
    f.args.client.query=async(sql,args)=>{
      if(check==="owner"&&sql.includes("select id::text,type"))return {rowCount:1,rows:[{id:"9007199254740993",type:"group",created_by_profile_id:f.args.plan.peerId}]};
      if(check==="participants"&&sql.startsWith("select profile_id"))return {rowCount:3,rows:[]};
      if(check==="message"&&sql.includes("select id::text,sender_profile_id"))return {rowCount:2,rows:[]};
      return query(sql,args);
    };
    await assert.rejects(removeDeepLinkThread(f.args),/cleanup_unresolved/);
    assert.ok(!f.events.some(sql=>sql.startsWith("delete ")));
  }
});
test("retirement is repeatable after an already completed deletion",async()=>{
  const f=fixture();await seedDeepLinkThread(f.args);
  assert.deepEqual(await removeDeepLinkThread(f.args),{removed:true});f.events.length=0;
  assert.deepEqual(await removeDeepLinkThread(f.args),{removed:true});
  assert.ok(!f.events.some(sql=>sql.startsWith("delete ")));
});
test("attachments linked only by message also prevent cleanup",async()=>{
  const f=fixture();await seedDeepLinkThread(f.args);f.events.length=0;const query=f.args.client.query;
  f.args.client.query=async(sql,args)=>{
    if(sql.includes("from public.chat_attachments")) {
      assert.match(sql,/or message_id=\$2::bigint/);
      assert.deepEqual(args,["9007199254740993","9007199254740994"]);
      return {rows:[{count:"1"}]};
    }
    return query(sql,args);
  };
  await assert.rejects(removeDeepLinkThread(f.args),/cleanup_unresolved/);
  assert.ok(!f.events.some(sql=>sql.startsWith("delete ")));
});
