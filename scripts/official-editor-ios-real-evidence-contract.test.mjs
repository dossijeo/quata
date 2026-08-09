import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile(new URL("./official-editor-ios-real-evidence.mjs", import.meta.url), "utf8");
const shellRunner = await readFile(new URL("./run-ios-authenticated-official-editor-ui-test.sh", import.meta.url), "utf8");
const uiTest = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedOfficialEditorUITests.swift", import.meta.url), "utf8");
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("iOS Official editor real evidence is explicit opt-in, marker-based and cleans exact backend rows", () => {
  assert.match(runner, /OFFICIAL-EDITOR-IOS-REAL-UI-001/);
  assert.match(runner, /I_ACCEPT_REVERSIBLE_OFFICIAL_POST_MUTATION/);
  assert.match(runner, /QUATA_OFFICIAL_E2E_REAL_MUTATION_OPT_IN/);
  assert.match(runner, /QUATA_IOS_SIMULATOR_UDID/);
  assert.match(runner, /official-ios-ui-\$\{randomUUID\(\)\}/);
  assert.match(runner, /QUATA_IOS_OFFICIAL_EDITOR_MARKER/);
  assert.match(runner, /scripts\/run-ios-authenticated-official-editor-ui-test\.sh/);
  assert.match(runner, /const remoteHead = \(await runSshScript/);
  assert.match(runner, /begin read only/);
  assert.match(runner, /where title like \$1 or content_html like \$1/);
  assert.match(runner, /resolvedIds/);
  assert.match(runner, /delete from public\.official_post_likes/);
  assert.match(runner, /delete from public\.official_post_comments/);
  assert.match(runner, /delete from public\.official_posts/);
  assert.match(runner, /verified_absent/);
  assert.match(runner, /rollback_pending/);
  assert.match(runner, /rejectUnauthorized: true/);
  assert.doesNotMatch(runner, /supabase db push|migration repair|service_role|SUPABASE_DB_URL\s*=/i);
  assert.doesNotMatch(runner, /21085800|\+240|68024260/);
});

test("iOS shell runner patches a temporary xctestrun and requires the real publish XCTest when marker is present", () => {
  assert.match(shellRunner, /patched_xctestrun="\$QUATA_IOS_OFFICIAL_EDITOR_UI_LOG_DIR/);
  assert.match(shellRunner, /cp "\$xctestrun" "\$patched_xctestrun"/);
  assert.match(shellRunner, /env\['QUATA_IOS_OFFICIAL_EDITOR_MARKER'\] = marker/);
  assert.match(shellRunner, /env\['QUATA_IOS_OFFICIAL_EDITOR_REAL_PUBLISH_OPT_IN'\] = opt_in/);
  assert.match(shellRunner, /testAuthenticatedSessionPublishesRealOfficialPost/);
  assert.match(shellRunner, /check-ios-xctest-executed\.py/);
});

test("iOS UI test performs validation, edits the common rich text field, publishes and skips translation only when shown", () => {
  assert.match(uiTest, /testAuthenticatedSessionPublishesRealOfficialPost/);
  assert.match(uiTest, /QUATA_IOS_OFFICIAL_EDITOR_REAL_PUBLISH_OPT_IN/);
  assert.match(uiTest, /QUATA_IOS_OFFICIAL_EDITOR_MARKER/);
  assert.match(uiTest, /official-editor-feedback/);
  assert.match(uiTest, /quata-portable-rich-text-field/);
  assert.match(uiTest, /official-editor-publish/);
  assert.match(uiTest, /Publicar solo este idioma/);
  assert.match(uiTest, /Publish only this language/);
  assert.doesNotMatch(uiTest, /SUPABASE_DB_URL|service_role|21085800|\+240|68024260/);
});

test("iOS real Official editor evidence is part of the fast and wave2 contract suites", () => {
  assert.match(packageJson.scripts["evidence:ios-official-editor-real"], /scripts\/official-editor-ios-real-evidence\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/official-editor-ios-real-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/official-editor-ios-real-evidence-contract\.test\.mjs/);
});
