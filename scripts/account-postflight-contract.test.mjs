import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => await readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("Account root exposes stable common navigation and safe lifecycle anchors", async () => {
  const [host, management] = await Promise.all([
    source("feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt"),
    source("feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileAccountManagementContent.kt"),
  ]);

  for (const anchor of [
    "profile.management.open",
    "profile.management.root",
    "profile.management.back",
    "profile.management.deactivate",
    "profile.management.delete",
    "profile.management.confirmation",
    "profile.management.confirm",
    "profile.management.cancel",
    "profile.logout",
  ]) {
    assert.match(host, new RegExp(anchor.replaceAll(".", "\\.")));
  }
  assert.match(management, /testTag\(action\.testTag\)/);
  assert.match(management, /contentDescription = action\.testTag/);
});

test("Account cancellation closes either confirmation without invoking lifecycle callbacks", async () => {
  const host = await source("feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt");
  assert.match(host, /dismissButton = \{[\s\S]*?onClick = \{ confirmation = null \}[\s\S]*?ProfileDangerCancelTestTag/);
  assert.match(host, /confirmButton = \{[\s\S]*?onDeactivateAccount\(\)[\s\S]*?onDeleteAccountData\(\)/);
  assert.doesNotMatch(host, /ProfileDangerCancelTestTag[\s\S]{0,300}(onDeactivateAccount|onDeleteAccountData)\(\)/);
});

test("Android, Web and iOS keep the shared Account host wired to real lifecycle edges", async () => {
  const [android, web, ios, swift] = await Promise.all([
    source("app/src/main/java/com/quata/feature/profile/presentation/ProfileScreen.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebProfileHost.kt"),
    source("feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileHost.kt"),
    source("iosApp/iosApp/QuataIosApp.swift"),
  ]);
  for (const adapter of [android, web, ios]) assert.match(adapter, /ProfileScreenHost\(/);
  assert.match(android, /onDeactivateAccount = onDeactivateAccount/);
  assert.match(android, /onDeleteAccountData = onDeleteAccountData/);
  assert.match(web, /onDeactivateAccount = onDeactivateAccount/);
  assert.match(web, /onDeleteAccountData = onDeleteAccountData/);
  assert.match(ios, /onDeactivateAccount = dependencies\.onDeactivateAccount/);
  assert.match(ios, /onDeleteAccountData = dependencies\.onDeleteAccountData/);
  assert.match(swift, /presentAccountLifecyclePrompt\(action: "deactivate"/);
  assert.match(swift, /presentAccountLifecyclePrompt\(action: "delete"/);
});

test("Android focal postflight cancels both destructive confirmations on the real authenticated host", async () => {
  const [testSource, runner] = await Promise.all([
    source("app/src/androidTest/java/com/quata/feature/profile/presentation/ProfilePostflightInstrumentedTest.kt"),
    source("scripts/account-postflight-android-evidence.mjs"),
  ]);
  assert.match(testSource, /authenticatedAccountRootNavigatesAndCancelsLifecycleActions/);
  assert.match(testSource, /openAndCancel\(ProfileDeactivateOpenTestTag\)/);
  assert.match(testSource, /openAndCancel\(ProfileDeleteOpenTestTag\)/);
  assert.match(testSource, /tapWithoutScroll\(ProfileSosBackTestTag\)[\s\S]*waitForGone\(ProfileSosRootTestTag\)/);
  assert.match(testSource, /assertEquals\("android_account_postflight_actor_changed"/);
  assert.match(testSource, /"destructiveCallbacksInvoked", false/);
  assert.doesNotMatch(testSource, /ProfileDangerConfirmTestTag[\s\S]{0,200}performClick/);
  assert.match(runner, /ProfilePostflightInstrumentedTest#authenticatedAccountRootNavigatesAndCancelsLifecycleActions/);
  assert.match(runner, /"cmd", "package", "compile", "-m", "speed", "-f", "com\.quata"/);
  assert.match(runner, /android_target_apk_precompiled_for_instrumentation/);
  assert.match(runner, /destructiveCallbacksInvoked !== false/);
  assert.match(runner, /sessionPreserved !== true/);
});

test("Web focal postflight drives shared state and rejects backend mutations", async () => {
  const [host, bridge, runner] = await Promise.all([
    source("feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebAccountPostflightE2eBridge.kt"),
    source("scripts/account-postflight-web-evidence.mjs"),
  ]);
  assert.match(host, /accountPostflightE2eBridge\?\.invoke/);
  assert.match(bridge, /quata-account-postflight-e2e/);
  assert.match(bridge, /__quataAccountPostflightE2EProduct/);
  for (const method of ["openManagement", "openDeactivateConfirmation", "openDeleteConfirmation", "cancelConfirmation", "backToOverview"]) {
    assert.match(runner, new RegExp(`invokeAccountPostflightBridge\\(page, "${method}"\\)`));
  }
  assert.match(runner, /mutationRequests\.length/);
  assert.match(runner, /storedActor !== session\.userId/);
  assert.doesNotMatch(runner, /method:\s*"PATCH"|restoreProfile|fetchProfile/);
});

test("iOS focal postflight selects one authenticated non-destructive XCTest", async () => {
  const [uiTest, shell, coordinator] = await Promise.all([
    source("iosApp/iosAppUITests/QuataIosAuthenticatedAccountPostflightUITests.swift"),
    source("scripts/run-ios-account-postflight-ui-test.sh"),
    source("scripts/account-postflight-ios-evidence.mjs"),
  ]);
  assert.match(uiTest, /openAndCancel\("profile\.management\.deactivate"/);
  assert.match(uiTest, /openAndCancel\("profile\.management\.delete"/);
  assert.match(uiTest, /Cancelling lifecycle confirmations must preserve the authenticated session/);
  assert.doesNotMatch(uiTest, /tapIdentifier\("profile\.management\.confirm"/);
  assert.match(shell, /-only-testing:"\$selected"/);
  assert.match(shell, /testAuthenticatedAccountRootNavigatesAndCancelsLifecycleActions/);
  assert.match(coordinator, /bash scripts\/run-ios-account-postflight-ui-test\.sh/);
  assert.match(coordinator, /temporaryCredentialsRemoved/);
  assert.doesNotMatch(coordinator, /restoreProfile|fetchProfile|method:\s*"PATCH"/);
});

test("Account postflight gates are registered in focal and fast entry points", async () => {
  const packageJson = JSON.parse(await source("package.json"));
  assert.match(packageJson.scripts["evidence:account-postflight-web"], /account-postflight-web-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:account-postflight-android"], /account-postflight-android-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:account-postflight-ios"], /account-postflight-ios-evidence\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /account-postflight-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /account-postflight-contract\.test\.mjs/);
});
