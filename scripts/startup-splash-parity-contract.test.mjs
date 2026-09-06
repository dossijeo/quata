import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const policy = await source("../feature/whatsnew/src/commonMain/kotlin/com/quata/feature/whatsnew/presentation/StartupPresentationPolicy.kt");
const policyTest = await source("../feature/whatsnew/src/commonTest/kotlin/com/quata/feature/whatsnew/presentation/StartupPresentationPolicyTest.kt");
const splash = await source("../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataSplashScreen.kt");
const androidNav = await source("../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt");
const webMain = await source("../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt");
const iosSwift = await source("../iosApp/iosApp/QuataIosApp.swift");
const iosSwiftTests = await source("../iosApp/iosAppTests/QuataFeedFrameworkTests.swift");
const iosHostUiTests = await source("../iosApp/iosAppUITests/QuataIosHostUITests.swift");
const iosSplashHost = await source("../designsystem/src/iosMain/kotlin/com/quata/core/ui/components/IosSplashHost.kt");
const webEvidenceRunner = await source("../scripts/startup-splash-web-evidence.mjs");
const androidEvidenceTest = await source("../app/src/androidTest/java/com/quata/core/startup/StartupSplashCommonInstrumentedTest.kt");
const androidEvidenceRunner = await source("../scripts/startup-splash-android-evidence.mjs");
const iosEvidenceRunner = await source("../scripts/run-ios-startup-splash-ui-test.sh");

