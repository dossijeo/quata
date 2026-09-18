import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";

const uiTest = await readFile(
  new URL("../iosApp/iosAppUITests/QuataIosHostUITests.swift", import.meta.url),
  "utf8",
);
const frameworkTest = await readFile(
  new URL("../iosApp/iosAppTests/QuataFeedFrameworkTests.swift", import.meta.url),
  "utf8",
);
const appHost = await readFile(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8");
const runner = await readFile(new URL("./run-ios-shell-layout-ui-test.sh", import.meta.url), "utf8");
const redactor = fileURLToPath(new URL("./redact-ios-diagnostics.py", import.meta.url));

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
  assert.match(uiTest, /func testAuthenticatedFeedShellOfflineBannerReservesAndRestoresViewport\(\)/);
  assert.match(uiTest, /fixtureApp\("shell-layout", shellOffline: true\)/);
  assert.match(uiTest, /staticTexts\["Sin conexión"\]/);
  assert.match(uiTest, /offlineTopFrame\.height - 28/);
  assert.match(uiTest, /offlineContentFrame\.minY - 28/);
  assert.match(uiTest, /ios-shell-layout-offline/);
  assert.match(uiTest, /ios-shell-layout-restored-online/);
});

test("the real iOS shell contains every route layout variant", () => {
  assert.match(uiTest, /func testAuthenticatedShellContainsEveryRouteLayoutVariant\(\)/);
  for (const route of [
    "feed",
    "chat",
    "official",
    "official-editor",
    "notifications",
    "profile-sos",
    "communities",
    "composer",
    "settings",
    "whats-new",
    "about",
    "release-history",
  ]) {
    assert.match(uiTest, new RegExp(`\\("${route}",`));
  }
  assert.match(uiTest, /fixtureApp\("shell-layout", shellRoute: scenario\.route\)/);
  assert.match(uiTest, /content\.value as\? String, scenario\.route/);
  assert.match(uiTest, /Chat must preserve its product rule that hides primary navigation/);
  assert.match(uiTest, /assertAuthenticatedViewportWithoutPrimaryNavigation/);
  assert.match(uiTest, /ios-shell-layout-route-/);
  assert.match(appHost, /-quata-ui-test-shell-route/);
  assert.match(appHost, /router\.installChatFactory/);
  assert.match(appHost, /router\.installOfficialFactory/);
  assert.match(appHost, /router\.installOfficialEditorFactory/);
  assert.match(appHost, /router\.installNotificationsFactory/);
  assert.match(appHost, /router\.installProfileSosFactory/);
  assert.match(appHost, /router\.installCommunitiesFactory/);
  assert.match(appHost, /router\.installComposerFactory/);
  assert.match(appHost, /router\.installSettingsFactory/);
  assert.match(appHost, /router\.installWhatsNewFactory/);
  assert.match(appHost, /router\.installAboutFactory/);
  assert.match(appHost, /router\.installReleaseHistoryFactory/);
});

