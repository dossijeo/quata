import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const migration = await readFile(new URL("../supabase/migrations/20261010090000_profile_phone_uniqueness.sql", import.meta.url), "utf8");
const correction = await readFile(new URL("../supabase/migrations/20261010101500_profile_phone_country_identity.sql", import.meta.url), "utf8");
const executor = await readFile(new URL("./selective-db-release-executor.mjs", import.meta.url), "utf8");
const audit = await readFile(new URL("./account-details-collision-audit.mjs", import.meta.url), "utf8");
const postdeploy = await readFile(new URL("./account-details-collision-postdeploy.mjs", import.meta.url), "utf8");

test("profile phone uniqueness migration refuses collisions and preserves the production index identities", () => {
  assert.match(migration, /lock table public\.community_profiles in share row exclusive mode/i);
  assert.match(migration, /profile_phone_local_collision_precondition_failed/);
  assert.match(migration, /profile_phone_normalized_collision_precondition_failed/);
  assert.match(migration, /community_profiles_phone_local_uidx/);
  assert.match(migration, /unique_phone_normalized/);
  assert.doesNotMatch(migration, /drop\s+(?:index|constraint|table)/i);
});

test("forward migration replaces global local-number uniqueness with canonical phone identity", () => {
  assert.match(correction, /lock table public\.community_profiles in share row exclusive mode/i);
  assert.match(correction, /group by country_code, phone_local/i);
  assert.match(correction, /group by phone_e164/i);
  assert.match(correction, /community_profiles_country_phone_local_uidx/);
  assert.match(correction, /community_profiles_phone_e164_uidx/);
  for (const obsolete of [
    "community_profiles_phone_local_key",
    "community_profiles_phone_local_uidx",
    "phone_unique",
    "unique_phone_normalized",
  ]) assert.match(correction, new RegExp(`drop index if exists public\\.${obsolete}`));
});

test("selective release binds the exact migration bytes and fail-closed postcondition", () => {
  assert.match(executor, /\["20261010090000", "2c1be23a5ece0c970a300464648ec148ce0e023437ead79791171bae0f45fada"\]/);
  assert.match(executor, /selective_release_profile_phone_uniqueness_postcondition_failed/);
  assert.match(executor, /local_values_unique/);
  assert.match(executor, /normalized_values_unique/);
  assert.match(executor, /\["20261010101500", "4e423db5fc68fe5e42411824a49242efdd0ae2e875c56a2b225bc4ebb0f0991e"\]/);
  assert.match(executor, /selective_release_profile_phone_country_identity_postcondition_failed/);
  assert.match(executor, /obsolete_global_local_indexes_absent/);
  assert.match(executor, /country_local_values_unique/);
  assert.match(executor, /e164_values_unique/);
});

test("production audit is aggregate-only and requires caller-supplied credential file paths", () => {
  assert.match(audit, /begin read only/i);
  assert.match(audit, /No profile identifiers, phone values, emails or credentials are emitted/);
  assert.match(audit, /requiredFile\("SUPABASE_DB_URL_FILE"\)/);
  assert.match(audit, /requiredFile\("SUPABASE_DB_TLS_CA_FILE"\)/);
  assert.doesNotMatch(audit, /C:\/Users\//i);
  assert.doesNotMatch(audit, /select\s+id\b/i);
});

test("postdeploy collision probes remain transactional, private and residue-free", () => {
  assert.match(postdeploy, /begin/);
  assert.match(postdeploy, /savepoint different_country_probe/);
  assert.match(postdeploy, /savepoint exact_identity_probe/);
  assert.match(postdeploy, /same-local-different-country/);
  assert.match(postdeploy, /same-country-and-local/);
  assert.match(postdeploy, /await client\.query\("rollback"\)/);
  assert.match(postdeploy, /error\?\.code !== "23505"/);
  assert.match(postdeploy, /residue: "zero"/);
  assert.match(postdeploy, /No profile identifiers, phone values, emails or credentials are emitted/);
  assert.doesNotMatch(postdeploy, /C:\/Users\//i);
});
