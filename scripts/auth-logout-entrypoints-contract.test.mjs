import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("each production UGC terms gate delegates logout to the platform session owner", async () => {
  const [android, web, ios] = await Promise.all([
    source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
    source("iosApp/iosApp/QuataIosApp.swift"),
  ]);

  assert.match(android, /QuataUgcTermsGateContent\([\s\S]*?onLogout = \{[\s\S]*?authRepository\.logout\(\)[\s\S]*?navigateToFeed\(\)/);
  assert.match(web, /QuataUgcTermsGateContent\([\s\S]*?onLogout = \{ completeLogout\(\) \}/);
  assert.match(ios, /installUgcTermsPromptFactory \{[\s\S]*?onLogout: \{ \[weak self\] in[\s\S]*?authenticatedHost\.performLogout\(\)/);
});

test("Android focal gate activates the common UGC control and the production auth repository", async () => {
  const [uiTest, runner] = await Promise.all([
    source("app/src/androidTest/java/com/quata/core/moderation/UgcTermsRemoteAcceptanceInstrumentedTest.kt"),
    source("scripts/ugc-terms-android-remote-evidence.mjs"),
  ]);
  assert.match(uiTest, /authenticatedUserLogsOutThroughCommonProductGate/);
  assert.match(uiTest, /onNodeWithTag\(QuataUgcTermsLogoutTestTag[\s\S]*?performClick\(\)/);
  assert.match(uiTest, /authRepository\.logout\(\)/);
  assert.match(uiTest, /sessionManager\.currentSession\(\) == null/);
  assert.match(runner, /UgcTermsRemoteAcceptanceInstrumentedTest#authenticatedUserLogsOutThroughCommonProductGate/);
  assert.match(runner, /productControlActivations !== 1/);
  assert.match(runner, /ugc_terms_android_logout_assertion_failed/);
});

test("Web focal gate clicks the visible logout affordance and proves storage stays clear after reload", async () => {
  const runner = await source("scripts/ugc-terms-web-evidence.mjs");
  assert.match(runner, /getByRole\("button", \{ name: \/Cerrar/);
  assert.match(runner, /await button\.click\(\{ timeout: 5_000, force: true \}\)/);
  for (const key of ["quata_web_access_token", "quata_web_refresh_token", "quata_web_session_token", "quata_web_user_id"]) {
    assert.match(runner, new RegExp(key));
  }
  assert.match(runner, /await page\.reload/);
  assert.match(runner, /quata\.auth\.e2e\.seeded/);
  assert.match(runner, /ugc_terms_logout_unexpected_acceptance/);
});

test("iOS focal gates use both real UGC and Settings logout controls", async () => {
  const [ugcTest, accountTest, ugcShell, accountShell, accountRunner] = await Promise.all([
    source("iosApp/iosAppUITests/QuataIosUgcTermsRemoteUITests.swift"),
    source("iosApp/iosAppUITests/QuataIosAuthenticatedAccountPostflightUITests.swift"),
    source("scripts/run-ios-ugc-terms-remote-ui-test.sh"),
    source("scripts/run-ios-account-postflight-ui-test.sh"),
    source("scripts/account-postflight-ios-evidence.mjs"),
  ]);
  assert.match(ugcTest, /testAuthenticatedUserLogsOutThroughProductGate/);
  assert.match(ugcTest, /matching\(identifier: "quata-ugc-terms-logout"\)/);
  assert.match(ugcTest, /matching\(identifier: "feed\.root"\)/);
  assert.match(ugcShell, /testAuthenticatedUserLogsOutThroughProductGate/);
  assert.match(accountTest, /testAuthenticatedSettingsLogoutReturnsToPublicFeedAndClearsRestoredSession/);
  assert.match(accountTest, /tapIdentifier\("settings-logout"/);
  assert.match(accountShell, /QUATA_IOS_SETTINGS_LOGOUT_UI_E2E/);
  assert.match(accountShell, /testAuthenticatedSettingsLogoutReturnsToPublicFeedAndClearsRestoredSession/);
  assert.match(accountRunner, /--settings-logout/);
});

test("focal commands remain separate from unrelated matrices", async () => {
  const packageJson = JSON.parse(await source("package.json"));
  assert.match(packageJson.scripts["evidence:auth-logout-entrypoints-web"], /--mode logout/);
  assert.match(packageJson.scripts["evidence:auth-logout-entrypoints-android"], /--logout/);
  assert.match(packageJson.scripts["evidence:auth-logout-entrypoints-ios-ugc"], /--logout/);
  assert.match(packageJson.scripts["evidence:auth-logout-entrypoints-ios-settings"], /--settings-logout/);
});
