import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../supabase/functions/quata-account-lifecycle/index.ts", import.meta.url), "utf8");

test("deactivation revokes all Web sessions and subscriptions before unlinking the profile", () => {
  const revoke = source.indexOf("await revokeWebSessions(admin, profile.id, authUserId)");
  const deactivate = source.indexOf('admin.rpc("quata_account_deactivate"');
  assert.ok(revoke >= 0 && deactivate > revoke);
  assert.match(source, /from\("web_push_subscriptions"\)[\s\S]*?disabled_at: now/);
  assert.match(source, /from\("web_client_sessions"\)[\s\S]*?revoked_at: now/);
  assert.match(source, /profile_id\.eq\.\$\{profileId\},auth_user_id\.eq\.\$\{authUserId\}/);
});
