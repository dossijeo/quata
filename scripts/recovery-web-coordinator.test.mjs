import test from "node:test";
import assert from "node:assert/strict";
import {randomUUID} from "node:crypto";
import {runRecoveryWebEvidence} from "./account-recovery-secret-web.mjs";

function fixture(status=400){
  const calls=[];
  const record={runId:randomUUID(),profileId:randomUUID(),authUserId:randomUUID()};
  const client={query:async sql=>{if(sql.startsWith("select p.display_name"))return {rowCount:1,rows:[{display_name:"Fixture",nombre:"Fixture",neighborhood:null,barrio:"",country_code:"240",code:"240",phone_local:"123456",telefono:"+240123456",phone:"+240123456",phone_normalized:"123456",phone_e164:"+240123456",avatar_url:null,avatar:null,contacts:0,memberships:0,phone_keys:["123456","240123456","240240123456"]}]};calls.push("audit");assert.match(sql,/^select/);return {rowCount:1,rows:[{account_status:"active",linked_profiles:1,unowned_auth_sessions:0,unrevoked_web_sessions:0}]};}};
  const fetchImpl=async(_,options)=>{calls.push("probe");assert.deepEqual(JSON.parse(options.body),{action:"update_recovery_secret",version:1});
    return {status,json:async()=>({error:status===401?"authentication_required":"password_required"})};};
  const input={client,record,directory:"not-accessed",backendUrl:"https://backend.example",publicKey:"public",fetchImpl,
    openPage:async()=>{calls.push("browser");throw Error("must_not_open");},closeResources:async()=>{calls.push("close");return true;},
    verifyCandidate:async()=>{calls.push("candidate");return false;}};
  return {calls,input};
}

test("coordinator rejects unavailable producer before candidate, journal and browser",async()=>{
  const f=fixture();const report=await runRecoveryWebEvidence(f.input);
  assert.deepEqual(f.calls,["audit","probe"]);assert.equal(report.status,"failed");
  assert.equal(report.failedPhase,"preflight");assert.equal(report.journalCreated,false);assert.equal(report.browserStarted,false);
});

test("candidate verification must pass before journal preparation or browser work",async()=>{
  const f=fixture(401);const report=await runRecoveryWebEvidence(f.input);
  assert.deepEqual(f.calls,["audit","probe","candidate"]);assert.equal(report.failedPhase,"candidate_verification");
  assert.equal(report.journalCreated,false);assert.equal(report.browserStarted,false);
});

test("failed preparation reports journal outcome unknown rather than claiming absence",async()=>{
  const f=fixture(401);f.input.verifyCandidate=async()=>true;
  const report=await runRecoveryWebEvidence(f.input);
  assert.equal(report.failedPhase,"preparation");assert.equal(report.journalCreated,null);
  assert.equal(report.browserStarted,false);assert.equal(report.resourcesClosed,null);
  assert.equal(f.calls.includes("browser"),false);
});
