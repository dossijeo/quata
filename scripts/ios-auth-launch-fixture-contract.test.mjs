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
  assert.doesNotMatch(
    uiTests,
    /(?:quata-ios-auth-launch-ready|containmentMarker)[\s\S]{0,160}waitForExistence/,
    "The UIKit containment marker cannot be the Auth readiness condition.",
  );
  assert.match(uiTests, /testMalformedAuthLaunchFixtureArgumentsFailClosedWithoutCompose/);
  assert.match(uiTests, /"quata-ios-test-invalid-fixture"/);
  assert.equal(uiTests.includes("typeText"), false);
});

test("auth-launch recovery fixture resolves the same common Recovery surface and tags", () => {
  assert.match(fixtureKotlin, /"recovery" -> AuthProductDestination\.Recovery/);
  assert.match(fixtureKotlin, /QuataAuthLaunchFixtureViewControllerForDestination\(destination: String\)/);
  assert.match(launcher, /QuataAuthLaunchFixtureViewControllerForDestination\(/);
  for (const tag of [
    "auth.recovery.root",
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
