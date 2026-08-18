import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const webRunner = readFileSync(new URL("./post-publish-web-evidence.mjs", import.meta.url), "utf8");
const androidRunner = readFileSync(new URL("./post-publish-android-evidence.mjs", import.meta.url), "utf8");
const iosRunner = readFileSync(new URL("./post-publish-ios-evidence.mjs", import.meta.url), "utf8");
const iosWrapper = readFileSync(new URL("./run-ios-post-publish-ui-test.sh", import.meta.url), "utf8");
const sharedFixtures = readFileSync(new URL("./e2e-fixtures/chat-attachments.mjs", import.meta.url), "utf8");
const mainActivity = readFileSync(new URL("../app/src/main/java/com/quata/MainActivity.kt", import.meta.url), "utf8");
const androidPostPublishTest = readFileSync(new URL("../app/src/androidTest/java/com/quata/feature/postcomposer/presentation/PostPublishRealInstrumentedTest.kt", import.meta.url), "utf8");
const iosPostPublishTest = readFileSync(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedPostPublishUITests.swift", import.meta.url), "utf8");
const commonPublishButton = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerPublishButtonContent.kt", import.meta.url), "utf8");
const webPostComposerHost = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerHost.kt", import.meta.url), "utf8");
const webPostComposerBridge = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerE2eBridge.kt", import.meta.url), "utf8");

test("post publish web runner uses the shared reversible fixture and cleanup", () => {
  assert.match(webRunner, /createPostPublishFixture/);
  assert.match(webRunner, /pollPostPublishFixture/);
  assert.match(webRunner, /cleanupPostPublishFixture/);
  assert.match(webRunner, /clickSemanticElement\(page, "composer-type-text"\)/);
  assert.match(webRunner, /clickSemanticElement\(page, "composer-publish", \{ reinforcePhysical: true \}\)/);
  assert.doesNotMatch(webRunner, /delete from public\.community_posts/);
});

test("post publish runner requires explicit reversible mutation opt-in", () => {
  assert.match(webRunner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  assert.match(androidRunner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  assert.match(iosRunner, /QUATA_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  assert.match(iosWrapper, /QUATA_IOS_POST_PUBLISH_REAL_MUTATION_OPT_IN/);
  assert.match(webRunner, /I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION/);
  assert.match(androidRunner, /I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION/);
  assert.match(iosRunner, /I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION/);
});

test("web composer submit uses a localhost opt-in bridge without replacing common UI state", () => {
  assert.match(webPostComposerHost, /installWebPostComposerE2eBridge/);
  assert.match(webPostComposerHost, /viewModel\.submit\(PostComposerType\.Text\)/);
  assert.match(webPostComposerHost, /textLength/);
  assert.match(webPostComposerBridge, /quata-post-publish-e2e/);
  assert.match(webPostComposerBridge, /__quataPostComposerE2eProduct/);
  assert.match(webPostComposerBridge, /state:/);
  assert.match(webRunner, /__quataPostComposerE2eProduct/);
  assert.match(webRunner, /web_post_publish_submitted_by_localhost_opt_in_product_bridge_after_visual_route/);
  assert.match(webRunner, /quata-supabase-url/);
  assert.match(webRunner, /post_publish_ui_error/);
  assert.doesNotMatch(webPostComposerHost, /publishButton =/);
});

test("shared post publish fixture owns community post cleanup and residue verification", () => {
  assert.match(sharedFixtures, /export async function cleanupPostPublishFixture/);
  assert.match(sharedFixtures, /delete from public\.community_post_likes/);
  assert.match(sharedFixtures, /delete from public\.community_comments/);
  assert.match(sharedFixtures, /delete from public\.community_posts/);
  assert.match(sharedFixtures, /cleanup_residue_detected:post_publish/);
});

test("post publish android runner delegates backend fixture ownership to shared helpers", () => {
  assert.match(androidRunner, /createPostPublishFixture/);
  assert.match(androidRunner, /pollPostPublishFixture/);
  assert.match(androidRunner, /cleanupPostPublishFixture/);
  assert.match(androidRunner, /PostPublishRealInstrumentedTest/);
  assert.doesNotMatch(androidRunner, /delete from public\.community_posts/);
});

test("post publish android evidence uses common composer tags through the debug start route", () => {
  assert.match(mainActivity, /AppDestinations\.CreatePost\.route/);
  assert.match(androidPostPublishTest, /CreatePostCommonRootTestTag/);
  assert.match(androidPostPublishTest, /ComposerTextInputTestTag/);
  assert.match(androidPostPublishTest, /ComposerPublishButtonTestTag/);
  assert.match(androidPostPublishTest, /START_DESTINATION_FOR_EVIDENCE/);
  assert.match(androidPostPublishTest, /"create_post"/);
});

test("post publish ios runner delegates backend fixture ownership to shared helpers", () => {
  assert.match(iosRunner, /createPostPublishFixture/);
  assert.match(iosRunner, /pollPostPublishFixture/);
  assert.match(iosRunner, /cleanupPostPublishFixture/);
  assert.match(iosRunner, /run-ios-post-publish-ui-test\.sh/);
  assert.match(iosRunner, /mac_checkout_sha_matches_local_candidate/);
  assert.doesNotMatch(iosRunner, /delete from public\.community_posts/);
});

test("post publish ios wrapper seeds Keychain and requires executed XCTest markers", () => {
  assert.match(iosWrapper, /QuataIosAuthenticatedSessionSeederTests\/testSeedAuthenticatedSessionForVisualGates/);
  assert.match(iosWrapper, /QuataIosAuthenticatedPostPublishUITests\/testAuthenticatedSessionPublishesRealTextPost/);
  assert.match(iosWrapper, /check-ios-xctest-executed\.py/);
  assert.match(iosWrapper, /--require-terminal-success-marker/);
  assert.match(iosWrapper, /IOS_POST_PUBLISH_UI_GATE_PASSED/);
});

test("post publish ios UI test uses common semantic anchors", () => {
  assert.match(iosPostPublishTest, /quata-ios-composer-host/);
  assert.match(iosPostPublishTest, /feed\.action\.publish\./);
  assert.match(iosPostPublishTest, /create-post-common-root/);
  assert.match(iosPostPublishTest, /composer-type-text/);
  assert.match(iosPostPublishTest, /composer-text-input/);
  assert.match(iosPostPublishTest, /composer-publish/);
  assert.match(iosPostPublishTest, /composer-feedback-success/);
});

test("common publish button exposes an accessibility action for iOS XCTest replay", () => {
  assert.match(commonPublishButton, /Button\(/);
  assert.match(commonPublishButton, /enabled = !isLoading/);
  assert.match(commonPublishButton, /onClick\(label = publishLabel\)/);
  assert.match(commonPublishButton, /if \(!isLoading\) onSubmit\(\)/);
  assert.doesNotMatch(commonPublishButton, /\.clickable\(/);
});
