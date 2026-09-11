import test from "node:test";
import assert from "node:assert/strict";
import {observeDeepLinkRefresh} from "./e2e-fixtures/chat-deep-link-refresh.mjs";

function setup() {
  const id=n=>`${n.repeat(8)}-${n.repeat(4)}-4${n.repeat(3)}-8${n.repeat(3)}-${n.repeat(12)}`;
  const record={runId:id("1"),profileId:id("2"),authUserId:id("3")};
  const ticket={...record,authSessionId:id("4"),webSessionId:id("5"),purpose:"deep_link",clientInstanceId:"owned-client"};
  let saved={...record,state:{sessions:[{...ticket,privateLoginResponse:{status:200,body:{
    profile:{id:record.profileId},session:{refresh_token:"original-private"},web_session:{token:"web-private"},
  }}}]}};
  const events=[];
  const token=claims=>`header.${Buffer.from(JSON.stringify(claims)).toString("base64url")}.signature`;
  const body={access_token:token({sub:record.authUserId,session_id:ticket.authSessionId}),refresh_token:"rotated-private",expires_at:2000000000};
  const args={record,ticket,session:{profileId:record.profileId,refreshToken:"original-private",webSessionToken:"web-private"},
    backendUrl:"https://example.test",publicKey:"public",
    journal:{read:async()=>structuredClone(saved),checkpoint:async state=>{saved={...saved,state:structuredClone(state)};events.push("checkpoint");}},
    client:{query:async()=>{events.push("db");return {rowCount:1,rows:[{auth_session_id:ticket.authSessionId,web_session_id:ticket.webSessionId}]};}},
    requestRefresh:async(url,options)=>{
      events.push("refresh");assert.equal(saved.state.sessions[0].refreshAttempt.requestStarted,true);
      assert.equal(url.pathname,"/auth/v1/token");assert.equal(url.search,"?grant_type=refresh_token");
      assert.equal(JSON.parse(options.body).refresh_token,"original-private");
      return {status:200,json:async()=>body};
    },
    fetchImpl:async()=>{events.push("verify");assert.equal(saved.state.sessions[0].refreshAttempt.privateResponse.body.access_token,body.access_token);
      return {ok:true,json:async()=>({id:record.authUserId})};},
  };
  return {args,body,token,events,state:()=>saved};
}

test("refresh intent precedes transport, response precedes validation, original receipt stays intact",async()=>{
  const s=setup();assert.deepEqual(await observeDeepLinkRefresh(s.args),{verified:true});
  assert.deepEqual(s.events,["db","checkpoint","refresh","checkpoint","verify","db","checkpoint"]);
  const entry=s.state().state.sessions[0];
  for(const [key,value] of Object.entries(s.args.ticket))assert.equal(entry[key],value);
  assert.equal(entry.refreshAttempt.verified,true);
  await assert.rejects(observeDeepLinkRefresh(s.args),/ticket_unavailable/);
  assert.equal(s.events.filter(e=>e==="refresh").length,1);
});

test("unowned or mismatched original session cannot send a refresh",async()=>{
  for(const patch of ["ownership","receipt","record"]) {
    const s=setup();
    if(patch==="ownership")s.args.client.query=async()=>({rowCount:0,rows:[]});
    if(patch==="receipt")s.args.ticket.authSessionId="66666666-6666-4666-8666-666666666666";
    if(patch==="record")s.args.record.runId="66666666-6666-4666-8666-666666666666";
    await assert.rejects(observeDeepLinkRefresh(s.args));
    assert.equal(s.events.includes("refresh"),false);
  }
});

test("mixed credentials cannot refresh another session before identity verification",async()=>{
  for(const key of ["refreshToken","webSessionToken"]) {
    const s=setup();s.args.session[key]="foreign-private";
    await assert.rejects(observeDeepLinkRefresh(s.args),{message:"deep_link_refresh_credentials_mismatch"});
    assert.deepEqual(s.events,[]);
  }
});

test("lost response and rejection retain unresolved intent and forbid retry",async()=>{
  for(const mode of ["lost","rejected","unreadable"]) {
    const s=setup();let requests=0;
    s.args.requestRefresh=async()=>{requests++;if(mode==="lost")throw Error("private remote data");
      return {status:400,json:async()=>{if(mode==="unreadable")throw Error("private body");return {error_code:"refresh_token_not_found"};}};};
    await assert.rejects(observeDeepLinkRefresh(s.args),{message:mode==="rejected"?"deep_link_refresh_unresolved_response":"deep_link_refresh_response_uncertain"});
    assert.equal(s.state().state.sessions[0].refreshAttempt.verified,undefined);
    await assert.rejects(observeDeepLinkRefresh(s.args),/ticket_unavailable/);assert.equal(requests,1);
  }
});

test("unexpected actor or session preserves response without overwriting receipt",async()=>{
  for(const mode of ["actor","claims","db","malformed"]) {
    const s=setup();
    if(mode==="actor")s.args.fetchImpl=async()=>({ok:true,json:async()=>({id:"another-actor"})});
    if(mode==="claims")s.body.access_token=s.token({sub:s.args.record.authUserId,session_id:"another-session"});
    if(mode==="malformed")s.body.access_token="private-malformed-token";
    if(mode==="db") {const original=s.args.client.query;let calls=0;s.args.client.query=async()=>++calls===1?original():{rowCount:0,rows:[]};}
    await assert.rejects(observeDeepLinkRefresh(s.args),{message:"deep_link_refresh_receipt_unverified"});
    const entry=s.state().state.sessions[0];assert.equal(entry.authSessionId,s.args.ticket.authSessionId);
    assert.deepEqual(entry.refreshAttempt.privateResponse.body,s.body);assert.equal(entry.refreshAttempt.verified,undefined);
  }
});

test("failed intent checkpoint prevents transport; failed verification checkpoint remains unresolved",async()=>{
  for(const failAt of [1,3]) {
    const s=setup();const checkpoint=s.args.journal.checkpoint;let calls=0;
    s.args.journal.checkpoint=async state=>{if(++calls===failAt)throw Error("disk full");await checkpoint(state);};
    await assert.rejects(observeDeepLinkRefresh(s.args),/disk full/);
    assert.equal(s.events.includes("refresh"),failAt===3);
    assert.equal(s.state().state.sessions[0].refreshAttempt?.verified,undefined);
  }
});

test("delivery follows durable response and precedes verification, even if delivery fails",async()=>{
  for(const failDelivery of [false,true]){
    const s=setup();s.args.responseJournaled=async()=>{
      assert.equal(s.state().state.sessions[0].refreshAttempt.privateResponse.status,200);
      assert.equal(s.events.includes("verify"),false);s.events.push("deliver");
      if(failDelivery)throw Error("synthetic browser closed");
    };
    assert.deepEqual(await observeDeepLinkRefresh(s.args),{verified:true});
    assert.ok(s.events.indexOf("deliver")<s.events.indexOf("verify"));
  }
});

test("response persistence failure prevents delivery and retains unresolved intent",async()=>{
  const s=setup();let writes=0,delivered=false;const checkpoint=s.args.journal.checkpoint;
  s.args.journal.checkpoint=async state=>{if(++writes===2)throw Error("disk full");await checkpoint(state);};
  s.args.responseJournaled=async()=>{delivered=true;};
  await assert.rejects(observeDeepLinkRefresh(s.args),/disk full/);
  assert.equal(delivered,false);assert.equal(s.state().state.sessions[0].refreshAttempt.verified,undefined);
});
