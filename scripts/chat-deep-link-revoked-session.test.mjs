import test from "node:test";
import assert from "node:assert/strict";
import {prepareRevokedDeepLinkSession,observeRevokedDeepLinkRefresh} from "./e2e-fixtures/chat-deep-link-revoked-session.mjs";
function setup(){
  const id=n=>`${n.repeat(8)}-${n.repeat(4)}-4${n.repeat(3)}-8${n.repeat(3)}-${n.repeat(12)}`;
  const record={runId:id("1"),profileId:id("2"),authUserId:id("3")};
  const ticket={...record,authSessionId:id("4"),webSessionId:id("5"),purpose:"deep_link",clientInstanceId:"owned-client"};
  const session={profileId:record.profileId,refreshToken:"original-private",webSessionToken:"web-private"};
  let saved={...record,state:{sessions:[{...ticket,privateLoginResponse:{status:200,body:{profile:{id:record.profileId},
    session:{refresh_token:session.refreshToken},web_session:{token:session.webSessionToken}}}}]}};
  const events=[];let auth=true,web=true;
  const args={record,ticket,session,backendUrl:"https://example.test",publicKey:"public",operationsSettled:async()=>true,
    journal:{read:async()=>structuredClone(saved),checkpoint:async state=>{saved={...saved,state:structuredClone(state)};events.push("checkpoint");}},
    client:{query:async(sql,params)=>{
      if(sql.includes("as owned")){events.push("audit");return {rowCount:1,rows:[{owned:true,auth_count:auth?1:0,exact_auth:auth,web_count:web?1:0,exact_web:params[7]===!web}]};}
      if(sql.startsWith("update public.web_client_sessions")){assert.equal(saved.state.sessions[0].revocation.started,true);assert.equal(params[0],ticket.webSessionId);web=false;events.push("revoke_web");}
      if(sql.startsWith("delete from auth.sessions")){assert.equal(params[0],ticket.authSessionId);assert.equal(params[1],record.authUserId);auth=false;events.push("revoke_auth");}
      if(sql.includes("as auth_count"))return {rows:[{auth_count:auth?1:0,web_count:web?1:0}]};
      return {rowCount:0,rows:[]};
    }},
    requestRefresh:async()=>{events.push("request");assert.equal(saved.state.sessions[0].refreshAttempt.requestStarted,true);
      return {status:400,json:async()=>({error_code:"refresh_token_not_found"})};},
    responseJournaled:async()=>{assert.equal(saved.state.sessions[0].refreshAttempt.privateResponse.status,400);events.push("deliver");},
  };
  return {args,events,state:()=>saved};
}
test("exact owned session is revoked after durable intent, rejection follows verified absence",async()=>{
  const s=setup();assert.deepEqual(await prepareRevokedDeepLinkSession(s.args),{revoked:true});
  assert.equal(s.state().state.sessions[0].revocation.verified,true);
  assert.ok(s.events.indexOf("checkpoint")<s.events.indexOf("revoke_web"));
  assert.deepEqual(await observeRevokedDeepLinkRefresh(s.args),{verified:true,rejected:true});
  assert.equal(s.state().state.sessions[0].refreshAttempt.verified,true);
  assert.equal(s.state().state.sessions[0].authSessionId,s.args.ticket.authSessionId);
  await assert.rejects(prepareRevokedDeepLinkSession(s.args),/ticket_used/);
  await assert.rejects(observeRevokedDeepLinkRefresh(s.args),/ticket_unavailable/);
  assert.equal(s.events.filter(e=>e==="request").length,1);
});
test("mixed credentials, foreign actor, additional ticket and unsettled browser cannot revoke",async()=>{
  for(const variant of ["token","actor","extra","browser"]){
    const s=setup();
    if(variant==="token")s.args.session.refreshToken="foreign";
    if(variant==="actor")s.args.client.query=async()=>({rowCount:1,rows:[{owned:false}]});
    if(variant==="extra"){const state=await s.args.journal.read();state.state.sessions.push({...s.args.ticket,clientInstanceId:"another-client"});await s.args.journal.checkpoint(state.state);s.events.length=0;}
    if(variant==="browser")s.args.operationsSettled=async()=>false;
    await assert.rejects(prepareRevokedDeepLinkSession(s.args));assert.equal(s.events.includes("revoke_web"),false);assert.equal(s.events.includes("revoke_auth"),false);
  }
});
test("failed revocation keeps unresolved intent and cannot be repeated",async()=>{
  const s=setup();const query=s.args.client.query;s.args.client.query=async(sql,params)=>{
    if(sql.startsWith("delete from auth.sessions"))throw Error("private SQL failure");return query(sql,params);
  };
  await assert.rejects(prepareRevokedDeepLinkSession(s.args));assert.equal(s.state().state.sessions[0].revocation.verified,undefined);
  await assert.rejects(prepareRevokedDeepLinkSession(s.args),/ticket_used/);
  await assert.rejects(observeRevokedDeepLinkRefresh(s.args),/ticket_unavailable/);
});
test("only known rejection with unchanged absence can verify; success and network failure cannot",async()=>{
  for(const variant of ["success","unknown","lost","new_session"]){
    const s=setup();await prepareRevokedDeepLinkSession(s.args);
    if(variant==="success")s.args.requestRefresh=async()=>({status:200,json:async()=>({access_token:"unexpected-private"})});
    if(variant==="unknown")s.args.requestRefresh=async()=>({status:500,json:async()=>({error_code:"unexpected_failure"})});
    if(variant==="lost")s.args.requestRefresh=async()=>{throw Error("private remote error");};
    if(variant==="new_session"){const query=s.args.client.query;let audits=0;s.args.client.query=async(sql,params)=>{
      if(sql.includes("as owned")&&++audits===2)return {rowCount:1,rows:[{owned:true,auth_count:1,exact_auth:true,web_count:0,exact_web:true}]};return query(sql,params);
    };}
    s.args.responseJournaled=async()=>{};
    await assert.rejects(observeRevokedDeepLinkRefresh(s.args));assert.equal(s.state().state.sessions[0].refreshAttempt.verified,undefined);
    await assert.rejects(observeRevokedDeepLinkRefresh(s.args),/ticket_unavailable/);
  }
});
test("no revocation receipt means no refresh request",async()=>{
  const s=setup();await assert.rejects(observeRevokedDeepLinkRefresh(s.args),/ticket_unavailable/);assert.deepEqual(s.events,[]);
});

test("failed intent persistence cannot revoke; failed response persistence cannot deliver",async()=>{
  const s=setup();s.args.journal.checkpoint=async()=>{throw Error("disk full");};
  await assert.rejects(prepareRevokedDeepLinkSession(s.args),/disk full/);
  assert.equal(s.events.includes("revoke_auth"),false);assert.equal(s.events.includes("revoke_web"),false);
  const t=setup();await prepareRevokedDeepLinkSession(t.args);const checkpoint=t.args.journal.checkpoint;let writes=0,delivered=false;
  t.args.journal.checkpoint=async state=>{if(++writes===2)throw Error("disk full");await checkpoint(state);};
  t.args.responseJournaled=async()=>{delivered=true;};
  await assert.rejects(observeRevokedDeepLinkRefresh(t.args),/disk full/);assert.equal(delivered,false);
  assert.equal(t.state().state.sessions[0].refreshAttempt.verified,undefined);
});
