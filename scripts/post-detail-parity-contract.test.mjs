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
const iosEvidence = await source("scripts/chat-actions-notifications-ios-evidence.mjs");
const iosUiWrapper = await source("scripts/run-ios-chat-actions-notifications-ui-test.sh");
const iosUiTest = await source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift");

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
  assert.match(feedHost, /val activeFocusedPostId = localFocusedPostId/);
  assert.match(feedHost, /val visiblePosts = activeFocusedPostId\?\.let \{ target -> state\.posts\.filter \{ post -> post\.id == target \} \} \?: state\.posts/);
  assert.match(feedHost, /activeFocusedPostId != null && onBackFromFocusedPost != null[\s\S]*?QuataPostDetailChromeContent\(/);
  assert.match(feedHost, /localFocusedPostId = null[\s\S]*?onBackFromFocusedPost\?\.invoke\(\)/);
  assert.match(feedHost, /rootTestTag = FeedPostDetailChromeTestTag/);
  assert.match(feedHost, /backTestTag = FeedPostDetailBackTestTag/);
  assert.match(androidNav, /onFocusedPostHandled = \{\}/);
  assert.match(androidNav, /onBackFromFocusedPost = \{ feedFocusedPostId = null \}/);
  assert.match(webMain, /onBackFromFocusedPost = navigation\.postId\?\.let \{ \{ navigation\.replace\("feed"\) \} \}/);
  assert.match(iosFeed, /val onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(iosFeedRuntime, /fun publicDependencies\([\s\S]*?onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(iosFeedRuntime, /fun authenticatedDependencies\([\s\S]*?onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(iosFeedRuntime, /onBackFromFocusedPost = onBackFromFocusedPost/);
  assert.match(iosApp, /func markFeedDetailClosed\(\)/);
  assert.match(iosApp, /onBackFromFocusedPost: postId == nil \? nil : \{ \[weak self\] in self\?\.authenticatedHost\.markFeedDetailClosed\(\) \}/);
});

test("Official focused-post mode exposes the same chrome contract", () => {
  assert.match(officialHost, /const val OfficialPostDetailChromeTestTag = "official\.detail\.chrome"/);
  assert.match(officialHost, /const val OfficialPostDetailBackTestTag = "official\.detail\.back"/);
  assert.match(officialHost, /onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(officialHost, /val activeFocusedPostId = localFocusedPostId/);
  assert.match(officialHost, /activeFocusedPostId != null && onBackFromFocusedPost != null[\s\S]*?QuataPostDetailChromeContent\(/);
  assert.match(officialHost, /localFocusedPostId = null[\s\S]*?onBackFromFocusedPost\?\.invoke\(\)/);
  assert.match(officialHost, /rootTestTag = OfficialPostDetailChromeTestTag/);
  assert.match(officialHost, /backTestTag = OfficialPostDetailBackTestTag/);
  assert.match(androidNav, /onBackFromFocusedPost = \{ officialFocusedPostId = null \}/);
  assert.match(webMain, /onBackFromFocusedPost = navigation\.officialPostId\?\.let \{ \{ navigation\.replace\("official"\) \} \}/);
  assert.match(iosOfficial, /val onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(iosApp, /func markOfficialDetailClosed\(\)/);
  assert.match(iosApp, /onBackFromFocusedPost: postId == nil \? nil : \{ \[weak self\] in self\?\.authenticatedHost\.markOfficialDetailClosed\(\) \}/);
});

test("iOS focal evidence has a post-detail-only stage with shared anchors", () => {
  assert.match(iosEvidence, /postDetailOnly = options\.postDetailOnly/);
  assert.match(iosEvidence, /--post-detail-only/);
  assert.match(iosEvidence, /QUATA_IOS_CHAT_POST_DETAIL_UI_E2E=\$\{postDetailOnly \? "1" : "0"\}/);
  assert.match(iosEvidence, /QUATA_IOS_CHAT_FEED_POST_BODY/);
  assert.match(iosEvidence, /QUATA_IOS_CHAT_OFFICIAL_TITLE/);
  assert.match(iosEvidence, /testFeedAndOfficialPostDetailsUseSharedChromeAndBack/);
  assert.match(iosUiWrapper, /QUATA_IOS_CHAT_POST_DETAIL_UI_E2E/);
  assert.match(iosUiWrapper, /post_detail='QuataIosUITests\/QuataIosAuthenticatedChatActionsNotificationsUITests\/testFeedAndOfficialPostDetailsUseSharedChromeAndBack'/);
  assert.match(iosUiTest, /func testFeedAndOfficialPostDetailsUseSharedChromeAndBack\(\) throws/);
  assert.match(iosUiTest, /chromeIdentifier: "feed\.detail\.chrome"/);
  assert.match(iosUiTest, /backIdentifier: "feed\.detail\.back"/);
  assert.match(iosUiTest, /chromeIdentifier: "official\.detail\.chrome"/);
  assert.match(iosUiTest, /backIdentifier: "official\.detail\.back"/);
  assert.match(iosUiTest, /ios-post-detail-feed-open/);
  assert.match(iosUiTest, /ios-post-detail-official-back/);
});
