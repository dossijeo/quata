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
const feedReelPost = await source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedReelPostContent.kt");
const feedAuthor = await source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/ReelAuthorContent.kt");
const androidFeedScreen = await source("app/src/main/java/com/quata/feature/feed/presentation/FeedScreen.kt");
const webFeedAvatar = await source("web/src/wasmJsMain/kotlin/com/quata/web/BrowserFeedAvatarContent.kt");
const iosFeedAvatar = await source("feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/IosFeedAvatarContent.kt");
const officialHost = await source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt");
const officialAuthor = await source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialAuthorHeaderContent.kt");
const officialDetailPanel = await source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostDetailPanelContent.kt");
const officialText = await source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostTextContent.kt");
const androidNav = await source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt");
const webMain = await source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt");
const iosApp = await source("iosApp/iosApp/QuataIosApp.swift");
const iosFeed = await source("feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/QuataFeedViewController.kt");
const iosFeedRuntime = await source("feature/feed/src/iosMain/kotlin/com/quata/feature/feed/presentation/IosFeedRuntimeBootstrap.kt");
const iosOfficial = await source("feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/QuataOfficialViewController.kt");
const iosEvidence = await source("scripts/chat-actions-notifications-ios-evidence.mjs");
const iosUiWrapper = await source("scripts/run-ios-chat-actions-notifications-ui-test.sh");
const iosUiTest = await source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift");
const fixture = await source("scripts/e2e-fixtures/chat-attachments.mjs");
const webEvidence = await source("scripts/post-detail-web-evidence.mjs");
const androidEvidence = await source("scripts/chat-actions-notifications-android-evidence.mjs");
const androidUiTest = await source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt");

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

