import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const migration = await readFile(new URL("../supabase/migrations/20261010090000_profile_phone_uniqueness.sql", import.meta.url), "utf8");
const executor = await readFile(new URL("./selective-db-release-executor.mjs", import.meta.url), "utf8");
const audit = await readFile(new URL("./account-details-collision-audit.mjs", import.meta.url), "utf8");

test("profile phone uniqueness migration refuses collisions and preserves the production index identities", () => {
  assert.match(migration, /lock table public\.community_profiles in share row exclusive mode/i);
  assert.match(migration, /profile_phone_local_collision_precondition_failed/);
  assert.match(migration, /profile_phone_normalized_collision_precondition_failed/);
  assert.match(migration, /community_profiles_phone_local_uidx/);
  assert.match(migration, /unique_phone_normalized/);
  assert.doesNotMatch(migration, /drop\s+(?:index|constraint|table)/i);
});

test("selective release binds the exact migration bytes and fail-closed postcondition", () => {
  assert.match(executor, /\["20261010090000", "2c1be23a5ece0c970a300464648ec148ce0e023437ead79791171bae0f45fada"\]/);
  assert.match(executor, /selective_release_profile_phone_uniqueness_postcondition_failed/);
  assert.match(executor, /local_values_unique/);
  assert.match(executor, /normalized_values_unique/);
});

test("production audit is aggregate-only and requires caller-supplied credential file paths", () => {
  assert.match(audit, /begin read only/i);
  assert.match(audit, /No profile identifiers, phone values, emails or credentials are emitted/);
  assert.match(audit, /requiredFile\("SUPABASE_DB_URL_FILE"\)/);
  assert.match(audit, /requiredFile\("SUPABASE_DB_TLS_CA_FILE"\)/);
  assert.doesNotMatch(audit, /C:\/Users\//i);
  assert.doesNotMatch(audit, /select\s+id\b/i);
});
