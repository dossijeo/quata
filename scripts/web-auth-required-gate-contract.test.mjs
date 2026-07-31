import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = path => readFile(new URL(`../${path}`, import.meta.url), "utf8");
const main = await source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt");
const feed = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebFeedHost.kt");
const neighborhoods = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsHost.kt");
const login = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebLoginHost.kt");
const bridge = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebAuthE2eBridge.kt");

test("anonymous Web uses Android's common participation dialog instead of redirecting public Feed to Login", () => {
  assert.match(main, /QuataAuthRequiredDialogContent\(/);
  assert.match(main, /fun requestAuthenticationFor\([\s\S]*?isAuthRequiredPromptOpen = true/);
  assert.match(main, /if \(navigation\.state\.requiresAuthentication\) navigation\.navigate\(""\)/);
  assert.match(main, /!isSessionReady && navigationState\.requiresAuthentication -> \{[\s\S]*?requestAuthenticationForCurrentRoute\(\)/);
  assert.match(main, /internal val WebNavigationState\.isPublicRoute[\s\S]*?route == "feed"[\s\S]*?route == "communities"[\s\S]*?route == "official"[\s\S]*?route == "notifications"/);
  assert.doesNotMatch(main, /requestAuthenticationForCurrentRoute\(\) \{[\s\S]*?navigation\.navigate\("auth"\)/);
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
    /route == "communities"/.test(
      main.slice(main.indexOf("internal val WebNavigationState.isPublicRoute")),
    ),
    true,
  );
  assert.match(main, /WebNeighborhoodsHost\([\s\S]*?onAuthRequired = ::requestAuthenticationForCurrentRoute/);
  assert.match(neighborhoods, /onAuthRequired: \(\) -> Unit/);
  assert.match(neighborhoods, /onFollowUser = \{[\s\S]*?currentUserId == null\) onAuthRequired\(\)[\s\S]*?toggleFollowUser/);
  assert.match(neighborhoods, /onOpenPrivateChat = \{[\s\S]*?currentUserId == null\) onAuthRequired\(\)[\s\S]*?openPrivateChat/);
  assert.match(neighborhoods, /onOpenChat = \{[\s\S]*?currentUserId == null\) onAuthRequired\(\)[\s\S]*?openChat/);
  assert.match(neighborhoods, /onSend = \{[\s\S]*?currentUserId == null\)[\s\S]*?onAuthRequired\(\)/);
});

test("Notifications follows Android's public header navigation and gates only private destinations opened from it", () => {
  assert.match(main, /onNotificationsClick = \{ navigation\.navigate\("notifications"\) \}/);
  assert.match(main, /route == "notifications"/);
  assert.match(main, /WebNotificationsHost\([\s\S]*?onOpenConversation = navigation::navigateConversation/);
});
