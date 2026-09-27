import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("./account-lifecycle-web-private.mjs", import.meta.url), "utf8");
const recovery = await readFile(new URL("./account-lifecycle-recover-private.mjs", import.meta.url), "utf8");

test("private runner receives credentials through bounded stdin and never emits private failures", () => {
  assert.match(source, /for await \(const chunk of process\.stdin\)/);
  assert.match(source, /size > 1024 \* 1024/);
  assert.match(source, /clear\.fill\(0\)/);
  assert.match(source, /runner_failed_before_report/);
  assert.doesNotMatch(source, /process\.env|console\.(?:log|error)|error\.message/);
});

test("private recovery opens the durable journal and removes it only after zero-residue verification", () => {
  assert.match(recovery, /openRecoveryJournal/);
  assert.match(recovery, /retireAccountLifecycleFixture/);
  assert.match(recovery, /verifyAccountDeleted/);
  assert.match(recovery, /removeAfterVerification/);
  assert.doesNotMatch(recovery, /process\.env|console\.(?:log|error)|error\.message/);
});

test("private runner freezes product, deployed Edge version and administrative database contract", () => {
  assert.match(source, /\["rev-parse", "HEAD"\]/);
  assert.match(source, /\["status", "--porcelain"\]/);
  assert.match(source, /quata-account-lifecycle/);
  assert.match(source, /edge\.version !== input\.expectedEdge\?\.version/);
  assert.match(source, /edge\.ezbr_sha256 !== input\.expectedEdge\?\.ezbr_sha256/);
  assert.match(source, /deactivated_auth_user_id/);
  assert.match(source, /has_function_privilege\('service_role'/);
});
