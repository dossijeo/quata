import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const evidence = JSON.parse(await read("../docs/runbooks/migration/evidence/auth-register-foundation-rollout-20260925.json"));
const executor = await read("./selective-db-release-executor.mjs");
const inventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const functionReadme = await read("../supabase/functions/quata-register/README.md");

test("registration foundation receipt is fail-closed and residue-free", () => {
  assert.equal(evidence.status, "passed_fail_closed");
  assert.equal(evidence.backup.scope, "Full");
  assert.equal(evidence.backup.restoreDrill, "passed");
  assert.equal(evidence.databaseRelease.commitStatus, "committed");
  assert.equal(evidence.postflight.allRegistrationTablesRlsEnabled, true);
  assert.equal(evidence.postflight.serviceRoleTableAndRpcAccess, true);
  assert.equal(evidence.postflight.anonTableAndRpcAccess, false);
  assert.equal(evidence.postflight.authenticatedTableAndRpcAccess, false);
  assert.equal(evidence.postflight.requestRows, 0);
  assert.equal(evidence.postflight.rateLimitRows, 0);
  assert.equal(evidence.postflight.cleanupEventRows, 0);
  assert.equal(evidence.configuration.registrationEnabled, false);
  assert.equal(evidence.configuration.turnstileSecretPresent, false);
  assert.deepEqual(evidence.configuration.externalProbe, {
    httpStatus: 503,
    error: "registration_unavailable",
  });
});

test("selective executor pins registration SQL and verifies its exact security boundary", () => {
  assert.match(executor, /20260726171004", "f60d2bbafc994215aaeb6a38c6f18ae16e97d6e12cbc1ce83778878e33a45606"/);
  assert.match(executor, /selectedVersions\.includes\("20260726171004"\)/);
  assert.match(executor, /all_rls_enabled/);
  assert.match(executor, /service_claim_execute/);
  assert.match(executor, /anon_claim_execute/);
  assert.match(executor, /authenticated_claim_execute/);
  assert.match(executor, /request_rows !== 0/);
  assert.match(executor, /selective_release_registration_postcondition_failed/);
});

test("operator documentation reports the deployed disabled state without claiming registration GO", () => {
  assert.match(inventory, /quata-register` v1 y `quata-auth-bridge` v81/);
  assert.match(inventory, /QUATA_WEB_REGISTRATION_ENABLED=false/);
  assert.match(inventory, /Falta una credencial Turnstile real/);
  assert.match(functionReadme, /registration disabled/);
  assert.match(functionReadme, /registration_unavailable/);
});
