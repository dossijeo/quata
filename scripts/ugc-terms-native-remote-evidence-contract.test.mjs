import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = path => readFile(new URL(path, import.meta.url), "utf8");
const androidTest = await source("../app/src/androidTest/java/com/quata/core/moderation/UgcTermsRemoteAcceptanceInstrumentedTest.kt");
const androidRunner = await source("./ugc-terms-android-remote-evidence.mjs");
const iosTest = await source("../iosApp/iosAppUITests/QuataIosUgcTermsRemoteUITests.swift");
const iosHost = await source("../iosApp/iosApp/QuataIosApp.swift");
const iosShell = await source("./run-ios-ugc-terms-remote-ui-test.sh");
const iosRunner = await source("./ugc-terms-ios-remote-evidence.mjs");

test("Android native evidence crosses the common UI and production moderation gateway", () => {
  assert.match(androidTest, /QuataUgcTermsGateContent\(/);
  assert.match(androidTest, /gateway = app\.container\.moderationRepository/);
  assert.match(androidTest, /performClick\(\)/);
  assert.match(androidTest, /flushPendingTermsForCurrentUser\(\)/);
  assert.match(androidTest, /hasAcceptedUgcTerms/);
  assert.doesNotMatch(androidTest, /quata_accept_ugc_terms|rest\/v1\/rpc/);
  assert.match(androidRunner, /fixture = await snapshotFixture\(client, session\.userId\);\s*await removeAcceptance\(client, fixture\.profileId\);/);
  assert.match(androidRunner, /restoreFixture/);
  assert.match(androidRunner, /cleanup\.restored/);
  assert.match(androidRunner, /if \(sensitiveCleanupFailed\) report\.status = "failed"/);
  assert.match(androidRunner, /gitMetadata\(\)/);
});

test("iOS native evidence uses a seeded normal launch and the product prompt", () => {
  assert.match(iosTest, /quata-ios-ugc-terms-dialog/);
  assert.match(iosTest, /quata-ugc-terms-accept/);
  assert.match(iosTest, /accept\.tap\(\)|accept\.coordinate/);
  assert.doesNotMatch(iosTest, /quata_accept_ugc_terms|rest\/v1\/rpc/);
  assert.match(iosShell, /testSeedAuthenticatedSessionForVisualGates/);
  assert.match(iosShell, /testAuthenticatedUserAcceptsTermsThroughProductGate/);
  assert.match(iosTest, /-quata-ui-test-reset-ugc-terms-profile/);
  assert.match(iosHost, /resetUgcTermsPreferenceIfRequested\(\)/);
  assert.match(iosHost, /UserDefaults\.standard\.removeObject/);
  assert.match(iosRunner, /ios_mac_checkout_not_exact_clean_head/);
  assert.match(iosRunner, /fixture = await snapshotFixture\(client, session\.userId\);\s*await removeAcceptance\(client, fixture\.profileId\);/);
  assert.match(iosRunner, /restoreFixture/);
  assert.match(iosRunner, /cleanup\.restored/);
  assert.match(iosRunner, /if \(sensitiveCleanupFailed\) report\.status = "failed"/);
  assert.match(iosRunner, /cleanupGeneratedProject/);
});
