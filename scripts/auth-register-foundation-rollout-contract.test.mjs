import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const evidence = JSON.parse(await read("../docs/runbooks/migration/evidence/auth-register-foundation-rollout-20260925.json"));
const executor = await read("./selective-db-release-executor.mjs");
const postconditions = await read("./sql/web-registration-release-postconditions.sql");
const inventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const functionReadme = await read("../supabase/functions/quata-register/README.md");

test("registration foundation receipt is fail-closed and residue-free", () => {
  assert.equal(evidence.status, "passed_fail_closed");
  assert.equal(evidence.backup.scope, "Full");
  assert.equal(evidence.backup.restoreDrill, "passed");
  assert.equal(evidence.databaseRelease.commitStatus, "committed");
  assert.equal(evidence.postflight.allRegistrationTablesRlsEnabled, true);
  assert.equal(evidence.postflight.serviceRoleGrantedTablePrivilegesComplete, true);
  assert.equal(evidence.postflight.serviceRoleAllFourRpcExecutes, true);
  assert.equal(evidence.postflight.anonAllTablePrivilegesAndRpcExecutesDenied, true);
  assert.equal(evidence.postflight.authenticatedAllTablePrivilegesAndRpcExecutesDenied, true);
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
  assert.match(executor, /web-registration-release-postconditions\.sql/);
  assert.match(executor, /service_table_acl_complete/);
  assert.match(executor, /untrusted_table_acl_denied/);
  assert.match(executor, /service_function_acl_complete/);
  assert.match(executor, /untrusted_function_acl_denied/);
  assert.match(executor, /rate_limit_rows !== 0/);
  assert.match(executor, /cleanup_event_rows !== 0/);
  assert.match(executor, /selective_release_registration_postcondition_failed/);
  for (const role of ["anon", "authenticated"]) {
    assert.match(postconditions, new RegExp(`\\('${role}'\\)`));
  }
  for (const table of ["web_registration_requests", "web_registration_rate_limits", "web_registration_cleanup_events"]) {
    assert.match(postconditions, new RegExp(`\\('${table}'\\)`));
  }
  assert.match(postconditions, /'SELECT'\), \('INSERT'\), \('UPDATE'\), \('DELETE'\)/);
  assert.match(postconditions, /'TRUNCATE'\), \('REFERENCES'\), \('TRIGGER'\)/);
  assert.match(postconditions, /has_table_privilege/);
  assert.match(postconditions, /has_function_privilege/);
});

test("operator documentation reports the deployed disabled state without claiming registration GO", () => {
  assert.match(inventory, /quata-register` v1 y `quata-auth-bridge` v81/);
  assert.match(inventory, /QUATA_WEB_REGISTRATION_ENABLED=false/);
  assert.match(inventory, /Falta una credencial Turnstile real/);
  assert.match(functionReadme, /registration disabled/);
  assert.match(functionReadme, /registration_unavailable/);
});
