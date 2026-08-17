import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const root = new URL("../", import.meta.url);
const source = (path) => readFileSync(new URL(path, root), "utf8");

test("shared action rail exposes stable comments anchors for Feed and Official", () => {
  const rail = source("designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataFeedActionRail.kt");
  const feed = source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedReelPostContent.kt");
  const official = source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostActionRailContent.kt");
  assert.match(rail, /actionTestTagPrefix/);
  assert.match(rail, /\$it\.comments/);
  assert.match(feed, /actionTestTagPrefix = "feed\.action"/);
  assert.match(official, /actionTestTagPrefix = "official\.action"/);
});

test("Feed and Official comments use the common emoji picker and common comment input tags", () => {
  const feed = source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt");
  const feedEvents = source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedUiEvent.kt");
  const feedViewModel = source("feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedViewModel.kt");
  const official = source("feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialCommentsPanelContent.kt");
  for (const [label, content, prefix] of [["feed", feed, "feed"], ["official", official, "official"]]) {
    assert.match(content, /CommunityEmojiPanelContent/, `${label}:picker`);
    assert.match(content, /insertAtSelection/, `${label}:insert`);
    assert.match(content, new RegExp(`${prefix}\\.comments\\.emoji`), `${label}:emoji_tag`);
    assert.match(content, new RegExp(`${prefix}\\.comments\\.input`), `${label}:input_tag`);
    assert.match(content, new RegExp(`${prefix}\\.comments\\.send`), `${label}:send_tag`);
  }
  assert.match(feedEvents, /data class FocusPost/);
  assert.match(feed, /FeedUiEvent\.FocusPost\(focusedPostId\)/);
  assert.match(feedViewModel, /repository\.refreshPost\(postId\)/);
  assert.match(feedViewModel, /feedStore\.prependIfMissing\(post\)/);
});

test("shared Feed/Official comments fixtures centralize SQL, cleanup and polling", () => {
  const fixtures = source("scripts/e2e-fixtures/chat-attachments.mjs");
  assert.match(fixtures, /export async function seedFeedOfficialCommentsFixture/);
  assert.match(fixtures, /export async function cleanupFeedOfficialCommentsFixture/);
  assert.match(fixtures, /export async function pollFeedOfficialComment/);
  assert.match(fixtures, /insert into public\.community_posts/);
  assert.match(fixtures, /insert into public\.official_posts/);
  assert.match(fixtures, /cleanup_verified_feed_official_comments_residue_absent/);
});

test("Web runner records a real Feed and Official emoji-comment flow", () => {
  const runner = source("scripts/chat-actions-notifications-web-evidence.mjs");
  assert.match(runner, /--feed-official-comments-only/);
  assert.match(runner, /options\.feedOfficialCommentsOnly/);
  assert.match(runner, /seedFeedOfficialCommentsFixture/);
  assert.match(runner, /feed\.action\.comments/);
  assert.match(runner, /official\.action\.comments/);
  assert.match(runner, /prefix: "feed\.comments"/);
  assert.match(runner, /prefix: "official\.comments"/);
  assert.match(runner, /feed_comments_emoji_created_from_ui_and_verified_by_db/);
  assert.match(runner, /official_comments_emoji_created_from_ui_and_verified_by_db/);
  assert.match(runner, /cleanup_verified_feed_official_comments_residue_absent/);
});

test("Android runner records the same Feed and Official emoji-comment flow", () => {
  const runner = source("scripts/chat-actions-notifications-android-evidence.mjs");
  const uiTest = source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt");
  assert.match(runner, /--feed-official-comments-only/);
  assert.match(runner, /seedFeedOfficialCommentsFixture/);
  assert.match(runner, /pollFeedOfficialComment/);
  assert.match(runner, /cleanup_verified_feed_official_comments_residue_absent/);
  assert.match(uiTest, /"feed-official-comments"/);
  assert.match(uiTest, /feed\.action\.comments/);
  assert.match(uiTest, /official\.action\.comments/);
  assert.match(uiTest, /feed\.comments\.emoji/);
  assert.match(uiTest, /official\.comments\.emoji/);
  assert.match(uiTest, /community\.emoji\.cell\.frequent\.0/);
});

test("iOS runner records the same Feed and Official emoji-comment flow", () => {
  const runner = source("scripts/chat-actions-notifications-ios-evidence.mjs");
  const wrapper = source("scripts/run-ios-chat-actions-notifications-ui-test.sh");
  const uiTest = source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift");
  assert.match(runner, /--feed-official-comments-only/);
  assert.match(runner, /QUATA_IOS_CHAT_FEED_OFFICIAL_COMMENTS_UI_E2E/);
  assert.match(runner, /seedFeedOfficialCommentsFixture/);
  assert.match(runner, /pollFeedOfficialComment/);
  assert.match(runner, /cleanup_verified_feed_official_comments_residue_absent/);
  assert.match(wrapper, /testFeedAndOfficialCommentsUseSharedEmojiPicker/);
  assert.match(wrapper, /QUATA_IOS_CHAT_FEED_COMMENTS_POST_ID/);
  assert.match(wrapper, /QUATA_IOS_CHAT_OFFICIAL_COMMENTS_POST_ID/);
  assert.match(uiTest, /func testFeedAndOfficialCommentsUseSharedEmojiPicker/);
  assert.match(uiTest, /feed\.action\.comments/);
  assert.match(uiTest, /official\.action\.comments/);
  assert.match(uiTest, /feed\.comments\.emoji/);
  assert.match(uiTest, /official\.comments\.emoji/);
  assert.match(uiTest, /community\.emoji\.cell\.frequent\.0/);
});
