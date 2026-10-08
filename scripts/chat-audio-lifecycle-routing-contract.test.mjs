import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

test("Android Chat audio owns focus and pauses when the active route becomes noisy", () => {
  const android = source("core/src/androidMain/kotlin/com/quata/core/platform/AndroidPlatformServices.kt");

  assert.match(android, /setAudioAttributes\(AndroidChatAudioAttributes, true\)/);
  assert.match(android, /setHandleAudioBecomingNoisy\(true\)/);
  assert.match(android, /setUsage\(C\.USAGE_MEDIA\)/);
  assert.match(android, /setContentType\(C\.AUDIO_CONTENT_TYPE_SPEECH\)/);
});

test("Web Chat audio pauses on document background and removes its listener on cleanup", () => {
  const web = source("core/src/wasmJsMain/kotlin/com/quata/core/platform/BrowserAudioPlayerService.wasm.kt");

  assert.match(web, /document\.addEventListener\('visibilitychange', pauseForHiddenDocument\)/);
  assert.match(web, /document\.visibilityState !== 'visible'/);
  assert.match(web, /element\.pause\(\)/);
  assert.match(web, /document\.removeEventListener\('visibilitychange', pauseForHiddenDocument\)/);
  assert.match(web, /element\.__quataCleanup = cleanup/);
  assert.match(web, /typeof element\.__quataCleanup === 'function'/);

  const webTest = source("core/src/wasmJsTest/kotlin/com/quata/core/platform/BrowserAudioPlayerLifecycleTest.kt");
  assert.match(webTest, /assertEquals\(0, browserAudioLifecycleVisibilityListenerCount\(\)\)/);
});

test("iOS Chat audio pauses for interruption, route loss and background without auto-resume", () => {
  const ios = source("iosApp/iosApp/IosAvPlayerAudioEngine.swift");
  const iosTest = source("iosApp/iosAppTests/IosAvPlayerAudioEngineLifecycleTests.swift");

  assert.match(ios, /AVAudioSession\.interruptionNotification/);
  assert.match(ios, /InterruptionType\(rawValue: number\.uintValue\) == \.began/);
  assert.match(ios, /AVAudioSession\.routeChangeNotification/);
  assert.match(ios, /RouteChangeReason\(rawValue: number\.uintValue\) == \.oldDeviceUnavailable/);
  assert.match(ios, /UIApplication\.willResignActiveNotification/);
  assert.match(ios, /pauseForLifecycleChange/);
  assert.match(iosTest, /interruption end must not restart Chat audio without user intent/);
  assert.match(iosTest, /testOldRouteUnavailablePausesPlayback/);
  assert.match(iosTest, /testResigningActivePausesPlayback/);
});
