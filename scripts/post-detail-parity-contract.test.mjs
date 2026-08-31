import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");

async function source(path) {
  return readFile(resolve(root, path), "utf8");
}

const chrome = await source("designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataPostDetailChromeContent.kt");
const feedHost = await source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt");
const officialHost = await source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt");
const androidNav = await source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt");
const webMain = await source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt");
const iosApp = await source("iosApp/iosApp/QuataIosApp.swift");
const iosFeed = await source("feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/QuataFeedViewController.kt");
const iosFeedRuntime = await source("feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/IosFeedRuntimeBootstrap.kt");
const iosOfficial = await source("feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/QuataOfficialViewController.kt");

test("post detail chrome is a common component with semantic anchors", () => {
  assert.match(chrome, /fun QuataPostDetailChromeContent\(/);
  assert.match(chrome, /rootTestTag: String/);
  assert.match(chrome, /backTestTag: String/);
  assert.match(chrome, /\.testTag\(rootTestTag\)/);
  assert.match(chrome, /contentDescription = rootTestTag/);
  assert.match(chrome, /\.testTag\(backTestTag\)/);
  assert.match(chrome, /Icons\.AutoMirrored\.Filled\.ArrowBack/);
});

test("Feed focused-post mode exposes shared chrome and a real back callback", () => {
  assert.match(feedHost, /const val FeedPostDetailChromeTestTag = "feed\.detail\.chrome"/);
  assert.match(feedHost, /const val FeedPostDetailBackTestTag = "feed\.detail\.back"/);
  assert.match(feedHost, /onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(feedHost, /focusedPostId != null && onBackFromFocusedPost != null[\s\S]*?QuataPostDetailChromeContent\(/);
  assert.match(feedHost, /rootTestTag = FeedPostDetailChromeTestTag/);
  assert.match(feedHost, /backTestTag = FeedPostDetailBackTestTag/);
  assert.match(androidNav, /onFocusedPostHandled = \{\}/);
  assert.match(androidNav, /onBackFromFocusedPost = \{ feedFocusedPostId = null \}/);
  assert.match(webMain, /onBackFromFocusedPost = navigation\.postId\?\.let \{ \{ navigation\.navigate\("feed"\) \} \}/);
  assert.match(iosFeed, /val onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(iosFeedRuntime, /fun publicDependencies\([\s\S]*?onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(iosFeedRuntime, /fun authenticatedDependencies\([\s\S]*?onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(iosFeedRuntime, /onBackFromFocusedPost = onBackFromFocusedPost/);
  assert.match(iosApp, /onBackFromFocusedPost: postId == nil \? nil : \{ \[weak self\] in self\?\.authenticatedHost\.showFeed\(postId: nil\) \}/);
});

test("Official focused-post mode exposes the same chrome contract", () => {
  assert.match(officialHost, /const val OfficialPostDetailChromeTestTag = "official\.detail\.chrome"/);
  assert.match(officialHost, /const val OfficialPostDetailBackTestTag = "official\.detail\.back"/);
  assert.match(officialHost, /onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(officialHost, /focusedPostId != null && onBackFromFocusedPost != null[\s\S]*?QuataPostDetailChromeContent\(/);
  assert.match(officialHost, /rootTestTag = OfficialPostDetailChromeTestTag/);
  assert.match(officialHost, /backTestTag = OfficialPostDetailBackTestTag/);
  assert.match(androidNav, /onBackFromFocusedPost = \{ officialFocusedPostId = null \}/);
  assert.match(webMain, /onBackFromFocusedPost = navigation\.officialPostId\?\.let \{ \{ navigation\.navigate\("official"\) \} \}/);
  assert.match(iosOfficial, /val onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(iosApp, /onBackFromFocusedPost: postId == nil \? nil : \{ \[weak self\] in self\?\.authenticatedHost\.showOfficial\(postId: nil\) \}/);
});
