import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

const app = read("iosApp/iosApp/QuataIosApp.swift");
const bridge = read("iosApp/iosApp/IosApnsLifecycleBridge.swift");
const evidence = read("docs/IOS_PUSH_SIMULATOR_VALIDATION.md");
const inventory = read("docs/SCREEN_MIGRATION_INVENTORY_V2.md");

test("denied iOS notification permission delegates to the supported notification-settings URL", () => {
  assert.match(
    bridge,
    /case \.denied:\s*\.openSettings/,
    "denied permission must expose the settings action",
  );
  assert.match(
    app,
    /case \.openSettings:[\s\S]*?URL\(string: UIApplication\.openNotificationSettingsURLString\)[\s\S]*?UIApplication\.shared\.open\(url\)/,
    "the action must use Apple's notification-specific settings URL",
  );
});

test("the Simulator boundary remains fail-closed and is not relabeled as functional acceptance", () => {
  assert.match(evidence, /settingsDestinationAccepted: false/);
  assert.match(evidence, /Una llamada\s+informó `opened: true`/);
  assert.match(evidence, /volvió a observarse la raíz de Ajustes/);
  assert.match(inventory, /frontera externa del sistema\/Simulator/);
  assert.match(inventory, /no acredita la página propia/);
});
