import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const host = await readFile(new URL("../iosApp/iosAppUITests/QuataIosHostUITests.swift", import.meta.url), "utf8");
const composer = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedPostPublishUITests.swift", import.meta.url), "utf8");
const official = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedOfficialEditorUITests.swift", import.meta.url), "utf8");
const runner = await readFile(new URL("./run-ios-layout-overlays-ui-test.sh", import.meta.url), "utf8");
const seeder = await readFile(new URL("../iosApp/iosAppTests/QuataIosAuthenticatedSessionSeederTests.swift", import.meta.url), "utf8");
const authLayout = await readFile(new URL("../feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/AuthResponsiveContent.kt", import.meta.url), "utf8");
const destinationSelector = await readFile(new URL("../feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/ComposerDestinationSelectorContent.kt", import.meta.url), "utf8");

test("Auth keeps the real focused phone draft keyboard-safe through rotation", () => {
  assert.match(host, /testAuthLaunchKeepsPhoneDraftAndKeyboardSafeAcrossRotation/);
  assert.match(host, /fixtureApp\("auth-launch"\)/);
  assert.match(host, /auth\.phone\.input/);
  assert.match(host, /ios-auth-keyboard-(?:portrait|landscape|restored-portrait)/);
  assert.match(host, /keyboard\.frame\.minY/);
  assert.match(host, /matching\(identifier: input\.identifier\)[\s\S]{0,120}hasKeyboardFocus == 1/);
  assert.match(authLayout, /portraitScrollState = rememberScrollState\(\)/);
  assert.match(authLayout, /landscapeScrollState = rememberScrollState\(\)/);
  assert.match(authLayout, /LaunchedEffect\(isLandscape\)/);
  assert.match(authLayout, /verticalScroll\(landscapeScrollState\)\.imePadding\(\)/);
  assert.match(authLayout, /verticalScroll\(portraitScrollState\)\.imePadding\(\)/);
  assert.match(authLayout, /verticalScroll\(landscapeScrollState\)[\s\S]{0,180}verticalArrangement = Arrangement\.Top/);
});

test("authenticated composer and Official editor preserve exact drafts without publishing", () => {
  assert.match(composer, /testAuthenticatedComposerKeepsFocusedDraftAcrossRotation/);
  assert.match(composer, /composer-text-input/);
  assert.match(composer, /withNormalizedOffset: CGVector\(dx: 0\.5, dy: 0\.85\)/);
  assert.match(destinationSelector, /LazyColumn\(/);
  assert.match(destinationSelector, /height\(280\.dp\)/);
  assert.match(destinationSelector, /items\(destinations, key = \{ it\.wallId \}\)/);
  assert.match(composer, /ios-composer-keyboard-landscape/);
  assert.match(composer, /matching\(identifier: input\.identifier\)[\s\S]{0,120}hasKeyboardFocus == 1/);
  assert.doesNotMatch(composer.match(/func testAuthenticatedComposerKeepsFocusedDraftAcrossRotation[\s\S]*?\n    }/)?.[0] ?? "", /tapPublish/);
  assert.match(official, /testAuthenticatedOfficialEditorKeepsFocusedBodyAcrossRotation/);
  assert.match(official, /quata-portable-rich-text-field/);
  assert.match(official, /assertDraftReady\(in: app, marker: "Layout focal"\)/);
  assert.match(official, /matching\(identifier: input\.identifier\)[\s\S]{0,120}hasKeyboardFocus == 1/);
  assert.doesNotMatch(official.match(/func testAuthenticatedOfficialEditorKeepsFocusedBodyAcrossRotation[\s\S]*?\n    }/)?.[0] ?? "", /tapPublish/);
});

test("the focal runner seeds once and selects only the three layout methods", () => {
  assert.match(runner, /QUATA_IOS_LAYOUT_UI_E2E/);
  assert.match(runner, /testSeedAuthenticatedSessionForVisualGates/);
  assert.match(runner, /trap 'finish \$\?' EXIT/);
  assert.match(runner, /testClearAuthenticatedSessionAfterVisualGates/);
  assert.match(seeder, /func testClearAuthenticatedSessionAfterVisualGates\(\)/);
  assert.match(seeder, /XCTAssertNil\(session\.restoredSession\(\)/);
  assert.match(runner, /redact_diagnostics < "\$log"/);
  assert.doesNotMatch(runner, /cat "\$log"/);
  const pythonPattern = runner.match(/secret = re\.compile\(r"([^"]+)"\)/)?.[1];
  assert.ok(pythonPattern, "The runner must keep one inspectable redaction expression.");
  const redactor = new RegExp(pythonPattern.replace(/^\(\?i\)/, ""), "gi");
  for (const value of [
    "Authorization: Bearer SECRET_VALUE",
    "Bearer SECRET_VALUE",
    "password=SECRET_VALUE",
  ]) {
    const redacted = value.replace(redactor, (_match, prefix) => `${prefix}[REDACTED]`);
    assert.doesNotMatch(redacted, /SECRET_VALUE/);
    assert.match(redacted, /\[REDACTED\]/);
  }
  for (const method of [
    "testAuthLaunchKeepsPhoneDraftAndKeyboardSafeAcrossRotation",
    "testAuthenticatedComposerKeepsFocusedDraftAcrossRotation",
    "testAuthenticatedOfficialEditorKeepsFocusedBodyAcrossRotation",
  ]) assert.match(runner, new RegExp(method));
  assert.match(runner, /check-ios-xctest-executed\.py/);
  assert.match(runner, /IOS_LAYOUT_OVERLAYS_UI_GATE_PASSED/);
  assert.doesNotMatch(runner, /POST_PUBLISH_REAL_MUTATION|OFFICIAL_EDITOR_REAL_PUBLISH/);
});
