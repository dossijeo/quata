import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const uiTest = await readFile(
  new URL("../iosApp/iosAppUITests/QuataIosHostUITests.swift", import.meta.url),
  "utf8",
);
const appHost = await readFile(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8");
const runner = await readFile(new URL("./run-ios-shell-layout-ui-test.sh", import.meta.url), "utf8");

test("the focal iOS shell test observes the real authenticated host across rotation", () => {
  assert.match(uiTest, /func testAuthenticatedFeedShellKeepsSafeViewportAcrossRotation\(\)/);
  assert.match(uiTest, /fixtureApp\("shell-layout"\)/);
  assert.match(uiTest, /quata-ios-shell-layout-content-frame/);
  assert.match(uiTest, /quata-ios-authenticated-top-chrome-layout-frame/);
  assert.match(uiTest, /quata-ios-authenticated-primary-navigation-layout-frame/);
  assert.match(uiTest, /device\.orientation = \.landscapeLeft/);
  assert.match(uiTest, /device\.orientation = \.portrait/);
  assert.match(uiTest, /topFrame\.maxY[\s\S]*contentFrame\.minY/);
  assert.match(uiTest, /contentFrame\.maxY[\s\S]*navigationFrame\.minY/);
  assert.match(uiTest, /ios-shell-layout-portrait/);
  assert.match(uiTest, /ios-shell-layout-landscape/);
  assert.match(uiTest, /ios-shell-layout-restored-portrait/);
});

test("layout frame markers are confined to the deterministic UI-test fixture", () => {
  assert.match(appHost, /CommandLine\.arguments\.contains\("-quata-ui-test-fixture"\)/);
  assert.match(appHost, /case "shell-layout":/);
  assert.match(appHost, /router\.installFeedFactory \{ _ in makeShellLayoutFeedFixtureViewController\(\) \}/);
  assert.match(appHost, /authenticatedTopChromeLayoutMarker/);
  assert.match(appHost, /primaryNavigationLayoutMarker/);
  assert.match(appHost, /quata-ios-authenticated-top-chrome-layout-frame/);
  assert.match(appHost, /quata-ios-authenticated-primary-navigation-layout-frame/);
});

test("the focal runner is bounded and proves that the selected XCTest executed", () => {
  assert.match(runner, /^set -euo pipefail$/m);
  assert.match(runner, /QUATA_IOS_DERIVED_DATA_PATH/);
  assert.match(runner, /QUATA_IOS_SIMULATOR_UDID/);
  assert.match(runner, /run-ios-command-watchdog\.py/);
  assert.match(runner, /test-without-building/);
  assert.match(runner, /-only-testing:"\$selected"/);
  assert.match(runner, /check-ios-xctest-executed\.py/);
  assert.match(runner, /PASS_EXECUTED:%s/);
  assert.match(runner, /IOS_SHELL_LAYOUT_UI_GATE_PASSED/);
});
