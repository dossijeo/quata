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
  assert.match(testSource, /waitFor\(ProfileSosOpenTestTag\)/);
  assert.doesNotMatch(testSource, /tap\(ProfileSosOpenTestTag\)/);
  assert.match(testSource, /assertEquals\("android_account_postflight_actor_changed"/);
  assert.match(testSource, /"destructiveCallbacksInvoked", false/);
  assert.doesNotMatch(testSource, /ProfileDangerConfirmTestTag[\s\S]{0,200}performClick/);
  assert.match(runner, /const testMethod = LOGOUT_MODE/);
  assert.match(runner, /"authenticatedAccountRootNavigatesAndCancelsLifecycleActions"/);
  assert.match(runner, /ProfilePostflightInstrumentedTest#\$\{testMethod\}/);
  assert.match(runner, /"cmd", "package", "compile", "-m", "speed", "-f", "com\.quata"/);
  assert.match(runner, /android_target_apk_precompiled_for_instrumentation/);
  assert.match(runner, /destructiveCallbacksInvoked !== false/);
  assert.match(runner, /sessionPreserved !== true/);
  assert.match(testSource, /authenticatedAccountLifecycleActionExecutesFromProductUi/);
  assert.match(testSource, /quataAccountLifecycleEvidence/);
  assert.match(testSource, /QuataAccountLifecycleTestTags\.Password[\s\S]*?performTextInput\(credentials\.password\)/);
  assert.match(testSource, /QuataAccountLifecycleTestTags\.Confirmation[\s\S]*?account_delete_confirmation_word/);
  assert.match(testSource, /tap\(QuataAccountLifecycleTestTags\.Confirm\)/);
  assert.match(testSource, /ActivityScenario\.launch<MainActivity>\(mainIntent\("feed"\)\)/);
  assert.match(testSource, /android_account_lifecycle_session_restored_after_relaunch/);
  assert.match(testSource, /"productControlActivations", 1/);
  assert.match(runner, /--lifecycle-action/);
  assert.match(runner, /quataAccountLifecycleAction/);
  assert.match(runner, /productControlActivations !== 1/);
  assert.match(runner, /platformReport\?\.sessionCleared !== true/);
});

