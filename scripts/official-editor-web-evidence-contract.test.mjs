import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile(new URL("./official-editor-web-evidence.mjs", import.meta.url), "utf8");
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("Official editor Web evidence keeps the permission fixture hermetic and mutation-free", () => {
  assert.match(runner, /OFFICIAL-EDITOR-WEB-CTA-001/);
  assert.match(runner, /assertDistributionRevision/);
  assert.match(runner, /gitMetadata/);
  assert.match(runner, /--require-pr-identity/);
  assert.match(runner, /assertPullRequestIdentity/);
  assert.match(runner, /pr_identity_checkout_not_merge/);
  assert.match(runner, /quata_web_access_token/);
  assert.match(runner, /community_profiles/);
  assert.match(runner, /url\.searchParams\.get\("id"\) !== `in\.\(\$\{PROFILE_ID\}\)`/);
  assert.match(runner, /request\.headers\.authorization === `Bearer \$\{ACCESS_TOKEN\}`/);
  assert.match(runner, /is_official: "true"/);
  assert.match(runner, /empty_publish_shows_shared_validation_feedback_without_mutation/);
  assert.match(runner, /official_editor_invalid_draft_mutated/);
  assert.match(runner, /valid_publish_attempt_uses_shared_postgrest_plan_and_fails_closed/);
  assert.match(runner, /fixture_publish_forbidden/);
  assert.match(runner, /fixture_mutation_forbidden/);
  assert.doesNotMatch(runner, /SUPABASE_DB_URL|SERVICE_ROLE|21085800|\+240|68024260/);
});

test("Official editor Web evidence runner and contract are callable from package scripts", () => {
  const fast = packageJson.scripts["test:ci-fast-contracts"];
  const wave2 = packageJson.scripts["test:web-wave2-contracts"];
  const evidence = packageJson.scripts["evidence:web-official-editor"];
  assert.match(fast, /scripts\/official-editor-web-evidence-contract\.test\.mjs/);
  assert.match(wave2, /scripts\/official-editor-web-evidence-contract\.test\.mjs/);
  assert.match(evidence, /scripts\/official-editor-web-evidence\.mjs/);
});
