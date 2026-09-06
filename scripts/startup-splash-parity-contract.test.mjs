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

test("startup presentation policy is the shared source of truth", () => {
  assert.match(policy, /object StartupPresentationPolicy/);
  assert.match(policy, /shouldEvaluateWhatsNew\(/);
  assert.match(policy, /destinationAfterEvaluation\(/);
  assert.match(policy, /shouldPresentWhatsNew\(/);
  assert.match(policy, /fun startupRouteKind\(/);
  assert.match(policy, /fun shouldPresentStartupWhatsNew\(/);

  assert.match(policyTest, /startupWhatsNewEvaluatesOnlyAfterAuthenticatedSessionIsResolvedOnce/);
  assert.match(policyTest, /lateWhatsNewDecisionCanOnlyReplaceVisibleFeed/);
  assert.match(policyTest, /routeClassifierKeepsFeedAuthAndUnknownSeparate/);
  assert.match(policyTest, /swiftVisibleFeedBridgeUsesTheSamePolicy/);
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

  assert.match(iosSwift, /StartupPresentationPolicyKt\.shouldPresentStartupWhatsNew/);
  assert.match(iosSwiftTests, /testStartupWhatsNewOpensOnlyWhileThePublicFeedIsStillVisible/);
});

test("the common splash exposes a stable semantic anchor", () => {
  assert.match(splash, /const val QuataSplashRootTestTag = "quata-splash-root"/);
  assert.match(splash, /\.testTag\(QuataSplashRootTestTag\)/);
  assert.match(splash, /contentDescription = QuataSplashRootTestTag/);
  assert.match(splash, /fun QuataSplashScreen\(/);
});

async function source(path) {
  return readFile(new URL(path, import.meta.url), "utf8");
}
