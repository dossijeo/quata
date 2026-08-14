import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const webRunner = await readFile(new URL("./chat-actions-notifications-web-evidence.mjs", import.meta.url), "utf8");
const androidRunner = await readFile(new URL("./chat-actions-notifications-android-evidence.mjs", import.meta.url), "utf8");
const iosRunner = await readFile(new URL("./chat-actions-notifications-ios-evidence.mjs", import.meta.url), "utf8");
const iosWrapper = await readFile(new URL("./run-ios-chat-actions-notifications-ui-test.sh", import.meta.url), "utf8");
const sharedFixtures = await readFile(new URL("./e2e-fixtures/chat-attachments.mjs", import.meta.url), "utf8");
const androidUiTest = await readFile(new URL("../app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt", import.meta.url), "utf8");
const iosUiTest = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift", import.meta.url), "utf8");
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("PROF-CONTENT evidence mode is opt-in, redacted and reversible", () => {
  for (const runner of [webRunner, androidRunner, iosRunner]) {
    assert.match(runner, /--profile-content-only/);
    assert.match(runner, /prepareProfileContentFixture/);
    assert.match(runner, /cleanupProfileContentFixture/);
    assert.match(runner, /qadata-profile-content-/);
    assert.match(runner, /cleanup_verified_profile_content_residue_absent/);
    assert.match(runner, /pollProfileContentComment/);
    assert.match(runner, /profile_content_comment_created_from_ui_and_verified_by_db/);
    assert.match(runner, /attachmentMessageId/);
    assert.match(runner, /seedProfileContentFixture/);
    assert.match(runner, /cleanupSharedProfileContentFixture/);
    assert.match(runner, /pollSharedProfileContentComment/);
    assert.doesNotMatch(runner, /profile content attachment \$\{marker\}/);
    assert.doesNotMatch(runner, /quata_chat_register_attachment"[\s\S]*profile-content-attachment-\$\{marker\}/);
    assert.doesNotMatch(runner, /insert into public\.community_posts/);
    assert.doesNotMatch(runner, /else if \(options\.profileOnly \|\| options\.profileFollowOnly \|\| options\.profileListsOnly \|\| options\.profileContentOnly\) \{\s*\}\s*else if/);
    assert.doesNotMatch(runner, /profile_content_fixture_not_implemented/);
    assert.doesNotMatch(runner, /680242607|680242608|21085800|SERVICE_ROLE\s*=/);
  }
  assert.match(sharedFixtures, /export async function seedProfileContentFixture/);
  assert.match(sharedFixtures, /export async function cleanupProfileContentFixture/);
  assert.match(sharedFixtures, /export async function pollProfileContentComment/);
  assert.match(sharedFixtures, /cleanup\?\.trackStorageObject/);
  assert.match(sharedFixtures, /community_posts/);
  assert.match(sharedFixtures, /community_comments/);
  assert.match(sharedFixtures, /community_post_likes/);
  assert.match(sharedFixtures, /chat_attachments/);
  assert.match(sharedFixtures, /cleanup_verified_profile_content_residue_absent/);
  assert.match(iosRunner, /profile_content_shared_attachment_rpc_verified/);
  assert.match(iosRunner, /profile_content_shared_attachment_rpc_missing/);
  assert.match(iosRunner, /quata_chat_list_shared_attachments/);
});

test("PROF-CONTENT evidence uses common public-profile content anchors on every platform", () => {
  for (const source of [webRunner, androidUiTest, iosUiTest]) {
    assert.match(source, /public-profile\.kpi\.posts\./);
    assert.match(source, /public-profile\.gallery\.header\./);
    assert.match(source, /public-profile\.gallery\./);
    assert.match(source, /public-profile\.gallery\.post\./);
    assert.match(source, /public-profile\.post\.preview\./);
    assert.match(source, /public-profile\.post\.action\.comments\./);
    assert.match(source, /public-profile\.comments\.panel/);
    assert.match(source, /public-profile\.comments\.list/);
    assert.match(source, /public-profile\.comments\.row\./);
    assert.match(source, /public-profile\.comments\.input/);
    assert.match(source, /public-profile\.comments\.send/);
    assert.match(source, /public-profile\.attachments/);
    assert.match(source, /public-profile\.attachments\.item\./);
  }
  assert.match(androidUiTest, /performTextReplacement\(uiComment\)/);
  assert.match(androidUiTest, /public-profile\.attachments\.item\.sb:\$attachmentId/);
  assert.match(iosUiTest, /public-profile\.attachments\.item\.sb:\\\(attachmentId\)/);
  assert.match(webRunner, /public-profile\.attachments\.item\.sb:\$\{fixture\.attachmentId\}/);
  assert.match(androidUiTest, /"profile-content" -> \{\s*openProfileFromPeerMessage\(peerProbe\.orEmpty\(\), profileId\.orEmpty\(\)\)/);
  assert.match(iosUiTest, /QUATA_IOS_CHAT_PROFILE_CONTENT_UI_COMMENT/);
  assert.match(iosUiTest, /typeText\(uiComment, into: "public-profile\.comments\.input", in: app\)/);
  assert.match(iosUiTest, /public-profile\.comments\.close/);
  assert.match(iosUiTest, /dismissProfileCommentsPanel\(in: app\)/);
  assert.match(iosUiTest, /profile comment submitted from iOS must remain visible/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E/);
  assert.match(iosWrapper, /testProfileContentFromChatUsesSharedPublicProfileSurface/);
  assert.match(iosWrapper, /profile-content\.log/);
  assert.match(iosWrapper, /elif \[\[ "\$QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E" == "1" \]\]; then\s+run_and_require "\$profile_content" "\$profile_content_method"/);
  assert.match(iosWrapper, /"\$QUATA_IOS_CHAT_PROFILE_CONTENT_UI_E2E" != "1"/);
});

test("Android PROF-CONTENT runner writes focal reports to requested paths", () => {
  assert.match(androidRunner, /function parseArgs\(argv\)/);
  assert.match(androidRunner, /"--out", "--evidence-dir"/);
  assert.match(androidRunner, /const evidenceDir = options\.evidenceDir/);
  assert.match(androidRunner, /const output = options\.output/);
});

test("PROF-CONTENT runners provide delay to the shared comment poller", () => {
  for (const runner of [androidRunner, iosRunner]) {
    assert.match(runner, /setTimeout as delay/);
    assert.match(runner, /pollSharedProfileContentComment\(\{ fixture, marker, withDatabase, delay, timeout \}\)/);
  }
});

test("PROF-CONTENT contract is part of local fast contract suites", () => {
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/profile-content-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/profile-content-evidence-contract\.test\.mjs/);
});
