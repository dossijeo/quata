import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = path => readFile(new URL(`../${path}`, import.meta.url), "utf8");
const host = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebLoginHost.kt");
const main = await source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt");
const repository = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebAuthRepository.kt");
const capabilityRoute = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebFeatureCapabilities.kt");

test("production Web mounts the common Auth product root without browser visual overrides", () => {
  assert.match(host, /AuthProductHostContent\(/);
  assert.doesNotMatch(host, /AuthBrowserLoginHostContent|WebNativeInput|WebNativeButton|alert\s*\(/);
  assert.match(host, /preferences\.putString\(WebSessionReadyKey, "true"\)/);
});

test("successful Web login activates the existing shell/router and preserves its session contracts", () => {
  assert.match(main, /fun completeLogin\(\)[\s\S]*?isSessionReady = true[\s\S]*?currentUserId = authRepository\.activeProfileSessionOrNull\(\)\?\.userId[\s\S]*?navigation\.navigate\(pendingAuthenticationFragment \?: ""\)/);
  assert.match(main, /WebLoginHost\([\s\S]*?repository = authRepository,[\s\S]*?preferences = platformServices\.preferences,[\s\S]*?onLoginSuccess = ::completeLogin/);
  assert.match(main, /QuataAuthenticatedShellChrome\(/);
  assert.match(main, /return WebNavigationState\(route = "feed"/);
  assert.match(main, /quataChatDeepLinkOrNull\(\)|quataOfficialPostIdOrNull\(\)|quataPostIdOrNull\(\)/);
  assert.match(main, /sessionCoordinator\.reconcileCurrentSession\(\)/);
  assert.match(main, /sessionCoordinator\.logoutCurrentSession\(\)/);
  assert.match(repository, /put\("action", "web_login"\)/);
  assert.match(repository, /put\("client_instance_id", ensureWebClientInstanceId\(\)\)/);
  assert.match(repository, /WebSessionToken/);
});

test("product routes do not prepend capability diagnostics to the Android-comparable viewport", () => {
  assert.match(capabilityRoute, /showCapabilityNotice: Boolean = false/);
  assert.match(capabilityRoute, /if \(showCapabilityNotice\) \{[\s\S]*?WebFeatureCapabilityNotice\(/);
  assert.doesNotMatch(main, /showCapabilityNotice\s*=\s*true|WebFeatureCapabilityNotice/);
});
