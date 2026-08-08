import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile(new URL("./official-editor-web-real-evidence.mjs", import.meta.url), "utf8");
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("Official editor Web real evidence is opt-in, redacted, and reversible", () => {
  assert.match(runner, /OFFICIAL-EDITOR-WEB-REAL-UI-001/);
  assert.match(runner, /I_ACCEPT_REVERSIBLE_OFFICIAL_POST_MUTATION/);
  assert.match(runner, /QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN/);
  assert.match(runner, /quata-auth-bridge/);
  assert.match(runner, /action:\s*"web_login"/);
  assert.match(runner, /quata_web_access_token/);
  assert.match(runner, /quata_web_session_token/);
  assert.match(runner, /official_editor_invalid_draft_mutated/);
  assert.match(runner, /readCreatedRows/);
  assert.match(runner, /delete from public\.official_posts/);
  assert.match(runner, /values:\s*\[ids, groupIds\]/);
  assert.match(runner, /assertNoMarkerRows/);
  assert.match(runner, /marker_cleanup_verification_failed/);
  assert.doesNotMatch(runner, /SERVICE_ROLE|21085800|\+240|68024260/);
});

test("Official editor Web real evidence is callable but kept out of automatic fast CI mutation gates", () => {
  assert.match(packageJson.scripts["evidence:web-official-editor-real"], /scripts\/official-editor-web-real-evidence\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/official-editor-web-real-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/official-editor-web-real-evidence-contract\.test\.mjs/);
  assert.doesNotMatch(packageJson.scripts["test:ci-fast-contracts"], /official-editor-web-real-evidence\.mjs --/);
});
