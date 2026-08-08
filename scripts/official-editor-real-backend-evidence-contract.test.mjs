import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile(new URL("./official-editor-real-backend-evidence.mjs", import.meta.url), "utf8");
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("Official editor real backend evidence is explicit, reversible, and secret-free in source", () => {
  assert.match(runner, /OFFICIAL-EDITOR-REAL-BACKEND-001/);
  assert.match(runner, /I_ACCEPT_REVERSIBLE_OFFICIAL_POST_MUTATION/);
  assert.match(runner, /QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN/);
  assert.match(runner, /QUATA_OFFICIAL_E2E_OFFICIAL_PHONE/);
  assert.match(runner, /QUATA_OFFICIAL_E2E_NONOFFICIAL_PHONE/);
  assert.match(runner, /QUATA_OFFICIAL_E2E_PASSWORD/);
  assert.match(runner, /begin read only/);
  assert.match(runner, /values:\s*\[config\.countryCode,\s*\[config\.officialPhone,\s*config\.nonofficialPhone\]\]/);
  assert.match(runner, /delete from public\.official_post_likes where official_post_id = any\(\$1::uuid\[\]\)/);
  assert.match(runner, /delete from public\.official_post_comments where official_post_id = any\(\$1::uuid\[\]\)/);
  assert.match(runner, /delete from public\.official_posts/);
  assert.match(runner, /commit/);
  assert.match(runner, /rollback/);
  assert.match(runner, /marker_cleanup_verification_failed/);
  assert.match(runner, /denied_by_backend/);
  assert.match(runner, /failureStage/);
  assert.match(runner, /httpStatus/);
  assert.match(runner, /backendErrorCode/);
  assert.doesNotMatch(runner, /SERVICE_ROLE|supabase db push|migration repair|21085800|\+240|68024260[78]/);
});

test("Official editor real backend evidence report is redacted and records cleanup state", () => {
  assert.match(runner, /mutationPolicy/);
  assert.match(runner, /verified_official/);
  assert.match(runner, /verified_nonofficial/);
  assert.match(runner, /hard_deleted_verified/);
  assert.match(runner, /postIds/);
  assert.match(runner, /translationGroupId/);
  assert.doesNotMatch(runner, /refreshToken/);
  assert.doesNotMatch(runner, /report\s*=\s*\{[\s\S]*password/);
});

test("Official editor real backend evidence runner and contract are callable from package scripts", () => {
  const fast = packageJson.scripts["test:ci-fast-contracts"];
  const wave2 = packageJson.scripts["test:web-wave2-contracts"];
  const evidence = packageJson.scripts["evidence:official-editor-real-backend"];
  assert.match(fast, /scripts\/official-editor-real-backend-evidence-contract\.test\.mjs/);
  assert.match(wave2, /scripts\/official-editor-real-backend-evidence-contract\.test\.mjs/);
  assert.match(evidence, /scripts\/official-editor-real-backend-evidence\.mjs/);
});