test("the production iOS router relayouts across representative container sizes", () => {
  assert.match(
    frameworkTest,
    /func testSharedShellRelayoutsRealRouterAcrossRepresentativeContainerSizes\(\)/,
  );
  assert.match(frameworkTest, /IosFeedHostContainerViewController\(platformServices:/);
  assert.match(frameworkTest, /router\.installPublicFeed/);
  for (const size of [
    "CGSize(width: 320, height: 1_024)",
    "CGSize(width: 507, height: 1_024)",
    "CGSize(width: 600, height: 900)",
    "CGSize(width: 744, height: 1_133)",
    "CGSize(width: 834, height: 1_210)",
    "CGSize(width: 1_024, height: 768)",
  ]) {
    assert.ok(frameworkTest.includes(size), `missing representative container ${size}`);
  }
  assert.match(frameworkTest, /window\.frame = bounds/);
  assert.match(frameworkTest, /router\.view\.frame = bounds/);
  assert.match(frameworkTest, /XCTAssertEqual\(topChrome\.frame, expected\.topChrome/);
  assert.match(frameworkTest, /XCTAssertEqual\(publicFeed\.view\.frame, expected\.content/);
  assert.match(frameworkTest, /XCTAssertEqual\(primaryNavigation\.frame, expected\.bottomNavigation/);
  assert.match(frameworkTest, /Split View or[\s\S]*Stage Manager/);
  assert.match(frameworkTest, /native multitasking orchestration remains a separate edge/);
});

test("layout frame markers are confined to the deterministic UI-test fixture", () => {
  assert.match(appHost, /CommandLine\.arguments\.contains\("-quata-ui-test-fixture"\)/);
  assert.match(appHost, /case "shell-layout":/);
  assert.match(
    appHost,
    /router\.installFeedFactory \{ \[weak router\] _ in[\s\S]*makeShellLayoutFixtureViewController\(route: "feed"\) \{[\s\S]*router\?\.updateNetworkAvailable\(true\)/,
  );
  assert.match(appHost, /authenticatedTopChromeLayoutMarker/);
  assert.match(appHost, /primaryNavigationLayoutMarker/);
  assert.match(appHost, /quata-ios-authenticated-top-chrome-layout-frame/);
  assert.match(appHost, /quata-ios-authenticated-primary-navigation-layout-frame/);
  assert.match(appHost, /-quata-ui-test-shell-offline/);
  assert.match(appHost, /quata-ios-shell-layout-reconnect/);
  assert.match(appHost, /router\?\.updateNetworkAvailable\(true\)/);
  assert.match(appHost, /router\.updateNetworkAvailable\(false\)/);
});

test("the focal runner is bounded and proves that the selected XCTest executed", () => {
  assert.match(runner, /^set -euo pipefail$/m);
  assert.match(runner, /QUATA_IOS_DERIVED_DATA_PATH/);
  assert.match(runner, /QUATA_IOS_SIMULATOR_UDID/);
  assert.match(runner, /QUATA_IOS_SHELL_LAYOUT_TEST_METHOD/);
  assert.match(runner, /run-ios-command-watchdog\.py/);
  assert.match(runner, /capture_bounded_diagnostic 15[^]*xcrun simctl list devices/);
  assert.match(runner, /capture_bounded_diagnostic 15[^]*xcrun simctl spawn/);
  assert.match(runner, /redact_diagnostics < "\$diagnostic_dir\/simulator-system\.log"[^]*mv "\$diagnostic_dir\/simulator-system\.redacted\.log"/);
  assert.match(runner, /test-without-building/);
  assert.match(runner, /-only-testing:"\$selected"/);
  assert.match(runner, /--method "\$QUATA_IOS_SHELL_LAYOUT_TEST_METHOD"/);
  assert.match(runner, /check-ios-xctest-executed\.py/);
  assert.match(runner, /PASS_EXECUTED:%s/);
  assert.match(runner, /IOS_SHELL_LAYOUT_UI_GATE_PASSED/);
});

test("timeout diagnostics redact common credential forms", () => {
  const python = process.platform === "win32" ? "python" : "/usr/bin/python3";
  const result = spawnSync(python, [redactor], {
    encoding: "utf8",
    input: [
      "Bearer abc.def",
      "Authorization: secret-value",
      "Authorization: Bearer synthetic-test-token",
      "token = token-value",
      "password=pw-value",
      "apikey: key-value",
      "ordinary diagnostic",
    ].join("\n"),
  });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(
    result.stdout.replace(/\r\n/g, "\n"),
    [
      "Bearer [REDACTED]",
      "Authorization: [REDACTED]",
      "Authorization: Bearer [REDACTED]",
      "token = [REDACTED]",
      "password=[REDACTED]",
      "apikey: [REDACTED]",
      "ordinary diagnostic",
    ].join("\n"),
  );
});
