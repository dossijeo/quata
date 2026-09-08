import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const bridge = await readFile(new URL("./index.ts", import.meta.url), "utf8");
const readme = await readFile(new URL("./README.md", import.meta.url), "utf8");

test("quata-auth-bridge supports authenticated recovery secret updates before public login fallback", () => {
  const actionIndex = bridge.indexOf('action === "update_recovery_secret"');
  const profileLookupIndex = bridge.indexOf("const profile = await findProfile(admin, payload)");
  const passwordRequiredIndex = bridge.indexOf('password_required');
  assert.ok(actionIndex > 0, "missing update_recovery_secret action");
  assert.ok(profileLookupIndex > actionIndex, "update_recovery_secret must not depend on public phone lookup");
  assert.ok(passwordRequiredIndex > actionIndex, "update_recovery_secret must run before login password fallback");
  assert.match(bridge, /payload\.version !== 1/);
  assert.match(bridge, /admin\.auth\.getUser\(bearer\)/);
  assert.match(bridge, /recoverySecretPatch\(payload\.secret_question, payload\.secret_answer, pepper\)/);
  assert.match(bridge, /\.from\("community_profiles"\)\.update\(patch\)[\s\S]*\.eq\("auth_user_id", identity\.user\.id\)/);
});

test("quata-auth-bridge keeps public recovery read/reset separate from authenticated secret write", () => {
  assert.match(bridge, /action === "recovery_question"[\s\S]*secret_question/);
  assert.match(bridge, /action === "reset_password"[\s\S]*handlePasswordReset/);
  assert.match(bridge, /secret_answer_hash/);
  assert.match(bridge, /constantTimeEqualHex/);
  assert.doesNotMatch(bridge, /console\.log\([^)]*secret_answer|console\.error\([^)]*secret_answer/);
});

test("quata-auth-bridge deploy remains a single no-jwt function operation", () => {
  assert.match(readme, /supabase functions deploy quata-auth-bridge --no-verify-jwt/);
  assert.doesNotMatch(readme, /db push|migration repair|supabase migration/);
});
