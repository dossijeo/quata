import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const android = await readFile(new URL("../app/src/androidTest/java/com/quata/feature/profile/presentation/ProfilePostflightInstrumentedTest.kt", import.meta.url), "utf8");
const ios = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedAccountPostflightUITests.swift", import.meta.url), "utf8");
const androidRunner = await readFile(new URL("./account-postflight-android-evidence.mjs", import.meta.url), "utf8");
const iosRunner = await readFile(new URL("./account-postflight-ios-evidence.mjs", import.meta.url), "utf8");
const iosShell = await readFile(new URL("./run-ios-account-postflight-ui-test.sh", import.meta.url), "utf8");

test("Android logout postflight uses the real product control and proves durable local retirement", () => {
  assert.match(android, /fun authenticatedLogoutReturnsToPublicFeedAndClearsOwnedSession\(\)/);
  assert.match(android, /tap\(ProfileLogoutTestTag\)[\s\S]*waitFor\(FeedRootTestTag\)/);
  assert.match(android, /currentSession\(\) == null[\s\S]*mainIntent\("feed"\)[\s\S]*currentSession\(\) == null/);
  assert.doesNotMatch(android, /authenticatedLogoutReturnsToPublicFeedAndClearsOwnedSession[\s\S]*clearSession\(/);
});

test("platform runners select the logout methods and fail closed on missing execution", () => {
  assert.match(androidRunner, /--logout/);
  assert.match(androidRunner, /authenticatedLogoutReturnsToPublicFeedAndClearsOwnedSession/);
  assert.match(androidRunner, /android_instrumentation_semantic_failure/);
  assert.match(iosRunner, /AUTH-LOGOUT-IOS-REAL-001/);
  assert.match(iosRunner, /QUATA_IOS_AUTH_LOGOUT_UI_E2E/);
  assert.match(iosShell, /testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession/);
  assert.match(iosShell, /check-ios-xctest-executed\.py/);
});

test("iOS logout postflight activates Profile logout and rejects restored private state", () => {
  assert.match(ios, /func testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession\(\)/);
  assert.match(ios, /tapIdentifier\("profile\.logout"[\s\S]*assertVisible\("feed\.root"/);
  assert.match(ios, /navigation\.primary\.profile[\s\S]*waitForNonExistence[\s\S]*relaunch[\s\S]*feed\.root[\s\S]*waitForNonExistence/);
  assert.doesNotMatch(ios, /testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession[\s\S]*clear\(/);
});
