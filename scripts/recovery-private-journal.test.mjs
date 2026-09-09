import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, readFile, readdir, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { createRecoveryJournal, openRecoveryJournal } from "./e2e-fixtures/recovery-private-journal.mjs";

test("Windows DPAPI journal survives reopening and retains recovery data until all cleanup checks pass", {skip: process.platform !== "win32"}, async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "quata-recovery-journal-test-"));
  const record = {runId: randomUUID(), profileId: randomUUID(), authUserId: randomUUID(),
    originalPassword: "synthetic-password-only", secretSnapshot: {secret_question: "pet", secret_answer: "synthetic-answer-only", secret_answer_hash: null}, state: "prepared"};
  const file = path.join(directory, `recovery-${record.profileId}.dpapi`);
  try {
    const initial = await createRecoveryJournal({directory, record});
    assert.equal(JSON.stringify(initial), "{}");
    const disk = await readFile(file);
    for (const value of [record.originalPassword, record.secretSnapshot.secret_answer, record.profileId]) assert.equal(disk.includes(Buffer.from(value)), false);
    await assert.rejects(createRecoveryJournal({directory, record: {...record, runId: randomUUID()}}), /EEXIST/);
    await initial.checkpoint("password_potentially_changed");
    const reopened = await openRecoveryJournal({file, identity: record});
    const restored = await reopened.read();
    assert.equal(restored.originalPassword, record.originalPassword);
    assert.deepEqual(restored.secretSnapshot, record.secretSnapshot);
    assert.equal(restored.state, "password_potentially_changed");
    await assert.rejects(openRecoveryJournal({file, identity: {...record, authUserId: randomUUID()}}), /identity_mismatch/);
    await assert.rejects(reopened.removeAfterVerification(async()=>({password:true,secret:false,sessions:true})), /cleanup_unverified/);
    assert.equal((await readdir(directory)).length, 1);
    let beginVerification;
    const entered = new Promise(resolve=>{beginVerification=resolve;});
    let finishVerification;
    const finish = new Promise(resolve=>{finishVerification=resolve;});
    const removing = reopened.removeAfterVerification(async()=>{beginVerification();await finish;return {password:true,secret:true,sessions:true};});
    await entered;
    try { await assert.rejects(initial.checkpoint("new_state_while_removing"), /concurrent_operation/); }
    finally { finishVerification(); }
    await removing;
    assert.deepEqual(await readdir(directory), []);
  } finally {
    // The test owns this mkdtemp directory and stores synthetic data only.
    assert.equal(path.dirname(path.resolve(directory)), path.resolve(tmpdir()));
    assert.ok(path.basename(directory).startsWith("quata-recovery-journal-test-"));
    await rm(directory, {recursive:true, force:true});
  }
});
