import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

const [
  feedHost,
  commonTest,
  androidTest,
  androidHost,
  webHost,
  iosHost,
  iosUiTest,
  packageJson,
] = await Promise.all([
  source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt"),
  source("feature/feed/src/commonTest/kotlin/com/quata/feature/feed/presentation/FeedRootStatesTest.kt"),
  source("app/src/androidTest/java/com/quata/feature/feed/presentation/FeedRootStatesInstrumentedTest.kt"),
  source("app/src/main/java/com/quata/feature/feed/presentation/FeedScreen.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebFeedHost.kt"),
  source("feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/QuataFeedViewController.kt"),
  source("iosApp/iosAppUITests/QuataIosFeedPlaybackUITests.swift"),
  source("package.json").then(JSON.parse),
]);

test("Feed root owns stable loading, empty, error and retry semantics in commonMain", () => {
  for (const marker of [
    'FeedRootTestTag = "feed.root"',
    'FeedLoadingTestTag = "feed.loading"',
    'FeedStatusMessageTestTag = "feed.status.message"',
    'FeedStatusRetryTestTag = "feed.status.retry"',
  ]) assert.match(feedHost, new RegExp(marker.replaceAll(".", "\\.")));

  assert.match(feedHost, /Column\(modifier\.fillMaxSize\(\)\.testTag\(FeedRootTestTag\)\)/);
  assert.match(feedHost, /state\.posts\.isEmpty\(\) && state\.isLoading[\s\S]{0,500}FeedLoadingTestTag/);
  assert.match(feedHost, /state\.error != null && state\.posts\.isEmpty\(\)[\s\S]{0,700}FeedStatusRetryTestTag/);
  assert.match(feedHost, /state\.posts\.isEmpty\(\) -> FeedStatusContent[\s\S]{0,500}FeedStatusRetryTestTag/);
});

test("Feed root state transitions and retry are executed by common and Android tests", () => {
  for (const testSource of [commonTest, androidTest]) {
    assert.match(testSource, /FeedUiState\(isLoading = true\)/);
    assert.match(testSource, /FeedUiState\(isLoading = false\)/);
    assert.match(testSource, /error = "forced-feed-error"/);
    assert.match(testSource, /FeedStatusRetryTestTag/);
    assert.match(testSource, /assertEquals\(2, holder\.refreshes\)/);
  }
  assert.match(commonTest, /populatedRootKeepsTheSharedPagerInsideTheSameStableAnchor/);
});

test("Android, Web and iOS keep mounting the shared Feed root", () => {
  assert.match(androidHost, /FeedScreenHost\(/);
  assert.match(webHost, /FeedScreenHost\(/);
  assert.match(iosHost, /FeedScreenHost\(/);
  assert.match(iosUiTest, /matching\(identifier: "feed\.root"\)/);
});

test("Feed root contract stays in mandatory fast suites", () => {
  for (const suite of ["test:ci-fast-contracts", "test:web-wave2-contracts"]) {
    assert.match(packageJson.scripts[suite], /scripts\/feed-root-postflight-contract\.test\.mjs/);
  }
});