test("Android destructive lifecycle trial keeps shared custody and invokes the real product runner once per actor", async () => {
  const [coordinator, adapter, privateRunner] = await Promise.all([
    source("scripts/account-lifecycle-trial.mjs"),
    source("scripts/e2e-fixtures/account-lifecycle-android.mjs"),
    source("scripts/account-lifecycle-android-private.mjs"),
  ]);
  assert.match(coordinator, /for \(const action of \["deactivate", "delete"\]\)/);
  assert.match(coordinator, /createAccountLifecycleFixture/);
  assert.match(coordinator, /loginAccountLifecycleSession/);
  assert.match(coordinator, /verifyAccountDeactivated/);
  assert.match(coordinator, /verifyAccountDeleted/);
  assert.match(privateRunner, /runAccountLifecycleTrial\(\{ platform: "android"/);
  assert.match(privateRunner, /createAccountLifecycleAndroidTrial/);
  assert.match(adapter, /scripts\/account-postflight-android-evidence\.mjs/);
  assert.match(adapter, /"--lifecycle-action", action/);
  assert.match(adapter, /if \(completedActions > 0\) args\.push\("--skip-build"\)/);
  assert.match(adapter, /platform\.productControlActivations !== 1/);
  assert.match(adapter, /platform\.sessionCleared !== true/);
  assert.match(adapter, /operationsSettled: \(\) => pending === 0 && !uncertain/);
  assert.doesNotMatch(adapter, /console\.(?:log|error)/);
});

test("Web focal postflight drives shared state and proves lifecycle callbacks stay dormant", async () => {
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
  assert.match(runner, /assertProfileRoutePreserved\(page\)/);
  assert.match(runner, /accountLifecycleCallbacksInvoked = false/);
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
  assert.match(uiTest, /reopen Account after relaunch/);
  assert.match(uiTest, /authenticated Account session after relaunch/);
  assert.doesNotMatch(uiTest, /quata-ios-feed-host/);
  const safeMethod = uiTest.slice(
    uiTest.indexOf("func testAuthenticatedAccountRootNavigatesAndCancelsLifecycleActions"),
    uiTest.indexOf("func testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession"),
  );
  assert.doesNotMatch(safeMethod, /tapIdentifier\("profile\.management\.confirm"/);
  assert.match(shell, /-only-testing:"\$selected"/);
  assert.match(shell, /testAuthenticatedAccountRootNavigatesAndCancelsLifecycleActions/);
  assert.match(coordinator, /bash scripts\/run-ios-account-postflight-ui-test\.sh/);
  assert.match(coordinator, /temporaryCredentialsRemoved/);
  assert.doesNotMatch(coordinator, /restoreProfile|fetchProfile|method:\s*"PATCH"/);
});

test("iOS destructive lifecycle mode drives both product confirmations and a natural anonymous relaunch", async () => {
  const [app, uiTest, shell, coordinator] = await Promise.all([
    source("iosApp/iosApp/QuataIosApp.swift"),
    source("iosApp/iosAppUITests/QuataIosAuthenticatedAccountPostflightUITests.swift"),
    source("scripts/run-ios-account-postflight-ui-test.sh"),
    source("scripts/account-postflight-ios-evidence.mjs"),
  ]);
  for (const anchor of ["account.lifecycle.prompt", "account.lifecycle.password", "account.lifecycle.delete-confirmation"]) {
    assert.match(app, new RegExp(anchor.replaceAll(".", "\\.")));
    assert.match(uiTest, new RegExp(anchor.replaceAll(".", "\\.")));
  }
  assert.match(uiTest, /testAuthenticatedAccountLifecycleExecutesFromProductUI/);
  assert.match(uiTest, /tapIdentifier\("profile\.management\.confirm"/);
  assert.match(uiTest, /XCUIApplication\(\)[\s\S]*?relaunch/);
  assert.match(shell, /QUATA_IOS_ACCOUNT_LIFECYCLE_ACTION/);
  assert.match(coordinator, /--lifecycle-action/);
});

test("Account postflight gates are registered in focal and fast entry points", async () => {
  const packageJson = JSON.parse(await source("package.json"));
  assert.match(packageJson.scripts["evidence:account-postflight-web"], /account-postflight-web-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:account-postflight-android"], /account-postflight-android-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:account-postflight-ios"], /account-postflight-ios-evidence\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /account-postflight-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /account-postflight-contract\.test\.mjs/);
});

test("Real account lifecycle fixtures fail closed and expose stable shared form anchors", async () => {
  const [dialog, settings, fixture] = await Promise.all([
    source("designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataAccountLifecycleConfirmationDialogContent.kt"),
    source("feature/settings/src/commonMain/kotlin/com/quata/feature/settings/presentation/SettingsAppearanceControls.kt"),
    source("scripts/e2e-fixtures/account-lifecycle.mjs"),
  ]);
  for (const anchor of ["account-lifecycle.dialog", "account-lifecycle.password", "account-lifecycle.confirmation",
    "account-lifecycle.cancel", "account-lifecycle.confirm", "account-lifecycle.error", "account-lifecycle.progress"]) {
    assert.match(dialog, new RegExp(anchor.replaceAll(".", "\\.")));
  }
  assert.match(settings, /settings-account-lifecycle-deactivate/);
  assert.match(settings, /settings-account-lifecycle-delete/);
  assert.match(fixture, /fixtureCreationStarted = true[\s\S]*?journal\.checkpoint[\s\S]*?adminRequest/);
  assert.match(fixture, /raw_app_meta_data->'quata_e2e'->>'unit'/);
  assert.match(fixture, /deactivated_auth_user_id/);
  assert.match(fixture, /record\.runId\}\/\$\{record\.profileId/);
  assert.match(fixture, /account_deletion_requests/);
  assert.match(fixture, /storage\.objects/);
});

test("Account deactivation revokes server-side browser state instead of only clearing local storage", async () => {
  const edge = await source("supabase/functions/quata-account-lifecycle/index.ts");
  assert.match(edge, /await revokeWebSessions\(admin, profile\.id, authUserId\)/);
  assert.match(edge, /web_push_subscriptions/);
  assert.match(edge, /web_client_sessions/);
  assert.match(edge, /revoked_at: now/);
});
