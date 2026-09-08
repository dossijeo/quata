import assert from "node:assert/strict";
import test from "node:test";
import { runRecoverySecretEvidence } from "./account-recovery-secret-evidence.mjs";

function fixture({timeoutAfterReset=false,restoreFails=false,foreignSecret=false,preflightFails=false}={}) {
  const calls=[];let secret="original",password="original",removed=false,state;
  const record={runId:"synthetic-run",profileId:"synthetic-profile",authUserId:"synthetic-auth",storageFormat:"legacy-v32",state:{phase:"prepared"},originalPassword:"original",temporaryPassword:"temporary",temporaryQuestion:"pet",temporaryAnswer:"synthetic-answer"};
  const journal={read:async()=>record,checkpoint:async next=>{state=next;calls.push(`checkpoint:${next.phase}`);},removeAfterVerification:async verify=>{assert.deepEqual(await verify(),{password:true,secret:true,sessions:true});removed=true;}};
  const snapshot={verify:async()=>secret==="original",restore:async expected=>{assert.deepEqual(expected,{secret_question:"pet",secret_answer:"synthetic-answer"});calls.push("restore_secret");assert.equal(password,"original");secret="original";return true;}};
  const product={login:async()=>{assert.equal(state.sessions.length,1);calls.push("login");return true;},openAccount:async()=>{},configureSecret:async()=>{},
    saveSecret:async()=>{assert.equal(state.secretPotentiallyChanged,true);calls.push("save_secret");secret="temporary";},
    readPermittedState:async()=>({visible:true,question:"pet",answerEmpty:true,saving:false,failed:false,saved:true}),logout:async()=>{},
    recoverPassword:async()=>{assert.equal(state.passwordPotentiallyChanged,true);calls.push("reset_password");password="temporary";if(timeoutAfterReset)throw new Error("private raw failure must not be exported");},close:async()=>true};
  const backend={auditRecoverySessions:async()=>true,confirmOperationsSettled:async()=>true,preflight:async()=>{if(preflightFails){secret="foreign";return false;}return true;},planSession:async({purpose})=>({purpose}),secretMatchesPlanned:async()=>secret==="temporary",
    readRecoveryQuestion:async()=>({secret_question:"pet"}),restorePassword:async()=>{calls.push("restore_password");if(restoreFails)throw Error("private secret");password="original";if(foreignSecret)secret="foreign";return true;},
    verifyLogin:async candidate=>{calls.push(`verify:${candidate}`);return candidate===password;},revokeSessions:async()=>{calls.push("revoke_sessions");},sessionsClean:async()=>true};
  return {input:{journal,snapshot,product,backend},calls,get:()=>({secret,password,removed})};
}

test("focal runner saves once, journals before mutations and restores password before secret",async()=>{
  const f=fixture();const report=await runRecoverySecretEvidence(f.input);assert.equal(report.status,"passed");
  assert.equal(f.calls.filter(x=>x==="save_secret").length,1);
  assert.ok(f.calls.indexOf("restore_password")<f.calls.indexOf("restore_secret"));
  assert.deepEqual(f.get(),{secret:"original",password:"original",removed:true});
  assert.equal(JSON.stringify(report).includes("synthetic-answer"),false);
});

test("reset rejection after mutation still restores both values and reports failure without raw exception",async()=>{
  const f=fixture({timeoutAfterReset:true});const report=await runRecoverySecretEvidence(f.input);
  assert.equal(report.status,"failed");assert.deepEqual(f.get(),{secret:"original",password:"original",removed:true});
  assert.equal(JSON.stringify(report).includes("private raw"),false);
});

test("failed password restoration preserves temporary secret and journal but revokes sessions",async()=>{
  const f=fixture({restoreFails:true});const report=await runRecoverySecretEvidence(f.input);
  assert.equal(report.status,"failed");assert.equal(f.calls.includes("restore_secret"),false);
  assert.equal(f.calls.includes("revoke_sessions"),true);assert.equal(f.get().removed,false);assert.equal(f.get().secret,"temporary");
});

test("a foreign secret is never overwritten, including failure before producer",async()=>{
  for(const option of [{foreignSecret:true},{preflightFails:true}]){const f=fixture(option);const report=await runRecoverySecretEvidence(f.input);
    assert.equal(report.status,"failed");assert.equal(f.calls.includes("restore_secret"),false);assert.equal(f.get().secret,"foreign");assert.equal(f.get().removed,false);}
});

test("incomplete or nonterminal visible states cannot certify the flow",async()=>{
  for(const patch of [{visible:false},{saving:true},{failed:true},{saved:false},{answerEmpty:false},{question:"wrong"},{unexpected:"private"},{saving:undefined}]) {
    const f=fixture();const read=f.input.product.readPermittedState;
    f.input.product.readPermittedState=async()=>({...await read(),...patch});
    const report=await runRecoverySecretEvidence(f.input);
    assert.equal(report.status,"failed");assert.equal(f.calls.includes("reset_password"),false);
    assert.deepEqual(f.get(),{secret:"original",password:"original",removed:true});
  }
  const f=fixture();f.input.product.readPermittedState=async()=>({question:"pet",answerEmpty:true});
  assert.equal((await runRecoverySecretEvidence(f.input)).status,"failed");
});

test("a prepared phase cannot conceal pending sessions or mutations",async()=>{
  for(const patch of [{sessions:[{purpose:"previous"}]},{secretPotentiallyChanged:true},{passwordPotentiallyChanged:true}]) {
    const f=fixture();const read=f.input.journal.read;
    f.input.journal.read=async()=>{const record=await read();return {...record,state:{...record.state,...patch}};};
    await assert.rejects(runRecoverySecretEvidence(f.input),/prepared_journal_required/);
    assert.deepEqual(f.calls,[]);
  }
});

test("uncertain remote completion retains recovery path and journal despite an empty session check",async()=>{
  for(const reject of [false,true]) {
    const f=fixture({timeoutAfterReset:true});
    f.input.backend.confirmOperationsSettled=async()=>{if(reject)throw Error("private transport detail");return false;};
    const report=await runRecoverySecretEvidence(f.input);
    assert.equal(report.status,"failed");assert.equal(report.cleanup.sessions,false);
    assert.equal(f.calls.includes("restore_password"),false);assert.equal(f.calls.includes("restore_secret"),false);
    assert.equal(f.calls.includes("revoke_sessions"),true);
    assert.deepEqual(f.get(),{secret:"temporary",password:"temporary",removed:false});
    assert.equal(JSON.stringify(report).includes("private"),false);
  }
});

test("a newly detected unrelated session prevents password reset",async()=>{
  const f=fixture();let audits=0;f.input.backend.auditRecoverySessions=async()=>++audits!==2;
  const report=await runRecoverySecretEvidence(f.input);
  assert.equal(report.status,"failed");assert.equal(f.calls.includes("reset_password"),false);
  assert.equal(f.calls.includes("restore_password"),false);
  assert.deepEqual(f.get(),{secret:"original",password:"original",removed:true});
});