test("startup presentation policy is the shared source of truth", () => {
  assert.match(policy, /object StartupPresentationPolicy/);
  assert.match(policy, /shouldEvaluateWhatsNew\(/);
  assert.match(policy, /destinationAfterEvaluation\(/);
  assert.match(policy, /shouldPresentWhatsNew\(/);
  assert.match(policy, /fun startupRouteKind\(/);
  assert.match(policy, /fun shouldPresentStartupWhatsNew\(/);
  assert.match(policy, /isSessionResolved: Boolean/);
  assert.match(policy, /isAuthenticated: Boolean/);
  assert.match(policy, /hasEvaluated: Boolean/);

  assert.match(policyTest, /startupWhatsNewEvaluatesOnlyAfterAuthenticatedSessionIsResolvedOnce/);
  assert.match(policyTest, /lateWhatsNewDecisionCanOnlyReplaceVisibleFeed/);
  assert.match(policyTest, /routeClassifierKeepsFeedAuthAndUnknownSeparate/);
  assert.match(policyTest, /swiftVisibleFeedBridgeUsesTheSamePolicy/);
  assert.match(policyTest, /isAuthenticated = false/);
});

test("platform launchers use the common startup policy instead of local late-route heuristics", () => {
  assert.match(androidNav, /StartupPresentationPolicy\.shouldEvaluateWhatsNew/);
  assert.match(androidNav, /StartupPresentationPolicy\.destinationAfterEvaluation/);
  assert.match(androidNav, /startupRouteKind\(/);
  assert.doesNotMatch(androidNav, /mutableStateOf\(if \(currentUserId == null\) StartupDestination\.Main else StartupDestination\.Loading\)/);

  assert.match(webMain, /StartupPresentationPolicy\.shouldEvaluateWhatsNew/);
  assert.match(webMain, /StartupPresentationPolicy\.shouldPresentWhatsNew/);
  assert.match(webMain, /startupRouteKind\(navigationState\.route, feedRoute = "feed", authRoutes = setOf\("auth"\)\)/);
  assert.doesNotMatch(webMain, /if \(navigationState\.route != "feed"\) return@LaunchedEffect/);
  assert.match(webMain, /var splashAnimationFinished by remember \{ mutableStateOf\(false\) \}/);
  assert.match(webMain, /if \(!splashAnimationFinished \|\| !isSessionResolved\)/);
  assert.match(webMain, /QuataSplashScreen\([\s\S]*?onFinished = \{ splashAnimationFinished = true \}/);

  assert.match(iosSwift, /StartupPresentationPolicyKt\.shouldPresentStartupWhatsNew/);
  assert.match(iosSwift, /hasEvaluatedWhatsNewStartup/);
  assert.doesNotMatch(iosSwift, /installPublicFeedIfConfigured\(\)\s*[\r\n]+\s*evaluateWhatsNewStartupIfAvailable\(\)/);
  assert.match(iosSwift, /isAuthenticated: self\.hasValidatedAuthenticatedSession/);
  assert.match(iosSwift, /startupSplashController: UIViewController\?/);
  assert.match(iosSwift, /IosSplashHostKt\.QuataSplashViewController/);
  assert.match(iosSwift, /dismissStartupSplashIfNeeded\(\)/);
  assert.match(iosSwift, /view\.bringSubviewToFront\(splashView\)/);
  assert.match(iosSplashHost, /fun QuataSplashViewController\(onFinished: \(\) -> Unit\): UIViewController/);
  assert.match(iosSplashHost, /QuataSplashScreen\(/);
  assert.match(iosSwiftTests, /testStartupWhatsNewOpensOnlyWhileThePublicFeedIsStillVisible/);
  assert.match(iosHostUiTests, /testNormalLaunchShowsSharedStartupSplashAndThenMigrationSurface/);
  assert.match(iosHostUiTests, /matching\(identifier: "quata-splash-root"\)/);
  assert.match(iosHostUiTests, /startup-splash-ios/);
});

test("the common splash exposes a stable semantic anchor", () => {
  assert.match(splash, /const val QuataSplashRootTestTag = "quata-splash-root"/);
  assert.match(splash, /\.testTag\(QuataSplashRootTestTag\)/);
  assert.match(splash, /contentDescription = QuataSplashRootTestTag/);
  assert.match(splash, /fun QuataSplashScreen\(/);
});

test("web startup evidence captures the shared splash and feed transition", () => {
  assert.match(webEvidenceRunner, /FLOW-SPLASH-STARTUP-WEB-001/);
  assert.match(webEvidenceRunner, /const SplashAnchor = "quata-splash-root"/);
  assert.match(webEvidenceRunner, /page\.getByLabel\(SplashAnchor\)/);
  assert.match(webEvidenceRunner, /startup_splash_missing_accessible_anchor/);
  assert.match(webEvidenceRunner, /__quataStartupRouteHistory/);
  assert.match(webEvidenceRunner, /startup_unexpected_intermediate_route/);
  assert.match(webEvidenceRunner, /shared_splash_visible_with_accessible_anchor/);
  assert.match(webEvidenceRunner, /startup_transition_reached_public_feed_without_auth_flash/);
  assert.match(webEvidenceRunner, /localStorage\.getItem\("web\.navigation\.route"\)/);
  assert.match(webEvidenceRunner, /document\.documentElement\.getAttribute\("data-quata-shell-route"\)/);
  assert.match(webEvidenceRunner, /gitMetadata\(\)/);
});

test("android startup evidence captures the shared splash through semantics", () => {
  assert.match(androidEvidenceTest, /class StartupSplashCommonInstrumentedTest/);
  assert.match(androidEvidenceTest, /QuataSplashScreen\(/);
  assert.match(androidEvidenceTest, /onNodeWithTag\(QuataSplashRootTestTag/);
  assert.match(androidEvidenceTest, /ActivityScenario\.launch<MainActivity>/);
  assert.match(androidEvidenceTest, /compose\.mainClock\.advanceTimeBy\(4_500\)/);
  assert.match(androidEvidenceTest, /main_activity_shared_splash_visible_with_accessible_anchor/);
  assert.match(androidEvidenceTest, /main_activity_shared_splash_dismissed_after_common_clock_advance/);
  assert.match(androidEvidenceTest, /shared_splash_finished_from_common_callback/);
  assert.match(androidEvidenceTest, /FLOW-SPLASH-STARTUP-ANDROID-001/);
  assert.match(androidEvidenceRunner, /StartupSplashCommonInstrumentedTest/);
  assert.match(androidEvidenceRunner, /runStartupSplashTest\("sharedSplashRendersAndFinishesFromCommonCallback"\)/);
  assert.match(androidEvidenceRunner, /runStartupSplashTest\("mainActivityLaunchMountsSharedSplashAndDismissesIt"\)/);
  assert.match(androidEvidenceRunner, /am", "force-stop", "com\.quata"/);
  assert.match(androidEvidenceRunner, /android_debug_and_test_apks_built/);
  assert.match(androidEvidenceRunner, /android_shared_startup_splash_test_passed/);
  assert.match(androidEvidenceRunner, /const requiredEvidenceFiles = \[/);
  assert.match(androidEvidenceRunner, /android-startup-launcher-evidence\.json/);
  assert.match(androidEvidenceRunner, /android-main-activity-after-startup\.png/);
  assert.match(androidEvidenceRunner, /assertRequiredEvidence\(report\.evidence\.files\)/);
  assert.match(androidEvidenceRunner, /evidenceFileHashes/);
});

test("ios startup evidence runs the normal-launch shared splash gate", () => {
  assert.match(iosEvidenceRunner, /testNormalLaunchShowsSharedStartupSplashAndThenMigrationSurface/);
  assert.match(iosEvidenceRunner, /QUATA_IOS_DERIVED_DATA_PATH/);
  assert.match(iosEvidenceRunner, /QUATA_IOS_SIMULATOR_UDID/);
  assert.match(iosEvidenceRunner, /run-ios-command-watchdog\.py/);
  assert.match(iosEvidenceRunner, /check-ios-xctest-executed\.py/);
  assert.match(iosEvidenceRunner, /QUATA_IOS_STARTUP_SPLASH_UI_LOG_DIR:=build-reports\/ios\/STARTUP-SPLASH-ui/);
  assert.match(iosEvidenceRunner, /QUATA_IOS_STARTUP_SPLASH_UI_RESULT_BUNDLE_DIR:=build-reports\/ios\/STARTUP-SPLASH-ui/);
  assert.match(iosEvidenceRunner, /result_args=\(-resultBundlePath "\$result_bundle"\)/);
  assert.match(iosEvidenceRunner, /IOS_STARTUP_SPLASH_UI_GATE_PASSED/);
  assert.match(iosEvidenceRunner, /tee -a "\$QUATA_IOS_STARTUP_SPLASH_UI_LOG_DIR\/ui\.log"/);
});

async function source(path) {
  return readFile(new URL(path, import.meta.url), "utf8");
}
