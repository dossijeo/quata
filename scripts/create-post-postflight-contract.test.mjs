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

test("Android postflight opens from Feed and returns without publishing", async () => {
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
  assert.match(coordinator, /CreatePostPostflightInstrumentedTest#authenticatedCreatePostRootOpensAndReturnsWithoutPublishing/);
  assert.match(coordinator, /publishCallbacksInvoked !== false/);
});

test("Web postflight uses the real Feed entry and observes zero publish requests", async () => {
  const runner = await source("scripts/create-post-postflight-web-evidence.mjs");
  assert.match(runner, /\[id\^='feed\.action\.publish\.'\]/);
  assert.match(runner, /data-quata-shell-route"\) === "composer"/);
  assert.ok(runner.includes('#navigation\\\\.primary\\\\.feed'));
  assert.match(runner, /publishRequests\.length/);
  assert.match(runner, /storedActor !== session\.userId/);
  assert.doesNotMatch(runner, /I_ACCEPT_REVERSIBLE_POST_PUBLISH_MUTATION|composer-publish/);
});

test("iOS postflight runs one authenticated non-publishing XCTest", async () => {
  const [uiTest, shell, coordinator] = await Promise.all([
    source("iosApp/iosAppUITests/QuataIosAuthenticatedCreatePostPostflightUITests.swift"),
    source("scripts/run-ios-create-post-postflight-ui-test.sh"),
    source("scripts/create-post-postflight-ios-evidence.mjs"),
  ]);
  assert.match(uiTest, /tapPrefix\("feed\.action\.publish\."/);
  assert.match(uiTest, /tapIdentifier\("navigation\.primary\.feed"/);
  assert.match(uiTest, /create-post-common-root/);
  assert.doesNotMatch(uiTest, /composer-publish|tapPublish|POST_PUBLISH_REAL_MUTATION/);
  assert.match(shell, /-only-testing:"\$selected"/);
  assert.match(shell, /testAuthenticatedCreatePostRootOpensAndReturnsWithoutPublishing/);
  assert.match(coordinator, /bash scripts\/run-ios-create-post-postflight-ui-test\.sh/);
  assert.match(coordinator, /publishCallbacksInvoked: false/);
});

test("Create Post postflight gates are registered in focal and fast entry points", async () => {
  const packageJson = JSON.parse(await source("package.json"));
  assert.match(packageJson.scripts["evidence:create-post-postflight-web"], /create-post-postflight-web-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:create-post-postflight-android"], /create-post-postflight-android-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:create-post-postflight-ios"], /create-post-postflight-ios-evidence\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /create-post-postflight-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /create-post-postflight-contract\.test\.mjs/);
});
