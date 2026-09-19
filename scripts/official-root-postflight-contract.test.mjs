import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

const [
  host,
  statusContent,
  viewModel,
  commonTest,
  androidTest,
  androidHost,
  webHost,
  iosHost,
  iosUiTest,
  packageJson,
] = await Promise.all([
  source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt"),
  source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialStatusContent.kt"),
  source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedViewModel.kt"),
  source("feature/official/src/commonTest/kotlin/com/quata/feature/official/presentation/OfficialRootStatesTest.kt"),
  source("app/src/androidTest/java/com/quata/feature/official/presentation/OfficialRootStatesInstrumentedTest.kt"),
  source("app/src/main/java/com/quata/feature/official/presentation/OfficialFeedScreen.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialHost.kt"),
  source("feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/QuataOfficialViewController.kt"),
  source("iosApp/iosAppUITests/QuataIosAuthenticatedOfficialEditorUITests.swift"),
  source("package.json").then(JSON.parse),
]);

test("Official root owns stable loading, empty, error and retry semantics in commonMain", () => {
  for (const marker of [
    'OfficialFeedRootTestTag = "official-feed-common-root"',
    'OfficialFeedLoadingTestTag = "official-feed-loading"',
    'OfficialFeedEmptyMessageTestTag = "official-feed-empty-message"',
    'OfficialFeedErrorMessageTestTag = "official-feed-error-message"',
    'OfficialFeedRetryTestTag = "official-feed-retry"',
  ]) {
    assert.ok(host.includes(marker) || statusContent.includes(marker), `missing marker: ${marker}`);
  }
  assert.match(host, /stateHolder: OfficialFeedStateHolder\? = null/);
  assert.match(host, /stateHolder \?: checkNotNull\(ownedViewModel\)/);
  assert.match(viewModel, /class OfficialFeedViewModel\([\s\S]*\) : OfficialFeedStateHolder/);
});

test("Official root state transitions and retry run in common and Android tests", () => {
  for (const testSource of [commonTest, androidTest]) {
    assert.match(testSource, /OfficialFeedUiState\(isLoading = true\)/);
    assert.match(testSource, /OfficialFeedUiState\(isLoading = false\)/);
    assert.match(testSource, /error = "forced-official-error"/);
    assert.match(testSource, /OfficialFeedRetryTestTag/);
    assert.match(testSource, /assertEquals\(1, (?:holder\.)?refreshes\)/);
  }
  assert.match(commonTest, /populatedRootKeepsTheOfficialPostInsideTheSameStableAnchor/);
  assert.match(commonTest, /onNodeWithText\("official-root-visible"\)\.assertIsDisplayed\(\)/);
});

test("Android, Web and iOS keep mounting the shared Official root", () => {
  assert.match(androidHost, /OfficialFeedScreenHost\(/);
  assert.match(webHost, /OfficialFeedScreenHost\(/);
  assert.match(iosHost, /OfficialFeedScreenHost\(/);
  assert.match(iosUiTest, /matching\(identifier: "official-feed-common-root"\)/);
});

test("Official root contract stays in mandatory fast suites", () => {
  for (const suite of ["test:ci-fast-contracts", "test:web-wave2-contracts"]) {
    assert.match(packageJson.scripts[suite], /scripts\/official-root-postflight-contract\.test\.mjs/);
  }
});
