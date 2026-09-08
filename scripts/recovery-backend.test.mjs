import assert from "node:assert/strict";
import test from "node:test";
import {randomUUID} from "node:crypto";
import {createRecoveryBackend} from "./e2e-fixtures/recovery-backend.mjs";

function fixture(){
  const record={runId:randomUUID(),profileId:randomUUID(),authUserId:randomUUID(),countryCode:"240",phone:"synthetic",
    temporaryQuestion:"pet",temporaryAnswer:"synthetic-answer",originalPassword:"original-password"};
  let state={sessions:[]};const queries=[],requests=[];
  const journal={read:async()=>({...record,state:structuredClone(state)}),checkpoint:async value=>{state=structuredClone(value);}};
  const client={query:async(sql,values)=>{
    queries.push({sql,values});
    if(sql.startsWith("select p.display_name"))return {rowCount:1,rows:[{display_name:"Fixture",nombre:"Fixture",neighborhood:null,barrio:"",country_code:"240",code:"240",phone_local:"123456",telefono:"+240123456",phone:"+240123456",phone_normalized:"123456",phone_e164:"+240123456",avatar_url:null,avatar:null,contacts:0,memberships:0,phone_keys:["123456","240123456","240240123456"]}]};
    if(sql.includes("linked_profiles"))return {rowCount:1,rows:[{account_status:"active",linked_profiles:1,unowned_auth_sessions:0,unrevoked_web_sessions:2200}]};
    if(sql.includes("auth_count"))return {rowCount:1,rows:[{auth_count:0,web_count:0}]};
    return {rowCount:1,rows:[]};
  }};
  const fetchImpl=async(url,options)=>{const body=JSON.parse(options.body);requests.push(body);
    if(body.action==="update_recovery_secret")return {status:400,json:async()=>({error:"password_required"})};
    if(body.action==="web_login")return {status:401,json:async()=>({error:"invalid_credentials"})};
    if(body.action==="reset_password")return {status:200,json:async()=>({ok:true})};
    return {status:200,json:async()=>({secret_question:"pet"})};};
  const options={client,journal,record,backendUrl:"https://backend.example",publicKey:"public",pageOperationsSettled:()=>true,fetchImpl};
  return {record,journal,queries,requests,options,backend:createRecoveryBackend(options),set:value=>{state={sessions:value};}};
}

test("backend preflight fails closed on deployed-v21 producer mismatch without login",async()=>{
  const f=fixture();assert.equal(await f.backend.preflight(f.record),false);
  assert.deepEqual(f.requests,[{action:"update_recovery_secret",version:1}]);
  assert.ok(f.queries.every(q=>q.sql.startsWith("select")));
});

test("login needs a journaled ticket and records authoritative invalid-credentials without a session",async()=>{
  const f=fixture();const ticket=await f.backend.planSession({...f.record,purpose:"temporary_verification"});
  await assert.rejects(f.backend.verifyLogin("candidate",ticket),/ticket_missing/);assert.deepEqual(f.requests,[]);
  f.set([ticket]);assert.equal(await f.backend.verifyLogin("candidate",ticket),false);assert.equal(ticket.noSession,true);
  assert.equal((await f.journal.read()).state.sessions[0].noSession,true);
  assert.equal(await f.backend.sessionsClean([ticket]),true);
  assert.equal(f.requests[0].profile_id,f.record.profileId);
  await assert.rejects(f.backend.verifyLogin("another",ticket),/already_resolved/);
  assert.equal(f.requests.length,1);
});

test("cleanup targets only journaled receipt IDs and retains unresolved tickets",async()=>{
  const f=fixture();const owned={...f.record,purpose:"producer",clientInstanceId:randomUUID(),authSessionId:randomUUID(),webSessionId:randomUUID()};
  const unresolved={...f.record,purpose:"original_verification",clientInstanceId:randomUUID()};f.set([owned,unresolved]);
  await assert.rejects(f.backend.revokeSessions([unresolved,owned]),/receipt_missing/);
  const writes=f.queries.filter(q=>/^(update|delete)/.test(q.sql));assert.equal(writes.length,2);
  assert.deepEqual(writes[0].values,[owned.webSessionId,f.record.profileId,f.record.authUserId,owned.clientInstanceId]);
  assert.deepEqual(writes[1].values,[owned.authSessionId,f.record.authUserId]);
  assert.equal(await f.backend.sessionsClean([owned]),true);
  await assert.rejects(f.backend.sessionsClean([unresolved]),/receipt_missing/);
});

test("transport failure is sticky and an interrupted run needs an explicit settlement implementation",async()=>{
  const f=fixture();const backend=createRecoveryBackend({...f.options,fetchImpl:async()=>{throw Error("private transport");}});
  await assert.rejects(backend.readRecoveryQuestion(f.record),/^Error: recovery_backend_transport_uncertain$/);
  assert.equal(await backend.confirmOperationsSettled(),false);
  assert.equal(await backend.confirmInterruptedRunSettled(f.record),false);
  assert.equal(await backend.sessionsClean([]),false);
});

test("resolved receipt cannot be reused and conflicting no-session receipt cannot certify cleanup",async()=>{
  const f=fixture();const ticket=await f.backend.planSession({...f.record,purpose:"producer"});
  const resolved={...ticket,authSessionId:randomUUID(),webSessionId:randomUUID()};f.set([resolved]);
  await assert.rejects(f.backend.verifyLogin("candidate",ticket),/already_resolved/);assert.deepEqual(f.requests,[]);
  f.set([{...resolved,noSession:true}]);
  await assert.rejects(f.backend.sessionsClean([ticket]),/conflicting_receipt/);
  await assert.rejects(f.backend.revokeSessions([ticket]),/receipt_missing/);
  assert.ok(f.queries.every(q=>!/^delete|^update/.test(q.sql)));
});

test("an uncertain login attempt is persisted before transport and cannot reuse its ticket",async()=>{
  const f=fixture();let attempts=0;
  const backend=createRecoveryBackend({...f.options,fetchImpl:async()=>{attempts++;assert.equal((await f.journal.read()).state.sessions[0].requestStarted,true);throw Error("timeout");}});
  const ticket=await backend.planSession({...f.record,purpose:"producer"});f.set([ticket]);
  await assert.rejects(backend.verifyLogin("candidate",ticket),/transport_uncertain/);
  await assert.rejects(backend.verifyLogin("candidate",ticket),/already_resolved/);
  assert.equal(attempts,1);assert.equal(await backend.confirmOperationsSettled(),false);
});
