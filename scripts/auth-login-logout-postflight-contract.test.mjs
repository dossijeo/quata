import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const android = await readFile(new URL("../app/src/androidTest/java/com/quata/feature/profile/presentation/ProfilePostflightInstrumentedTest.kt", import.meta.url), "utf8");
const ios = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedAccountPostflightUITests.swift", import.meta.url), "utf8");
const androidRunner = await readFile(new URL("./account-postflight-android-evidence.mjs", import.meta.url), "utf8");
const iosRunner = await readFile(new URL("./account-postflight-ios-evidence.mjs", import.meta.url), "utf8");
const iosShell = await readFile(new URL("./run-ios-account-postflight-ui-test.sh", import.meta.url), "utf8");
const androidNavigation = await readFile(new URL("../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt", import.meta.url), "utf8");

test("Android logout postflight uses the real product control and proves durable local retirement", () => {
  assert.match(android, /fun authenticatedLogoutReturnsToPublicFeedAndClearsOwnedSession\(\)/);
  assert.match(android, /tap\(ProfileLogoutTestTag\)[\s\S]*waitFor\(FeedRootTestTag\)/);
  assert.match(android, /currentSession\(\) == null[\s\S]*authState\.value is AuthState\.LoggedOut[\s\S]*mainIntent\("feed"\)[\s\S]*currentSession\(\) == null/);
  assert.doesNotMatch(android, /authenticatedLogoutReturnsToPublicFeedAndClearsOwnedSession[\s\S]*clearSession\(/);
});

test("Android always retires the private route after logout mutates the session", () => {
  assert.equal([...androidNavigation.matchAll(/withContext\(Dispatchers\.IO\) \{[\s\S]{0,120}?authRepository\.logout\(\)/g)].length, 2);
  assert.match(androidNavigation, /composable\(AppDestinations\.Profile\.route\)[\s\S]{0,300}if \(!isAuthenticated\)[\s\S]{0,200}navigateToFeed\(\)/);
  assert.match(androidNavigation, /ugcTermsAccepted = null[\s\S]{0,160}currentRoute != AppDestinations\.Profile\.route[\s\S]{0,80}navigateToFeed\(\)/);
});

test("platform runners select the logout methods and fail closed on missing execution", () => {
  assert.match(androidRunner, /--logout/);
  assert.match(androidRunner, /authenticatedLogoutReturnsToPublicFeedAndClearsOwnedSession/);
  assert.match(androidRunner, /pm", "grant", "com\.quata", "android\.permission\.POST_NOTIFICATIONS/);
  assert.match(androidRunner, /am", "start", "-W", "-n", "com\.quata\/\.MainActivity/);
  assert.match(androidRunner, /am", "force-stop", "com\.quata/);
  assert.match(androidRunner, /compile", "-m", "speed", "-f", "com\.quata/);
  assert.match(androidRunner, /compile", "-m", "speed", "-f", "com\.quata\.test/);
  assert.match(androidRunner, /shell: process\.platform === "win32" && \/\(\?:\^\|\[\\\\\/\]\)\[\^\\\\\/\]\+\\\.bat\$\/i\.test\(command\)/);
  assert.match(androidRunner, /child\.on\("exit", \(code\) => setTimeout\(\(\) => finish\(code\), 250\)\)/);
  assert.match(androidRunner, /android_instrumentation_semantic_failure/);
  assert.match(iosRunner, /AUTH-LOGOUT-IOS-REAL-001/);
  assert.match(iosRunner, /QUATA_IOS_AUTH_LOGOUT_UI_E2E/);
  assert.match(iosShell, /testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession/);
  assert.match(iosShell, /check-ios-xctest-executed\.py/);
});

test("iOS logout postflight activates Profile logout and rejects restored private state", () => {
  assert.match(ios, /func testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession\(\)/);
  assert.match(ios, /tapIdentifier\("profile\.logout"[\s\S]*assertVisible\("feed\.root"/);
  assert.match(ios, /assertPrivateProfileAbsent[\s\S]*relaunch[\s\S]*feed\.root[\s\S]*assertPrivateProfileAbsent/);
  assert.match(ios, /request Account while anonymous[\s\S]*quata-ios-auth-required-dialog/);
  assert.match(ios, /for identifier in \["quata-ios-profile-sos-host", "profile\.logout"\]/);
  assert.doesNotMatch(ios, /testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession[\s\S]*clear\(/);
});
