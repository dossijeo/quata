import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => await readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("Create Post keeps one common root and stable type/navigation anchors", async () => {
  const [root, typePicker, navigation] = await Promise.all([
    source("feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/CreatePostRoot.kt"),
    source("feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerTypePickerContent.kt"),
    source("designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataBottomNavigation.kt"),
  ]);
  assert.match(root, /create-post-common-root/);
  assert.match(typePicker, /testTag\("composer-type-\$\{type\.name\.lowercase\(\)\}"\)/);
  for (const type of ["PostComposerType.Text", "PostComposerType.Image", "PostComposerType.Video"]) assert.match(typePicker, new RegExp(type.replace(".", "\\.")));
  assert.match(navigation, /"navigation\.primary\.\$\{item\.id\}"/);
});

test("Android, Web and iOS keep the authenticated Create Post route wired to the common product", async () => {
  const [android, web, webHost, ios] = await Promise.all([
    source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerHost.kt"),
    source("iosApp/iosApp/QuataIosApp.swift"),
  ]);
  assert.match(android, /composable\(AppDestinations\.CreatePost\.route\)[\s\S]*?CreatePostScreen\(/);
  assert.match(android, /onBack = \{[\s\S]*?AppDestinations\.Feed\.route/);
  assert.match(web, /navigation\.route == "composer"[\s\S]*?WebPostComposerRoute\(/);
  assert.match(webHost, /CreatePostRoot\(/);
  assert.match(ios, /func showComposer\(\) \{ route\(\.composer\) \}/);
  assert.match(ios, /installAuthenticatedComposerIfAvailable\(\)/);
});

test("Android postflight restores an exact draft after a real process restart without publishing", async () => {
  const [uiTest, coordinator] = await Promise.all([
    source("app/src/androidTest/java/com/quata/feature/postcomposer/presentation/CreatePostPostflightInstrumentedTest.kt"),
    source("scripts/create-post-postflight-android-evidence.mjs"),
  ]);
  assert.match(uiTest, /tapPrefix\("feed\.action\.publish\."\)/);
  assert.match(uiTest, /waitFor\(CreatePostCommonRootTestTag\)/);
  assert.match(uiTest, /waitFor\("navigation\.primary\.create_post"\)/);
  assert.match(uiTest, /tap\("navigation\.primary\.feed"\)/);
  assert.match(uiTest, /"publishCallbacksInvoked", false/);
  assert.doesNotMatch(uiTest, /composer-publish|ComposerPublishButtonTestTag|onPostCreated/);
  assert.match(uiTest, /seedAuthenticatedTextDraftForProcessRestart/);
  assert.match(uiTest, /restoreAuthenticatedTextDraftAfterProcessRestartAndDiscard/);
  assert.match(uiTest, /waitForExactText\(ComposerTextInputTestTag, marker\.orEmpty\(\)\)/);
  assert.match(uiTest, /SemanticsProperties\.EditableText\)\?\.text == expected/);
  assert.match(coordinator, /CreatePostPostflightInstrumentedTest#seedAuthenticatedTextDraftForProcessRestart/);
  assert.match(uiTest, /waitForPersistedTextDraft\(initialSession\?\.userId\.orEmpty\(\), marker\.orEmpty\(\)\)/);
  assert.match(uiTest, /store\.restore\(actorProfileId\)[\s\S]*restored\?\.step == CreatePostStep\.Text[\s\S]*restored\.text == expected/);
  assert.match(coordinator, /am", "force-stop", "com\.quata/);
  assert.match(coordinator, /CreatePostPostflightInstrumentedTest#restoreAuthenticatedTextDraftAfterProcessRestartAndDiscard/);
  assert.match(uiTest, /restore\(initialSession\?\.userId\.orEmpty\(\)\)[\s\S]*android_create_post_draft_step_missing_after_restart[\s\S]*android_create_post_draft_payload_missing_after_restart/);
  assert.match(uiTest, /onNodeWithTag\("composer-back"[\s\S]*performScrollTo\(\)[\s\S]*performClick\(\)[\s\S]*waitForGone\(CreatePostCommonRootTestTag\)[\s\S]*waitForPrefix\("feed\.action\.publish\."\)/);
  assert.match(uiTest, /waitForPersistedDraftCleared\(initialSession\?\.userId\.orEmpty\(\)\)/);
  assert.match(coordinator, /pm", "clear", "com\.quata/);
  assert.match(coordinator, /report\.cleanup\.appDataCleared = true/);
  assert.match(coordinator, /publishCallbacksInvoked !== false/);
});

test("Web postflight restores an exact draft after document reload and observes zero publish requests", async () => {
  const runner = await source("scripts/create-post-postflight-web-evidence.mjs");
  assert.match(runner, /\[id\^='feed\.action\.publish\.'\]/);
  assert.match(runner, /data-quata-shell-route"\) === "composer"/);
  assert.match(runner, /clickSemanticElement\(page, "composer-back"\)/);
  assert.match(runner, /data-quata-shell-route"\) === "feed"/);
  assert.match(runner, /restored_draft_explicitly_discarded/);
  assert.match(runner, /restored_draft_persistent_record_absent_after_reload/);
  assert.match(runner, /web_create_post_discarded_draft_restored_again/);
  assert.match(runner, /publishRequests\.length/);
  assert.match(runner, /storedActor !== session\.userId/);
  assert.match(runner, /page\.reload/);
  assert.match(runner, /expectSemanticInputValue\(page, "composer-text-input", draftMarker\)/);
  assert.doesNotMatch(runner, /I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION|composer-publish/);
});

test("iOS postflight restores an exact draft after app relaunch without publishing", async () => {
  const [uiTest, shell, coordinator] = await Promise.all([
    source("iosApp/iosAppUITests/QuataIosAuthenticatedCreatePostPostflightUITests.swift"),
    source("scripts/run-ios-create-post-postflight-ui-test.sh"),
    source("scripts/create-post-postflight-ios-evidence.mjs"),
  ]);
  assert.match(uiTest, /tapPrefix\("feed\.action\.publish\."/);
  assert.match(uiTest, /tapIdentifier\("navigation\.primary\.feed"/);
  assert.match(uiTest, /create-post-common-root/);
  assert.match(uiTest, /dismissStartupWhatsNewIfPresent/);
  assert.match(uiTest, /quata-ios-profile-sos-host/);
  assert.match(uiTest, /testAuthenticatedTextDraftRestoresAfterRelaunchAndDiscardsWithoutPublishing/);
  assert.match(uiTest, /assertTextInput\(restoredInput, equals: marker/);
  assert.match(uiTest, /tapScrollableIdentifier\([\s\S]*"composer-back"[\s\S]*inside: "create-post-common-root"[\s\S]*quata-ios-feed-host[\s\S]*waitForNonExistence/);
  assert.match(uiTest, /A discarded draft must not reappear after another app relaunch/);
  assert.match(coordinator, /ios_discarded_draft_absent_after_second_app_relaunch/);
  assert.match(uiTest, /while !element\.isHittable && remainingScrolls > 0[\s\S]*container\.swipeUp\(\)[\s\S]*XCTAssertTrue\(element\.isHittable/);
  assert.doesNotMatch(uiTest, /composer-publish|tapPublish/);
  assert.match(uiTest, /testAuthenticatedImageDraftRestoresAfterRelaunchAndDiscardsWithoutPublishing[\s\S]*?QUATA_IOS_POST_PUBLISH_REAL_MUTATION_OPT_IN[\s\S]*?composer-media\.selected-image-preview\.persisted[\s\S]*?app\.terminate\(\)/);
  assert.match(shell, /-only-testing:"\$selected"/);
  assert.match(shell, /testAuthenticatedTextDraftRestoresAfterRelaunchAndDiscardsWithoutPublishing/);
  assert.match(shell, /testClearAuthenticatedSessionAfterVisualGates/);
  assert.match(shell, /trap cleanup_on_exit EXIT/);
  assert.match(shell, /-only-testing:"\$selected"[\s\S]*?local run_status=\$\?[\s\S]*?"\$run_status" != "0" && "\$run_status" != "124"[\s\S]*?return 1/);
  assert.match(shell, /--require-terminal-success-marker[\s\S]*?--accept-selected-pass-before-watchdog-timeout \|\| return 1/);
  assert.match(coordinator, /bash scripts\/run-ios-create-post-postflight-ui-test\.sh/);
  assert.match(coordinator, /publishCallbacksInvoked: false/);
  assert.match(coordinator, /cleanupRemoteSimulatorState\(options\)/);
  assert.match(coordinator, /simulatorAppContainerRemoved = true/);
  assert.match(coordinator, /simctl uninstall/);
  assert.match(coordinator, /simctl bootstatus/);
  assert.match(coordinator, /simctl getenv/);
  assert.match(coordinator, /No such file or directory/);
});

test("Create Post postflight gates are registered in focal and fast entry points", async () => {
  const packageJson = JSON.parse(await source("package.json"));
  assert.match(packageJson.scripts["evidence:create-post-postflight-web"], /create-post-postflight-web-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:create-post-postflight-android"], /create-post-postflight-android-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:create-post-postflight-ios"], /create-post-postflight-ios-evidence\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /create-post-postflight-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /create-post-postflight-contract\.test\.mjs/);
});
