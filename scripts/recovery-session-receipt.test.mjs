import assert from "node:assert/strict";
import test from "node:test";
import {randomUUID,createHash} from "node:crypto";
import {recordRecoverySession,recordRecoveryNativeSession,createRecoveryWebActorVerifier} from "./e2e-fixtures/recovery-session-receipt.mjs";

function fixture(){
  const ticket={runId:randomUUID(),profileId:randomUUID(),authUserId:randomUUID(),clientInstanceId:randomUUID(),purpose:"producer"};
  const authSessionId=randomUUID(),webSessionId=randomUUID(),webSessionToken="synthetic-web-token";
  const accessToken=`synthetic.${Buffer.from(JSON.stringify({sub:ticket.authUserId,session_id:authSessionId})).toString("base64url")}.signature`;
  let durable={...ticket,state:{sessions:[{...ticket}]}},writes=0,reads=0;
  const journal={read:async()=>structuredClone(durable),checkpoint:async state=>{assert.equal(ticket.authSessionId,undefined);durable.state=structuredClone(state);writes++;}};
  const client={query:async(sql,values)=>{reads++;assert.match(sql,/s\.id=\$1::uuid/);assert.match(sql,/w\.token_hash=\$5/);
    assert.deepEqual(values,[authSessionId,ticket.authUserId,ticket.profileId,ticket.clientInstanceId,createHash("sha256").update(webSessionToken).digest("hex")]);
    return {rowCount:1,rows:[{auth_session_id:authSessionId,web_session_id:webSessionId}]};}};
  const fetchImpl=async(url,options)=>{assert.equal(url.pathname,"/auth/v1/user");assert.equal(options.headers.Authorization,`Bearer ${accessToken}`);return {ok:true,json:async()=>({id:ticket.authUserId})};};
  return {input:{client,journal,ticket,accessToken,webSessionToken,backendUrl:"https://backend.example",publicKey:"public",fetchImpl},get:()=>({durable,writes,reads})};
}

test("verified exact session receipt persists IDs before updating the core ticket, without tokens",async()=>{
  const f=fixture();assert.equal(await recordRecoverySession(f.input),true);
  const serialized=JSON.stringify(f.get().durable);
  assert.equal(serialized.includes(f.input.accessToken),false);assert.equal(serialized.includes(f.input.webSessionToken),false);
  assert.equal(f.get().writes,1);assert.ok(f.input.ticket.authSessionId);assert.ok(f.input.ticket.webSessionId);
  await assert.rejects(recordRecoverySession(f.input),/receipt_unverified/);
  assert.equal(f.get().writes,1);
});

test("invalid authentication, missing session and persistence failure never produce a verified receipt",async()=>{
  for(const scenario of ["auth","session","journal"]){
    const f=fixture();
    if(scenario==="auth")f.input.fetchImpl=async()=>({ok:false});
    if(scenario==="session")f.input.client.query=async()=>({rowCount:0,rows:[]});
    if(scenario==="journal")f.input.journal.checkpoint=async()=>{throw Error("private token detail");};
    await assert.rejects(recordRecoverySession(f.input),/^Error: recovery_session_receipt_unverified$/);
    assert.equal(f.input.ticket.authSessionId,undefined);assert.equal(f.get().writes,0);
    if(scenario==="auth")assert.equal(f.get().reads,0);
  }
});

test("Web verifier binds browser credentials to the prepared actor and durable receipt",async()=>{
  const f=fixture();const verify=createRecoveryWebActorVerifier(f.input);
  const credentials={profileId:f.input.ticket.profileId,accessToken:f.input.accessToken,webSessionToken:f.input.webSessionToken};
  await assert.rejects(verify({...f.input.ticket,runId:randomUUID()},f.input.ticket,credentials),/actor_mismatch/);
  assert.equal(f.get().reads,0);assert.equal(f.get().writes,0);
  assert.equal(await verify({...f.input.ticket},f.input.ticket,credentials),true);
  assert.equal(f.get().writes,1);assert.ok(f.input.ticket.authSessionId);
});

function nativeFixture(){
  const f=fixture();const ticket=f.input.ticket;
  delete ticket.clientInstanceId;ticket.kind="native";ticket.ticketId=randomUUID();
  let durable={...ticket,state:{sessions:[{...ticket}]}},writes=0,authenticated=false;
  const authSessionId=JSON.parse(Buffer.from(f.input.accessToken.split(".")[1],"base64url")).session_id;
  f.input.journal={read:async()=>structuredClone(durable),checkpoint:async state=>{
    assert.equal(ticket.authSessionId,undefined);durable.state=structuredClone(state);writes++;
  }};
  const fetchImpl=f.input.fetchImpl;
  f.input.fetchImpl=async(...args)=>{const result=await fetchImpl(...args);authenticated=true;return result;};
  f.input.client={query:async(sql,values)=>{
    assert.equal(authenticated,true);assert.doesNotMatch(sql,/web_client_sessions/);
    assert.deepEqual(values,[authSessionId,ticket.authUserId,ticket.profileId]);
    return {rowCount:1,rows:[{auth_session_id:authSessionId}]};
  }};
  return {...f,authSessionId,getNative:()=>({durable,writes}),setSessions:sessions=>{durable.state.sessions=sessions;}};
}

test("native receipt authenticates exact bearer and persists only its Auth session, without Web identity",async()=>{
  const f=nativeFixture();assert.equal(await recordRecoveryNativeSession(f.input),true);
  assert.equal(f.input.ticket.authSessionId,f.authSessionId);
  const serialized=JSON.stringify(f.getNative().durable);
  for(const forbidden of [f.input.accessToken,"webSessionId","clientInstanceId"])assert.equal(serialized.includes(forbidden),false);
  assert.equal(f.getNative().writes,1);
  await assert.rejects(recordRecoveryNativeSession(f.input),/native_session_receipt_unverified/);
});

test("native receipt rejects wrong actors, missing sessions, Web fields, reused session IDs and failed persistence",async()=>{
  for(const scenario of ["auth","actor","session","web","duplicate","journal"]){
    const f=nativeFixture();
    if(scenario==="auth")f.input.fetchImpl=async()=>({ok:false});
    if(scenario==="actor")f.input.fetchImpl=async()=>({ok:true,json:async()=>({id:randomUUID()})});
    if(scenario==="session")f.input.client.query=async()=>({rowCount:0,rows:[]});
    if(scenario==="web")f.input.ticket.clientInstanceId=randomUUID();
    if(scenario==="duplicate")f.setSessions([{...f.input.ticket},{...f.input.ticket,ticketId:randomUUID(),authSessionId:f.authSessionId}]);
    if(scenario==="journal")f.input.journal.checkpoint=async()=>{throw Error("private bearer");};
    await assert.rejects(recordRecoveryNativeSession(f.input),/^Error: recovery_native_session_receipt_unverified$/);
    assert.equal(f.input.ticket.authSessionId,undefined);assert.equal(f.getNative().writes,0);
  }
});
