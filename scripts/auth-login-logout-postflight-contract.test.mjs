import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const android = await readFile(new URL("../app/src/androidTest/java/com/quata/feature/profile/presentation/ProfilePostflightInstrumentedTest.kt", import.meta.url), "utf8");
const ios = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedAccountPostflightUITests.swift", import.meta.url), "utf8");
const androidRunner = await readFile(new URL("./account-postflight-android-evidence.mjs", import.meta.url), "utf8");
const iosRunner = await readFile(new URL("./account-postflight-ios-evidence.mjs", import.meta.url), "utf8");
const iosShell = await readFile(new URL("./run-ios-account-postflight-ui-test.sh", import.meta.url), "utf8");
const androidNavigation = await readFile(new URL("../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt", import.meta.url), "utf8");
const iosAuthRepository = await readFile(new URL("../feature/auth/src/iosMain/kotlin/com/quata/feature/auth/data/IosAuthRepository.kt", import.meta.url), "utf8");
const iosLogoutOrdering = await readFile(new URL("../feature/auth/src/iosTest/kotlin/com/quata/feature/auth/data/IosAuthLogoutOrderingTest.kt", import.meta.url), "utf8");
const iosSeeder = await readFile(new URL("../iosApp/iosAppTests/QuataIosAuthenticatedSessionSeederTests.swift", import.meta.url), "utf8");
const iosHost = await readFile(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8");

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
  assert.match(androidRunner, /android-logout-remote-verification\.py/);
  assert.match(androidRunner, /exact_auth_session_revoked_remotely/);
  assert.match(androidRunner, /exact_android_push_token_disabled_on_logout/);
  assert.match(androidRunner, /no_active_android_push_token_after_logout/);
  assert.match(android, /owned_push_token_registered_before_logout/);
  assert.match(android, /account-postflight-private/);
  assert.match(iosRunner, /AUTH-LOGOUT-IOS-REAL-001/);
  assert.match(iosRunner, /QUATA_IOS_AUTH_LOGOUT_UI_E2E/);
  assert.match(iosShell, /testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession/);
  assert.match(iosShell, /check-ios-xctest-executed\.py/);
});

