import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = path => readFile(new URL(`../${path}`, import.meta.url), "utf8");
const main = await source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt");
const feed = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebFeedHost.kt");
const login = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebLoginHost.kt");

test("anonymous Web uses Android's common participation dialog instead of redirecting public Feed to Login", () => {
  assert.match(main, /QuataAuthRequiredDialogContent\(/);
  assert.match(main, /fun requestAuthenticationFor\([\s\S]*?isAuthRequiredPromptOpen = true/);
  assert.match(main, /if \(navigation\.state\.requiresAuthentication\) navigation\.navigate\(""\)/);
  assert.match(main, /!isSessionReady && navigationState\.requiresAuthentication -> \{[\s\S]*?requestAuthenticationForCurrentRoute\(\)/);
  assert.match(main, /internal val WebNavigationState\.isPublicRoute[\s\S]*?route == "feed"[\s\S]*?route == "official"/);
  assert.doesNotMatch(main, /requestAuthenticationForCurrentRoute\(\) \{[\s\S]*?navigation\.navigate\("auth"\)/);
});

test("the prompt opens the shared full-screen Auth root only after the user chooses account or login", () => {
  assert.match(main, /onCreateAccount = \{ openAuth\(AuthProductDestination\.Register\) \}/);
  assert.match(main, /onLogin = \{ openAuth\(AuthProductDestination\.Login\) \}/);
  assert.match(main, /navigationState\.isAuthenticationRoute -> \{[\s\S]*?WebLoginHost\(/);
  assert.match(main, /fun completeLogin\(\)[\s\S]*?navigation\.navigate\(pendingAuthenticationFragment \?: ""\)/);
  assert.match(login, /initialDestination: AuthProductDestination = AuthProductDestination\.Login/);
  assert.match(login, /initialDestination = initialDestination/);
});

test("public Feed actions use the common gate while authenticated primary routes still navigate normally", () => {
  assert.match(feed, /currentUserId: String\? = null/);
  assert.match(feed, /onAuthRequired: \(\) -> Unit = \{\}/);
  assert.match(feed, /currentUserId = currentUserId/);
  assert.match(feed, /onAuthRequired = onAuthRequired/);
  assert.match(main, /fun selectPrimaryRoute\([\s\S]*?!isSessionReady && fragment\.toWebNavigationState\(\)\.requiresAuthentication[\s\S]*?requestAuthenticationFor\(fragment\)[\s\S]*?navigation\.navigate\(fragment\)/);
});
