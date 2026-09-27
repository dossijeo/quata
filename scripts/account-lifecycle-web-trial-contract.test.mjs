import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const [source, web] = await Promise.all([
  readFile(new URL("./account-lifecycle-trial.mjs", import.meta.url), "utf8"),
  readFile(new URL("./account-lifecycle-web-trial.mjs", import.meta.url), "utf8"),
]);

test("coordinator uses distinct owned actors and the real Web product adapter for both destructive actions", () => {
  assert.match(source, /for \(const action of \["deactivate", "delete"\]\)/);
  assert.match(source, /createAccountLifecycleFixture/);
  assert.match(source, /loginAccountLifecycleSession/);
  assert.match(source, /ui\.run\(\{ action, session, record, password/);
  assert.match(source, /verifyAccountDeactivated/);
  assert.match(source, /verifyAccountDeleted/);
  assert.match(web, /runAccountLifecycleTrial\(\{ platform: "web"/);
  assert.match(web, /createAccountLifecycleWebTrial/);
});

test("coordinator retains recovery custody until transport and residue checks settle", () => {
  assert.match(source, /`account-lifecycle-\$\{platform\}\.lock`/);
  assert.match(source, /pendingAdmin === 0 && !adminUncertain/);
  assert.match(source, /retireAccountLifecycleFixture/);
  assert.match(source, /removeAfterVerification/);
  assert.match(source, /cleanupComplete = false/);
  assert.doesNotMatch(source, /console\.(?:log|error)|process\.env|serviceKey\s*:/);
});

test("deactivation proves the original Auth token and a protected RPC are rejected", () => {
  assert.match(source, /\/auth\/v1\/user/);
  assert.match(source, /\/rest\/v1\/rpc\/quata_chat_get_thread/);
  assert.match(source, /response\.status === 401 \|\| response\.status === 403/);
});
