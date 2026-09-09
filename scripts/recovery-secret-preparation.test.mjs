import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, readFile, readdir, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { prepareRecoverySecretEvidence } from "./account-recovery-secret-evidence.mjs";
import { openRecoveryJournal } from "./e2e-fixtures/recovery-private-journal.mjs";
import { resumeRecoverySecretSnapshot } from "./e2e-fixtures/account-recovery-secret.mjs";

test("preparation binds exact snapshot to persistent journal before exposing a runner context", {skip:process.platform !== "win32"}, async()=>{
  const directory=await mkdtemp(path.join(tmpdir(),"quata-recovery-preparation-test-"));
  const record={runId:randomUUID(),profileId:randomUUID(),authUserId:randomUUID(),
    originalPassword:"synthetic-original",temporaryPassword:"synthetic-temporary",
    temporaryQuestion:"pet",temporaryAnswer:"synthetic-answer"};
  const original={secret_question:"school",secret_answer:"synthetic-original-answer"};
  const calls=[];
  const client={query:async(sql,values)=>{
    calls.push(sql);if(sql.startsWith("select p.display_name"))return {rowCount:1,rows:[{display_name:"Fixture",nombre:"Fixture",neighborhood:null,barrio:"",country_code:"240",code:"240",phone_local:"123456",telefono:"+240123456",phone:"+240123456",phone_normalized:"123456",phone_e164:"+240123456",avatar_url:null,avatar:null,contacts:0,memberships:0,phone_keys:["123456","240123456","240240123456"]}]};assert.match(sql,/^select secret_question, secret_answer\s/);
    assert.deepEqual(values,[record.profileId,record.authUserId]);
    return {rowCount:1,rows:[{...original}]};
  }};
  try {
    const prepared=await prepareRecoverySecretEvidence({client,directory,record});
    const file=path.join(directory,`recovery-${record.profileId}.dpapi`);
    const disk=await readFile(file);
    for(const value of [record.originalPassword,record.temporaryAnswer,original.secret_answer]) {
      assert.equal(disk.includes(Buffer.from(value)),false);
      assert.equal(JSON.stringify(prepared).includes(value),false);
    }
    const journal=await openRecoveryJournal({file,identity:record});
    const durable=await journal.read();
    assert.deepEqual(durable.secretSnapshot,original);
    assert.equal(durable.storageFormat,"legacy-v32");
    assert.deepEqual(durable.state,{phase:"prepared",sessions:[],secretPotentiallyChanged:false,passwordPotentiallyChanged:false});
    const resumed=await resumeRecoverySecretSnapshot({client,...record,original:durable.secretSnapshot,storageFormat:durable.storageFormat});
    assert.equal(await resumed.verify(),true);
    const before=await readFile(file);
    await assert.rejects(prepareRecoverySecretEvidence({client,directory,record:{...record,runId:randomUUID()}}),/EEXIST/);
    assert.deepEqual(await readFile(file),before);
    assert.ok(calls.every(sql=>sql.startsWith("select")));
    await journal.removeAfterVerification(async()=>({password:true,secret:await resumed.verify(),sessions:true}));
    assert.deepEqual(await readdir(directory),[]);
    await assert.rejects(prepareRecoverySecretEvidence({client,directory,record:{...record,temporaryAnswer:" spaced "}}),/values_invalid/);
    assert.deepEqual(await readdir(directory),[]);
  } finally {
    assert.equal(path.dirname(path.resolve(directory)),path.resolve(tmpdir()));
    assert.ok(path.basename(directory).startsWith("quata-recovery-preparation-test-"));
    await rm(directory,{recursive:true,force:true});
  }
});
