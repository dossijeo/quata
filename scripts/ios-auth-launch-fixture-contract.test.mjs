import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const fixtureKotlin = readFileSync(
  new URL("../feature/auth/src/iosMain/kotlin/com/quata/feature/auth/presentation/IosAuthLaunchFixtureHost.kt", import.meta.url),
  "utf8",
);
const fixtureSwift = readFileSync(
  new URL("../iosApp/iosApp/IosAuthLaunchFixtureContainerViewController.swift", import.meta.url),
  "utf8",
);
const recoveryForm = readFileSync(
  new URL("../feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/recovery/ForgotPasswordForm.kt", import.meta.url),
  "utf8",
);
const recoveryTags = readFileSync(
  new URL("../feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/recovery/ForgotPasswordTestTags.kt", import.meta.url),
  "utf8",
);
const launcher = readFileSync(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8");
const uiTests = readFileSync(new URL("../iosApp/iosAppUITests/QuataIosHostUITests.swift", import.meta.url), "utf8");
const iosAuthHost = readFileSync(
  new URL("../feature/auth/src/iosMain/kotlin/com/quata/feature/auth/presentation/IosAuthHost.kt", import.meta.url),
  "utf8",
);
const realRecoveryRunner = readFileSync(
  new URL("./run-ios-auth-recovery-real-ui-test.sh", import.meta.url),
  "utf8",
);

test("auth-launch fixture stays isolated from runtime configuration, storage and transport", () => {
  for (const forbidden of [
    "NSURLSession",
    "URLSession",
    "IosKeychainSessionStorage",
    "IosPublicRuntimeConfiguration",
    "IosAuthRuntimeConfiguration",
    "IosFeedRuntimeConfiguration",
    "SUPABASE",
    "http://",
    "https://",
  ]) {
    assert.equal(fixtureKotlin.includes(forbidden) || fixtureSwift.includes(forbidden), false, forbidden);
  }
  assert.match(fixtureKotlin, /private class IosAuthLaunchFixtureRepository : AuthRepository/);
  assert.match(fixtureKotlin, /Result\.failure\(IllegalStateException\("fixture_auth_unavailable"\)\)/);
  assert.match(fixtureKotlin, /repository = IosAuthLaunchFixtureRepository\(\)/);
  assert.match(fixtureKotlin, /onLoginSuccess = \{\}/);
  for (const source of [fixtureKotlin, fixtureSwift]) {
    assert.equal(
      /"(?:[^"\\]|\\.)*(?:token|password|secret|supabase|https?:\/\/)(?:[^"\\]|\\.)*"/i.test(source),
      false,
      "Fixture sources must not embed credential, endpoint, or backend literals.",
    );
  }
});

test("auth-launch uses real Compose Auth inside a complete UIKit containment shell", () => {
  assert.match(launcher, /case "auth-launch"/);
  assert.match(launcher, /IosAuthLaunchFixtureHostKt\.QuataAuthLaunchFixtureViewController\(\)/);
  assert.match(launcher, /guard let fixtureIndex = arguments\.firstIndex\(of: "-quata-ui-test-fixture"\) else \{ return nil \}/);
  assert.match(launcher, /guard arguments\.indices\.contains\(fixtureIndex \+ 1\) else \{/);
  assert.match(launcher, /"quata-ios-test-invalid-fixture"/);
  for (const required of ["addChild(controller)", "view.insertSubview(controller.view", "controller.didMove(toParent: self)", "willMove(toParent: nil)", "removeFromParent()", "quata-ios-auth-launch-ready"]) {
    assert.match(fixtureSwift, new RegExp(required.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
});

test("auth-launch UI contract proves two cold launches with real Compose readiness", () => {
  assert.match(uiTests, /fixtureApp\("auth-launch"\)/);
  assert.match(uiTests, /for launchNumber in 1\.\.\.2/);
  assert.match(uiTests, /auth-launch-cold-start-/);
  assert.match(uiTests, /testNormalLaunchExposesTheUnconfiguredComposeMigrationSemantics/);
  assert.match(uiTests, /matching\(identifier: "auth\.submit"\)/);
  assert.match(uiTests, /composeSubmit\.waitForExistence\(timeout: 10\)/);
  assert.match(uiTests, /testAuthLaunchFixtureCanColdStartSharedRecoverySurface/);
  assert.match(uiTests, /authDestination: "recovery"/);
  assert.match(uiTests, /"auth\.recovery\.root"/);
  assert.match(uiTests, /"auth\.recovery\.submit"/);
  assert.doesNotMatch(
    uiTests,
    /(?:quata-ios-auth-launch-ready|containmentMarker)[\s\S]{0,160}waitForExistence/,
    "The UIKit containment marker cannot be the Auth readiness condition.",
  );
  assert.match(uiTests, /testMalformedAuthLaunchFixtureArgumentsFailClosedWithoutCompose/);
  assert.match(uiTests, /"quata-ios-test-invalid-fixture"/);
  const hermeticRecoveryTest = uiTests.match(/func testAuthLaunchFixtureCanColdStartSharedRecoverySurface\(\)[\s\S]*?\n    func testRealAuthRecoveryFixtureRoundTripsPasswordAndKeepsEvidence/)?.[0] ?? "";
  assert.doesNotMatch(hermeticRecoveryTest, /typeText|QUATA_IOS_AUTH_RECOVERY_E2E_FILE|I_ACCEPT_IOS_PASSWORD_RESET_ROUNDTRIP/);
});

test("auth-launch recovery fixture resolves the same common Recovery surface and tags", () => {
  assert.match(fixtureKotlin, /"recovery" -> AuthProductDestination\.Recovery/);
  assert.match(fixtureKotlin, /QuataAuthLaunchFixtureViewControllerForDestination\(destination: String\)/);
  assert.match(launcher, /QuataAuthLaunchFixtureViewControllerForDestination\(/);
  for (const tag of [
    "auth.recovery.root",
    "auth.recovery.country-prefix",
    "auth.recovery.phone",
    "auth.recovery.question",
    "auth.recovery.secret-answer",
    "auth.recovery.new-password",
    "auth.recovery.error",
    "auth.recovery.submit",
    "auth.recovery.back",
  ]) {
    assert.match(recoveryTags, new RegExp(JSON.stringify(tag).slice(1, -1)));
  }
  for (const required of [
    "ForgotPasswordTestTags.CountryPrefix",
    "ForgotPasswordTestTags.Phone",
    "ForgotPasswordTestTags.Question",
    "ForgotPasswordTestTags.SecretAnswer",
    "ForgotPasswordTestTags.NewPassword",
    "ForgotPasswordTestTags.Error",
    "ForgotPasswordTestTags.Submit",
    "ForgotPasswordTestTags.Back",
  ]) {
    assert.match(recoveryForm, new RegExp(required.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
});

test("real iOS recovery fixture is opt-in, uses the production repository and keeps screenshots", () => {
  assert.match(iosAuthHost, /createIosAuthHostDependenciesForDestination/);
  assert.match(iosAuthHost, /"recovery" -> AuthProductDestination\.Recovery/);
  assert.match(launcher, /case "auth-recovery-real"/);
  assert.match(launcher, /createAuthRepository\(/);
  assert.match(launcher, /createIosAuthHostDependenciesForDestination\(/);
  assert.match(launcher, /destination: "recovery"/);
  assert.match(launcher, /IosAuthHostKt\.QuataAuthViewController\(dependencies: dependencies\)/);
  assert.doesNotMatch(launcher, /case "auth-recovery-real"[\s\S]*IosAuthLaunchFixtureRepository/);

  assert.match(uiTests, /testRealAuthRecoveryFixtureRoundTripsPasswordAndKeepsEvidence/);
  assert.match(uiTests, /I_ACCEPT_IOS_PASSWORD_RESET_ROUNDTRIP/);
  assert.match(uiTests, /QUATA_IOS_AUTH_RECOVERY_E2E_FILE/);
  assert.match(uiTests, /AuthRecoveryUiCredentials\.load/);
  assert.match(uiTests, /fixtureApp\("auth-recovery-real"\)/);
  assert.match(uiTests, /auth-recovery-real-mounted/);
  assert.match(uiTests, /auth-recovery-real-missing-account/);
  assert.match(uiTests, /evidencePrefix: "auth-recovery-real-temporary"/);
  assert.match(uiTests, /evidencePrefix: "auth-recovery-real-restored"/);
  assert.match(uiTests, /\\\(evidencePrefix\)-login-return/);
  assert.match(uiTests, /openRecoveryFromLogin/);
  assert.match(uiTests, /temporaryPassword != restorePassword/);
  assert.doesNotMatch(uiTests, /service_role|SUPABASE_DB_URL|supabase db push|migration repair|deleteUser|admin\/users/);

  assert.match(realRecoveryRunner, /QUATA_IOS_AUTH_RECOVERY_E2E_FILE/);
  assert.match(realRecoveryRunner, /I_ACCEPT_IOS_PASSWORD_RESET_ROUNDTRIP/);
  assert.match(realRecoveryRunner, /find "\$QUATA_IOS_DERIVED_DATA_PATH\/Build\/Products" -name '\*\.xctestrun'/);
  assert.match(realRecoveryRunner, /QuataIosUITests\/QuataIosHostUITests\/testRealAuthRecoveryFixtureRoundTripsPasswordAndKeepsEvidence/);
  assert.match(realRecoveryRunner, /check-ios-xctest-executed\.py/);
  assert.match(realRecoveryRunner, /PASS_EXECUTED:%s/);
  assert.doesNotMatch(realRecoveryRunner, /service_role|SUPABASE_DB_URL|supabase db push|migration repair|deleteUser|admin\/users/);
});
