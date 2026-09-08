import assert from "node:assert/strict";
import test from "node:test";
import { snapshotRecoverySecret } from "./e2e-fixtures/account-recovery-secret.mjs";

function database({ missing = false, failReadback = false } = {}) {
  const original = { secret_question: "pet", secret_answer: null, secret_answer_hash: "private-original-hash" };
  let row = { ...original };
  const calls = [];
  let updated = false;
  return {
    calls,
    change() { row = { secret_question: "school", secret_answer: null, secret_answer_hash: "temporary-hash" }; },
    client: { async query(sql, values) {
      calls.push({ sql, values });
      if (/^select/.test(sql)) return { rowCount: missing ? 0 : 1, rows: [{ ...row, ...(updated && failReadback ? { secret_question: "unexpected" } : {}) }] };
      if (/^update/.test(sql)) {
        assert.deepEqual(values.slice(3), ["authorized-profile", "authorized-auth"]);
        assert.doesNotMatch(sql, /pass_hash|pass_plain|display_name|neighborhood|phone\s*=/);
        row = { secret_question: values[0], secret_answer: values[1], secret_answer_hash: values[2] };
        updated = true;
        return { rowCount: 1 };
      }
      return { rowCount: 0 };
    } },
  };
}

const identity = { profileId: "authorized-profile", authUserId: "authorized-auth" };

test("snapshot exposes no secret data and restores only the exact actor's three fields", async () => {
  const db = database();
  const snapshot = await snapshotRecoverySecret({ client: db.client, ...identity });
  assert.equal(JSON.stringify(snapshot), "{}");
  db.change();
  assert.equal(await snapshot.verify(), false);
  assert.equal(await snapshot.restore(), true);
  assert.equal(await snapshot.verify(), true);
  assert.equal(db.calls.at(-2).sql, "commit");
  assert.equal(await snapshot.restore(), true);
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
  await assert.rejects(snapshot.restore(), /readback_mismatch/);
  assert.equal(db.calls.at(-1).sql, "rollback");
  assert.equal(db.calls.some(({ sql }) => sql === "commit"), false);
});

test("an account changed after restoration is detected rather than overwritten again", async () => {
  const db = database();
  const snapshot = await snapshotRecoverySecret({ client: db.client, ...identity });
  await snapshot.restore();
  db.change();
  await assert.rejects(snapshot.restore(), /changed_after_verification/);
  assert.equal(db.calls.filter(({ sql }) => /^update/.test(sql)).length, 1);
});