test("Feed author profile entry is owned by the common reel row", () => {
  assert.match(feedReelPost, /onOpenAuthorProfile: \(\) -> Unit = \{\}/);
  assert.match(feedReelPost, /authorProfileTestTag = feedAuthorAvatarTestTag\(post\.author\.id\)/);
  assert.match(feedReelPost, /onOpenAuthorProfile = onOpenAuthorProfile/);
  assert.match(feedAuthor, /authorProfileTestTag: String\? = null/);
  assert.match(feedAuthor, /onOpenAuthorProfile: \(\(\) -> Unit\)\? = null/);
  assert.match(feedAuthor, /\.testTag\(it\)\.semantics \{ contentDescription = it \}/);
  assert.match(feedAuthor, /Modifier\.clickable\(onClick = it\)/);
  assert.match(feedHost, /onOpenAuthorProfile = \{ onOpenUserProfile\(post\.author\.id\) \}/);
  assert.doesNotMatch(androidFeedScreen, /ClickableProfileAvatar\(/);
  assert.doesNotMatch(androidFeedScreen, /avatar = \{ post ->[\s\S]{0,700}feedAuthorAvatarTestTag\(post\.author\.id\)/);
  assert.doesNotMatch(webFeedAvatar, /feedAuthorAvatarTestTag\(post\.author\.id\)/);
  assert.doesNotMatch(webFeedAvatar, /\.clickable\(/);
  assert.doesNotMatch(iosFeedAvatar, /feedAuthorAvatarTestTag\(post\.author\.id\)/);
  assert.doesNotMatch(iosFeedAvatar, /\.clickable\(/);
});

test("Official focused-post mode exposes the same chrome contract", () => {
  assert.match(officialHost, /const val OfficialPostDetailChromeTestTag = "official\.detail\.chrome"/);
  assert.match(officialHost, /const val OfficialPostDetailBackTestTag = "official\.detail\.back"/);
  assert.match(officialHost, /onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(officialHost, /val activeFocusedPostId = localFocusedPostId/);
  assert.match(officialHost, /activeFocusedPostId != null && onBackFromFocusedPost != null[\s\S]*?QuataPostDetailChromeContent\(/);
  assert.match(officialHost, /localFocusedPostId = null[\s\S]*?onBackFromFocusedPost\?\.invoke\(\)/);
  assert.match(officialHost, /LaunchedEffect\(detailPost\?\.id, detailPost\?\.title, detailPost\?\.summary, detailPost\?\.contentPlain, detailPost\?\.linkUrl\)/);
  assert.match(officialHost, /rootTestTag = OfficialPostDetailChromeTestTag/);
  assert.match(officialHost, /backTestTag = OfficialPostDetailBackTestTag/);
  assert.match(androidNav, /onBackFromFocusedPost = \{ officialFocusedPostId = null \}/);
  assert.match(webMain, /onBackFromFocusedPost = navigation\.officialPostId\?\.let \{ \{ navigation\.replace\("official"\) \} \}/);
  assert.match(iosOfficial, /val onBackFromFocusedPost: \(\(\) -> Unit\)\? = null/);
  assert.match(iosApp, /func markOfficialDetailClosed\(\)/);
  assert.match(iosApp, /onBackFromFocusedPost: postId == nil \? nil : \{ \[weak self\] in self\?\.authenticatedHost\.markOfficialDetailClosed\(\) \}/);
});

test("Official author profile entry is owned by the common author header", () => {
  assert.match(officialAuthor, /authorProfileTestTag: String\? = null/);
  assert.match(officialAuthor, /onOpenAuthorProfile: \(\(\) -> Unit\)\? = null/);
  assert.match(officialAuthor, /\.testTag\(it\)\.semantics \{ contentDescription = it \}/);
  assert.match(officialAuthor, /Modifier\.clickable\(onClick = it\)/);
  assert.match(officialHost, /authorProfileTestTag = officialAuthorAvatarTestTag\(post\.author\.id\)/);
  assert.match(officialHost, /onOpenAuthorProfile = \{ onOpenUserProfile\(post\.author\.id\) \}/);
  assert.doesNotMatch(officialHost, /modifier = authorModifier[\s\S]{0,180}\.clickable \{ onOpenUserProfile\(post\.author\.id\) \}/);
});

test("Official detail panel exposes common anchors for article link media and profile actions", () => {
  assert.match(officialDetailPanel, /const val OfficialPostDetailPanelTestTag = "official\.detail\.panel"/);
  assert.match(officialDetailPanel, /const val OfficialPostDetailCloseTestTag = "official\.detail\.panel\.close"/);
  assert.match(officialDetailPanel, /const val OfficialPostDetailArticleTestTag = "official\.detail\.article"/);
  assert.match(officialDetailPanel, /const val OfficialPostDetailMediaTestTag = "official\.detail\.media"/);
  assert.match(officialDetailPanel, /const val OfficialPostDetailLinkTestTag = "official\.detail\.link"/);
  assert.match(officialDetailPanel, /const val OfficialPostDetailProfileTestTag = "official\.detail\.profile"/);
  assert.match(officialDetailPanel, /\.testTag\(OfficialPostDetailPanelTestTag\)/);
  assert.match(officialDetailPanel, /\.testTag\(OfficialPostDetailArticleTestTag\)/);
  assert.match(officialDetailPanel, /\.testTag\(OfficialPostDetailLinkTestTag\)/);
  assert.match(officialDetailPanel, /\.testTag\(OfficialPostDetailProfileTestTag\)/);
  assert.match(officialText, /fun officialPostReadMoreTestTag\(postId: String\): String = "official\.detail\.read-more\.\$postId"/);
  assert.match(officialText, /\.testTag\(tag\)[\s\S]*?\.semantics \{ contentDescription = tag \}/);
});

test("post-detail evidence exercises Official article link and profile routes on all platforms", () => {
  assert.match(fixture, /article: `Detalle ampliado reversible \$\{marker\}`/);
  assert.match(fixture, /linkUrl: `https:\/\/example\.com\/quata-post-detail\/\$\{marker\.slice\(-18\)\}`/);
  assert.match(fixture, /content_html[\s\S]*?\$\{fixture\.official\.article\}/);
  assert.match(fixture, /link_url[\s\S]*?fixture\.official\.linkUrl/);

  for (const source of [webEvidence, androidUiTest, iosUiTest]) {
    assert.match(source, /official\.detail\.read-more/);
    assert.match(source, /official\.detail\.panel/);
    assert.match(source, /official\.detail\.article/);
    assert.match(source, /official\.detail\.link/);
    assert.match(source, /official\.detail\.profile/);
  }

  assert.match(webEvidence, /official_detail_article_marker_missing/);
  assert.match(webEvidence, /official_detail_link_marker_missing/);
  assert.match(webEvidence, /waitForAttributeContains/);
  assert.match(webEvidence, /scrollPattern = \[0, -700, -700, -700, 1400, 700, 700, -1400\]/);
  assert.match(webEvidence, /locator\.click\(\{ timeout: 2_000 \}\)/);
  assert.match(webEvidence, /openRoute\(page, origin, `official-\$\{encodeURIComponent\(state\.official\.postId\)\}`/);
  assert.match(webEvidence, /data-quata-official-detail-article/);
  assert.match(webEvidence, /data-quata-official-detail-link/);
  assert.match(webEvidence, /officialArticleVisibleInAccessibility/);
  assert.match(webEvidence, /officialLinkVisibleInAccessibility/);
  assert.match(webEvidence, /data-quata-member-profile-id/);
  assert.match(androidUiTest, /officialArticle/);
  assert.match(androidUiTest, /officialLink/);
  assert.match(androidUiTest, /public-profile\.user\.\$officialProfileId/);
  assert.match(iosEvidence, /QUATA_IOS_CHAT_OFFICIAL_ARTICLE/);
  assert.match(iosEvidence, /QUATA_IOS_CHAT_OFFICIAL_LINK/);
  assert.match(iosUiWrapper, /QUATA_IOS_CHAT_OFFICIAL_ARTICLE/);
  assert.match(iosUiWrapper, /QUATA_IOS_CHAT_OFFICIAL_LINK/);
  assert.match(iosUiTest, /assertOfficialPostDetailPanel/);
  assert.match(iosUiTest, /public-profile\.user\.\\\(peerProfileId\)/);
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