test("iOS single-gesture logout binds the seeded Auth session to fail-closed backend verification", () => {
  assert.match(iosSeeder, /QUATA_IOS_AUTH_LOGOUT_SESSION_RECEIPT_FILE/);
  assert.match(iosSeeder, /let storedSession = interactiveSession\.restoredSession\(\)/);
  assert.match(iosSeeder, /let accessToken = session\.accessToken[\s\S]*jwtSessionId\(accessToken\)/);
  assert.match(iosSeeder, /\["session_id": sessionId, "auth_user_id": authUserId\]/);
  assert.match(iosSeeder, /\.posixPermissions: 0o600/);
  assert.doesNotMatch(iosSeeder, /refreshToken|"access_token"|"refresh_token"/);
  assert.match(iosShell, /logout_mode == '1' and logout_receipt/);
  assert.match(iosRunner, /--verify-backend-revocation/);
  assert.match(iosRunner, /exists\(select 1 from auth\.sessions where id=\$1::uuid and user_id=\$2::uuid\)/);
  assert.match(iosRunner, /count\(\*\) filter\(where revoked is not true\)::int as active_refresh_tokens/);
  assert.match(iosRunner, /row\?\.session_exists !== false \|\| row\?\.active_refresh_tokens !== 0/);
  assert.match(iosRunner, /ios_exact_seeded_auth_session_absent_after_single_ui_logout/);
  assert.match(iosRunner, /ios_exact_seeded_refresh_chain_has_zero_active_tokens/);
  assert.match(iosRunner, /rm", "-rf", remoteLogoutReceiptDir/);
  assert.match(iosRunner, /logoutSessionReceiptRemoved/);
});

test("iOS logout postflight activates Profile logout and rejects restored private state", () => {
  assert.match(ios, /func testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession\(\)/);
  assert.match(ios, /launchArguments \+= \[[\s\S]*-quata-ui-test-reset-primary-route/);
  assert.match(iosHost, /arguments\.contains\("-quata-ui-test-reset-primary-route"\)[\s\S]*clearPersistedPrimaryRouteForTesting\(\)[\s\S]*guard let fixtureIndex/);
  assert.match(ios, /tapIdentifier\("profile\.logout"[\s\S]*assertVisible\("feed\.root"/);
  assert.match(ios, /assertPrivateProfileAbsent[\s\S]*relaunch[\s\S]*feed\.root[\s\S]*assertPrivateProfileAbsent/);
  assert.match(ios, /request Account while anonymous[\s\S]*quata-ios-auth-required-dialog/);
  assert.match(ios, /for identifier in \["quata-ios-profile-sos-host", "profile\.logout"\]/);
  assert.doesNotMatch(ios, /testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession[\s\S]*clear\(/);
});

test("iOS settles remote logout before clearing Keychain and preserves offline local retirement", () => {
  assert.match(iosAuthRepository, /internal const val IOS_AUTH_LOGOUT_TIMEOUT_MILLIS = 15_000L/);
  assert.match(iosAuthRepository, /val bearerToken = session\.restoredSession\(\)\?\.bearerToken[\s\S]*try \{[\s\S]*withTimeoutOrNull\(IOS_AUTH_LOGOUT_TIMEOUT_MILLIS\)[\s\S]*configuration\.supabaseLogoutEndpoint\(\)[\s\S]*catch \(cancelled: CancellationException\)[\s\S]*throw cancelled[\s\S]*finally \{[\s\S]*session\.clear\(\)/);
  assert.doesNotMatch(iosAuthRepository, /catch \(_: TimeoutCancellationException\)/);
  assert.doesNotMatch(iosAuthRepository, /logoutScope|logoutScope\.launch/);
  assert.match(iosLogoutOrdering, /remoteLogoutSettlesBeforeKeychainSessionIsCleared/);
  assert.match(iosLogoutOrdering, /assertFalse\(logout\.isCompleted\)[\s\S]*assertNotNull\(session\.restoredSession\(\)\)[\s\S]*releaseRemote\.complete\(Unit\)[\s\S]*assertNull\(session\.restoredSession\(\)\)/);
  assert.match(iosLogoutOrdering, /failedRemoteLogoutStillClearsTheLocalKeychainSessionAfterTheAttempt[\s\S]*IosAuthHttpResponse\(503[\s\S]*assertNull\(session\.restoredSession\(\)\)/);
  assert.match(iosLogoutOrdering, /transportExceptionStillClearsTheLocalKeychainSessionAfterTheAttempt[\s\S]*error\("transport_offline"\)[\s\S]*assertNull\(session\.restoredSession\(\)\)/);
  assert.match(iosLogoutOrdering, /nonresponsiveRemoteIsCancelledAtTheBoundAndLocalKeychainSessionIsCleared[\s\S]*awaitCancellation\(\)[\s\S]*remoteCancelled\.await\(\)[\s\S]*assertEquals\(IOS_AUTH_LOGOUT_TIMEOUT_MILLIS, currentTime - startedAt\)[\s\S]*assertNull\(session\.restoredSession\(\)\)/);
  assert.match(iosLogoutOrdering, /callerTimeoutPropagatesBeforeTheInternalBoundAndLocalKeychainSessionIsCleared[\s\S]*withTimeout\(callerTimeoutMillis\) \{ repository\.logout\(\) \}[\s\S]*failure is TimeoutCancellationException[\s\S]*assertEquals\(callerTimeoutMillis, currentTime - startedAt\)[\s\S]*assertNull\(session\.restoredSession\(\)\)/);
  assert.match(iosLogoutOrdering, /cancellationPropagatesAfterTheLocalKeychainSessionIsCleared[\s\S]*logout\.cancelAndJoin\(\)[\s\S]*assertTrue\(logout\.isCancelled\)[\s\S]*assertNull\(session\.restoredSession\(\)\)/);
});
