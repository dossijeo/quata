import assert from "node:assert/strict";
import test from "node:test";
import { snapshotRecoverySecret, resumeRecoverySecretSnapshot } from "./e2e-fixtures/account-recovery-secret.mjs";

function database({ missing = false, failReadback = false } = {}) {
  const original = { secret_question: "pet", secret_answer: "private-original-answer" };
  let row = { ...original };
  const calls = [];
  let updated = false;
  return {
    calls,
    current() { return {...row}; },
    change() { row = {secret_question:"school",secret_answer:"temporary-answer"}; },
    client: { async query(sql, values) {
      calls.push({ sql, values });
      assert.doesNotMatch(sql, /secret_answer_hash/);
      if (/^select/.test(sql)) return { rowCount: missing ? 0 : 1, rows: [{ ...row, ...(updated && failReadback ? { secret_question: "unexpected" } : {}) }] };
      if (/^update/.test(sql)) {
        assert.deepEqual(values.slice(2, 4), ["authorized-profile", "authorized-auth"]);
        const fields = Object.keys(original);
        assert.ok(fields.every(field => sql.includes(`${field} is not distinct from`)));
        if (!fields.every((field, index) => row[field] === values[fields.length + 2 + index])) return {rowCount:0};
        assert.doesNotMatch(sql, /pass_hash|pass_plain|display_name|neighborhood|phone\s*=/);
        row = {secret_question:values[0],secret_answer:values[1]};
        updated = true;
        return { rowCount: 1 };
      }
      return { rowCount: 0 };
    } },
  };
}

const identity = { profileId: "authorized-profile", authUserId: "authorized-auth", storageFormat: "legacy-v32" };

test("snapshot exposes no secret data and restores only the exact actor's two fields", async () => {
  const db = database();
  const snapshot = await snapshotRecoverySecret({ client: db.client, ...identity });
  assert.equal(JSON.stringify(snapshot), "{}");
  db.change();
  assert.equal(await snapshot.verify(), false);
  assert.equal(await snapshot.restore(db.current()), true);
  assert.equal(await snapshot.verify(), true);
  assert.equal(db.calls.at(-2).sql, "commit");
  assert.equal(await snapshot.restore(db.current()), true);
  assert.equal(db.calls.filter(({ sql }) => /^update/.test(sql)).length, 1);
});

test("missing or mismatched identity fails before any mutation", async () => {
  const db = database({ missing: true });
  await assert.rejects(snapshotRecoverySecret({ client: db.client, ...identity }), /identity_mismatch/);
  assert.equal(db.calls.filter(({ sql }) => /^update/.test(sql)).length, 0);
  await assert.rejects(snapshotRecoverySecret({ client: db.client }), /identity_required/);
});

test("failed restoration readback rolls back and never reports success", async () => {
  const db = database({ failReadback: true });
  const snapshot = await snapshotRecoverySecret({ client: db.client, ...identity });
  db.change();
  await assert.rejects(snapshot.restore(db.current()), /readback_mismatch/);
  assert.equal(db.calls.at(-1).sql, "rollback");
  assert.equal(db.calls.some(({ sql }) => sql === "commit"), false);
});

test("an account changed after restoration is detected rather than overwritten again", async () => {
  const db = database();
  const snapshot = await snapshotRecoverySecret({ client: db.client, ...identity });
  await snapshot.restore(db.current());
  db.change();
  await assert.rejects(snapshot.restore(db.current()), /changed_after_verification/);
  assert.equal(db.calls.filter(({ sql }) => /^update/.test(sql)).length, 1);
});

test("journal failure prevents returning a prepared snapshot; recovered snapshot restores original values", async () => {
  const db = database();
  await assert.rejects(snapshotRecoverySecret({client:db.client,...identity,persistSnapshot:async()=>{throw new Error("journal_failed");}}), /journal_failed/);
  assert.equal(db.calls.some(({sql})=>/^update/.test(sql)),false);
  let durable;
  await snapshotRecoverySecret({client:db.client,...identity,persistSnapshot:async original=>{durable=original;}});
  db.change();
  const resumed = await resumeRecoverySecretSnapshot({client:db.client,...identity,original:durable});
  assert.equal(await resumed.verify(),false);
  assert.equal(await resumed.restore(db.current()),true);
  assert.equal(await resumed.verify(),true);
  await assert.rejects(resumeRecoverySecretSnapshot({client:db.client,...identity,original:{...durable,pass_hash:"unrelated"}}),/fields_invalid/);
});

test("explicit legacy-v32 restores the published two-field format without probing or silently ignoring a hash column", async () => {
  const db=database(); let durable;
  const options={client:db.client,...identity,storageFormat:"legacy-v32"};
  await snapshotRecoverySecret({...options,persistSnapshot:async snapshot=>{durable=snapshot;}});
  db.change();
  const resumed=await resumeRecoverySecretSnapshot({...options,original:durable});
  assert.equal(await resumed.restore(db.current()),true);
  assert.equal(await resumed.verify(),true);
  assert.deepEqual(Object.keys(durable).sort(),["secret_answer","secret_question"]);
  for (const storageFormat of [undefined,"autodetect","hashed-v1"]) {
    const callsBefore=db.calls.length;
    await assert.rejects(snapshotRecoverySecret({...options,storageFormat}),/storage_format_invalid/);
    await assert.rejects(resumeRecoverySecretSnapshot({...options,storageFormat,original:durable}),/storage_format_invalid/);
    assert.equal(db.calls.length,callsBefore);
  }
});

 test("atomic expected-value guard preserves an interleaved foreign change", async () => {
   const db=database();
   const snapshot=await snapshotRecoverySecret({client:db.client,...identity,storageFormat:"legacy-v32"});
   const expected=db.current();
   db.change();
   const foreign=db.current();
   await assert.rejects(snapshot.restore(expected),/identity_mismatch/);
   assert.deepEqual(db.current(),foreign);
   assert.equal(db.calls.at(-1).sql,"rollback");
   await assert.rejects(snapshot.restore(),/expected_fields_required/);
 });
