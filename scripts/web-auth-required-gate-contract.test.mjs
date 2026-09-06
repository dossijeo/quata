import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = path => readFile(new URL(`../${path}`, import.meta.url), "utf8");
const main = await source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt");
const feed = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebFeedHost.kt");
const neighborhoods = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsHost.kt");
const login = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebLoginHost.kt");
const androidNav = await source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt");
const bridge = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebAuthE2eBridge.kt");
const shellPolicy = await source("core/src/commonMain/kotlin/com/quata/core/navigation/ShellNavigationPolicy.kt");
const authDialog = await source(
  "designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataAuthRequiredDialogContent.kt",
);

test("anonymous Web uses Android's common participation dialog instead of redirecting public Feed to Login", () => {
  assert.match(main, /QuataAuthRequiredDialogContent\(/);
  assert.match(main, /fun requestAuthenticationFor\([\s\S]*?isAuthRequiredPromptOpen = true/);
  assert.match(main, /if \(navigation\.state\.requiresAuthentication\) navigation\.navigate\(""\)/);
  assert.match(main, /!isSessionReady && navigationState\.requiresAuthentication -> \{[\s\S]*?requestAuthenticationForCurrentRoute\(\)/);
  assert.match(main, /internal val WebNavigationState\.isPublicRoute[\s\S]*?quataWebRouteAccess\(/);
  assert.match(shellPolicy, /fun quataWebRouteAccess\([\s\S]*?"feed"[\s\S]*?"communities"[\s\S]*?"official"[\s\S]*?"notifications"/);
  assert.doesNotMatch(main, /requestAuthenticationForCurrentRoute\(\) \{[\s\S]*?navigation\.navigate\("auth"\)/);
});

test("the common auth prompt renders check requirements with a portable vector instead of a font glyph", () => {
  assert.match(authDialog, /if \(!requirement\.startsWith\(AUTH_REQUIREMENT_CHECK_PREFIX\)\) \{[\s\S]*?Text\(requirement\)[\s\S]*?return/);
  assert.match(authDialog, /imageVector = Icons\.Filled\.Check/);
  assert.match(authDialog, /requirement\.removePrefix\(AUTH_REQUIREMENT_CHECK_PREFIX\)\.trimStart\(\)/);
  assert.equal(authDialog.match(/private const val AUTH_REQUIREMENT_CHECK_PREFIX = "✓"/g)?.length, 1);
  assert.doesNotMatch(authDialog, /Text\(requirement\)\s*\n\s*\}/);
});

test("the prompt opens the shared full-screen Auth root only after the user chooses account or login", () => {
  assert.match(main, /fun chooseLoginFromPrompt\(\) = openAuth\(AuthProductDestination\.Login\)/);
  assert.match(main, /fun chooseRegisterFromPrompt\(\) = openAuth\(AuthProductDestination\.Register\)/);
  assert.match(main, /chooseLogin = ::chooseLoginFromPrompt/);
  assert.match(main, /chooseRegister = ::chooseRegisterFromPrompt/);
  assert.match(main, /onCreateAccount = ::chooseRegisterFromPrompt/);
  assert.match(main, /onLogin = ::chooseLoginFromPrompt/);
  assert.match(main, /navigationState\.isAuthenticationRoute -> \{[\s\S]*?WebLoginHost\(/);
  assert.match(main, /fun completeLogin\(\)[\s\S]*?navigation\.navigate\(pendingAuthenticationFragment \?: ""\)/);
  assert.match(login, /initialDestination: AuthProductDestination = AuthProductDestination\.Login/);
  assert.match(login, /initialDestination = initialDestination/);
  assert.match(main, /setWebAuthPromptMarker\(/);
  assert.match(main, /setWebAuthSurfaceMarker\(/);
  assert.match(bridge, /__quataAuthGateE2eProduct/);
  assert.match(bridge, /chooseLogin: \(\) => chooseLogin\(\)/);
  assert.match(bridge, /chooseRegister: \(\) => chooseRegister\(\)/);
  assert.doesNotMatch(bridge, /innerHTML|createElement/);
});

test("Web history Back from the Auth surface cancels stale private-route intent", () => {
  assert.match(main, /var authSurfaceCancellationArmed by remember \{ mutableStateOf\(false\) \}/);
  assert.match(main, /fun openAuth\(destination: AuthProductDestination\) \{[\s\S]*?authSurfaceCancellationArmed = true[\s\S]*?navigation\.navigate\("auth"\)/);
  assert.match(main, /fun completeLogin\(\) \{[\s\S]*?authSurfaceCancellationArmed = false[\s\S]*?navigation\.navigate\(pendingAuthenticationFragment \?: ""\)/);
  assert.match(
    main,
    /LaunchedEffect\(navigationState\.route, isSessionReady\) \{[\s\S]*?authSurfaceCancellationArmed && !navigationState\.isAuthenticationRoute && !isSessionReady[\s\S]*?authSurfaceCancellationArmed = false[\s\S]*?if \(navigationState\.requiresAuthentication\) \{[\s\S]*?requestAuthenticationFor\(navigationState\.pendingAuthenticationFragment\(\)\)[\s\S]*?\} else \{[\s\S]*?pendingAuthenticationFragment = null[\s\S]*?isAuthRequiredPromptOpen = false[\s\S]*?authInitialDestination = AuthProductDestination\.Login/,
  );
  assert.match(main, /internal fun WebNavigationState\.pendingAuthenticationFragment\(\): String = when \{[\s\S]*?route == "settings" -> "settings"[\s\S]*?route == "profile" -> "profile"[\s\S]*?route == "composer" -> "composer"[\s\S]*?route == "official-editor" -> "official-editor"/);
});

test("Android preserves private shell intent across the common Auth prompt", () => {
  assert.match(androidNav, /var pendingAuthenticationRoute by rememberSaveable/);
  assert.match(androidNav, /var pendingAuthenticationConversationId by rememberSaveable/);
  assert.match(androidNav, /var pendingAuthenticationFocusedMessageId by rememberSaveable/);
  assert.match(androidNav, /fun requestAuthentication\([\s\S]*?route: String\? = null[\s\S]*?conversationId: String\? = null[\s\S]*?focusedMessageId: String\? = null[\s\S]*?pendingAuthenticationRoute = route[\s\S]*?pendingAuthenticationConversationId = conversationId[\s\S]*?pendingAuthenticationFocusedMessageId = focusedMessageId/);
  assert.match(androidNav, /fun navigateAfterAuthentication\(\)[\s\S]*?val pendingConversationId = pendingAuthenticationConversationId[\s\S]*?clearPendingAuthenticationDestination\(\)[\s\S]*?AppDestinations\.Chat\.createRoute\(pendingConversationId\)[\s\S]*?navigateAuthenticatedDestination\(pendingRoute\)/);
  assert.match(androidNav, /onLoginSuccess = ::navigateAfterAuthentication/);
  assert.match(androidNav, /onRegisterSuccess = ::navigateAfterAuthentication/);
  assert.doesNotMatch(androidNav, /onLoginSuccess = \{[\s\S]{0,220}?navController\.navigate\(AppDestinations\.Feed\.route\)/);
});

test("public Feed actions use the common gate while authenticated primary routes still navigate normally", () => {
  assert.match(feed, /currentUserId: String\? = null/);
  assert.match(feed, /onAuthRequired: \(\) -> Unit = \{\}/);
  assert.match(feed, /currentUserId = currentUserId/);
  assert.match(feed, /onAuthRequired = onAuthRequired/);
  assert.match(main, /fun selectPrimaryRoute\([\s\S]*?!isSessionReady && fragment\.toWebNavigationState\(\)\.requiresAuthentication[\s\S]*?requestAuthenticationFor\(fragment\)[\s\S]*?navigation\.navigate\(fragment\)/);
});

test("Qüata/Neighborhoods is public while its follow, chat and comment actions remain gated", () => {
  assert.match(main, /fragment\.toWebNavigationState\(\)\.requiresAuthentication/);
  assert.equal(
    /"communities"/.test(
      shellPolicy.slice(shellPolicy.indexOf("fun quataWebRouteAccess")),
    ),
    true,
  );
  assert.match(main, /WebNeighborhoodsHost\([\s\S]*?onAuthRequired = ::requestAuthenticationForCurrentRoute/);
  assert.match(neighborhoods, /onAuthRequired: \(\) -> Unit/);
  assert.match(neighborhoods, /NeighborhoodsScreenHost\(/);
  assert.match(neighborhoods, /CommunityProfileScreenHost\(/);
  assert.match(neighborhoods, /currentUserId = currentUserId/);
  assert.match(neighborhoods, /onAuthRequired = onAuthRequired/);
});

test("Notifications follows Android's public header navigation and gates only private destinations opened from it", () => {
  assert.match(main, /onNotificationsClick = \{ navigation\.navigate\("notifications"\) \}/);
  assert.match(main, /route == "notifications"/);
  assert.match(main, /WebNotificationsHost\([\s\S]*?onOpenConversation = navigation::navigateConversation/);
});
