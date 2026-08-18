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
const commonDestinationSelector = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerDestinationSelectorContent.kt", import.meta.url), "utf8");
const commonLocationSection = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerLocationSectionContent.kt", import.meta.url), "utf8");
const commonLocationEditor = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerLocationTextEditorContent.kt", import.meta.url), "utf8");
const commonComposerRepository = readFileSync(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/data/ActorBoundPostComposerRepository.kt", import.meta.url), "utf8");
const iosComposerHost = readFileSync(new URL("../feature/postcomposer/src/iosMain/kotlin/com/quata/feature/postcomposer/presentation/IosComposerHost.kt", import.meta.url), "utf8");
const iosApp = readFileSync(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8");
const webPostComposerHost = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerHost.kt", import.meta.url), "utf8");
const webPostComposerBridge = readFileSync(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerE2eBridge.kt", import.meta.url), "utf8");

test("post publish web runner uses the shared reversible fixture and cleanup", () => {
  assert.match(webRunner, /createPostPublishFixture/);
  assert.match(webRunner, /pollPostPublishFixture/);
  assert.match(webRunner, /cleanupPostPublishFixture/);
  assert.match(webRunner, /selectPostPublishDestinationFixture/);
  assert.match(webRunner, /clickSemanticElement\(page, `composer-type-\$\{composerType\}`\)/);
  assert.match(webRunner, /const composerType = options\.mode === "image-location" \? "image" : "text"/);
  assert.match(webRunner, /composer-destination-option\.\$\{destination\.wallId\}/);
  assert.match(webRunner, /clickSemanticElement\(page, "composer-publish", \{ reinforcePhysical: true \}\)/);
  assert.match(webRunner, /expectedWallId/);
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
  assert.match(webPostComposerHost, /CreatePostUiEvent\.TextChanged/);
  assert.match(webPostComposerHost, /CreatePostUiEvent\.ImageSelected/);
  assert.match(webPostComposerHost, /CreatePostUiEvent\.LocationLabelChanged/);
  assert.match(webPostComposerHost, /viewModel\.submit\(PostComposerType\.Text\)/);
  assert.match(webPostComposerHost, /viewModel\.submit\(PostComposerType\.Image\)/);
  assert.match(webPostComposerHost, /textLength/);
  assert.match(webPostComposerHost, /locationLabel/);
  assert.match(webPostComposerHost, /selectedDestinationWallId/);
  assert.match(webPostComposerBridge, /quata-post-publish-e2e/);
  assert.match(webPostComposerBridge, /__quataPostComposerE2eProduct/);
  assert.match(webPostComposerBridge, /setText:/);
  assert.match(webPostComposerBridge, /setImage:/);
  assert.match(webPostComposerBridge, /setLocation:/);
  assert.match(webPostComposerBridge, /submitImage:/);
  assert.match(webPostComposerBridge, /state:/);
  assert.match(webRunner, /__quataPostComposerE2eProduct/);
  assert.match(webRunner, /image-location/);
  assert.match(webRunner, /validPngFixture/);
  assert.match(webRunner, /expectedLocationLabel/);
  assert.match(webRunner, /web_text_written_by_localhost_opt_in_product_bridge_after_compose_keyboard_limit/);
  assert.match(webRunner, /web_post_publish_submitted_by_localhost_opt_in_product_bridge_after_visual_route/);
  assert.match(webRunner, /quata-supabase-url/);
  assert.match(webRunner, /post_publish_ui_error/);
  assert.doesNotMatch(webPostComposerHost, /publishButton =/);
});

test("common destination selector exposes stable anchors for all platform replays", () => {
  assert.match(commonDestinationSelector, /ComposerDestinationSelectorTestTag = "composer-destination-selector"/);
  assert.match(commonDestinationSelector, /ComposerDestinationSelectedTestTag = "composer-destination-selected"/);
  assert.match(commonDestinationSelector, /composer-destination-option\.\$\{destination\.wallId\}/);
  assert.match(commonDestinationSelector, /contentDescription = "Destino:/);
});

test("post location uses common anchors and the shared remote metadata codec", () => {
  assert.match(commonLocationSection, /ComposerLocationSectionTestTag = "composer-location-section"/);
  assert.match(commonLocationSection, /ComposerLocationValueTestTag = "composer-location-value"/);
  assert.match(commonLocationSection, /ComposerLocationEditTestTag = "composer-location-edit"/);
  assert.match(commonLocationSection, /contentDescription = "\$title: \$locationText"/);
  assert.match(commonLocationEditor, /ComposerLocationInputTestTag = "composer-location-input"/);
  assert.match(commonComposerRepository, /PostComposerType\.Image -> buildPostBodyWithMeta\(imageLocation = locationLabel, channel = "feed"\)/);
  assert.match(iosComposerHost, /val location: LocationService/);
  assert.match(iosComposerHost, /val permissions: PermissionService/);
  assert.match(iosComposerHost, /requestLocation = \{ resolved ->/);
  assert.match(iosComposerHost, /dependencies\.location\.currentLocation\(\)/);
  assert.match(iosApp, /location: services\.location/);
  assert.match(iosApp, /permissions: services\.permissions/);
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
  assert.match(androidRunner, /selectPostPublishDestinationFixture/);
  assert.match(androidRunner, /quataPostPublishDestinationWallId/);
  assert.match(androidRunner, /expectedWallId/);
  assert.match(androidRunner, /PostPublishRealInstrumentedTest/);
  assert.doesNotMatch(androidRunner, /delete from public\.community_posts/);
});

test("post publish android evidence uses common composer tags through the debug start route", () => {
  assert.match(mainActivity, /AppDestinations\.CreatePost\.route/);
  assert.match(androidPostPublishTest, /CreatePostCommonRootTestTag/);
  assert.match(androidPostPublishTest, /ComposerTextInputTestTag/);
  assert.match(androidPostPublishTest, /composer-destination-option\.\$destinationWallId/);
  assert.match(androidPostPublishTest, /ComposerPublishButtonTestTag/);
  assert.match(androidPostPublishTest, /START_DESTINATION_FOR_EVIDENCE/);
  assert.match(androidPostPublishTest, /"create_post"/);
});

test("post publish ios runner delegates backend fixture ownership to shared helpers", () => {
  assert.match(iosRunner, /createPostPublishFixture/);
  assert.match(iosRunner, /pollPostPublishFixture/);
  assert.match(iosRunner, /cleanupPostPublishFixture/);
  assert.match(iosRunner, /selectPostPublishDestinationFixture/);
  assert.match(iosRunner, /QUATA_IOS_POST_PUBLISH_DESTINATION_WALL_ID/);
  assert.match(iosRunner, /expectedWallId/);
  assert.match(iosRunner, /run-ios-post-publish-ui-test\.sh/);
  assert.match(iosRunner, /mac_checkout_sha_matches_local_candidate/);
  assert.doesNotMatch(iosRunner, /delete from public\.community_posts/);
});

test("post publish ios wrapper seeds Keychain and requires executed XCTest markers", () => {
  assert.match(iosWrapper, /QuataIosAuthenticatedSessionSeederTests\/testSeedAuthenticatedSessionForVisualGates/);
  assert.match(iosWrapper, /QuataIosAuthenticatedPostPublishUITests\/testAuthenticatedSessionPublishesRealTextPost/);
  assert.match(iosWrapper, /QUATA_IOS_POST_PUBLISH_DESTINATION_WALL_ID/);
  assert.match(iosWrapper, /check-ios-xctest-executed\.py/);
  assert.match(iosWrapper, /--require-terminal-success-marker/);
  assert.match(iosWrapper, /IOS_POST_PUBLISH_UI_GATE_PASSED/);
});

test("post publish ios UI test uses common semantic anchors", () => {
  assert.match(iosPostPublishTest, /quata-ios-composer-host/);
  assert.match(iosPostPublishTest, /feed\.action\.publish\./);
  assert.match(iosPostPublishTest, /create-post-common-root/);
  assert.match(iosPostPublishTest, /composer-type-text/);
  assert.match(iosPostPublishTest, /composer-destination-option\.\\\(wallId\)/);
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
