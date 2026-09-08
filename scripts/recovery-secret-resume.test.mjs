import assert from "node:assert/strict";
import test from "node:test";
import { resumeRecoverySecretCleanup } from "./account-recovery-secret-evidence.mjs";

function interrupted({alreadyRestored=false,settled=true,restoreFails=false}={}) {
  const original={secret_question:"old",secret_answer:"synthetic-old"};
  let row=alreadyRestored ? {...original} : {secret_question:"pet",secret_answer:"synthetic-temporary"};
  let password=alreadyRestored ? "original" : "temporary", removed=false;
  const calls=[];
  const record={runId:"run",profileId:"profile",authUserId:"auth",storageFormat:"legacy-v32",secretSnapshot:original,
    originalPassword:"original",temporaryPassword:"temporary",temporaryQuestion:"pet",temporaryAnswer:"synthetic-temporary",
    state:{phase:"cleanup_required",sessions:[{purpose:"producer",id:"retained-ticket"}],secretPotentiallyChanged:true,passwordPotentiallyChanged:true}};
  const journal={read:async()=>structuredClone(record),checkpoint:async state=>{record.state=structuredClone(state);},
    removeAfterVerification:async verify=>{assert.deepEqual(await verify(),{password:true,secret:true,sessions:true});removed=true;}};
  const client={query:async(sql,values)=>{
    calls.push(sql.split(/\s/)[0]);
    if(sql.startsWith("select"))return {rowCount:1,rows:[{...row}]};
    if(sql.startsWith("update")){
      assert.equal(password,"original");assert.deepEqual(values.slice(2,4),["profile","auth"]);
      assert.deepEqual(values.slice(4),[row.secret_question,row.secret_answer]);
      row={...original};return {rowCount:1};
    }
    return {rowCount:0};
  }};
  const product={close:async()=>{calls.push("close");return true;}};
  const backend={auditRecoverySessions:async()=>true,confirmInterruptedRunSettled:async()=>settled,planSession:async({purpose})=>({purpose}),
    verifyLogin:async candidate=>{calls.push("verify_login");return candidate===password;},
    restorePassword:async()=>{calls.push("restore_password");if(restoreFails)throw Error("private failure");password="original";return true;},
    secretMatchesPlanned:async()=>row.secret_answer==="synthetic-temporary",
    revokeSessions:async tickets=>{assert.equal(tickets[0].id,"retained-ticket");calls.push("revoke");},sessionsClean:async()=>true};
  return {input:{journal,client,product,backend},calls,get:()=>({row,password,removed,record})};
}

test("interrupted cleanup restores in order, retains session tickets and never claims E2E",async()=>{
  const f=interrupted();const report=await resumeRecoverySecretCleanup(f.input);
  assert.equal(report.status,"restored");assert.equal(report.check,"ACCOUNT-RECOVERY-SECRET-CLEANUP-001");
  assert.deepEqual(report.steps,[]);assert.equal(f.get().removed,true);
  assert.ok(f.calls.indexOf("restore_password")<f.calls.indexOf("update"));
  assert.equal(JSON.stringify(report).includes("synthetic"),false);
});

test("restart after successful restoration verifies original without reusing the temporary secret",async()=>{
  const f=interrupted({alreadyRestored:true});const report=await resumeRecoverySecretCleanup(f.input);
  assert.equal(report.status,"restored");assert.equal(f.calls.includes("restore_password"),false);
  assert.equal(f.calls.includes("update"),false);assert.equal(f.get().removed,true);
});

test("unsettled interrupted operations prevent every cleanup action",async()=>{
  const f=interrupted({settled:false});
  await assert.rejects(resumeRecoverySecretCleanup(f.input),/interrupted_operations_unresolved/);
  assert.deepEqual(f.calls,[]);assert.equal(f.get().removed,false);
});

test("failed resumed password restore retains secret and journal while revoking owned sessions",async()=>{
  const f=interrupted({restoreFails:true});const report=await resumeRecoverySecretCleanup(f.input);
  assert.equal(report.status,"failed");assert.equal(f.get().removed,false);
  assert.equal(f.calls.includes("update"),false);assert.equal(f.calls.includes("revoke"),true);
  assert.equal(f.get().record.state.phase,"cleanup_required");
});

test("missing actor or failed snapshot read still revokes tickets and closes resources",async()=>{
  for (const missing of [true,false]) {
    const f=interrupted();
    f.input.client.query=async()=>{if(missing)return {rowCount:0,rows:[]};throw Error("private database detail");};
    const report=await resumeRecoverySecretCleanup(f.input);
    assert.equal(report.status,"failed");assert.equal(report.cleanupFailure,"recovery_snapshot_unavailable");
    assert.equal(f.get().removed,false);assert.equal(f.calls.includes("revoke"),true);assert.equal(f.calls.includes("close"),true);
    assert.equal(f.calls.includes("restore_password"),false);assert.equal(f.calls.includes("verify_login"),false);
    assert.equal(report.cleanup.secret,false);assert.equal(report.cleanup.password,false);
    assert.equal(JSON.stringify(report).includes("private"),false);
  }
});

test("unrelated sessions block resumed login and password restoration while retaining the journal",async()=>{
  const f=interrupted();f.input.backend.auditRecoverySessions=async()=>false;
  const report=await resumeRecoverySecretCleanup(f.input);
  assert.equal(report.status,"failed");assert.equal(f.get().removed,false);
  assert.equal(f.calls.includes("verify_login"),false);assert.equal(f.calls.includes("restore_password"),false);
  assert.equal(f.calls.includes("revoke"),true);assert.equal(f.calls.includes("update"),false);
});
