import test from "node:test";
import assert from "node:assert/strict";
import {loginDeepLinkSession,revokeDeepLinkSessions} from "./e2e-fixtures/chat-deep-link-session.mjs";

function setup() {
  const record={runId:"11111111-1111-4111-8111-111111111111",profileId:"22222222-2222-4222-8222-222222222222",
    authUserId:"33333333-3333-4333-8333-333333333333",countryCode:"240",phone:"123456"};
  const ticket={runId:record.runId,profileId:record.profileId,authUserId:record.authUserId,purpose:"deep_link",clientInstanceId:"deep-link-client"};
  let saved={...record,state:{sessions:[{...ticket}]}};
  const events=[];
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{saved={...saved,state:structuredClone(state)};events.push("checkpoint");}};
  const body={profile:{id:record.profileId},session:{access_token:"private-access",refresh_token:"private-refresh",expires_at:2000000000},web_session:{token:"private-web"}};
  const args={record,ticket,journal,client:{query:async()=>({rowCount:1,rows:[{owned:true,unique_active:true,phone_matches:true,no_sessions:true}]})},backendUrl:"https://example.test",publicKey:"public",password:"private-password",
    fetchImpl:async(url,options)=>{events.push("request");assert.equal(JSON.parse(options.body).profile_id,record.profileId);return {status:200,json:async()=>body};},
    recordReceipt:async()=>{events.push("receipt");assert.deepEqual(saved.state.sessions[0].privateLoginResponse.body,body);}};
  return {args,events,body,state:()=>saved};
}
test("journals intent before login and private response before receipt verification",async()=>{
  const s=setup();const session=await loginDeepLinkSession(s.args);
  assert.deepEqual(s.events,["checkpoint","request","checkpoint","receipt"]);
  assert.equal(session.profileId,s.args.record.profileId);
  await assert.rejects(loginDeepLinkSession(s.args),/ticket_unavailable/);
  assert.equal(s.events.filter(e=>e==="request").length,1);
});
test("timeout preserves unresolved intent and forbids repeating a possibly successful login",async()=>{
  const s=setup();s.args.fetchImpl=async()=>{throw Error("private remote error");};
  await assert.rejects(loginDeepLinkSession(s.args),{message:"deep_link_login_response_uncertain"});
  assert.equal(s.state().state.sessions[0].requestStarted,true);
  assert.equal(s.state().state.sessions[0].noSession,undefined);
  await assert.rejects(loginDeepLinkSession(s.args),/ticket_unavailable/);
});

test("UI login transport runs once after durable intent and cannot replace receipt transport",async()=>{
  const s=setup();let requests=0;
  const receiptTransport=async()=>{throw Error("not a login transport");};
  s.args.fetchImpl=receiptTransport;
  s.args.requestLogin=async(url,options)=>{
    requests++;
    assert.equal(s.state().state.sessions[0].requestStarted,true);
    assert.equal(url.pathname,"/functions/v1/quata-auth-bridge");
    assert.equal(JSON.parse(options.body).client_instance_id,s.args.ticket.clientInstanceId);
    return {status:200,json:async()=>s.body};
  };
  s.args.recordReceipt=async args=>{
    assert.equal(args.fetchImpl,receiptTransport);
    assert.deepEqual(s.state().state.sessions[0].privateLoginResponse.body,s.body);
  };
  await loginDeepLinkSession(s.args);
  await assert.rejects(loginDeepLinkSession(s.args),/ticket_unavailable/);
  assert.equal(requests,1);
});

test("UI login remains uncertain on failure and is never invoked for an unowned actor",async()=>{
  const s=setup();let requests=0;
  s.args.requestLogin=async()=>{requests++;throw Error("UI response lost");};
  s.args.client.query=async()=>({rowCount:0,rows:[]});
  await assert.rejects(loginDeepLinkSession(s.args),/requires_exclusive_fixture/);
  assert.equal(requests,0);
  s.args.client.query=setup().args.client.query;
  await assert.rejects(loginDeepLinkSession(s.args),/response_uncertain/);
  await assert.rejects(loginDeepLinkSession(s.args),/ticket_unavailable/);
  assert.equal(requests,1);
  assert.equal(s.state().state.sessions[0].requestStarted,true);
  assert.equal(s.state().state.sessions[0].noSession,undefined);
});
test("only explicit invalid credentials resolves a response as no session",async()=>{
  for(const status of [401,500]) {
    const s=setup();s.args.fetchImpl=async()=>({status,json:async()=>({error:status===401?"invalid_credentials":"server_error"})});
    await assert.rejects(loginDeepLinkSession(s.args),status===401?/invalid_credentials/:/unresolved_response/);
    assert.equal(s.state().state.sessions[0].noSession,status===401?true:undefined);
  }
});
test("failed durable checkpoint prevents request; receipt failure preserves private response",async()=>{
  const s=setup();s.args.journal.checkpoint=async()=>{throw Error("disk full");};
  await assert.rejects(loginDeepLinkSession(s.args),/disk full/);assert.deepEqual(s.events,[]);
  const t=setup();t.args.recordReceipt=async()=>{throw Error("receipt_failed");};
  await assert.rejects(loginDeepLinkSession(t.args),/receipt_failed/);
  assert.equal(t.state().state.sessions[0].privateLoginResponse.status,200);
});
test("invalid backend and changed actor are rejected before any request",async()=>{
  for(const patch of [{backendUrl:"http://example.test"},{record:{...setup().args.record,phone:"999999"}}]) {
    const s=setup();Object.assign(s.args,patch);
    await assert.rejects(loginDeepLinkSession(s.args));assert.deepEqual(s.events,[]);
  }
});
test("session cleanup refuses ongoing UI before reading a journal or mutating",async()=>{
  await assert.rejects(revokeDeepLinkSessions({operationsSettled:async()=>false}),/operations_unsettled/);
});
test("native or mixed tickets are refused before starting a Web login",async()=>{
  for (const patch of [{kind:"native"},{ticketId:"native-ticket"},{kind:"web"}]) {
    const s=setup();Object.assign(s.args.ticket,patch);
    await assert.rejects(loginDeepLinkSession(s.args),/ticket_unavailable/);assert.deepEqual(s.events,[]);
    const t=setup();const durable=await t.args.journal.read();Object.assign(durable.state.sessions[0],patch);
    await t.args.journal.checkpoint(durable.state);t.events.length=0;
    await assert.rejects(loginDeepLinkSession(t.args),/ticket_unavailable/);assert.deepEqual(t.events,[]);
  }
});
test("refuses existing accounts, foreign sessions and incomplete ownership audits before login",async()=>{
  for(const key of ["owned","unique_active","phone_matches","no_sessions"]) {
    for(const value of [false,null,undefined]) {
      const s=setup();s.args.client.query=async()=>({rowCount:1,rows:[{owned:true,unique_active:true,phone_matches:true,no_sessions:true,[key]:value}]});
      await assert.rejects(loginDeepLinkSession(s.args),/requires_exclusive_fixture/);assert.deepEqual(s.events,[]);
    }
  }
});
