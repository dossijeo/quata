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
  const testSource = await source("app/src/androidTest/java/com/quata/feature/profile/presentation/ProfilePostflightInstrumentedTest.kt");
  assert.match(testSource, /authenticatedAccountRootNavigatesAndCancelsLifecycleActions/);
  assert.match(testSource, /openAndCancel\(ProfileDeactivateOpenTestTag\)/);
  assert.match(testSource, /openAndCancel\(ProfileDeleteOpenTestTag\)/);
  assert.match(testSource, /assertEquals\("android_account_postflight_actor_changed"/);
  assert.match(testSource, /"destructiveCallbacksInvoked", false/);
  assert.doesNotMatch(testSource, /ProfileDangerConfirmTestTag[\s\S]{0,200}performClick/);
});
