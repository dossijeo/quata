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
  assert.match(fixtureKotlin, /registrationEnabled = false/);
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
  for (const required of ["addChild(controller)", "view.insertSubview(controller.view", "controller.didMove(toParent: self)", "willMove(toParent: nil)", "removeFromParent()", "quata-ios-auth-launch-ready"]) {
    assert.match(fixtureSwift, new RegExp(required.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
});

test("auth-launch UI contract proves two cold launches without claiming credential entry", () => {
  assert.match(uiTests, /fixtureApp\("auth-launch"\)/);
  assert.match(uiTests, /for launchNumber in 1\.\.\.2/);
  assert.match(uiTests, /auth-launch-cold-start-/);
  assert.equal(uiTests.includes("typeText"), false);
});
