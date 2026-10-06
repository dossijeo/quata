import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const [web, iosCoordinator, iosShell, iosBuild, iosApp, iosUi, packageJson] = await Promise.all([
  readFile(new URL("./chat-outbox-durable-web-evidence.mjs", import.meta.url), "utf8"),
  readFile(new URL("./chat-outbox-durable-ios-evidence.mjs", import.meta.url), "utf8"),
  readFile(new URL("./run-ios-chat-outbox-durable-ui-test.sh", import.meta.url), "utf8"),
  readFile(new URL("./build-ios-intel-simulator-signed.sh", import.meta.url), "utf8"),
  readFile(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8"),
  readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift", import.meta.url), "utf8"),
  readFile(new URL("../package.json", import.meta.url), "utf8"),
]);

test("Web evidence crosses the real composer, durable IndexedDB and recovery replay", () => {
  assert.match(web, /__quataChatComposerE2eProduct/);
  assert.match(web, /context\.setOffline\(true\)[\s\S]*sendThroughProductComposer\(page, state\.marker\)/);
  assert.match(web, /indexedDB\.open\("quata-chat-outbox", 1\)/);
  assert.match(web, /objectStore\("messages"\)\.getAll\(\)/);
  assert.doesNotMatch(web, /objectStore\("messages"\)\.(?:put|add|delete|clear)\(/);
  assert.match(web, /page\.close\(\)[\s\S]*new_product_page_restored_same_durable_outbox_row/);
  assert.match(web, /context\.setOffline\(true\)[\s\S]*context\.setOffline\(false\)[\s\S]*pollExactMarker/);
  assert.match(web, /allRemote\.length !== 1/);
  assert.match(web, /clientMessageIdMatched/);
  assert.match(web, /cleanup_verified_physical_residue_absent/);
});

test("iOS evidence gates network control and proves replay after process recreation", () => {
  assert.match(iosApp, /I_ACCEPT_IOS_CHAT_OUTBOX_DURABLE_NETWORK_FIXTURE/);
  assert.match(iosApp, /case "offline":[\s\S]*setDeviceNetworkAvailable\(isAvailable: false\)/);
  assert.match(iosApp, /case "recover":[\s\S]*setDeviceNetworkAvailable\(isAvailable: false\)[\s\S]*setDeviceNetworkAvailable\(isAvailable: true\)/);
  assert.match(iosUi, /testDurableOutboxSurvivesAppRecreationAndReplaysAfterNetworkRecovery/);
  assert.match(iosUi, /NETWORK_MODE"] = "offline"[\s\S]*chat\.composer\.send[\s\S]*app\.terminate\(\)[\s\S]*NETWORK_MODE"] = "offline"[\s\S]*restored-offline[\s\S]*app\.terminate\(\)[\s\S]*NETWORK_MODE"] = "recover"[\s\S]*app\.launch\(\)/);
  assert.match(iosShell, /testSeedAuthenticatedSessionForVisualGates/);
  assert.match(iosShell, /testDurableOutboxSurvivesAppRecreationAndReplaysAfterNetworkRecovery/);
  assert.match(iosShell, /testClearAuthenticatedSessionAfterVisualGates/);
  assert.match(iosCoordinator, /backend_observed_exact_message_once_after_ios_replay/);
  assert.match(iosCoordinator, /all\.length !== 1/);
  assert.match(iosCoordinator, /cleanup_verified_physical_residue_absent/);
  assert.match(iosCoordinator, /session_\$\{session\.label\.toLowerCase\(\)\}_revoked/);
  assert.match(iosBuild, /:ios-shared:compileKotlinIosX64 :ios-shared:linkDebugFrameworkIosX64 --configure-on-demand/);
});

test("durable-outbox contract remains mandatory in the fast contract suite", () => {
  const occurrences = packageJson.match(/scripts\/chat-outbox-durable-evidence-contract\.test\.mjs/g) ?? [];
  assert.ok(occurrences.length >= 2, "expected a direct script and the mandatory fast-suite entry");
  assert.match(packageJson, /"evidence:chat-outbox-durable-web"/);
  assert.match(packageJson, /"evidence:chat-outbox-durable-ios"/);
});
