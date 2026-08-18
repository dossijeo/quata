import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile("scripts/supabase-e2e-sb06.mjs", "utf8");
const packageJson = JSON.parse(await readFile("package.json", "utf8"));

test("SB-06 Profile/SOS runner is part of mandatory fast contract suites", () => {
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/supabase-e2e-sb06-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/supabase-e2e-sb06-contract\.test\.mjs/);
});

test("SB-06 can use the approved credentials file without hardcoding secrets", () => {
  assert.match(runner, /QUATA_CHAT_GROUP_CREDENTIALS_FILE/);
  assert.match(runner, /usersFromPrivateFile/);
  assert.match(runner, /two_approved_existing_profiles_public_key/);
  assert.doesNotMatch(runner, /680242607|680242608|21085800/);
});

test("SB-06 restores preexisting SOS contacts for approved existing profiles", () => {
  assert.match(runner, /existing_sos_set_snapshotted_for_restore/);
  assert.match(runner, /restoreContacts/);
  assert.match(runner, /snapshot_restore_mismatch/);
  assert.match(runner, /sos_contacts_restored_from_snapshot_after_failure/);
  assert.match(runner, /profile_field_and_contact_snapshot_restored/);
});

test("SB-06 report redacts identities and rejects privileged keys", () => {
  assert.match(runner, /profileIdSha256/);
  assert.match(runner, /sha256\(session\.profileId\)\.slice\(0, 16\)/);
  assert.match(runner, /replace\(\s*\/\[0-9a-f\]\{8\}-\[0-9a-f\]\{4\}/);
  assert.match(runner, /await report\(output, failure\)/);
  assert.match(runner, /invalid_or_privileged_supabase_key/);
  assert.match(runner, /No service-role, database URL, SQL, DDL, RPC, schema changes, or user creation/);
});
